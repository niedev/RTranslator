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

import android.Manifest;
import android.app.ActivityManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.konovalov.vad.silero.Vad;
import com.konovalov.vad.silero.VadSilero;
import com.konovalov.vad.silero.config.FrameSize;
import com.konovalov.vad.silero.config.Mode;
import com.konovalov.vad.silero.config.SampleRate;

import java.io.File;
import java.util.ArrayList;

import ai.onnxruntime.OrtException;
import nie.translator.rtranslator.access.AccessActivity;
import nie.translator.rtranslator.downloader2.DownloadGroupInfo;
import nie.translator.rtranslator.downloader2.DownloadInfo;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.ErrorCodes;
import nie.translator.rtranslator.tools.TTS;
import nie.translator.rtranslator.voice_translation._conversation_mode._conversation.ConversationService;
import nie.translator.rtranslator.voice_translation._conversation_mode.communication.ConversationBluetoothCommunicator;
import nie.translator.rtranslator.bluetooth.BluetoothCommunicator;
import nie.translator.rtranslator.bluetooth.Peer;
import nie.translator.rtranslator.voice_translation._conversation_mode.communication.recent_peer.RecentPeersDataManager;
import nie.translator.rtranslator.voice_translation._text_translation.TranslationFragment;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApi;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;
import nie.translator.rtranslator.voice_translation.neural_networks.voice.Recognizer;
import nie.translator.rtranslator.voice_translation.neural_networks.voice.Recorder;


public class Global extends Application implements DefaultLifecycleObserver {
    public static final boolean ONLY_TEXT_TRANSLATION_MODE = false;
    public static final boolean USE_EXTERNAL_MEMORY_FOR_RESOURCES = true;

    public static final int REQUEST_CODE_PERMISSIONS_PAIRING = 2;
    public static String[] REQUIRED_PERMISSIONS_PAIRING;
    public static final int REQUEST_CODE_PERMISSIONS_CONVERSATION = 3;
    public static final int REQUEST_CODE_PERMISSIONS_WALKIETALKIE = 4;
    public static final String[] REQUIRED_PERMISSIONS_VOICE = new String[]{
            Manifest.permission.RECORD_AUDIO,
    };

    public enum RTranslatorMode {
        TEXT_TRANSLATION_MODE,
        WALKIE_TALKIE_MODE,
        CONVERSATION_MODE
    }
    private ArrayList<CustomLocale> languages = new ArrayList<>();
    private ArrayList<CustomLocale> translatorLanguages = new ArrayList<>();
    private ArrayList<Translator.MozillaLanguageInfo> mozillaLanguages = new ArrayList<>();
    private ArrayList<CustomLocale> ttsLanguages = new ArrayList<>();
    private CustomLocale language;
    private CustomLocale firstLanguage;
    private CustomLocale secondLanguage;
    private CustomLocale firstTextLanguage;
    private CustomLocale secondTextLanguage;
    private RecentPeersDataManager recentPeersDataManager;
    private ConversationBluetoothCommunicator bluetoothCommunicator;
    private Translator translator;
    private Recognizer speechRecognizer;
    private String name = "";
    private int micSensitivity = -1;
    private int speechTimeout = -1;
    private int prevVoiceDuration = -1;
    private int beamSize = -1;
    private int amplitudeThreshold = Recorder.DEFAULT_AMPLITUDE_THRESHOLD;
    private boolean isForeground = false;
    @Nullable
    private AccessActivity accessActivity;
    private Handler mainHandler;
    private static Handler mHandler = new Handler();
    private final Object lock = new Object();
    private boolean useTatoeba;
    private boolean useTranslationDictionaries;
    private boolean useMozillaForVoiceTranslation;
    private boolean whisperReducedRam;
    private int translationMode;
    private VadSilero vad;
    private boolean modelsLoaded = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        recentPeersDataManager = new RecentPeersDataManager(this);
        //initializeBluetoothCommunicator();
        getMicSensitivity();
        createNotificationChannel();
        SharedPreferences sharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE);
        useTatoeba = sharedPreferences.getBoolean("useTatoeba", false);
        useTranslationDictionaries = sharedPreferences.getBoolean("useTranslationDictionaries", false);
        useMozillaForVoiceTranslation = sharedPreferences.getBoolean("useMozillaForVoiceTranslation", false);
        whisperReducedRam = sharedPreferences.getBoolean("whisperReducedRam", getTotalRamSize() <= 7000);
        translationMode = sharedPreferences.getInt("selectedTranslationModel", Translator.MOZILLA);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            REQUIRED_PERMISSIONS_PAIRING = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
            };
        }else{
            REQUIRED_PERMISSIONS_PAIRING = new String[]{
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        }
    }

    public void initializeTranslator(Translator.GeneralListener initListener){
        if(translator == null) {
            SharedPreferences sharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE);
            int mode = sharedPreferences.getInt("selectedTranslationModel", Translator.MOZILLA);
            translator = new Translator(this, mode, initListener);
        }else{
            initListener.onSuccess();
        }
    }

    public void initializeSpeechRecognizer(NeuralNetworkApi.InitListener initListener){
        if(speechRecognizer == null) {
            vad = Vad.builder()
                    .setContext(this)
                    .setSampleRate(SampleRate.SAMPLE_RATE_16K)
                    .setFrameSize(FrameSize.FRAME_SIZE_512)
                    .setMode(Mode.NORMAL)
                    .setSilenceDurationMs(300)
                    .setSpeechDurationMs(50)
                    .build();
            speechRecognizer = new Recognizer(this, true, initListener);
        }else{
            initListener.onInitializationFinished();
        }
    }

    public void initializeBluetoothCommunicator(){
        if(bluetoothCommunicator == null){
            bluetoothCommunicator = new ConversationBluetoothCommunicator(this, getName(), BluetoothCommunicator.STRATEGY_P2P_WITH_RECONNECTION);
        }
    }

    public VadSilero getVad() {
        return vad;
    }

    public void restartTranslator(Translator.GeneralListener listener){
        getLanguages(RTranslatorMode.TEXT_TRANSLATION_MODE, false);
        SharedPreferences sharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE);
        int mode = sharedPreferences.getInt("selectedTranslationModel", Translator.MOZILLA);
        translator.restart(mode, new Translator.GeneralListener() {
            @Override
            public void onSuccess() {
                getTranslatorLanguages(RTranslatorMode.TEXT_TRANSLATION_MODE, false);  //refresh languages
                listener.onSuccess();
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                listener.onFailure(reasons, value);
            }
        });
    }

    @Nullable
    public ConversationBluetoothCommunicator getBluetoothCommunicator() {
        return bluetoothCommunicator;
    }

    public void resetBluetoothCommunicator() {
        bluetoothCommunicator.destroy(new BluetoothCommunicator.DestroyCallback() {
            @Override
            public void onDestroyed() {
                bluetoothCommunicator = new ConversationBluetoothCommunicator(Global.this, getName(), BluetoothCommunicator.STRATEGY_P2P_WITH_RECONNECTION);
            }
        });
    }

    public boolean areModelsLoaded() {
        return modelsLoaded;
    }

    public void setModelsLoaded(boolean areModelsLoaded) {
        this.modelsLoaded = areModelsLoaded;
    }

    public void getLanguagesAndCheckTTS(final boolean recycleResult, boolean ignoreTTSError, final GetLocalesListListener responseListener) {
        if (recycleResult && !languages.isEmpty()) {
            responseListener.onSuccess(languages);
        } else {
            TTS.getSupportedLanguages(this, new TTS.SupportedLanguagesListener() {    //we load TTS languages to catch eventual TTS errors
                @Override
                public void onLanguagesListAvailable(ArrayList<CustomLocale> ttsLanguages) {
                    responseListener.onSuccess(getLanguages(RTranslatorMode.TEXT_TRANSLATION_MODE, recycleResult));
                }

                @Override
                public void onError(int reason) {
                    responseListener.onFailure(new int[]{reason}, 0);
                }
            });
        }
    }

    public ArrayList<CustomLocale> getLanguages(RTranslatorMode rtranslatorMode, final boolean recycleResult) {
        ArrayList<CustomLocale> translatorLanguages = getTranslatorLanguages(rtranslatorMode, recycleResult);
        ArrayList<CustomLocale> speechRecognizerLanguages = Recognizer.getSupportedLanguages(Global.this);
        //we return only the languages compatible with the speech recognizer and the translator (without loading TTS languages)
        final ArrayList<CustomLocale> compatibleLanguages = new ArrayList<>();
        for (CustomLocale translatorLanguage : translatorLanguages) {
            if (CustomLocale.containsLanguage(speechRecognizerLanguages, translatorLanguage)) {
                compatibleLanguages.add(translatorLanguage);
            }
        }
        languages = compatibleLanguages;
        return compatibleLanguages;
    }

    public ArrayList<CustomLocale> getTranslatorLanguages(RTranslatorMode rtranslatorMode, final boolean recycleResult) {
        if (recycleResult) {
            if(isUseMozillaForVoiceTranslation() && (rtranslatorMode == RTranslatorMode.CONVERSATION_MODE || rtranslatorMode == RTranslatorMode.WALKIE_TALKIE_MODE)) {
                return getMozillaLanguages(true);
            }else{
                if(!translatorLanguages.isEmpty()) return translatorLanguages;
            }
        }

        // recycleResult is false
        int mode;
        if(translator != null){
            mode = translator.getMode();
        }else{
            SharedPreferences sharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE);
            mode = sharedPreferences.getInt("selectedTranslationModel", Translator.MOZILLA);
        }
        ArrayList<CustomLocale> languages = Translator.getSupportedLanguages(Global.this, mode);
        translatorLanguages = languages;
        ArrayList<CustomLocale> mozillaLanguages = getMozillaLanguages(false);
        if(isUseMozillaForVoiceTranslation() && (rtranslatorMode == RTranslatorMode.CONVERSATION_MODE || rtranslatorMode == RTranslatorMode.WALKIE_TALKIE_MODE)) {
            return mozillaLanguages;
        }else{
            return languages;
        }
    }

    private ArrayList<CustomLocale> getMozillaLanguages(final boolean recycleResult){
        ArrayList<Translator.MozillaLanguageInfo> mozillaLanguageInfos = getMozillaLanguagesInfo(recycleResult);
        ArrayList<CustomLocale> mozillaLanguages = new ArrayList<>(mozillaLanguageInfos.size());
        for(Translator.MozillaLanguageInfo info: mozillaLanguageInfos){
            mozillaLanguages.add(info.lang);
        }
        return mozillaLanguages;
    }

    public ArrayList<Translator.MozillaLanguageInfo> getMozillaLanguagesInfo(final boolean recycleResult) {
        if (recycleResult && !mozillaLanguages.isEmpty()) {
            return mozillaLanguages;
        } else {
            ArrayList<Translator.MozillaLanguageInfo> languages = Translator.getMozillaLanguagesInfo(this);
            mozillaLanguages = languages;
            return languages;
        }
    }

    public void getTTSLanguages(final boolean recycleResult, final GetLocalesListListener responseListener){
        if(recycleResult && !ttsLanguages.isEmpty()){
            responseListener.onSuccess(ttsLanguages);
        }else{
            TTS.getSupportedLanguages(this, new TTS.SupportedLanguagesListener() {    //we load TTS languages to catch eventual TTS errors
                @Override
                public void onLanguagesListAvailable(ArrayList<CustomLocale> ttsLanguages) {
                    Global.this.ttsLanguages = ttsLanguages;
                    responseListener.onSuccess(ttsLanguages);
                }

                @Override
                public void onError(int reason) {
                    responseListener.onSuccess(new ArrayList<>());
                }
            });
        }
    }


    public ArrayList<MozillaLanguageDownloadInfo> getMozillaLanguagesDownloadInfo(final boolean recycleResult){
        String downloadFolder = getFilesDir().getAbsolutePath()+"/Translation/Mozilla/";
        String baseUrl = "https://github.com/niedev/OnnxModelsEnhancer/releases/download/v1.0.0-beta/";
        ArrayList<MozillaLanguageDownloadInfo> languageDownloadInfos = new ArrayList<>();
        for(Translator.MozillaLanguageInfo langInfo : getMozillaLanguagesInfo(recycleResult)){
            String fileName = "Mozilla_"+langInfo.lang.getLanguage()+".zip";
            languageDownloadInfos.add(new MozillaLanguageDownloadInfo(
                    langInfo.lang,
                    new DownloadGroupInfo(new DownloadInfo[]{new DownloadInfo(fileName, baseUrl+fileName, downloadFolder, langInfo.sizeKb, false, true)})
            ));
        }
        return languageDownloadInfos;
    }

    public static class MozillaLanguageDownloadInfo {
        public CustomLocale lang;
        public DownloadGroupInfo downloadGroupInfo;

        public MozillaLanguageDownloadInfo(CustomLocale lang, DownloadGroupInfo downloadGroupInfo) {
            this.lang = lang;
            this.downloadGroupInfo = downloadGroupInfo;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if(obj instanceof CustomLocale){
                return lang.equals(obj);
            }
            return super.equals(obj);
        }
    }

    public DownloadGroupInfo getHyMtDownloadInfo(){
        String downloadFolder = getFilesDir().getAbsolutePath();
        String baseUrl = "https://github.com/niedev/OnnxModelsEnhancer/releases/download/v1.0.0-beta/";
        return new DownloadGroupInfo(new DownloadInfo[]{
                new DownloadInfo(
                        "HY-MT.zip",
                        baseUrl + "HY-MT.zip",
                        downloadFolder + "/Translation/",
                        1790000,
                        true,
                        true)
        });
    }

    public DownloadGroupInfo getMadladDownloadInfo(){
        String downloadFolder = getFilesDir().getAbsolutePath();
        String baseUrl = "https://github.com/niedev/OnnxModelsEnhancer/releases/download/v1.0.0-beta/";
        return new DownloadGroupInfo(new DownloadInfo[]{
                new DownloadInfo(
                        "Madlad.zip",
                        baseUrl + "Madlad.zip",
                        downloadFolder + "/Translation/",
                        1380000,
                        true,
                        true)

        });
    }

    public DownloadGroupInfo getTatoebaDownloadInfo(){
        String downloadFolder = getFilesDir().getAbsolutePath();
        String baseUrl = "https://github.com/niedev/OnnxModelsEnhancer/releases/download/v1.0.0-beta/";
        return new DownloadGroupInfo(new DownloadInfo[]{
                new DownloadInfo(
                        "Tatoeba.zip",
                        baseUrl + "Tatoeba.zip",
                        downloadFolder + "/Translation/",
                        596000,
                        false,
                        true)

        });
    }

    public DownloadGroupInfo getInitialDownloadInfo(){
        String downloadFolder = getFilesDir().getAbsolutePath();
        String baseUrl = "https://github.com/niedev/RTranslator/releases/download/2.0.0/";
        String baseUrlAlt = "https://github.com/niedev/OnnxModelsEnhancer/releases/download/v1.0.0-beta/";
        return new DownloadGroupInfo(new DownloadInfo[]{
                /*new DownloadInfoExtended(
                        "NLLB_cache_initializer.onnx",
                        baseUrl + "NLLB_cache_initializer.onnx",
                        downloadFolder,
                        24000,
                        true,
                        false
                ),
                new DownloadInfoExtended(
                        "NLLB_decoder.onnx",
                        baseUrl + "NLLB_decoder.onnx",
                        downloadFolder,
                        171000,
                        true,
                        false
                ),
                new DownloadInfoExtended(
                        "NLLB_embed_and_lm_head.onnx",
                        baseUrl + "NLLB_embed_and_lm_head.onnx",
                        downloadFolder,
                        500000,
                        true,
                        false
                ),
                new DownloadInfoExtended(
                        "NLLB_encoder.onnx",
                        baseUrl + "NLLB_encoder.onnx",
                        downloadFolder,
                        254000,
                        true,
                        false
                ),*/
                new DownloadInfo(
                        "Whisper_cache_initializer.onnx",
                        baseUrl + "Whisper_cache_initializer.onnx",
                        downloadFolder,
                        14000,
                        true,
                        false
                ),
                new DownloadInfo(
                        "Whisper_cache_initializer_batch.onnx",
                        baseUrl + "Whisper_cache_initializer_batch.onnx",
                        downloadFolder,
                        14000,
                        true,
                        false
                ),
                new DownloadInfo(
                        "Whisper_decoder.onnx",
                        baseUrl + "Whisper_decoder.onnx",
                        downloadFolder,
                        173000,
                        true,
                        false
                ),
                new DownloadInfo(
                        "Whisper_detokenizer.onnx",
                        baseUrl + "Whisper_detokenizer.onnx",
                        downloadFolder,
                        461,
                        true,
                        false
                ),
                new DownloadInfo(
                        "Whisper_encoder.onnx",
                        baseUrl + "Whisper_encoder.onnx",
                        downloadFolder,
                        88000,
                        true,
                        false
                ),
                new DownloadInfo(
                        "Whisper_initializer.onnx",
                        baseUrl + "Whisper_initializer.onnx",
                        downloadFolder,
                        69,
                        true,
                        false
                ),
                new DownloadInfo(
                        "TranslationDictionaries.zip",
                        baseUrlAlt + "TranslationDictionaries.zip",
                        downloadFolder + "/Translation/",
                        108000,
                        false,
                        true
                ),
        });
    }


    public Translator getTranslator() {
        return translator;
    }

    public void deleteTranslator(){
        translator = null;
    }

    public Recognizer getSpeechRecognizer() {
        return speechRecognizer;
    }

    public void deleteSpeechRecognizer(){
        speechRecognizer = null;
    }

    public boolean isForeground() {
        return isForeground;
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        DefaultLifecycleObserver.super.onStop(owner);
        //App in background
        isForeground = false;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        DefaultLifecycleObserver.super.onStart(owner);
        // App in foreground
        isForeground = true;
    }

    @Nullable
    public AccessActivity getRunningAccessActivity() {
        return accessActivity;
    }

    public void setAccessActivity(@Nullable AccessActivity accessActivity) {
        this.accessActivity = accessActivity;
    }

    public interface GetLocalesListListener {
        void onSuccess(ArrayList<CustomLocale> result);

        void onFailure(int[] reasons, long value);
    }

    public CustomLocale getLanguage(final boolean recycleResult) {
        ArrayList<CustomLocale> languages = getLanguages(RTranslatorMode.CONVERSATION_MODE, true);
        CustomLocale predefinedLanguage = CustomLocale.getDefault();
        CustomLocale language = null;
        if (recycleResult && Global.this.language != null) {
            language = Global.this.language;
        } else {
            SharedPreferences sharedPreferences = Global.this.getSharedPreferences("default", Context.MODE_PRIVATE);
            String code = sharedPreferences.getString("language", predefinedLanguage.getCode());
            if (code != null) {
                language = CustomLocale.getInstance(code);
            }
        }

        int index = CustomLocale.search(languages, language);
        if (index != -1) {
            language = languages.get(index);
        } else {
            int index2 = CustomLocale.search(languages, predefinedLanguage);
            if (index2 != -1) {
                language = predefinedLanguage;
            } else {
                language = new CustomLocale("en");
            }
        }

        Global.this.language = language;
        return language;
    }

    public CustomLocale getFirstLanguage(final boolean recycleResult) {
        final ArrayList<CustomLocale> languages = getLanguages(RTranslatorMode.WALKIE_TALKIE_MODE, true);
        CustomLocale predefinedLanguage = getLanguage(true);
        CustomLocale language = null;
        if (recycleResult && Global.this.firstLanguage != null) {
            language = Global.this.firstLanguage;
        } else {
            SharedPreferences sharedPreferences = Global.this.getSharedPreferences("default", Context.MODE_PRIVATE);
            String code = sharedPreferences.getString("firstLanguage", predefinedLanguage.getCode());
            if (code != null) {
                language = CustomLocale.getInstance(code);
            }
        }

        int index = CustomLocale.search(languages, language);
        if (index != -1) {
            language = languages.get(index);
        } else {
            int index2 = CustomLocale.search(languages, predefinedLanguage);
            if (index2 != -1) {
                language = predefinedLanguage;
            } else {
                language = new CustomLocale("en");
            }
        }

        Global.this.firstLanguage = language;
        return language;
    }

    public CustomLocale getSecondLanguage(final boolean recycleResult) {
        ArrayList<CustomLocale> languages = getLanguages(RTranslatorMode.WALKIE_TALKIE_MODE, true);
        CustomLocale predefinedLanguage = CustomLocale.getDefault();
        CustomLocale language = null;
        if (recycleResult && Global.this.secondLanguage != null) {
            language = Global.this.secondLanguage;
        } else {
            SharedPreferences sharedPreferences = Global.this.getSharedPreferences("default", Context.MODE_PRIVATE);
            String code = sharedPreferences.getString("secondLanguage", null);
            if (code != null) {
                language = CustomLocale.getInstance(code);
            }
        }

        int index = CustomLocale.search(languages, language);
        if (index != -1) {
            language = languages.get(index);
        } else {
            language = new CustomLocale("en");
        }

        Global.this.secondLanguage = language;
        return language;
    }

    public CustomLocale getFirstTextLanguage(final boolean recycleResult) {
        final ArrayList<CustomLocale> languages = getTranslatorLanguages(RTranslatorMode.TEXT_TRANSLATION_MODE, true);
        CustomLocale predefinedLanguage = getLanguage(true);
        CustomLocale language = null;
        if (recycleResult && Global.this.firstTextLanguage != null) {
            language = Global.this.firstTextLanguage;
        } else {
            SharedPreferences sharedPreferences = Global.this.getSharedPreferences("default", Context.MODE_PRIVATE);
            String code = sharedPreferences.getString("firstTextLanguage", predefinedLanguage.getCode());
            if (code != null) {
                language = CustomLocale.getInstance(code);
            }
        }

        int index = CustomLocale.search(languages, language);
        if (index != -1) {
            language = languages.get(index);
        } else {
            int index2 = CustomLocale.search(languages, predefinedLanguage);
            if (index2 != -1) {
                language = predefinedLanguage;
            } else {
                language = new CustomLocale("en");
            }
        }

        Global.this.firstTextLanguage = language;
        return language;
    }

    public CustomLocale getSecondTextLanguage(final boolean recycleResult) {
        ArrayList<CustomLocale> languages = getTranslatorLanguages(RTranslatorMode.TEXT_TRANSLATION_MODE, true);
        CustomLocale language = null;
        if (recycleResult && Global.this.secondTextLanguage != null) {
            language = Global.this.secondTextLanguage;
        } else {
            SharedPreferences sharedPreferences = Global.this.getSharedPreferences("default", Context.MODE_PRIVATE);
            String code = sharedPreferences.getString("secondTextLanguage", null);
            if (code != null) {
                language = CustomLocale.getInstance(code);
            }
        }

        int index = CustomLocale.search(languages, language);
        if (index != -1) {
            language = languages.get(index);
        } else {
            language = new CustomLocale("en");
        }

        Global.this.secondTextLanguage = language;
        return language;
    }

    public interface GetLocaleListener {
        void onSuccess(CustomLocale result);

        void onFailure(int[] reasons, long value);
    }

    public interface GetTwoLocaleListener {
        void onSuccess(CustomLocale language1, CustomLocale language2);

        void onFailure(int[] reasons, long value);
    }

    public void setLanguage(CustomLocale language) {
        this.language = language;
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("language", language.getCode());
        editor.apply();
        if(ConversationService.isRunning){
            translator.loadTgtLangResourcesForConversation(language, null);
            if(getBluetoothCommunicator() != null){
                getBluetoothCommunicator().sendLanguage(language);
            }
        }
    }

    public void setFirstLanguage(CustomLocale language, @Nullable Translator.GeneralListener listener) {
        this.firstLanguage = language;
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("firstLanguage", language.getCode());
        editor.apply();
        loadLanguagesResources(language, getSecondLanguage(true), RTranslatorMode.WALKIE_TALKIE_MODE, listener);
    }

    public void setSecondLanguage(CustomLocale language, @Nullable Translator.GeneralListener listener) {
        this.secondLanguage = language;
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("secondLanguage", language.getCode());
        editor.apply();
        loadLanguagesResources(getFirstLanguage(true), language, RTranslatorMode.WALKIE_TALKIE_MODE, listener);
    }

    public void setFirstTextLanguage(CustomLocale language, @Nullable Translator.GeneralListener listener) {
        this.firstTextLanguage = language;
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("firstTextLanguage", language.getCode());
        editor.apply();
        loadLanguagesResources(language, getSecondTextLanguage(true), RTranslatorMode.TEXT_TRANSLATION_MODE, listener);
    }

    public void setSecondTextLanguage(CustomLocale language, @Nullable Translator.GeneralListener listener) {
        this.secondTextLanguage = language;
        CustomLocale firstLanguage = getFirstTextLanguage(true);
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("secondTextLanguage", language.getCode());
        editor.apply();
        loadLanguagesResources(getFirstTextLanguage(true), language, RTranslatorMode.TEXT_TRANSLATION_MODE, listener);
    }

    public void switchTextLanguages(@Nullable Translator.GeneralListener listener) {
        CustomLocale firstLanguage = getFirstTextLanguage(true);
        CustomLocale secondLanguage = getSecondTextLanguage(true);
        this.firstTextLanguage = secondLanguage;
        this.secondTextLanguage = firstLanguage;
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("firstTextLanguage", this.firstTextLanguage.getCode());
        editor.putString("secondTextLanguage", this.secondTextLanguage.getCode());
        editor.apply();
        loadLanguagesResources(secondLanguage, firstLanguage, RTranslatorMode.TEXT_TRANSLATION_MODE, listener);
    }


    public int getAmplitudeThreshold() {
        return amplitudeThreshold;
    }


    public int getMicSensitivity() {
        if (micSensitivity == -1) {
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            micSensitivity = sharedPreferences.getInt("micSensibility", 50);
            setAmplitudeThreshold(micSensitivity);
        }
        return micSensitivity;
    }

    public void setMicSensitivity(int value) {
        micSensitivity = value;
        setAmplitudeThreshold(micSensitivity);
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("micSensibility", value);
        editor.apply();
    }

    public int getSpeechTimeout() {
        if (speechTimeout == -1) {
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            speechTimeout = sharedPreferences.getInt("speechTimeout", Recorder.DEFAULT_SPEECH_TIMEOUT_MILLIS);
        }
        return speechTimeout;
    }

    public void setSpeechTimeout(int value) {
        speechTimeout = value;
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("speechTimeout", value);
        editor.apply();
    }

    public int getPrevVoiceDuration() {
        if (prevVoiceDuration == -1) {
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            prevVoiceDuration = sharedPreferences.getInt("prevVoiceDuration", Recorder.DEFAULT_PREV_VOICE_DURATION);
        }
        return prevVoiceDuration;
    }

    public void setPrevVoiceDuration(int value) {
        prevVoiceDuration = value;
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("prevVoiceDuration", value);
        editor.apply();
    }

    private void setAmplitudeThreshold(int micSensitivity) {
        float amplitudePercentage = 1f - (micSensitivity / 100f);
        if (amplitudePercentage < 0.5f) {
            amplitudeThreshold = Math.round(Recorder.MIN_AMPLITUDE_THRESHOLD + ((Recorder.DEFAULT_AMPLITUDE_THRESHOLD - Recorder.MIN_AMPLITUDE_THRESHOLD) * (amplitudePercentage * 2)));
        } else {
            amplitudeThreshold = Math.round(Recorder.DEFAULT_AMPLITUDE_THRESHOLD + ((Recorder.MAX_AMPLITUDE_THRESHOLD - Recorder.DEFAULT_AMPLITUDE_THRESHOLD) * ((amplitudePercentage - 0.5F) * 2)));
        }
    }

    public int getBeamSize() {
        if (beamSize == -1) {
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            beamSize = sharedPreferences.getInt("beamSize", TranslationFragment.DEFAULT_BEAM_SIZE);
        }
        return beamSize;
    }

    public void setBeamSize(int value) {
        beamSize = value;
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("beamSize", value);
        editor.apply();
    }

    public boolean isUseTatoeba(){
        return useTatoeba;
    }

    public void setUseTatoeba(boolean useTatoeba) {
        this.useTatoeba = useTatoeba;
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("useTatoeba", useTatoeba);
        editor.apply();
        // loading of tatoeba
        if(useTatoeba) {
            translator.loadAllTatoebaResources(null);
        }else{
            translator.unloadAllTatoebaResources();
        }
    }

    public boolean isUseTranslationDictionaries(){
        return useTranslationDictionaries;
    }

    public void setUseTranslationDictionaries(boolean useTranslationDictionaries) {
        this.useTranslationDictionaries = useTranslationDictionaries;
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("useTranslationDictionaries", useTranslationDictionaries);
        editor.apply();
        // loading or unloading of translation dictionaries resources
        if(translator != null) {
            if (useTranslationDictionaries) {
                translator.loadAllTranslationDictionariesResources(null);
            } else {
                translator.unloadAllTranslationDictionariesResources();
            }
        }
    }

    public boolean isUseMozillaForVoiceTranslation() {
        return useMozillaForVoiceTranslation;
    }

    public void setUseMozillaForVoiceTranslation(boolean useMozillaForVoiceTranslation, @Nullable Translator.GeneralListener listener) {
        if(this.useMozillaForVoiceTranslation != useMozillaForVoiceTranslation) {
            this.useMozillaForVoiceTranslation = useMozillaForVoiceTranslation;
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("useMozillaForVoiceTranslation", useMozillaForVoiceTranslation);
            editor.apply();
            // loading or unloading of mozilla resources
            if (translator != null) {
                if (useMozillaForVoiceTranslation && translator.getMode() != Translator.MOZILLA) {
                    translator.loadAllMozillaResources(listener);
                } else if (!useMozillaForVoiceTranslation && translator.getMode() != Translator.MOZILLA) {
                    translator.unloadAllMozillaResources(listener);
                }
            }
        }else{
            if(listener != null) listener.onSuccess();
        }
    }

    public boolean isWhisperReducedRam() {
        return whisperReducedRam;
    }

    public void setWhisperReducedRam(boolean whisperReducedRam, @Nullable Translator.GeneralListener listener) {
        boolean changed = this.whisperReducedRam != whisperReducedRam;
        if(changed){
            this.whisperReducedRam = whisperReducedRam;
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("whisperReducedRam", whisperReducedRam);
            editor.apply();
            // restart of encoder session with the new option
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if(speechRecognizer != null) speechRecognizer.initializeEncoderSession();
                        if(listener != null) listener.onSuccess();
                    } catch (OrtException e) {
                        e.printStackTrace();
                        if(listener != null) listener.onFailure(new int[]{ErrorCodes.ERROR}, 0);
                    }
                }
            }).start();
        }else{
            if(listener != null) listener.onSuccess();
        }
    }

    public int getTranslationMode() {
        return translationMode;
    }

    public void setTranslationMode(int translationMode, @NonNull Translator.GeneralListener listener) {
        int oldMode = this.translationMode;
        boolean changed = oldMode != translationMode;
        if(changed) {
            this.translationMode = translationMode;
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("selectedTranslationModel", translationMode);
            editor.apply();
            // restart of the translator with the new model mode
            restartTranslator(listener);
        }else{
            listener.onSuccess();
        }
    }

    public String getName() {
        if (name.length() == 0) {
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            name = sharedPreferences.getString("name", "user");
        }
        return name;
    }

    public void setName(String savedName) {
        name = savedName;
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("name", savedName);
        editor.apply();
        if(getBluetoothCommunicator() != null) {
            getBluetoothCommunicator().setName(savedName);  //we update the name also for communicator
        }
    }

    public Peer getMyPeer() {
        return new Peer(null, getName(), false);
    }

    public abstract static class MyPeerListener {
        public abstract void onSuccess(Peer myPeer);

        public void onFailure(int[] reasons, long value) {
        }
    }

    public void getMyID(final MyIDListener responseListener) {
        responseListener.onSuccess(Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID));
    }

    public abstract static class MyIDListener {
        public abstract void onSuccess(String id);

        public void onFailure(int[] reasons, long value) {
        }
    }

    public RecentPeersDataManager getRecentPeersDataManager() {
        return recentPeersDataManager;
    }

    public abstract static class ResponseListener {
        public void onSuccess() {

        }

        public void onFailure(int[] reasons, long value) {
        }
    }

    public boolean isFirstStart() {
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean("firstStart", true);
    }

    public void setFirstStart(boolean firstStart) {
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("firstStart", firstStart);
        editor.apply();
    }


    private void createNotificationChannel(){
        String channelID = "service_background_notification";
        String channelName = getResources().getString(R.string.notification_channel_name);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(channelID, channelName, NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    /**
     * Returns the total RAM size of the device in MB
     */
    public int getTotalRamSize(){
        ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        long totalMemory = memInfo.totalMem / 1000000L;
        android.util.Log.i("memory", "Total memory: " + totalMemory);
        return (int) totalMemory;
    }

    /**
     * Returns the available RAM size of the device in MB
     */
    public int getAvailableRamSize(){
        ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        long availableMemory = memInfo.availMem;
        android.util.Log.i("memory", "Total memory: " + availableMemory);
        return (int) (availableMemory / 1000000);
    }

    /**
     * Returns (in MB) the absolute maximum native RAM allocatable by forcing the OS
     * to kill all other killable processes.
     */
    public int getMaxAllocatableRAM() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return 0;

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        // Total physical RAM available to the OS (already excludes GPU-reserved RAM)
        long totalRam = memoryInfo.totalMem;

        // Android doesn't have an API that shows the max amount of RAM available for native allocations before a crash occurs,
        // so the best way (also hinted here https://developer.android.com/games/optimize/memory-allocation#conservative-memory-budgets)
        // is to use a percentage of the total RAM between 50% and 25% as a hard limit of native RAM usage for a single app.
        double maxPercentage = 0.45;

        // The absolute theoretical maximum you can allocate before the OS targets YOU
        long maxAllocatable = (long) (totalRam - (totalRam*maxPercentage)) / 1000000L;

        // Return the result, ensuring we don't return a negative number on heavily constrained devices
        return (int) Math.max(0, maxAllocatable);
    }

    public int getRamThreshold(){
        ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        long threshold = memInfo.threshold;
        return (int) (threshold / 1000000);
    }

    /**
     * Returns the available internal memory space in MB
     */
    public long getAvailableInternalMemorySize() {
        File internalFilesDir = this.getFilesDir();
        if(internalFilesDir != null) {
            long freeMBInternal = new File(internalFilesDir.getAbsoluteFile().toString()).getFreeSpace() / 1000000L;
            return freeMBInternal;
        }
        return -1;
    }

    /**
     * Returns the available external memory space in MB
     */
    public long getAvailableExternalMemorySize() {
        File externalFilesDir = this.getExternalFilesDir(null);
        if(externalFilesDir != null) {
            long freeMBExternal = new File(externalFilesDir.getAbsoluteFile().toString()).getFreeSpace() / 1000000L;
            return freeMBExternal;
        }
        return -1;
    }



    public boolean isNetworkOnWifi() {
        WifiManager wifi_m = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifi_m.isWifiEnabled()) { // if wifi is on
            WifiInfo wifi_i = wifi_m.getConnectionInfo();
            if (wifi_i.getNetworkId() == -1) {
                return false; // Not connected to any wifi device
            }
            return true; // Connected to some wifi device
        } else {
            return false; // user turned off wifi
        }
    }

    private void loadLanguagesResources(CustomLocale firstLanguage, CustomLocale secondLanguage, RTranslatorMode mode, Translator.GeneralListener listener){
        translator.loadLanguageResources(firstLanguage, secondLanguage, mode, listener);
    }
}

