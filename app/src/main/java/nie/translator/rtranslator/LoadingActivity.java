/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.rtranslator;

import static nie.translator.rtranslator.tools.DownloaderTools.checkMozillaModelsPresence;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.splashscreen.SplashScreen;

import java.io.File;
import java.util.ArrayList;

import nie.translator.rtranslator.access.AccessActivity;
import nie.translator.rtranslator.downloader.DownloadGroupInfo;
import nie.translator.rtranslator.downloader.DownloadInfo;
import nie.translator.rtranslator.downloader.DownloadManager;
import nie.translator.rtranslator.settings.SettingsActivity;
import nie.translator.rtranslator.settings.SettingsFragment;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.DownloaderTools;
import nie.translator.rtranslator.tools.ErrorCodes;
import nie.translator.rtranslator.tools.ImageActivity;
import nie.translator.rtranslator.voice_translation.VoiceTranslationActivity;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApi;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;


public class LoadingActivity extends GeneralActivity {
    private final boolean START_IMAGE = false;
    private Handler mainHandler;
    private boolean isVisible = false;
    private Global global;
    private boolean startingActivity = false;
    private boolean showingError = false;

    public LoadingActivity() {
        // Required empty public constructor
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        String previousActivity = intent.getStringExtra("activity");
        boolean fromWidget = intent.getBooleanExtra("from_widget", false);
        SplashScreen splashScreen = null;
        if(previousActivity == null && !fromWidget) {  //if this activity is called by another activity or from the widget (instead of on launch), we don't use the splash screen
            // Handle the splash screen transition (it must remain before the super.onCreate() call).
            splashScreen = SplashScreen.installSplashScreen(this);
        }
        super.onCreate(savedInstanceState);
        if(splashScreen == null){
            setTheme(R.style.Theme_RTranslator);
        }
        setContentView(R.layout.activity_loading);
        mainHandler = new Handler(Looper.getMainLooper());

        // Keep the splash screen visible for this Activity.
        if(splashScreen != null) {
            splashScreen.setKeepOnScreenCondition(new SplashScreen.KeepOnScreenCondition() {
                @Override
                public boolean shouldKeepOnScreen() {
                    return !showingError;
                }
            });
        }
        /*super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_RTranslator);
        setContentView(R.layout.activity_loading);
        mainHandler = new Handler(Looper.getMainLooper());*/
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Update the original intent with the new one.
        // This is used when this activity is launched when it is already running.
        // If we don't set the newIntent, the new one passed in the launch is ignored.
        setIntent(intent);
    }

    public void onResume() {
        super.onResume();
        isVisible = true;
        global = (Global) getApplication();
        if (isFirstStart()) {
            startAccessActivity();
        } else if (global.isTranslatorInitializing() || global.isTranslatorInitialized()) {
            if(checkIfResourcesPreferencesRespectDownloads()) {
                startVoiceTranslationActivity();
            }else{
                startSettingsActivityWithModelManager();
            }
        } else {
            initializeApp();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isVisible = false;
    }

    private boolean isFirstStart(){
        if(!global.isFirstStart()){  //if first start == false, we check if it's the first start after an update from the version 2.0
            // we check if there are some nllb files, if so we delete those and set foundNllb = true (this confirms that it's the first start after an update from the version 2.0)
            boolean foundNllb = false;
            DownloadGroupInfo nllbDownloadInfo = global.getNllbDownloadInfo();
            for(DownloadInfo download : nllbDownloadInfo.downloadsInfo){
                File file = new File(download.getDestinationCompletePath());
                if(file.exists()){
                    if(file.delete()) {
                        foundNllb = true;
                    }
                }
            }
            // if foundNllb == true we add the initial download status to the saved download status in the preferences and then we set first start to true and return true,
            // so the access activity will be started with the fragment started that will depend on the missing data.
            if(foundNllb){
                DownloadGroupInfo initialDownloadInfo = global.getInitialDownloadInfo();
                int firsIncompleteDownload = -1;
                if(!DownloadManager.getSavedDownloadStatus(this).contains(initialDownloadInfo)) {
                    for (int i = 0; i < initialDownloadInfo.downloadsInfo.length; i++) {
                        DownloadInfo download = initialDownloadInfo.downloadsInfo[i];
                        File file = new File(download.getDestinationCompletePath());
                        if (file.exists()) {
                            download.setDownloadCompleted(true);
                            if(download.shouldUnzip()) download.setUnzipped(true);
                            if(download.shouldTestIntegrity()) download.setIntegrityTested(true);
                        }else if(firsIncompleteDownload == -1){
                            firsIncompleteDownload = i;
                        }
                    }
                }
                initialDownloadInfo.setRunningDownloadIndex(firsIncompleteDownload);
                DownloaderTools.addDownloadGroupInfoPreference(this, initialDownloadInfo);
                global.setFirstStart(true);
                return true;
            }else{
                return false;
            }
        }else{
            return true;
        }
    }

    private void initializeApp() {
        Log.i("app", "App initialization");
        adaptResourcesPreferencesToDownloads();
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // if this is called before the onTranslatorInitialized, we remove the engine listener callback,
                // and we start voice translation activity
                global.setInitEngineListener(null);
                Log.i("init_load_status", "started voice translation activity from timer");
                if (isVisible) {
                    startVoiceTranslationActivity();
                }
            }
        }, 1000);
        global.setInitEngineListener(new Global.InitEngineListener() {
            @Override
            public void onTranslatorInitialized() {
                // if this is called before the postDelayed is called, we remove the postDelayed callback,
                // and we start voice translation activity
                mainHandler.removeCallbacksAndMessages(null);
                global.setInitEngineListener(null);
                Log.i("init_load_status", "started voice translation activity from engine init listener");
                if (isVisible) {
                    startVoiceTranslationActivity();
                }
            }
        });
        global.initializeEngine();
    }

    private void startAccessActivity(){
        Intent intent = new Intent(this, AccessActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void startVoiceTranslationActivity() {
        if(!START_IMAGE) {
            startingActivity = true;
            Intent intent = new Intent(LoadingActivity.this, VoiceTranslationActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Eventually we forward the start_mode value to VoiceTranslationActivity
            Intent mainIntent = getIntent();
            if(mainIntent != null && mainIntent.hasExtra("start_mode")){
                String mode = mainIntent.getStringExtra("start_mode");
                intent.putExtra("start_mode", mode);
                /* We remove the extra from the activity intent.
                * This is because otherwise, the next time we launch this Activity without an intent, this intent will be used again and again.
                * For this reason, the intent request must be executed only one time for each time it is passed from outside.
                */
                mainIntent.removeExtra("start_mode");
            }

            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }else{
            startImageActivity();
        }
    }

    private void startSettingsActivityWithModelManager(){
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("start_mode", SettingsActivity.MODEL_MANAGER);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void startImageActivity() {
        startingActivity = true;
        Intent intent = new Intent(LoadingActivity.this, ImageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private boolean checkIfResourcesPreferencesRespectDownloads(){
        long initTime = System.currentTimeMillis();
        // check of translation model preference
        DownloadManager downloadManager = new DownloadManager(this);
        int translationMode = global.getTranslationMode();
        switch (translationMode) {
            case Translator.MOZILLA:
                if(!checkMozillaModelsPresence(downloadManager)){
                    return false;
                }
                break;
            case Translator.HY_MT:
                if(!downloadManager.checkDownloadCompleted(global.getHyMtDownloadInfo())){
                    return false;
                }
                break;
            case Translator.MADLAD_CACHE:
                if(!downloadManager.checkDownloadCompleted(global.getMadladDownloadInfo())){
                    return false;
                }
                break;
        }
        // check of Mozilla for voice translation modes preference
        if(translationMode != Translator.MOZILLA && global.isUseMozillaForVoiceTranslation() && !checkMozillaModelsPresence(downloadManager)){
            return false;
        }
        // check of Tatoeba preference
        if(global.isUseTatoeba() && !downloadManager.checkDownloadCompleted(global.getTatoebaDownloadInfo())){
            return false;
        }
        android.util.Log.i("performance", "checkIfResourcesPreferencesRespectDownloads done in: " + (System.currentTimeMillis() - initTime) + "ms");
        return true;
    }

    /**
     * This method must be called before the app initialization (initializeApp()).
     * In the case the user has deleted some resources in the ModelManagerFragment and then
     * terminated the app before applying the settings (with the relative checks).
     * When the app is restarted there can be some preferences that require some resources that
     * have been deleted. This method will restore the correct settings to match the downloaded resource.
     * <p>
     * Note: When deleting the resource the user cannot cancel all the translation models downloaded,
     * so this method can always change the preferences to make the app work (the app needs at least
     * a downloaded translation model, and nothing else)
     */
    private void adaptResourcesPreferencesToDownloads(){
        long initTime = System.currentTimeMillis();
        // eventual adaptation of translation model preference
        DownloadManager downloadManager = new DownloadManager(this);
        int translationMode = global.getTranslationMode();
        ArrayList<Integer> availableModels = new ArrayList<>();
        if(checkMozillaModelsPresence(downloadManager)){
            availableModels.add(Translator.MOZILLA);
        }
        if(downloadManager.checkDownloadCompleted(global.getHyMtDownloadInfo())){
            availableModels.add(Translator.HY_MT);
        }
        if(downloadManager.checkDownloadCompleted(global.getMadladDownloadInfo())){
            availableModels.add(Translator.MADLAD_CACHE);
        }
        if(!availableModels.contains(translationMode)){
            int newTranslationMode = -1;
            if(availableModels.contains(Translator.MOZILLA)){
                newTranslationMode = Translator.MOZILLA;
            }else if(availableModels.contains(Translator.HY_MT)){
                newTranslationMode = Translator.HY_MT;
            }else if(availableModels.contains(Translator.MADLAD_CACHE)){
                newTranslationMode = Translator.MADLAD_CACHE;
            }
            if(newTranslationMode != -1) {
                final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("selectedTranslationModel", newTranslationMode);
                editor.apply();
            }
        }
        // eventual adaptation of Mozilla for voice translation modes preference
        if(!availableModels.contains(Translator.MOZILLA)){
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("useMozillaForVoiceTranslation", false);
            editor.apply();
        }
        // eventual adaptation of Tatoeba preference
        if(!downloadManager.checkDownloadCompleted(global.getTatoebaDownloadInfo())){
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("useTatoeba", false);
            editor.apply();
        }
        android.util.Log.i("performance", "adaptResourcesPreferencesToDownloads done in: " + (System.currentTimeMillis() - initTime) + "ms");
    }
}
