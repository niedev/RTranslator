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

package nie.translator.rtranslatordevedition.voice_translation._walkie_talkie_mode.recognizer_services;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.GeneralService;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.tools.CustomLocale;
import nie.translator.rtranslatordevedition.tools.services_communication.ServiceCallback;
import nie.translator.rtranslatordevedition.tools.services_communication.ServiceCommunicator;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Peer;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.CloudApiResult;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.translation.Translator;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.voice.Recognizer;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.voice.RecognizerListener;


public abstract class RecognizerService extends GeneralService {
    // commands
    public static final int START_RECOGNITION = 0;
    public static final int RECOGNIZE = 1;
    public static final int STOP_RECOGNITION = 2;
    public static final int CHANGE_LANGUAGE = 5;
    // callbacks
    public static final int ON_RESULT = 2;
    // objects
    private Translator translator;
    private Recognizer mVoiceRecognizer;
    private Handler fatherHandler;
    private String languageCode="";


    @Override
    public void onCreate() {
        super.onCreate();
        fatherHandler = new Handler(new Handler.Callback() { //riceve i messaggi dal padre
            @Override
            public boolean handleMessage(android.os.Message message) {
                final Bundle data = message.getData();
                int command = data.getInt("command", -1);
                if (!RecognizerService.super.executeCommand(command, message.getData())) {
                    switch (command) {
                        case START_RECOGNITION: {
                            if(languageCode.length()>0) {
                                mVoiceRecognizer.startRecognizing(languageCode,data.getInt("sampleRate"), true);
                            }
                            break;
                        }
                        case RECOGNIZE: {
                            mVoiceRecognizer.recognize(data.getByteArray("data"), data.getInt("size"));
                            break;
                        }
                        case STOP_RECOGNITION: {
                            mVoiceRecognizer.finishRecognizing();
                            break;
                        }
                        case CHANGE_LANGUAGE: {
                            new Thread("changeLanguage") {
                                @Override
                                public void run() {
                                    super.run();
                                    languageCode=data.getString("language");
                                }
                            }.start();
                            break;
                        }
                    }
                }
                return false;
            }
        });

        //start translator
        translator = new Translator((Global) getApplication());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // receive the language to listen with
        languageCode = ((CustomLocale) intent.getSerializableExtra("language")).getCode();

        // start Cloud Speech API
        mVoiceRecognizer = new Recognizer(this, true, new RecognizerListener() {
            @Override
            public void onSpeechRecognizedResult(final String text, String languageCode, final float confidenceScore, final boolean isFinal) {
                new Thread("elaborateSpeechResult") {
                    @Override
                    public void run() {
                        super.run();
                        // the result is always final
                        if (text != null && !text.equals("")) {
                            translator.detectLanguage(new CloudApiResult(text, confidenceScore, isFinal), new Translator.DetectLanguageListener() {
                                @Override
                                public void onDetectedText(CloudApiResult result) {
                                    //notify result
                                    Bundle bundle = new Bundle();
                                    bundle.putInt("callback", ON_RESULT);
                                    bundle.putSerializable("result", result);
                                    RecognizerService.super.notifyToClient(bundle);
                                }

                                @Override
                                public void onFailure(int[] reasons, long value) {
                                    notifyError(reasons,value);
                                }
                            });
                        } else {
                            //notify result
                            Bundle bundle = new Bundle();
                            bundle.putInt("callback", ON_RESULT);
                            bundle.putSerializable("result", new CloudApiResult(text, confidenceScore, isFinal));
                            RecognizerService.super.notifyToClient(bundle);
                        }
                    }
                }.start();
            }

            @Override
            public void onError(int[] reasons, long value) {
                notifyError(reasons,value);
            }
        });
        return new Messenger(fatherHandler).getBinder();   // send the messenger with which the father will send messages to this service
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop Cloud Speech API
        mVoiceRecognizer.destroy();
        mVoiceRecognizer = null;
    }


    // connection
    public static class RecognizerServiceCommunicator extends ServiceCommunicator {
        private ArrayList<RecognizerServiceCallback> clientCallbacks = new ArrayList<>();

        public RecognizerServiceCommunicator(int id) {
            super(id);
            super.serviceHandler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(android.os.Message msg) {
                    msg.getData().setClassLoader(Peer.class.getClassLoader());
                    int callbackMessage = msg.getData().getInt("callback", -1);
                    if (callbackMessage != -1) {
                        switch (callbackMessage) {
                            case ON_RESULT: {
                                CloudApiResult result = (CloudApiResult) msg.getData().getSerializable("result");
                                for (int i = 0; i < clientCallbacks.size(); i++) {
                                    clientCallbacks.get(i).onResult(result);
                                }
                                break;
                            }
                            case ON_ERROR: {
                                int[] reasons=msg.getData().getIntArray("reasons");
                                long value=msg.getData().getLong("value");
                                for (int i = 0; i < clientCallbacks.size(); i++) {
                                    clientCallbacks.get(i).onError(reasons,value);
                                }
                                break;
                            }
                        }
                    }
                    return false;
                }
            });
        }

        @Override
        public void initializeCommunication(Messenger serviceMessenger) {
            super.initializeCommunication(serviceMessenger);
            // we send our serviceMessenger which the service will use to communicate with us
            Bundle bundle = new Bundle();
            bundle.putInt("command", INITIALIZE_COMMUNICATION);
            bundle.putParcelable("messenger", new Messenger(serviceHandler));
            super.sendToService(bundle);
        }

        //commands
        public void startRecognition(int sampleRate) {
            Bundle bundle = new Bundle();
            bundle.putInt("command", START_RECOGNITION);
            bundle.putInt("sampleRate", sampleRate);
            super.sendToService(bundle);
        }

        public void recognize(byte[] data, int size) {
            Bundle bundle = new Bundle();
            bundle.putInt("command", RecognizerService.RECOGNIZE);
            bundle.putByteArray("data", data);
            bundle.putInt("size", size);
            super.sendToService(bundle);
        }

        public void stopRecognition() {
            Bundle bundle = new Bundle();
            bundle.putInt("command", STOP_RECOGNITION);
            super.sendToService(bundle);
        }

        public void changeLanguage(CustomLocale language) {
            Bundle bundle = new Bundle();
            bundle.putInt("command", CHANGE_LANGUAGE);
            bundle.putString("language", language.getCode());
            super.sendToService(bundle);
        }

        public void addCallback(ServiceCallback callback) {
            clientCallbacks.add((RecognizerServiceCallback) callback);
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        public int removeCallback(ServiceCallback callback) {
            clientCallbacks.remove(callback);
            return clientCallbacks.size();
        }
    }

    public static abstract class RecognizerServiceCallback extends ServiceCallback {
        public void onResult(CloudApiResult result) {
        }
    }
}
