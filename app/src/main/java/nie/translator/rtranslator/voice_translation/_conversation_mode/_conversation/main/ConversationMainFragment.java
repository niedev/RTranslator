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

package nie.translator.rtranslator.voice_translation._conversation_mode._conversation.main;


import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.ErrorCodes;
import nie.translator.rtranslator.tools.gui.ButtonKeyboard;
import nie.translator.rtranslator.tools.gui.ButtonMic;
import nie.translator.rtranslator.tools.gui.ButtonSound;
import nie.translator.rtranslator.tools.gui.DeactivableButton;
import nie.translator.rtranslator.tools.gui.messages.GuiMessage;
import nie.translator.rtranslator.tools.gui.messages.MessagesAdapter;
import nie.translator.rtranslator.tools.services_communication.ServiceCommunicator;
import nie.translator.rtranslator.tools.services_communication.ServiceCommunicatorListener;
import nie.translator.rtranslator.voice_translation.VoiceTranslationFragment;
import nie.translator.rtranslator.voice_translation.VoiceTranslationService;
import nie.translator.rtranslator.voice_translation._conversation_mode._conversation.ConversationService;


public class ConversationMainFragment extends VoiceTranslationFragment {
    private boolean isEditTextOpen = false;
    private boolean isInputActive = true;
    private ConstraintLayout container;
    private TextView micInput;
    private ButtonKeyboard keyboard;
    protected ButtonMic microphone;
    private ButtonSound sound;
    private EditText editText;
    private ImageButton micPlaceHolder;
    private Handler mHandler = new Handler();
    //connection
    protected VoiceTranslationService.VoiceTranslationServiceCommunicator conversationServiceCommunicator;
    protected VoiceTranslationService.VoiceTranslationServiceCallback conversationServiceCallback;
    
    // tts UI states
    private android.widget.ImageView currentPlayingIcon = null;
    private String currentPlayingUtteranceId = null;

    public ConversationMainFragment() {
        //an empty constructor is always needed for fragments
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        conversationServiceCommunicator = new ConversationService.ConversationServiceCommunicator(0);
        conversationServiceCallback = new ConversationServiceCallback() {
            @Override
            public void onBluetoothHeadsetConnected() {
                super.onBluetoothHeadsetConnected();
                if(getContext() != null && micInput != null) {
                    micInput.setText(getResources().getString(R.string.btHeadset));
                }
            }

            @Override
            public void onBluetoothHeadsetDisconnected() {
                super.onBluetoothHeadsetDisconnected();
                if(getContext() != null && micInput != null) {
                    micInput.setText(getResources().getString(R.string.mic));
                }
            }
        };
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_conversation_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        container = view.findViewById(R.id.conversation_main_container);
        micInput = view.findViewById(R.id.inputMicType);
        keyboard = view.findViewById(R.id.buttonKeyboard);
        microphone = view.findViewById(R.id.buttonMic);
        sound = view.findViewById(R.id.buttonSound);
        editText = view.findViewById(R.id.editText);
        micPlaceHolder = view.findViewById(R.id.buttonPlaceHolder);
        microphone.initialize(this, view.findViewById(R.id.leftLine), view.findViewById(R.id.centerLine), view.findViewById(R.id.rightLine));
        microphone.setEditText(editText);
        deactivateInputs(DeactivableButton.DEACTIVATED);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                if ((text == null || text.length() == 0) && microphone.getState() == ButtonMic.STATE_SEND) {
                    microphone.setState(ButtonMic.STATE_RETURN);
                } else if (microphone.getState() == ButtonMic.STATE_RETURN) {
                    microphone.setState(ButtonMic.STATE_SEND);
                }
            }
        });
        microphone.setMicInput(micInput);
        description.setText(R.string.description_conversation);
        container.setVisibility(View.INVISIBLE);  //we make the UI invisible until the restore of the attributes from the service (to avoid instant changes of the UI).
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final View.OnClickListener deactivatedClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Toast.makeText(activity, getResources().getString(R.string.error_wait_initialization), Toast.LENGTH_SHORT).show();
            }
        };
        sound.setOnClickListenerForDeactivated(deactivatedClickListener);
        sound.setOnClickListenerForTTSError(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(global, R.string.error_tts_toast, Toast.LENGTH_SHORT).show();
            }
        });
        sound.setOnClickListenerForActivated(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (sound.isMute()) {
                    startSound();
                } else {
                    stopSound();
                }
            }
        });

        keyboard.setOnClickListenerForActivated(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isEditTextOpen = true;
                keyboard.generateEditText(activity, ConversationMainFragment.this, microphone, editText, micPlaceHolder, true);
                conversationServiceCommunicator.setEditTextOpen(true);
            }
        });

        microphone.setOnClickListenerForActivated(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (microphone.getState()) {
                    case ButtonMic.STATE_NORMAL:
                        if (microphone.isMute()) {
                            startMicrophone(true);
                        } else {
                            stopMicrophone(true);
                        }
                        break;
                    case ButtonMic.STATE_RETURN:
                        isEditTextOpen = false;
                        conversationServiceCommunicator.setEditTextOpen(false);
                        microphone.deleteEditText(activity, ConversationMainFragment.this, keyboard, editText, micPlaceHolder);
                        break;
                    case ButtonMic.STATE_SEND:
                        // sending the message to be translated to the service
                        String text = editText.getText().toString();
                        /*if(text.length() <= 1){   //test code
                            text = "Also unlike 2014, there aren’t nearly as many loopholes. You can’t just buy a 150-watt incandescent or a three-way bulb, the ban covers any normal bulb that generates less than 45 lumens per watt, which pretty much rules out both incandescent and halogen tech in their entirety.";
                        }*/
                        if (!text.isEmpty()) {
                            conversationServiceCommunicator.receiveText(text);
                            editText.setText("");
                        }
                        break;
                }
            }
        });
        microphone.setOnClickListenerForDeactivatedForMissingMicPermission(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (microphone.getState() == ButtonMic.STATE_RETURN) {
                    isEditTextOpen = false;
                    conversationServiceCommunicator.setEditTextOpen(false);
                    microphone.deleteEditText(activity, ConversationMainFragment.this, keyboard, editText, micPlaceHolder);
                } else {
                    Toast.makeText(activity, R.string.error_missing_mic_permissions, Toast.LENGTH_SHORT).show();
                }
            }
        });
        microphone.setOnClickListenerForDeactivated(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (microphone.getState() == ButtonMic.STATE_RETURN) {
                    isEditTextOpen = false;
                    conversationServiceCommunicator.setEditTextOpen(false);
                    microphone.deleteEditText(activity, ConversationMainFragment.this, keyboard, editText, micPlaceHolder);
                } else {
                    deactivatedClickListener.onClick(v);
                }
            }
        });
    }

    /*@Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if(savedInstanceState!=null){
            //si riapre l' editText se era aperto al momento della distuzione dell' activity per motivi di sistema (es. rotazione o multi-windows)
            isEditTextOpen=savedInstanceState.getBoolean("isEditTextOpen");
            if(isEditTextOpen){
                keyboard.generateEditText(activity,microphone,editText,false);
            }
        }
    }*/

    @Override
    public void onStart() {
        super.onStart();
        if (getArguments() != null) {
            if (getArguments().getBoolean("firstStart", false)) {
                getArguments().remove("firstStart");
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        connectToService();
                    }
                }, 300);
            } else {
                connectToService();
            }
        } else {
            connectToService();
        }
    }

    @Override
    protected void connectToService() {
        activity.connectToConversationService(conversationServiceCallback, new ServiceCommunicatorListener() {
            @Override
            public void onServiceCommunicator(ServiceCommunicator serviceCommunicator) {
                conversationServiceCommunicator = (ConversationService.ConversationServiceCommunicator) serviceCommunicator;
                restoreAttributesFromService();
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                ConversationMainFragment.this.onFailureConnectingWithService(reasons, value);
            }
        });
    }

    @Override
    public void restoreAttributesFromService() {
        conversationServiceCommunicator.getAttributes(new VoiceTranslationService.AttributesListener() {
            @Override
            public void onSuccess(ArrayList<GuiMessage> messages, boolean isMicMute, boolean isAudioMute, boolean isTTSError, final boolean isEditTextOpen, boolean isBluetoothHeadsetConnected, boolean isMicAutomatic, boolean isMicActivated, int listeningMic) {
                container.setVisibility(View.VISIBLE);
                // initialization with service values
                mAdapter = new MessagesAdapter(messages, global, new MessagesAdapter.Callback() {
                    @Override
                    public void onFirstItemAdded() {
                        description.setVisibility(View.GONE);
                        mRecyclerView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onPlayAudio(final GuiMessage message, final android.widget.ImageView icon) {
                        if (icon.getTag() != null && ((int) icon.getTag()) == R.drawable.stop_icon) {
                            conversationServiceCommunicator.stopSpeakingText();
                            icon.setImageResource(R.drawable.sound_icon);
                            icon.setTag(R.drawable.sound_icon);
                            currentPlayingIcon = null;
                            currentPlayingUtteranceId = null;
                            return;
                        }

                        if (currentPlayingIcon != null) {
                            currentPlayingIcon.setImageResource(R.drawable.sound_icon);
                            currentPlayingIcon.setTag(R.drawable.sound_icon);
                            conversationServiceCommunicator.stopSpeakingText();
                        }

                        icon.setImageResource(R.drawable.stop_icon);
                        icon.setTag(R.drawable.stop_icon);
                        currentPlayingIcon = icon;

                        final String textToSpeak = message.getMessage().getText();
                        final String utteranceId = String.valueOf(System.currentTimeMillis());
                        currentPlayingUtteranceId = utteranceId;

                        global.getFirstAndSecondLanguages(true, new nie.translator.rtranslator.Global.GetTwoLocaleListener() {
                            @Override
                            public void onSuccess(nie.translator.rtranslator.tools.CustomLocale language1, nie.translator.rtranslator.tools.CustomLocale language2) {
                                String localeCode = message.isMine() ? language2.getCode() : language1.getCode();
                                conversationServiceCommunicator.speakText(textToSpeak, localeCode, utteranceId);
                            }
                            @Override
                            public void onFailure(int[] reasons, long value) {}
                        });
                    }
                });
                mRecyclerView.setAdapter(mAdapter);
                // restore microphone and sound status
                microphone.setMute(isMicMute, false);
                if(isMicActivated) {
                    if (listeningMic == VoiceTranslationService.AUTO_LANGUAGE) {
                        microphone.onVoiceStarted(false);
                    } else {
                        microphone.onVoiceEnded(false);
                    }
                }else{
                    microphone.onVoiceEnded(false);
                }
                sound.setMute(isAudioMute);
                if(isTTSError){
                    sound.deactivate(DeactivableButton.DEACTIVATED_FOR_TTS_ERROR);
                }
                // restore editText
                ConversationMainFragment.this.isEditTextOpen = isEditTextOpen;
                if (isEditTextOpen) {
                    keyboard.generateEditText(activity, ConversationMainFragment.this, microphone, editText, micPlaceHolder, false);
                }
                if (isBluetoothHeadsetConnected) {
                    conversationServiceCallback.onBluetoothHeadsetConnected();
                } else {
                    conversationServiceCallback.onBluetoothHeadsetDisconnected();
                }

                if(isMicActivated){
                    if (!microphone.isMute() && !isEditTextOpen) {
                        activateInputs(true);
                    } else {
                        activateInputs(false);
                    }
                }else{
                    deactivateInputs(DeactivableButton.DEACTIVATED);
                }
            }
        });
    }

    @Override
    public void startMicrophone(boolean changeAspect) {
        if (changeAspect) {
            microphone.setMute(false);
        }
        conversationServiceCommunicator.startMic();
    }

    @Override
    public void stopMicrophone(boolean changeAspect) {
        if (changeAspect) {
            microphone.setMute(true);
        }
        conversationServiceCommunicator.stopMic(changeAspect);
    }

    protected void startSound() {
        sound.setMute(false);
        conversationServiceCommunicator.startSound();
    }

    protected void stopSound() {
        sound.setMute(true);
        conversationServiceCommunicator.stopSound();
    }

    @Override
    protected void deactivateInputs(int cause) {
        microphone.deactivate(cause);
        if (cause == DeactivableButton.DEACTIVATED) {
            sound.deactivate(DeactivableButton.DEACTIVATED);
        } else {
            sound.activate(false);  // to activate the button sound which otherwise remains deactivated and when clicked it shows the message "wait for initialisation"
        }
    }

    @Override
    protected void activateInputs(boolean start) {
        microphone.activate(start);
        sound.activate(start);
    }

    public boolean isEditTextOpen() {
        return isEditTextOpen;
    }

    public void deleteEditText() {
        isEditTextOpen = false;
        conversationServiceCommunicator.setEditTextOpen(false);
        microphone.deleteEditText(activity, this, keyboard, editText, micPlaceHolder);
    }

    public boolean isInputActive() {
        return isInputActive;
    }

    public void setInputActive(boolean inputActive) {
        isInputActive = inputActive;
    }

    /**
     * Handles user acceptance (or denial) of our permission request.
     */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != VoiceTranslationService.REQUEST_CODE_REQUIRED_PERMISSIONS) {
            return;
        }

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(activity, R.string.error_missing_mic_permissions, Toast.LENGTH_LONG).show();
                deactivateInputs(DeactivableButton.DEACTIVATED_FOR_MISSING_MIC_PERMISSION);
                return;
            }
        }

        // possible activation of the mic
        if (!microphone.isMute() && microphone.getActivationStatus() == DeactivableButton.ACTIVATED) {
            startMicrophone(false);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mHandler.removeCallbacksAndMessages(null);
        activity.disconnectFromConversationService((ConversationService.ConversationServiceCommunicator) conversationServiceCommunicator);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }



    public class ConversationServiceCallback extends VoiceTranslationService.VoiceTranslationServiceCallback {
        @Override
        public void onTTSDone(String utteranceId) {
            super.onTTSDone(utteranceId);
            if (utteranceId != null && utteranceId.equals(currentPlayingUtteranceId)) {
                if (currentPlayingIcon != null) {
                    currentPlayingIcon.setImageResource(R.drawable.sound_icon);
                    currentPlayingIcon.setTag(R.drawable.sound_icon);
                    currentPlayingIcon = null;
                }
                currentPlayingUtteranceId = null;
            }
        }

        @Override
        public void onVoiceStarted(int mode) {
            super.onVoiceStarted(mode);
            if(!microphone.isMute()) {
                microphone.onVoiceStarted(true);
            }
        }

        @Override
        public void onVoiceEnded() {
            super.onVoiceEnded();
            microphone.onVoiceEnded(true);
        }

        @Override
        public void onVolumeLevel(float volumeLevel) {
            super.onVolumeLevel(volumeLevel);
            if(microphone.isListening()) {
                microphone.updateVolumeLevel(volumeLevel);
            }
        }

        @Override
        public void onMicActivated() {
            super.onMicActivated();
            if(!microphone.isActivated()) {
                microphone.activate(false);
            }
        }

        @Override
        public void onMicDeactivated() {
            super.onMicDeactivated();
            if(microphone.getState() == ButtonMic.STATE_NORMAL && microphone.isActivated()) {
                microphone.deactivate(DeactivableButton.DEACTIVATED);
            }
        }

        @Override
        public void onMessage(GuiMessage message) {
            super.onMessage(message);
            if (message != null) {
                int messageIndex = mAdapter.getMessageIndex(message.getMessageID());
                if(messageIndex != -1){
                    if((!mRecyclerView.isAnimating() && !mRecyclerView.getLayoutManager().isSmoothScrolling()) || message.isFinal()) {
                        if (message.isFinal()) {
                            if (mRecyclerView.getItemAnimator() != null) {
                                mRecyclerView.getItemAnimator().endAnimations();
                            }
                        }
                        mAdapter.setMessage(messageIndex, message);
                    }
                }else{
                    if(mRecyclerView.getItemAnimator() != null) {
                        mRecyclerView.getItemAnimator().endAnimations();
                    }
                    mAdapter.addMessage(message);
                    //we do an eventual automatic scroll (only if we are at the bottom of the recyclerview)
                    if(((LinearLayoutManager) mRecyclerView.getLayoutManager()).findLastVisibleItemPosition() == mAdapter.getItemCount()-2){
                        mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount()-1);
                    }
                }
            }
        }

        @Override
        public void onError(int[] reasons, long value) {
            for (int aReason : reasons) {
                switch (aReason) {
                    case ErrorCodes.SAFETY_NET_EXCEPTION:
                    case ErrorCodes.MISSED_CONNECTION:
                        activity.showInternetLackDialog(R.string.error_internet_lack_services, null);
                        break;
                    case ErrorCodes.MISSING_GOOGLE_TTS:
                        sound.deactivate(DeactivableButton.DEACTIVATED_FOR_TTS_ERROR);
                        //activity.showMissingGoogleTTSDialog();
                        break;
                    case ErrorCodes.GOOGLE_TTS_ERROR:
                        sound.deactivate(DeactivableButton.DEACTIVATED_FOR_TTS_ERROR);
                        //activity.showGoogleTTSErrorDialog();
                        break;
                    case VoiceTranslationService.MISSING_MIC_PERMISSION: {
                        if(getContext() != null) {
                            requestPermissions(VoiceTranslationService.REQUIRED_PERMISSIONS, VoiceTranslationService.REQUEST_CODE_REQUIRED_PERMISSIONS);
                        }
                        break;
                    }
                    default: {
                        activity.onError(aReason, value);
                        break;
                    }
                }
            }
        }
    }


}
