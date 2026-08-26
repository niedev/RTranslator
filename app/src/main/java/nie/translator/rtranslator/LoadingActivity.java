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

import java.io.File;
import java.util.ArrayList;
import nie.translator.rtranslator.access.AccessActivity;
import nie.translator.rtranslator.downloader2.DownloadGroupInfo;
import nie.translator.rtranslator.downloader2.DownloadInfo;
import nie.translator.rtranslator.downloader2.DownloadManager;
import nie.translator.rtranslator.settings.SettingsActivity;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.DownloaderTools;
import nie.translator.rtranslator.tools.ErrorCodes;
import nie.translator.rtranslator.tools.ImageActivity;
import nie.translator.rtranslator.voice_translation.VoiceTranslationActivity;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApi;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;

import androidx.core.splashscreen.SplashScreen;


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
        String previousActivity = getIntent().getStringExtra("activity");
        SplashScreen splashScreen = null;
        if(previousActivity == null) {  //if this activity is called by another activity (instead of on launch), we don't use the splash screen
            // Handle the splash screen transition (it must remain before the super.onCreate() call).
            splashScreen = SplashScreen.installSplashScreen(this);
        }
        super.onCreate(savedInstanceState);
        if(splashScreen == null){
            setTheme(R.style.Theme_Speech);
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
    }

    public void onResume() {
        super.onResume();
        isVisible = true;
        global = (Global) getApplication();
        if (isFirstStart()) {
            startAccessActivity();
        } else if (global.getTranslator() != null && (Global.ONLY_TEXT_TRANSLATION_MODE || global.getSpeechRecognizer() != null)) {
            if(checkIfResourcesPreferencesRespectDownloads()) {
                startVoiceTranslationActivity();
            }else{
                startSettingsActivityWithModelManager();
            }
        } else {
            initializeApp(false);
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
                if(!DownloadManager.getSavedDownloadStatus(this).contains(initialDownloadInfo)) {
                    for (DownloadInfo download : initialDownloadInfo.downloadsInfo) {
                        File file = new File(download.getDestinationCompletePath());
                        if (file.exists()) {
                            download.setDownloadCompleted(true);
                            if(download.shouldUnzip()) download.setUnzipped(true);
                            if(download.shouldTestIntegrity()) download.setIntegrityTested(true);
                        }
                    }
                }
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

    private void initializeApp(boolean ignoreTTSError) {
        Log.i("app", "App initialization");
        adaptResourcesPreferencesToDownloads();
        global.getLanguagesAndCheckTTS(false, ignoreTTSError, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> result) {
                global.initializeTranslator(new Translator.GeneralListener() {
                    @Override
                    public void onSuccess() {
                        NeuralNetworkApi.InitListener speechRecognizerInitListener = new NeuralNetworkApi.InitListener() {
                            @Override
                            public void onInitializationFinished() {
                                global.setModelsLoaded(true);
                                if (isVisible) {
                                    startVoiceTranslationActivity();
                                }
                            }

                            @Override
                            public void onError(int[] reasons, long value) {
                                global.deleteSpeechRecognizer();  //we do this to ensure the restart of the loading of models when the app is restarted
                                LoadingActivity.this.onFailure(reasons, value);
                            }
                        };
                        if(Global.ONLY_TEXT_TRANSLATION_MODE) {
                            speechRecognizerInitListener.onInitializationFinished();
                        }else{
                            global.initializeSpeechRecognizer(speechRecognizerInitListener);
                        }
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        global.deleteTranslator();   //we do this to ensure the restart of the loading of models when the app is restarted
                        LoadingActivity.this.onFailure(reasons, value);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                LoadingActivity.this.onFailure(reasons, value);
            }
        });
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
        intent.putExtra("startWithModelManager", true);
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

    private void notifyGoogleTTSErrorDialog() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                showGoogleTTSErrorDialog(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        initializeApp(true);
                    }
                });
            }
        });
    }

    public void notifyInternetLack() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isVisible) {
                    // creation of the dialog.
                    AlertDialog.Builder builder = new AlertDialog.Builder(LoadingActivity.this);
                    //builder.setCancelable(true);
                    builder.setMessage(R.string.error_internet_lack_loading);
                    builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
                    builder.setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            initializeApp(false);
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                }
            }
        });
    }

    public void notifyModelsLoadingError() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isVisible) {
                    // creation of the dialog.
                    AlertDialog.Builder builder = new AlertDialog.Builder(LoadingActivity.this);
                    //builder.setCancelable(true);
                    builder.setMessage(R.string.error_models_loading);
                    builder.setPositiveButton(R.string.fix, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if(global != null){
                                restartDownload();
                            }
                        }
                    });
                    builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                }
            }
        });
    }

    private void notifyMissingGoogleTTSDialog() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isVisible) {
                    showMissingGoogleTTSDialog(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            initializeApp(true);
                        }
                    });
                }
            }
        });
    }


    private void restartDownload(){
        //we reset all the download shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor;
        editor = sharedPreferences.edit();
        editor.putLong("currentDownloadId", -1);
        editor.apply();
        editor = sharedPreferences.edit();
        editor.putString("lastDownloadSuccess", "");
        editor.apply();
        editor = sharedPreferences.edit();
        editor.putString("lastTransferSuccess", "");
        editor.apply();
        editor = sharedPreferences.edit();
        editor.putString("lastTransferFailure", "");
        editor.apply();
        //we restart the download (only the corrupted files will be re-downloaded)
        global.setFirstStart(true);
        Intent intent = new Intent(LoadingActivity.this, AccessActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void onFailure(int[] reasons, long value) {
        for (int aReason : reasons) {
            switch (aReason) {
                case ErrorCodes.ERROR_LOADING_MODEL:
                    showingError = true;
                    notifyModelsLoadingError();
                    break;
                case ErrorCodes.SAFETY_NET_EXCEPTION:
                case ErrorCodes.MISSED_CONNECTION:
                    showingError = true;
                    notifyInternetLack();
                    break;
                case ErrorCodes.MISSING_GOOGLE_TTS:
                    showingError = true;
                    notifyMissingGoogleTTSDialog();
                    break;
                case ErrorCodes.GOOGLE_TTS_ERROR:
                    showingError = true;
                    notifyGoogleTTSErrorDialog();
                    break;
                default:
                    onError(aReason, value);
                    break;
            }
        }
    }
}
