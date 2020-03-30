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

package nie.translator.rtranslatordevedition.voice_translation._conversation_mode._conversation;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.NonNull;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.tools.BluetoothHeadsetUtils;
import nie.translator.rtranslatordevedition.tools.CustomLocale;
import nie.translator.rtranslatordevedition.tools.gui.messages.GuiMessage;
import nie.translator.rtranslatordevedition.tools.gui.peers.GuiPeer;
import nie.translator.rtranslatordevedition.voice_translation.VoiceTranslationService;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.ConversationBluetoothCommunicator;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Message;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Peer;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.Channel;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.CloudApiText;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.translation.Translator;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.voice.Recognizer;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.voice.Recorder;


public class ConversationService extends VoiceTranslationService {
    //commands
    public static final int CHANGE_LANGUAGE = 15;

    private static final long WAKELOCK_TIMEOUT = 20 * 1000L;  // 10 minutes, so if the service stopped without calling onDestroyed the wakeLock would still be released within 10 minutes
    private Channel.Timer wakeLockTimer;  // to reactivate the timer every 10 minutes, so as long as the service is active the wakelock will never expire
    private PowerManager.WakeLock screenWakeLock;
    private String textRecognized = "";
    private Translator translator;
    private String myPeerName;
    private Recognizer mVoiceRecognizer;
    private BluetoothHelper mBluetoothHelper;
    private Global global;
    private ConversationBluetoothCommunicator.Callback communicationCallback;
    private static Handler mHandler = new Handler();
    private Handler mainHandler;


    @Override
    public void onCreate() {
        super.onCreate();
        global = (Global) getApplication();
        mainHandler = new Handler(Looper.getMainLooper());
        // wake lock initialization (to keep the process active when the phone is on standby)
        acquireWakeLock();
        //startBluetoothSco
        mBluetoothHelper = new BluetoothHelper(this);
        mVoiceCallback = new Recorder.Callback() {
            @Override
            public void onListenStart() {
                if (mVoiceRecognizer != null) {
                    super.onListenStart();
                    Log.e("recorder","onListenStart");
                    global.getLanguage(true, new Global.GetLocaleListener() {
                        @Override
                        public void onSuccess(CustomLocale result) {
                            int sampleRate = getVoiceRecorderSampleRate();
                            if (sampleRate != 0) {
                                mVoiceRecognizer.startRecognizing(result.getCode(), sampleRate, false);
                            }
                        }

                        @Override
                        public void onFailure(int[] reasons, long value) {
                            ConversationService.super.notifyError(reasons, value);
                        }
                    });
                    super.onListenStart();
                }
            }

            @Override
            public void onVoiceStart() {
                if (mVoiceRecognizer != null) {
                    super.onVoiceStart();
                    Log.e("recorder","onVoiceStart");
                    //si notifica il client
                    ConversationService.super.notifyVoiceStart();
                }
            }

            @Override
            public void onVoice(@NonNull byte[] data, int size) {
                if (mVoiceRecognizer != null) {
                    super.onVoice(data,size);
                    mVoiceRecognizer.recognize(data, size);
                }
            }

            @Override
            public void onVoiceEnd() {
                if (mVoiceRecognizer != null) {
                    super.onVoiceEnd();
                    Log.e("recorder","onVoiceEnd");
                    // if the textRecognizer is not empty then it means that we have a result that has not been correctly recognized as final
                    if (!textRecognized.equals("")) {
                        onListenEnd();
                        textRecognized = "";
                    }
                    // the client is notified
                    ConversationService.super.notifyVoiceEnd();
                }
            }

            @Override
            public void onListenEnd() {
                if (mVoiceRecognizer != null) {
                    super.onListenEnd();
                    Log.e("recorder","onListenEnd");
                    mVoiceRecognizer.finishRecognizing();
                }
            }
        };
        clientHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(final android.os.Message message) {
                int command = message.getData().getInt("command", -1);
                if (command != -1) {
                    if (!ConversationService.super.executeCommand(command, message.getData())) {
                        switch (command) {
                            case RECEIVE_TEXT:
                                global.getLanguage(false,new Global.GetLocaleListener() {
                                    @Override
                                    public void onSuccess(CustomLocale language) {
                                        String text = message.getData().getString("text");
                                        if (text != null) {
                                            GuiMessage guiMessage = new GuiMessage(new Message(global, text), true, true);
                                            // send the message
                                            sendMessage(new ConversationMessage(new CloudApiText(text, language)));

                                            notifyMessage(guiMessage);
                                            // we save every new message in the exchanged messages so that the fragment can restore them
                                            addMessage(guiMessage);
                                        }
                                    }

                                    @Override
                                    public void onFailure(int[] reasons, long value) {
                                        ConversationService.super.notifyError(reasons, value);
                                    }
                                });
                                break;
                        }
                    }
                }
                return false;
            }
        });
        communicationCallback = new ConversationBluetoothCommunicator.Callback() {
            @Override
            public void onMessageReceived(final Message message) {
                super.onMessageReceived(message);
                global.getLanguage(false,new Global.GetLocaleListener() {
                    @Override
                    public void onSuccess(CustomLocale result) {
                        String completeText = message.getText();
                        int languageCodeSize = Integer.valueOf(completeText.substring(completeText.length() - 1));
                        String text = completeText.substring(0, completeText.length() - (languageCodeSize + 1));
                        String languageCode = completeText.substring(completeText.length() - (languageCodeSize + 1), completeText.length() - 1);

                        ConversationMessage conversationMessage = new ConversationMessage(message.getSender(), new CloudApiText(text, CustomLocale.getInstance(languageCode)));
                        translator.translateMessage(conversationMessage, result, new Translator.TranslateMessageListener() {
                            @Override
                            public void onTranslatedMessage(ConversationMessage conversationMessage) {
                                speak(conversationMessage.getPayload().getText(), conversationMessage.getPayload().getLanguage());
                                message.setText(conversationMessage.getPayload().getText());   // updating the text with the new translated text (and without the language code)
                                GuiMessage guiMessage = new GuiMessage(message, false, true);
                                notifyMessage(guiMessage);
                                // we save every new message in the exchanged messages so that the fragment can restore them
                                addMessage(guiMessage);
                            }

                            @Override
                            public void onFailure(int[] reasons, long value) {
                                ConversationService.super.notifyError(reasons, value);
                            }
                        });
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        ConversationService.super.notifyError(reasons, value);
                    }
                });
            }

            @Override
            public void onDisconnected(GuiPeer peer, int peersLeft) {
                super.onDisconnected(peer, peersLeft);
                if (peersLeft == 0) {
                    stopSelf();
                }
            }
        };
        global.getBluetoothCommunicator().addCallback(communicationCallback);

        // speech recognition and translation initialization
        translator = new Translator((Global) getApplication());
        mVoiceRecognizer = new Recognizer(ConversationService.this, false, new VoiceTranslationServiceRecognizerListener() {
            @Override
            public void onSpeechRecognizedResult(String text, String languageCode, float confidenceScore, boolean isFinal) {
                if (text != null && languageCode != null && !text.equals("")) {
                    CustomLocale language = CustomLocale.getInstance(languageCode);
                    GuiMessage guiMessage = new GuiMessage(new Message(global, text), true, isFinal);
                    if (isFinal) {
                        textRecognized = "";  // to ensure that we continue to listen since in this case the result is automatically extracted
                        // send the message
                        sendMessage(new ConversationMessage(new CloudApiText(text, language)));

                        notifyMessage(guiMessage);
                        // we save every new message in the exchanged messages so that the fragment can restore them
                        addMessage(guiMessage);
                    } else {
                        notifyMessage(guiMessage);
                        textRecognized = text;  // if it equals something then when calling voiceEnd we stop recognition
                    }
                }
            }

            @Override
            public void onError(int[] reasons, long value) {
                ConversationService.super.notifyError(reasons, value);
            }
        });
        mBluetoothHelper.start();
    }

    private void sendMessage(ConversationMessage conversationMessage) {
        String languageCode = conversationMessage.getPayload().getLanguage().getCode();
        global.getBluetoothCommunicator().sendMessage(new Message(global, conversationMessage.getPayload().getText() + languageCode + languageCode.length()));
    }

    public String getMyPeerName() {
        return myPeerName;
    }

    public void setMyPeerName(String myPeerName) {
        this.myPeerName = myPeerName;
    }

    @Override
    protected boolean shouldStopMicDuringTTS() {
        return !mBluetoothHelper.isOnHeadsetSco();
    }

    @Override
    protected boolean isBluetoothHeadsetConnected() {
        return mBluetoothHelper.isOnHeadsetSco();
    }

    private void startWakeLockReactivationTimer(final Channel.Timer.Callback callback) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                wakeLockTimer = new Channel.Timer(WAKELOCK_TIMEOUT - 10000);  //si riattiva 10 secondi prima cosi da essere sicuri che non ci siano istanti in cui il wakelock è rilasciato
                wakeLockTimer.setCallback(callback);
                wakeLockTimer.start();
            }
        });
    }

    private void resetWakeLockReactivationTimer() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (wakeLockTimer != null) {
                    wakeLockTimer.cancel();
                    wakeLockTimer = null;
                }
            }
        });
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        screenWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "speechGoogleUserEdition:screenWakeLock");
        screenWakeLock.acquire(WAKELOCK_TIMEOUT);
        startWakeLockReactivationTimer(new Channel.Timer.Callback() {
            @Override
            public void onFinished() {
                acquireWakeLock();
            }
        });
    }

    @Override
    public void onDestroy() {
        // Stop Cloud Speech API
        mVoiceRecognizer.destroy();
        mVoiceRecognizer = null;
        //stop Bluetooth helper
        mBluetoothHelper.stop();
        super.onDestroy();
        global.getBluetoothCommunicator().removeCallback(communicationCallback);
        //release wake lock
        resetWakeLockReactivationTimer();
        if (screenWakeLock != null) {
            while (screenWakeLock.isHeld()) {
                screenWakeLock.release();
            }
            screenWakeLock = null;
        }
    }

    private class BluetoothHelper extends BluetoothHeadsetUtils {
        private BluetoothHelper(Context context) {
            super(context);
        }

        @Override
        public void onHeadsetConnected() {
        }

        @Override
        public void onScoAudioConnected() {
            Bundle bundle = new Bundle();
            bundle.putInt("callback", ON_CONNECTED_BLUETOOTH_HEADSET);
            notifyToClient(bundle);
        }

        @Override
        public void onScoAudioDisconnected() {
            Bundle bundle = new Bundle();
            bundle.putInt("callback", ON_DISCONNECTED_BLUETOOTH_HEADSET);
            notifyToClient(bundle);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stop();
                    start();
                }
            }, 1000);
        }

        @Override
        public void onHeadsetDisconnected() {
        }
    }

    public static class ConversationServiceCommunicator extends VoiceTranslationServiceCommunicator {
        public ConversationServiceCommunicator(int id) {
            super(id);
            super.serviceHandler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(android.os.Message msg) {
                    msg.getData().setClassLoader(Peer.class.getClassLoader());
                    int callbackMessage = msg.getData().getInt("callback", -1);
                    Bundle data = msg.getData();
                    executeCallback(callbackMessage, data);
                    return true;
                }
            });
        }
    }
}