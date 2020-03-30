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

package nie.translator.rtranslatordevedition.voice_translation._walkie_talkie_mode._walkie_talkie;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.tools.CustomLocale;
import nie.translator.rtranslatordevedition.tools.gui.messages.GuiMessage;
import nie.translator.rtranslatordevedition.voice_translation.VoiceTranslationService;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Message;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Peer;
import nie.translator.rtranslatordevedition.voice_translation._walkie_talkie_mode.recognizer_services.FirstLanguageRecognizerService;
import nie.translator.rtranslatordevedition.voice_translation._walkie_talkie_mode.recognizer_services.RecognizerService;
import nie.translator.rtranslatordevedition.voice_translation._walkie_talkie_mode.recognizer_services.SecondLanguageRecognizerService;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.CloudApiResult;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.translation.Translator;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.voice.Recorder;


public class WalkieTalkieService extends VoiceTranslationService {
    // commands
    public static final int CHANGE_FIRST_LANGUAGE = 22;
    public static final int CHANGE_SECOND_LANGUAGE = 23;
    public static final int GET_FIRST_LANGUAGE = 24;
    public static final int GET_SECOND_LANGUAGE = 25;
    // callbacks
    public static final int ON_FIRST_LANGUAGE = 22;
    public static final int ON_SECOND_LANGUAGE = 23;
    // objects
    private Translator translator;
    private CustomLocale firstLanguage;
    private CustomLocale secondLanguage;
    private Translator.TranslateListener firstResultTranslateListener;
    private Translator.TranslateListener secondResultTranslateListener;
    // sending and receiving messages to and from children
    private ArrayList<CloudApiResult> firstLanguageQueue = new ArrayList<>();
    private ArrayList<CloudApiResult> secondLanguageQueue = new ArrayList<>();
    private ServiceConnection firstLanguageConnection;
    private ServiceConnection secondLanguageConnection;
    private RecognizerService.RecognizerServiceCommunicator firstLanguageServiceCommunicator = new RecognizerService.RecognizerServiceCommunicator(0);
    private RecognizerService.RecognizerServiceCommunicator secondLanguageServiceCommunicator = new RecognizerService.RecognizerServiceCommunicator(0);


    @Override
    public void onCreate() {
        super.onCreate();
        translator = new Translator((Global) getApplication());
        clientHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(android.os.Message message) {
                int command = message.getData().getInt("command", -1);
                if (command != -1) {
                    if (!WalkieTalkieService.super.executeCommand(command, message.getData())) {
                        switch (command) {
                            case CHANGE_FIRST_LANGUAGE:
                                CustomLocale newFirstLanguage = (CustomLocale) message.getData().getSerializable("language");
                                if (!firstLanguage.equals(newFirstLanguage)) {
                                    firstLanguage = newFirstLanguage;
                                    firstLanguageServiceCommunicator.changeLanguage(firstLanguage);
                                }
                                break;
                            case CHANGE_SECOND_LANGUAGE:
                                CustomLocale newSecondLanguage = (CustomLocale) message.getData().getSerializable("language");
                                if (!secondLanguage.equals(newSecondLanguage)) {
                                    secondLanguage = newSecondLanguage;
                                    secondLanguageServiceCommunicator.changeLanguage(secondLanguage);
                                }
                                break;
                            case GET_FIRST_LANGUAGE:
                                Bundle bundle = new Bundle();
                                bundle.putInt("callback", ON_FIRST_LANGUAGE);
                                bundle.putSerializable("language", firstLanguage);
                                WalkieTalkieService.super.notifyToClient(bundle);
                                break;
                            case GET_SECOND_LANGUAGE:
                                Bundle bundle2 = new Bundle();
                                bundle2.putInt("callback", ON_SECOND_LANGUAGE);
                                bundle2.putSerializable("language", secondLanguage);
                                WalkieTalkieService.super.notifyToClient(bundle2);
                                break;
                            case RECEIVE_TEXT:
                                String text = message.getData().getString("text", null);
                                if (text != null) {
                                    translator.detectLanguage(new CloudApiResult(text), new Translator.DetectLanguageListener() {
                                        @Override
                                        public void onDetectedText(final CloudApiResult result) {
                                            // here the result returns with the same language as the text sent from the Fragment editText
                                            if (result.getLanguage().equalsLanguage(firstLanguage)) {
                                                translator.translate(result.getText(), secondLanguage, firstResultTranslateListener);
                                            } else {
                                                translator.translate(result.getText(), firstLanguage, secondResultTranslateListener);
                                            }
                                        }

                                        @Override
                                        public void onFailure(int[] reasons, long value) {
                                            WalkieTalkieService.super.notifyError(reasons,value);
                                        }
                                    });
                                }
                                break;
                        }
                    }
                }
                return false;
            }
        });
        firstLanguageConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                firstLanguageServiceCommunicator.initializeCommunication(new Messenger(iBinder));
                firstLanguageServiceCommunicator.addCallback(new WalkieTalkieServiceRecognizerServiceCallback() {
                    @Override
                    public void onResult(CloudApiResult result) {
                        super.onResult(result);
                        firstLanguageQueue.add(result);
                        compareResults();
                    }
                });

                startVoiceRecorder();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };
        secondLanguageConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                secondLanguageServiceCommunicator.initializeCommunication(new Messenger(iBinder));
                secondLanguageServiceCommunicator.addCallback(new WalkieTalkieServiceRecognizerServiceCallback() {
                    @Override
                    public void onResult(CloudApiResult result) {
                        super.onResult(result);
                        secondLanguageQueue.add(result);
                        compareResults();
                    }
                });

                startVoiceRecorder();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };
        mVoiceCallback = new Recorder.SimpleCallback() {
            @Override
            public void onVoiceStart() {
                super.onVoiceStart();
                // the buffers are reset
                firstLanguageQueue = new ArrayList<>();
                secondLanguageQueue = new ArrayList<>();
                // we send request to start the recognition and the sample rate to the children
                int sampleRate = WalkieTalkieService.super.getVoiceRecorderSampleRate();
                if(sampleRate!=0) {
                    firstLanguageServiceCommunicator.startRecognition(sampleRate);
                    secondLanguageServiceCommunicator.startRecognition(sampleRate);
                    // we notify the client
                    WalkieTalkieService.super.notifyVoiceStart();
                }
            }

            @Override
            public void onVoice(@NonNull byte[] data, int size) {
                super.onVoice(data,size);
                // children are asked to recognize the data we send
                firstLanguageServiceCommunicator.recognize(data, size);
                secondLanguageServiceCommunicator.recognize(data, size);
            }

            @Override
            public void onVoiceEnd() {
                super.onVoiceEnd();
                // we ask to the children to stop the acknowledgment
                firstLanguageServiceCommunicator.stopRecognition();
                secondLanguageServiceCommunicator.stopRecognition();
                // we notify the client
                WalkieTalkieService.super.notifyVoiceEnd();
            }
        };
        firstResultTranslateListener = new Translator.TranslateListener() {
            @Override
            public void onTranslatedText(String text, CustomLocale languageOfText) {
                speak(text, languageOfText);
                GuiMessage message = new GuiMessage(new Message(WalkieTalkieService.this, text), true,true);
                WalkieTalkieService.super.notifyMessage(message);
                // we save every new message in the exchanged messages so that the fragment can restore them
                WalkieTalkieService.super.addMessage(message);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                WalkieTalkieService.super.notifyError(reasons,value);
            }
        };
        secondResultTranslateListener = new Translator.TranslateListener() {
            @Override
            public void onTranslatedText(String text, CustomLocale languageOfText) {
                speak(text, languageOfText);
                GuiMessage message = new GuiMessage(new Message(WalkieTalkieService.this, text), false,true);
                WalkieTalkieService.super.notifyMessage(message);
                // we save every new message in the exchanged messages so that the fragment can restore them
                WalkieTalkieService.super.addMessage(message);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                WalkieTalkieService.super.notifyError(reasons,value);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final CustomLocale finalFirstLanguage=this.firstLanguage;
        final CustomLocale finalSecondLanguage=this.secondLanguage;

        //getGroup the languages
        firstLanguage = (CustomLocale) intent.getSerializableExtra("firstLanguage");
        secondLanguage = (CustomLocale) intent.getSerializableExtra("secondLanguage");

        if(finalFirstLanguage==null || finalSecondLanguage==null ) {  //se è il primo avvio
            //create parameters for starting
            Intent intent1 = new Intent(this, FirstLanguageRecognizerService.class);
            Intent intent2 = new Intent(this, SecondLanguageRecognizerService.class);
            intent1.putExtra("language", firstLanguage);
            intent2.putExtra("language", secondLanguage);
            //bind services
            bindService(intent1, firstLanguageConnection, Service.BIND_AUTO_CREATE);
            bindService(intent2, secondLanguageConnection, Service.BIND_AUTO_CREATE);
        }else{
            //change languages
            firstLanguageServiceCommunicator.changeLanguage(firstLanguage);
            secondLanguageServiceCommunicator.changeLanguage(secondLanguage);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        //stop the two services that recognizes voice
        firstLanguageServiceCommunicator.stopCommunication();
        secondLanguageServiceCommunicator.stopCommunication();
        unbindService(firstLanguageConnection);
        unbindService(secondLanguageConnection);
        super.onDestroy();
    }

    private void
    compareResults() {
        if (firstLanguageQueue.size() >= 1 && secondLanguageQueue.size() >= 1) {
            CloudApiResult firstResult = firstLanguageQueue.remove(0);
            CloudApiResult secondResult = secondLanguageQueue.remove(0);
            // if one of the two results was not found
            boolean isFirstResultValid = firstResult.getText() != null && !firstResult.getText().equals("");
            boolean isSecondResultValid = secondResult.getText() != null && !secondResult.getText().equals("");
            if (isFirstResultValid) {
                if (isSecondResultValid) { // if both results were found
                    if (firstResult.getLanguage().equalsLanguage(firstLanguage)) {   // if one of the two languages​was not found
                        if (secondResult.getLanguage().equalsLanguage(secondLanguage)) {  // if both have recognized their respective language
                            compareResultsConfidence(firstResult, secondResult);
                        } else {  // if only the first service recognized its language
                            translator.translate(firstResult.getText(), secondLanguage, firstResultTranslateListener);
                        }
                    } else {
                        if (secondResult.getLanguage().equalsLanguage(secondLanguage)) {  // if only the second service has not recognized its language
                            translator.translate(secondResult.getText(), firstLanguage, secondResultTranslateListener);
                        } else {  // if neither of them recognized his language but another (or none)
                            compareResultsConfidence(firstResult, secondResult);
                        }
                    }
                } else {  // if only the first result was found
                    translator.translate(firstResult.getText(), secondLanguage, firstResultTranslateListener);
                }
            } else {
                if (isSecondResultValid) {  // if only the second result was found
                    translator.translate(secondResult.getText(), firstLanguage, secondResultTranslateListener);
                }
                // if neither result was found, we do nothing
            }
        }
    }

    private void compareResultsConfidence(CloudApiResult firstResult, CloudApiResult secondResult) {
        if (firstResult.getConfidenceScore() >= secondResult.getConfidenceScore()) {
            translator.translate(firstResult.getText(), secondLanguage, firstResultTranslateListener);
        } else {
            translator.translate(secondResult.getText(), firstLanguage, secondResultTranslateListener);
        }
    }

    private abstract class WalkieTalkieServiceRecognizerServiceCallback extends RecognizerService.RecognizerServiceCallback {
        @Override
        public void onError(int[] reasons,long value) {
            super.onError(reasons,value);
            WalkieTalkieService.super.notifyError(reasons,value);
        }
    }

    public static class WalkieTalkieServiceCommunicator extends VoiceTranslationServiceCommunicator {
        private ArrayList<LanguageListener> firstLanguageListeners = new ArrayList<>();
        private ArrayList<LanguageListener> secondLanguageListeners = new ArrayList<>();

        public WalkieTalkieServiceCommunicator(int id) {
            super(id);
            super.serviceHandler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(android.os.Message msg) {
                    msg.getData().setClassLoader(Peer.class.getClassLoader());
                    int callbackMessage = msg.getData().getInt("callback", -1);
                    Bundle data= msg.getData();
                    if(!executeCallback(callbackMessage,data)){
                        switch (callbackMessage){
                            case ON_FIRST_LANGUAGE:{
                                CustomLocale language = (CustomLocale) data.getSerializable("language");
                                while (firstLanguageListeners.size() > 0) {
                                    firstLanguageListeners.remove(0).onLanguage(language);
                                }
                                break;
                            }
                            case ON_SECOND_LANGUAGE:{
                                CustomLocale language = (CustomLocale) data.getSerializable("language");
                                while (secondLanguageListeners.size() > 0) {
                                    secondLanguageListeners.remove(0).onLanguage(language);
                                }
                                break;
                            }
                        }
                    }
                    return true;
                }
            });
        }

        public void getFirstLanguage(LanguageListener responseListener){
            firstLanguageListeners.add(responseListener);
            if (firstLanguageListeners.size() == 1) {
                Bundle bundle = new Bundle();
                bundle.putInt("command", GET_FIRST_LANGUAGE);
                super.sendToService(bundle);
            }
        }

        public void getSecondLanguage(LanguageListener responseListener){
            secondLanguageListeners.add(responseListener);
            if (secondLanguageListeners.size() == 1) {
                Bundle bundle = new Bundle();
                bundle.putInt("command", GET_SECOND_LANGUAGE);
                super.sendToService(bundle);
            }
        }

        public void changeFirstLanguage(CustomLocale language) {
            Bundle bundle = new Bundle();
            bundle.putInt("command", WalkieTalkieService.CHANGE_FIRST_LANGUAGE);
            bundle.putSerializable("language", language);
            super.sendToService(bundle);
        }

        public void changeSecondLanguage(CustomLocale language) {
            Bundle bundle = new Bundle();
            bundle.putInt("command", WalkieTalkieService.CHANGE_SECOND_LANGUAGE);
            bundle.putSerializable("language", language);
            super.sendToService(bundle);
        }
    }

    public interface LanguageListener{
        void onLanguage(CustomLocale language);
    }
}