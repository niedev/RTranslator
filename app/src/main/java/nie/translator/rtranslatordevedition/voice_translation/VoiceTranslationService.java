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

package nie.translator.rtranslatordevedition.voice_translation;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.GeneralService;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.tools.CustomLocale;
import nie.translator.rtranslatordevedition.tools.TTS;
import nie.translator.rtranslatordevedition.tools.Tools;
import nie.translator.rtranslatordevedition.tools.gui.messages.GuiMessage;
import nie.translator.rtranslatordevedition.tools.services_communication.ServiceCallback;
import nie.translator.rtranslatordevedition.tools.services_communication.ServiceCommunicator;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.voice.RecognizerListener;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.voice.Recorder;


public abstract class VoiceTranslationService extends GeneralService {
    // commands
    public static final int GET_ATTRIBUTES = 6;
    public static final int START_MIC = 0;
    public static final int STOP_MIC = 1;
    public static final int START_SOUND = 2;
    public static final int STOP_SOUND = 3;
    public static final int SET_EDIT_TEXT_OPEN = 7;
    public static final int RECEIVE_TEXT = 4;
    // callbacks
    public static final int ON_ATTRIBUTES = 5;
    public static final int ON_VOICE_STARTED = 0;
    public static final int ON_VOICE_ENDED = 1;
    public static final int ON_MESSAGE = 2;
    public static final int ON_CONNECTED_BLUETOOTH_HEADSET = 15;
    public static final int ON_DISCONNECTED_BLUETOOTH_HEADSET = 16;
    public static final int ON_STOPPED = 6;

    // permissions
    public static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 3;
    public static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.RECORD_AUDIO,
    };

    // errors
    public static final int MISSING_MIC_PERMISSION = 400;

    // objects
    Notification notification;
    protected Recorder.Callback mVoiceCallback;
    protected Handler clientHandler;
    private Recorder mVoiceRecorder;
    private UtteranceProgressListener ttsListener;
    private TTS tts;

    // variables
    private ArrayList<GuiMessage> messages = new ArrayList<>(); // messages exchanged since the beginning of the service
    private boolean isMicMute = false;
    private boolean isAudioMute = false;
    private boolean isEditTextOpen = false;
    private int utterancesCurrentlySpeaking = 0;
    private final Object mLock = new Object();


    @Override
    public void onCreate() {
        super.onCreate();
        // tts initialization
        ttsListener = new UtteranceProgressListener() {
            @Override
            public void onStart(String s) {
            }

            @Override
            public void onDone(String s) {
                synchronized (mLock) {
                    if (utterancesCurrentlySpeaking > 0) {
                        utterancesCurrentlySpeaking--;
                    }
                    if (!isMicMute && utterancesCurrentlySpeaking == 0) {
                        // start the task because this thread is not allowed to start the Recorder
                        StartVoiceRecorderTask startVoiceRecorderTask = new StartVoiceRecorderTask();
                        startVoiceRecorderTask.execute(VoiceTranslationService.this);
                    }
                }
            }

            @Override
            public void onError(String s) {
            }
        };

        initializeTTS();
    }

    private void initializeTTS() {
        tts = new TTS(this, new TTS.InitListener() {  // tts initialization (to be improved, automatic package installation)
            @Override
            public void onInit() {
                tts.setOnUtteranceProgressListener(ttsListener);
            }

            @Override
            public void onError(int reason) {
                notifyError(new int[]{reason}, -1);
                isAudioMute = true;
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (notification == null) {
            notification = intent.getParcelableExtra("notification");
        }
        return super.onStartCommand(intent, flags, startId);
    }

    protected boolean isAudioMute() {
        return isAudioMute;
    }

    // voice recorder

    private static class StartVoiceRecorderTask extends AsyncTask<VoiceTranslationService, Void, VoiceTranslationService> {
        @Override
        protected VoiceTranslationService doInBackground(VoiceTranslationService... voiceTranslationServices) {
            if (voiceTranslationServices.length > 0) {
                return voiceTranslationServices[0];
            }
            return null;
        }

        @Override
        protected void onPostExecute(VoiceTranslationService voiceTranslationService) {
            super.onPostExecute(voiceTranslationService);
            if (voiceTranslationService != null) {
                voiceTranslationService.startVoiceRecorder();
            }
        }
    }

    public void startVoiceRecorder() {
        if (!Tools.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            notifyError(new int[]{MISSING_MIC_PERMISSION}, -1);
        } else {
            if (mVoiceRecorder == null && !isMicMute) {
                mVoiceRecorder = new Recorder((Global) getApplication(), mVoiceCallback);
                mVoiceRecorder.start();
            }
        }
    }

    public void stopVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
    }

    protected int getVoiceRecorderSampleRate() {
        if (mVoiceRecorder != null) {
            return mVoiceRecorder.getSampleRate();
        } else {
            return 0;
        }
    }

    // tts

    public synchronized void speak(String result, CustomLocale language) {
        synchronized (mLock) {
            if (tts.isActive() && !isAudioMute) {
                utterancesCurrentlySpeaking++;
                if (shouldStopMicDuringTTS()) {
                    stopVoiceRecorder();  // used instead of dismiss when the result is final since stop also implements dismiss ()
                }
                if (tts.getVoice() != null && language.equals(new CustomLocale(tts.getVoice().getLocale()))) {
                    tts.speak(result, TextToSpeech.QUEUE_ADD, null, "c01");
                } else {
                    tts.setLanguage(language,this);
                    tts.speak(result, TextToSpeech.QUEUE_ADD, null, "c01");
                }
            }
        }
    }

    protected boolean shouldStopMicDuringTTS() {
        return true;
    }

    protected boolean isBluetoothHeadsetConnected() {
        return false;
    }

    // messages

    protected void addMessage(GuiMessage message) {
        messages.add(message);
    }

    // other

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        // Stop listening to voice
        stopVoiceRecorder();
        //stop tts
        tts.stop();
        tts.shutdown();
    }

    // communication

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new Messenger(clientHandler).getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (notification != null) {
            startForeground(11, notification);
        }
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    protected boolean executeCommand(int command, Bundle data) {
        if (!super.executeCommand(command, data)) {
            switch (command) {
                case START_MIC:
                    isMicMute = false;
                    startVoiceRecorder();
                    return true;
                case STOP_MIC:
                    if (data.getBoolean("permanent")) {
                        isMicMute = true;
                    }
                    stopVoiceRecorder();
                    return true;
                case START_SOUND:
                    isAudioMute = false;
                    if (!tts.isActive()) {
                        initializeTTS();
                    }
                    return true;
                case STOP_SOUND:
                    isAudioMute = true;
                    if (utterancesCurrentlySpeaking > 0) {
                        utterancesCurrentlySpeaking = 0;
                        tts.stop();
                        ttsListener.onDone("");
                    }
                    return true;
                case SET_EDIT_TEXT_OPEN:
                    isEditTextOpen = data.getBoolean("value");
                    return true;
                case GET_ATTRIBUTES:
                    Bundle bundle = new Bundle();
                    bundle.putInt("callback", ON_ATTRIBUTES);
                    bundle.putParcelableArrayList("messages", messages);
                    bundle.putBoolean("isMicMute", isMicMute);
                    bundle.putBoolean("isAudioMute", isAudioMute);
                    bundle.putBoolean("isEditTextOpen", isEditTextOpen);
                    bundle.putBoolean("isBluetoothHeadsetConnected", isBluetoothHeadsetConnected());
                    super.notifyToClient(bundle);
                    return true;
            }
            return false;
        }
        return true;
    }

    public void notifyMessage(GuiMessage message) {
        Bundle bundle = new Bundle();
        bundle.putInt("callback", ON_MESSAGE);
        bundle.putParcelable("message", message);
        super.notifyToClient(bundle);
    }

    protected void notifyVoiceStart() {
        Bundle bundle = new Bundle();
        bundle.clear();
        bundle.putInt("callback", ON_VOICE_STARTED);
        super.notifyToClient(bundle);
    }

    protected void notifyVoiceEnd() {
        Bundle bundle = new Bundle();
        bundle.clear();
        bundle.putInt("callback", ON_VOICE_ENDED);
        super.notifyToClient(bundle);
    }

    public void notifyError(int[] reasons, long value) {
        super.notifyError(reasons, value);
        if (mVoiceRecorder != null) {
            mVoiceCallback.onListenEnd();
        }
    }

    // connection with clients
    public static abstract class VoiceTranslationServiceCommunicator extends ServiceCommunicator {
        private ArrayList<VoiceTranslationServiceCallback> clientCallbacks = new ArrayList<>();
        private ArrayList<AttributesListener> attributesListeners = new ArrayList<>();

        protected VoiceTranslationServiceCommunicator(int id) {
            super(id);
        }

        protected boolean executeCallback(int callback, Bundle data) {
            if (callback != -1) {
                switch (callback) {
                    case ON_ATTRIBUTES: {
                        ArrayList<GuiMessage> messages = data.getParcelableArrayList("messages");
                        boolean isMicMute = data.getBoolean("isMicMute");
                        boolean isAudioMute = data.getBoolean("isAudioMute");
                        boolean isEditTextOpen = data.getBoolean("isEditTextOpen");
                        boolean isBluetoothHeadsetConnected = data.getBoolean("isBluetoothHeadsetConnected");
                        while (attributesListeners.size() > 0) {
                            attributesListeners.remove(0).onSuccess(messages, isMicMute, isAudioMute, isEditTextOpen, isBluetoothHeadsetConnected);
                        }
                        return true;
                    }
                    case ON_VOICE_STARTED: {
                        for (int i = 0; i < clientCallbacks.size(); i++) {
                            clientCallbacks.get(i).onVoiceStarted();
                        }
                        return true;
                    }
                    case ON_VOICE_ENDED: {
                        for (int i = 0; i < clientCallbacks.size(); i++) {
                            clientCallbacks.get(i).onVoiceEnded();
                        }
                        return true;
                    }
                    case ON_MESSAGE: {
                        GuiMessage message = data.getParcelable("message");
                        for (int i = 0; i < clientCallbacks.size(); i++) {
                            clientCallbacks.get(i).onMessage(message);
                        }
                        return true;
                    }
                    case ON_CONNECTED_BLUETOOTH_HEADSET: {
                        for (int i = 0; i < clientCallbacks.size(); i++) {
                            clientCallbacks.get(i).onBluetoothHeadsetConnected();
                        }
                        return true;
                    }
                    case ON_DISCONNECTED_BLUETOOTH_HEADSET: {
                        for (int i = 0; i < clientCallbacks.size(); i++) {
                            clientCallbacks.get(i).onBluetoothHeadsetDisconnected();
                        }
                        return true;
                    }
                    case ON_ERROR: {
                        int[] reasons = data.getIntArray("reasons");
                        long value = data.getLong("value");
                        for (int i = 0; i < clientCallbacks.size(); i++) {
                            clientCallbacks.get(i).onError(reasons, value);
                        }
                        return true;
                    }
                }
            }
            return false;
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

        // commands

        public void getAttributes(AttributesListener responseListener) {
            attributesListeners.add(responseListener);
            if (attributesListeners.size() == 1) {
                Bundle bundle = new Bundle();
                bundle.putInt("command", GET_ATTRIBUTES);
                super.sendToService(bundle);
            }
        }

        public void startMic() {
            Bundle bundle = new Bundle();
            bundle.putInt("command", START_MIC);
            super.sendToService(bundle);
        }

        public void stopMic(boolean permanent) {  //permanent=true se l' ha settato l' utente anzichè essere un automatismo
            Bundle bundle = new Bundle();
            bundle.putInt("command", STOP_MIC);
            bundle.putBoolean("permanent", permanent);
            super.sendToService(bundle);
        }

        public void startSound() {
            Bundle bundle = new Bundle();
            bundle.putInt("command", START_SOUND);
            super.sendToService(bundle);
        }

        public void stopSound() {
            Bundle bundle = new Bundle();
            bundle.putInt("command", STOP_SOUND);
            super.sendToService(bundle);
        }

        public void setEditTextOpen(boolean editTextOpen) {
            Bundle bundle = new Bundle();
            bundle.putInt("command", SET_EDIT_TEXT_OPEN);
            bundle.putBoolean("value", editTextOpen);
            super.sendToService(bundle);
        }

        public void receiveText(String text) {
            Bundle bundle = new Bundle();
            bundle.putInt("command", RECEIVE_TEXT);
            bundle.putString("text", text);
            super.sendToService(bundle);
        }

        public void addCallback(ServiceCallback callback) {
            clientCallbacks.add((VoiceTranslationServiceCallback) callback);
        }

        public int removeCallback(ServiceCallback callback) {
            clientCallbacks.remove(callback);
            return clientCallbacks.size();
        }
    }

    public static abstract class VoiceTranslationServiceCallback extends ServiceCallback {
        public void onVoiceStarted() {
        }

        public void onVoiceEnded() {
        }

        public void onMessage(GuiMessage message) {
        }

        public void onBluetoothHeadsetConnected() {
        }

        public void onBluetoothHeadsetDisconnected() {
        }
    }

    public interface AttributesListener {
        void onSuccess(ArrayList<GuiMessage> messages, boolean isMicMute, boolean isAudioMute, boolean isEditTextOpen, boolean isBluetoothHeadsetConnected);
    }

    protected abstract class VoiceTranslationServiceRecognizerListener implements RecognizerListener {
        @Override
        public void onError(int[] reasons, long value) {
            notifyError(reasons, value);
        }
    }
}
