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

package nie.translator.rtranslator.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

public class TTS {
    //object
    private TextToSpeech tts;

    //Attributes used for getting the supported languages
    private static Thread getSupportedLanguageThread;
    private static ArrayDeque<SupportedLanguagesListener> supportedLanguagesListeners = new ArrayDeque<>();
    private static final Object lock = new Object();
    private static final ArrayList<CustomLocale> ttsLanguages = new ArrayList<>();


    public TTS(Context context, final InitListener listener) {
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    /*List<EngineInfo> engineInfos=TTS.this.getEngines();   //method 1
                    boolean found=false;
                    for (int i=0;i<engineInfos.size() && !found;i++){
                        if(engineInfos.get(i).name.equals("com.google.android.tts")){
                            found=true;
                        }
                    }*/

                    /*boolean found = false;*/    //method 2
                    if (tts != null) {
                        /*ArrayList<TextToSpeech.EngineInfo> engines = new ArrayList<>(tts.getEngines());
                        for (int i = 0; i < engines.size() && !found; i++) {
                            switch (engines.get(i).name) {
                                case "com.google.android.tts":
                                    found = true; // Check Google TTS here.
                                    break;
                                case "com.samsung.SMT": // Check Samsung TTS here.
                                    found = true;
                                    break;
                                case "com.huawei.hiai": // Check Huawei TTS here.
                                    found = true;
                                    break;
                            }
                        } // Look forward to supporting more TTS engine.
                        if (!found) {
                            tts = null;
                            listener.onError(ErrorCodes.MISSING_GOOGLE_TTS);
                        } else {
                            listener.onInit();
                        }
                        return;*/
                        listener.onInit();
                        return; // Set TTS to the default TTS directly.
                    }
                }
                tts = null;
                listener.onError(ErrorCodes.GOOGLE_TTS_ERROR);
            }
        },
        null);// use default TTS when this is null
    }

    public boolean isActive() {
        return tts != null;
    }

    public int speak(CharSequence text, int queueMode, Bundle params, String utteranceId) {
        if (isActive()) {
            return tts.speak(text, queueMode, params, utteranceId);
        }
        return TextToSpeech.ERROR;
    }

    @Nullable
    public Voice getVoice() {
        if (isActive()) {
            return tts.getVoice();
        }
        return null;
    }

    @Nullable
    public Set<Voice> getVoices() {
        if (isActive()) {
            return tts.getVoices();
        }
        return null;
    }

    public int setOnUtteranceProgressListener(UtteranceProgressListener listener) {
        if (isActive()) {
            return tts.setOnUtteranceProgressListener(listener);
        }
        return TextToSpeech.ERROR;
    }

    public int setLanguage(CustomLocale loc, Context context) {
        if (isActive()) {
            return tts.setLanguage(new Locale(loc.getLocale().getLanguage()));
        }
        return TextToSpeech.ERROR;
    }

    public int stop() {
        if (isActive()) {
            return tts.stop();
        }
        return TextToSpeech.ERROR;
    }

    public void shutdown() {
        if (isActive()) {
            tts.shutdown();
        }
    }

    public static void getSupportedLanguages(Context context, SupportedLanguagesListener responseListener){
        synchronized (lock) {
            if (responseListener != null) {
                supportedLanguagesListeners.addLast(responseListener);
            }
            if (getSupportedLanguageThread == null) {
                getSupportedLanguageThread = new Thread(new GetSupportedLanguageRunnable(context, new SupportedLanguagesListener() {
                    @Override
                    public void onLanguagesListAvailable(ArrayList<CustomLocale> languages) {
                        notifyGetSupportedLanguagesSuccess(languages);
                    }
                    @Override
                    public void onError(int reason) {
                        notifyGetSupportedLanguagesFailure(reason);
                    }
                }), "getSupportedLanguagePerformer");
                getSupportedLanguageThread.start();
            }
        }
    }

    private static void notifyGetSupportedLanguagesSuccess(ArrayList<CustomLocale> languages) {
        synchronized (lock) {
            while (supportedLanguagesListeners.peekFirst() != null) {
                supportedLanguagesListeners.pollFirst().onLanguagesListAvailable(languages);
            }
            getSupportedLanguageThread = null;
        }
    }

    private static void notifyGetSupportedLanguagesFailure(final int reasons) {
        synchronized (lock) {
            while (supportedLanguagesListeners.peekFirst() != null) {
                supportedLanguagesListeners.pollFirst().onError(reasons);
            }
            getSupportedLanguageThread = null;
        }
    }

    private static class GetSupportedLanguageRunnable implements Runnable {
        private SupportedLanguagesListener responseListener;
        private Context context;
        private static TTS tempTts;
        private static android.os.Handler mainHandler;   // handler that can be used to post to the main thread



        private GetSupportedLanguageRunnable(Context context, final SupportedLanguagesListener responseListener) {
            this.responseListener = responseListener;
            this.context = context;
            mainHandler = new android.os.Handler(Looper.getMainLooper());
        }

        @Override
        public void run() {
            long time = System.currentTimeMillis();
            tempTts = new TTS((context), new TTS.InitListener() {    // tts initialization (to be improved, automatic package installation)
                @Override
                public void onInit() {
                    android.util.Log.i("performance_lang", "TTS init: " + (System.currentTimeMillis() - time) + "ms");
                    Set<Voice> set = tempTts.getVoices();
                    SharedPreferences sharedPreferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
                    boolean qualityLow = sharedPreferences.getBoolean("languagesQualityLow", false);
                    int quality;
                    if (qualityLow) {
                        quality = Voice.QUALITY_VERY_LOW;
                    } else {
                        quality = Voice.QUALITY_NORMAL;
                    }
                    boolean foundLanguage = false; // if there is available languages
                    if (set != null) {
                        // we filter the languages that have a tts that reflects the quality characteristics we want
                        for (Voice aSet : set) {
                            if (aSet.getQuality() >= quality && (qualityLow || !aSet.getFeatures().contains("legacySetLanguageVoice"))) {
                                CustomLocale language;
                                if(aSet.getLocale() != null){
                                    language = new CustomLocale(aSet.getLocale()); // Use .getLocale() for google
                                    foundLanguage = true;
                                }else{
                                    language = CustomLocale.getInstance(aSet.getName()); // Use .getName() for samsung/huawei (maybe others also)
                                    foundLanguage = true;
                                }

                                ttsLanguages.add(language);
                            }
                        }
                    }
                    if (foundLanguage) {    // start TTS if the above lines find at least 1 supported language
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                responseListener.onLanguagesListAvailable(ttsLanguages);
                            }
                        });
                    } else {
                        onError(ErrorCodes.GOOGLE_TTS_ERROR);
                    }
                    tempTts.stop();
                    tempTts.shutdown();
                }

                @Override
                public void onError(final int reason) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            responseListener.onError(reason);
                        }
                    });
                }
            });
        }
    }

    public interface InitListener {
        void onInit();

        void onError(int reason);
    }

    public interface SupportedLanguagesListener {
        void onLanguagesListAvailable(ArrayList<CustomLocale> languages);
        void onError(int reason);
    }
}
