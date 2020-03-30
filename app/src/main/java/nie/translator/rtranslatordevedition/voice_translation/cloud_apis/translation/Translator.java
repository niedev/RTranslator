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

package nie.translator.rtranslatordevedition.voice_translation.cloud_apis.translation;

import android.content.SharedPreferences;
import android.os.Looper;
import android.speech.tts.Voice;
import android.text.Html;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.api.services.translate.Translate;
import com.google.api.services.translate.model.DetectionsListResponse;
import com.google.api.services.translate.model.LanguagesListResponse;
import com.google.api.services.translate.model.LanguagesResource;
import com.google.api.services.translate.model.TranslationsListResponse;
import com.google.api.services.translate.model.TranslationsResource;
import com.google.auth.oauth2.AccessToken;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.tools.CustomLocale;
import nie.translator.rtranslatordevedition.tools.ErrorCodes;
import nie.translator.rtranslatordevedition.tools.TTS;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode._conversation.ConversationMessage;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.CloudApi;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.CloudApiResult;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.voice.Recognizer;


public class Translator extends CloudApi {  // separate thread for internet translation
    public static final float COST_PER_CHAR = 0.00002f;
    private Translate translator;
    private TTS tts;
    private Thread getSupportedLanguageThread;
    private ArrayDeque<SupportedLanguagesListener> supportedLanguagesListeners = new ArrayDeque<>();
    private final Object lock = new Object();
    private android.os.Handler mainHandler;   // handler that can be used to post to the main thread
    private ArrayList<CustomLocale> fullySupportedLanguages= new ArrayList<>();  // here are the languages in which country can be used as well as just the language

    public Translator(@NonNull Global global) {
        this.global = global;
        mainHandler = new android.os.Handler(Looper.getMainLooper());
        this.apiTokenListener = new Global.ApiTokenListener() {
            @Override
            public void onSuccess(AccessToken apiToken) {
                Translator.this.apiToken = apiToken;
            }

            @Override
            public void onFailure(int[] reasons, long value) {
            }
        };

        global.getApiToken(true, apiTokenListener);

        translator = new Translate.Builder(
                com.google.api.client.extensions.android.http.AndroidHttp.newCompatibleTransport()
                , com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance(), null)
                .setApplicationName("speechGoogle")
                .build();
    }

    public void translate(final String textToTranslate, final CustomLocale languageOutput, final TranslateListener responseListener) {  // what the thread does
        final Thread t = new Thread("textTranslation") {
            public void run() {
                final float cost = calculateCreditConsumption(textToTranslate.length());
                if (apiToken != null && apiToken.getExpirationTime().getTime() > System.currentTimeMillis()) {
                    performTextTranslation(textToTranslate, null, languageOutput, cost, responseListener);
                } else {
                    global.getApiToken(true, new Global.ApiTokenListener() {
                        @Override
                        public void onSuccess(AccessToken apiToken) {
                            apiTokenListener.onSuccess(apiToken);
                            performTextTranslation(textToTranslate, null, languageOutput, cost, responseListener);
                        }

                        @Override
                        public void onFailure(final int[] reasons, final long value) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    responseListener.onFailure(reasons, value);
                                }
                            });
                            global.getApiToken(true, apiTokenListener);
                        }
                    });
                }
            }
        };
        t.start();
    }

    public interface TranslateListener extends TranslatorListener {
        void onTranslatedText(String text, CustomLocale languageOfText);
    }

    public void translateMessage(final ConversationMessage conversationMessageToTranslate, final CustomLocale languageOutput, final TranslateMessageListener responseListener) {  // what the thread does
        Thread t = new Thread("messageTranslationPerformer") {
            public void run() {
                final String text = conversationMessageToTranslate.getPayload().getText();
                final float cost = calculateCreditConsumption(text.length());
                final CustomLocale languageInput = conversationMessageToTranslate.getPayload().getLanguage();
                if (!languageInput.equals(languageOutput)) {
                    if (apiToken != null && apiToken.getExpirationTime().getTime() > System.currentTimeMillis()) {
                        performMessageTranslation(conversationMessageToTranslate, languageInput, languageOutput, cost, responseListener);
                    } else {
                        global.getApiToken(true, new Global.ApiTokenListener() {
                            @Override
                            public void onSuccess(AccessToken apiToken) {
                                apiTokenListener.onSuccess(apiToken);
                                performMessageTranslation(conversationMessageToTranslate, languageInput, languageOutput, cost, responseListener);
                            }

                            @Override
                            public void onFailure(final int[] reasons, final long value) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        responseListener.onFailure(reasons, value);
                                    }
                                });
                                global.getApiToken(true, apiTokenListener);
                            }
                        });
                    }
                } else {  // means that the language to be translated corresponds to ours
                    responseListener.onTranslatedMessage(conversationMessageToTranslate);
                }
            }
        };
        t.start();
    }

    public interface TranslateMessageListener extends TranslatorListener {
        void onTranslatedMessage(ConversationMessage conversationMessage);
    }

    public void detectLanguage(final CloudApiResult result, final DetectLanguageListener responseListener) {
        Thread t = new Thread("languageDetectionPerformer") {
            public void run() {
                final float cost = calculateCreditConsumption(result.getText().length());
                if (apiToken != null && apiToken.getExpirationTime().getTime() > System.currentTimeMillis()) {
                    performLanguageDetection(result, cost, responseListener);
                } else {
                    global.getApiToken(true, new Global.ApiTokenListener() {
                        @Override
                        public void onSuccess(AccessToken apiToken) {
                            apiTokenListener.onSuccess(apiToken);
                            performLanguageDetection(result, cost, responseListener);
                        }

                        @Override
                        public void onFailure(final int[] reasons, final long value) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    responseListener.onFailure(reasons, value);
                                }
                            });
                            global.getApiToken(true, apiTokenListener);
                        }
                    });
                }
            }
        };
        t.start();
    }

    public interface DetectLanguageListener extends TranslatorListener {
        void onDetectedText(CloudApiResult result);
    }

    public void getSupportedLanguages(final CustomLocale languageReturned, @Nullable final SupportedLanguagesListener responseListener) {
        synchronized (lock) {
            if (responseListener != null) {
                supportedLanguagesListeners.addLast(responseListener);
            }
            if (getSupportedLanguageThread == null) {
                getSupportedLanguageThread = new Thread(new GetSupportedLanguageRunnable(languageReturned, new SupportedLanguagesListener() {
                    @Override
                    public void onLanguagesListAvailable(ArrayList<CustomLocale> languages) {
                        notifyGetSupportedLanguagesSuccess(languages);
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        notifyGetSupportedLanguagesFailure(reasons, value);
                    }
                }), "getSupportedLanguagePerformer");
                getSupportedLanguageThread.start();
            }
        }
    }

    private void notifyGetSupportedLanguagesSuccess(ArrayList<CustomLocale> languages) {
        synchronized (lock) {
            while (supportedLanguagesListeners.peekFirst() != null) {
                supportedLanguagesListeners.pollFirst().onLanguagesListAvailable(languages);
            }
            getSupportedLanguageThread = null;
        }
    }

    private void notifyGetSupportedLanguagesFailure(final int[] reasons, final long value) {
        synchronized (lock) {
            while (supportedLanguagesListeners.peekFirst() != null) {
                supportedLanguagesListeners.pollFirst().onFailure(reasons, value);
            }
            getSupportedLanguageThread = null;
        }
    }

    private class GetSupportedLanguageRunnable implements Runnable {
        private CustomLocale languageReturned;
        private SupportedLanguagesListener responseListener;

        private GetSupportedLanguageRunnable(final CustomLocale languageReturned, final SupportedLanguagesListener responseListener) {
            this.languageReturned = languageReturned;
            this.responseListener = responseListener;
        }

        @Override
        public void run() {
            global.getApiToken(true, new Global.ApiTokenListener() {
                @Override
                public void onSuccess(AccessToken apiToken) {
                    apiTokenListener.onSuccess(apiToken);
                    performGetSupportedLanguages(languageReturned, responseListener);
                }

                @Override
                public void onFailure(final int[] reasons, final long value) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            responseListener.onFailure(reasons, value);
                        }
                    });
                    global.getApiToken(true, apiTokenListener);
                }
            });
        }
    }

    public interface SupportedLanguagesListener extends TranslatorListener {
        void onLanguagesListAvailable(ArrayList<CustomLocale> languages);
    }

    private void performTextTranslation(final String textToTranslate, @Nullable final CustomLocale inputLanguage, final CustomLocale outputLanguage, final float cost, final TranslateListener responseListener) {
        new Thread("textTranslationPerformer") {
            public void run() {
                final String translatedText = translateSimply(textToTranslate, inputLanguage, outputLanguage, responseListener);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        responseListener.onTranslatedText(translatedText, outputLanguage);
                    }
                });

                global.addUsage(cost);
            }
        }.start();
    }

    private void performMessageTranslation(final ConversationMessage conversationMessageToTranslate, final CustomLocale inputLanguage, final CustomLocale outputLanguage, final float cost, final TranslateMessageListener responseListener) {
        new Thread("messageTranslationPerformer") {
            public void run() {
                final String text = conversationMessageToTranslate.getPayload().getText();
                String translatedText = translateSimply(text, inputLanguage, outputLanguage, responseListener);
                conversationMessageToTranslate.getPayload().setText(translatedText);
                conversationMessageToTranslate.getPayload().setLanguage(outputLanguage);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        responseListener.onTranslatedMessage(conversationMessageToTranslate);
                    }
                });

                global.addUsage(cost);
            }
        }.start();
    }

    private void performLanguageDetection(final CloudApiResult result, final float cost, final DetectLanguageListener responseListener) {
        new Thread("languageDetectionPerformer") {
            public void run() {
                DetectionsListResponse response = null;
                List<String> parameters = new ArrayList<>();
                parameters.add(result.getText());
                try {
                    Translate.Detections.List list = translator.new Detections().list(parameters); //Pass in list of strings to be translated and the target language
                    list.setAccessToken(apiToken.getTokenValue());
                    response = list.execute();
                    result.setLanguage(CustomLocale.getInstance(response.getDetections().get(0).get(0).getLanguage()));

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            responseListener.onDetectedText(result);
                        }
                    });

                    global.addUsage(cost);
                } catch (IOException e) {
                    e.printStackTrace();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            responseListener.onFailure(new int[]{ErrorCodes.MISSED_CONNECTION}, 0);
                        }
                    });
                }
            }
        }.start();
    }

    private void performGetSupportedLanguages(final CustomLocale languageReturned, final SupportedLanguagesListener responseListener) {
        new Thread("getSupportedLanguagePerformer") {
            public void run() {
                ArrayList<CustomLocale> listLanguages = new ArrayList<>();
                LanguagesListResponse response = null;
                String localLanguage = languageReturned.getLanguage();
                try {
                    Translate.Languages.List list = translator.new Languages().list();  //Pass in list of strings to be translated and the target language
                    list.setTarget(localLanguage);
                    list.setAccessToken(apiToken.getTokenValue());
                    response = list.execute();
                    List<LanguagesResource> languages = response.getLanguages();
                    for (int i = 0; i < languages.size(); i++) {
                        listLanguages.add(CustomLocale.getInstance(languages.get(i).getLanguage().toLowerCase()));
                    }
                    filterAndSendLanguages(listLanguages, responseListener);
                } catch (IOException e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            responseListener.onFailure(new int[]{ErrorCodes.MISSED_CONNECTION}, 0);
                        }
                    });
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private String translateSimply(String text, @Nullable final CustomLocale inputLanguage, final CustomLocale outputLanguage, final TranslatorListener responseListener) {
        TranslationsListResponse response = null;
        String inputLanguageCode = null;
        String outputLanguageCode;
        if (inputLanguage != null) {
            if (fullySupportedLanguages.contains(inputLanguage)) {
                inputLanguageCode = inputLanguage.getCode();
            } else {
                inputLanguageCode = inputLanguage.getLanguage();
            }
        }
        if (fullySupportedLanguages.contains(outputLanguage)) {
            outputLanguageCode = outputLanguage.getCode();
        } else {
            outputLanguageCode = outputLanguage.getLanguage();
        }
        try {
            Translate.Translations.List list = translator.new Translations().list(Arrays.asList(text), outputLanguageCode); //Pass in list of strings to be translated and the target language
            if (inputLanguageCode != null) {
                list.setSource(inputLanguageCode);
            }
            list.setAccessToken(apiToken.getTokenValue());
            response = list.execute();
            List<TranslationsResource> tr = response.getTranslations();
            return Html.fromHtml(tr.get(0).getTranslatedText()).toString();   // serves to transform &#39; in apostrophe
        } catch (Exception e) {
            e.printStackTrace();
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    responseListener.onFailure(new int[]{ErrorCodes.MISSED_CONNECTION}, 0);
                }
            });
        }
        return null;
    }

    private void filterAndSendLanguages(final ArrayList<CustomLocale> translatorLanguages, final SupportedLanguagesListener responseListener) {
        tts = new TTS((global), new TTS.InitListener() {    // tts initialization (to be improved, automatic package installation)
            @Override
            public void onInit() {
                ArrayList<CustomLocale> ttsLanguages = new ArrayList<>();
                Set<Voice> set = tts.getVoices();
                SharedPreferences sharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(global);
                boolean qualityLow = sharedPreferences.getBoolean("languagesQualityLow", false);
                int quality;
                if (qualityLow) {
                    quality = Voice.QUALITY_VERY_LOW;
                } else {
                    quality = Voice.QUALITY_HIGH;
                }
                if (set != null) {
                    // we filter the languages ​​that have a tts that reflects the characteristics we want
                    for (Voice aSet : set) {
                        if (aSet.getQuality() >= quality && !aSet.getFeatures().contains("legacySetLanguageVoice")) {
                            CustomLocale language = new CustomLocale(aSet.getLocale());
                            ttsLanguages.add(language);
                        }
                    }
                    ArrayList<CustomLocale> recognizerLanguages = Recognizer.getSupportedLanguages(global);

                    // the languages ​​are filtered so that they are compatible with both the Recognizer and the Translator and with the TTS
                    fullySupportedLanguages.clear();
                    final ArrayList<CustomLocale> compatibleLanguages = new ArrayList<>();
                    ArrayList<CustomLocale> compatibleTranslatorLanguages = new ArrayList<>();   // the list of compatible languages, later it will be used to get all the variants from the recognizerLanguages
                    for (CustomLocale translatorLanguage : translatorLanguages) {
                        if (CustomLocale.containsLanguage(ttsLanguages, translatorLanguage) && CustomLocale.containsLanguage(recognizerLanguages, translatorLanguage)) {
                            if (!translatorLanguage.getCountry().isEmpty()) {
                                fullySupportedLanguages.add(translatorLanguage);
                            }
                            compatibleTranslatorLanguages.add(translatorLanguage);
                        }
                    }

                    for (CustomLocale recognizerLanguage : recognizerLanguages) {
                        if (CustomLocale.containsLanguage(compatibleTranslatorLanguages, recognizerLanguage)) {
                            compatibleLanguages.add(recognizerLanguage);
                        }
                    }
                    // alphabetical ordering
                    Collections.sort(compatibleLanguages);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            responseListener.onLanguagesListAvailable(compatibleLanguages);
                        }
                    });
                } else {
                    onError(ErrorCodes.GOOGLE_TTS_ERROR);
                }
                tts.stop();
                tts.shutdown();
            }

            @Override
            public void onError(final int reason) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        responseListener.onFailure(new int[]{reason}, 0);
                    }
                });
            }
        });
    }

    private float calculateCreditConsumption(int characters) {
        return characters * COST_PER_CHAR;
    }

    private interface TranslatorListener {
        void onFailure(int[] reasons, long value);
    }
}