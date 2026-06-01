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

package nie.translator.rtranslator.voice_translation._walkie_talkie_mode._walkie_talkie;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.settings.SettingsActivity;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.ErrorCodes;
import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.tools.gui.AnimatedTextView;
import nie.translator.rtranslator.tools.gui.ButtonMic;
import nie.translator.rtranslator.tools.gui.ButtonSound;
import nie.translator.rtranslator.tools.gui.DeactivableButton;
import nie.translator.rtranslator.tools.gui.LanguageListAdapter;
import nie.translator.rtranslator.tools.gui.messages.GuiMessage;
import nie.translator.rtranslator.tools.gui.messages.MessagesAdapter;
import nie.translator.rtranslator.tools.services_communication.ServiceCommunicator;
import nie.translator.rtranslator.tools.services_communication.ServiceCommunicatorListener;
import nie.translator.rtranslator.voice_translation.VoiceTranslationFragment;
import nie.translator.rtranslator.voice_translation.VoiceTranslationService;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;


public class WalkieTalkieFragment extends VoiceTranslationFragment {
    public static final int INITIALIZE = 0;
    public static final long LONG_PRESS_THRESHOLD_MS = 700;
    private boolean isMicAutomatic = true;
    private ConstraintLayout container;
    protected ButtonMic microphone;
    private ButtonMic leftMicrophone;
    private ButtonMic rightMicrophone;
    private AnimatedTextView leftMicLanguage;
    private AnimatedTextView rightMicLanguage;
    private ConstraintLayout constraintLayout;
    private AppCompatImageButton exitButton;
    private ConstraintLayout firstLanguageSelector;
    private ConstraintLayout secondLanguageSelector;
    private AppCompatImageButton settingsButton;
    private ButtonSound sound;
    private long lastPressedLeftMic = -1;
    private long lastPressedRightMic = -1;
    //connection
    protected WalkieTalkieService.WalkieTalkieServiceCommunicator walkieTalkieServiceCommunicator;
    protected VoiceTranslationService.VoiceTranslationServiceCallback walkieTalkieServiceCallback;

    //languageListDialog
    private LanguageListAdapter listView;
    private ListView listViewGui;
    private ProgressBar progressBar;
    private ImageButton reloadButton;
    private String selectedLanguageCode;
    private AlertDialog dialog;
    private Handler mHandler = new Handler();

    public WalkieTalkieFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        walkieTalkieServiceCommunicator = new WalkieTalkieService.WalkieTalkieServiceCommunicator(0);
        walkieTalkieServiceCallback = new WalkieTalkieServiceCallback();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_walkie_talkie, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        constraintLayout = view.findViewById(R.id.container);
        container = view.findViewById(R.id.walkie_talkie_main_container);
        firstLanguageSelector = view.findViewById(R.id.firstLanguageSelector);
        secondLanguageSelector = view.findViewById(R.id.secondLanguageSelector);
        exitButton = view.findViewById(R.id.exitButton);
        sound = view.findViewById(R.id.soundButton);
        microphone = view.findViewById(R.id.buttonMic);
        microphone.initialize(this, view.findViewById(R.id.leftLine), view.findViewById(R.id.centerLine), view.findViewById(R.id.rightLine));
        leftMicrophone = view.findViewById(R.id.buttonMicLeft);
        leftMicrophone.initialize(null, view.findViewById(R.id.leftLineL), view.findViewById(R.id.centerLineL), view.findViewById(R.id.rightLineL));
        rightMicrophone = view.findViewById(R.id.buttonMicRight);
        rightMicrophone.initialize(null, view.findViewById(R.id.leftLineR), view.findViewById(R.id.centerLineR), view.findViewById(R.id.rightLineR));
        leftMicLanguage = view.findViewById(R.id.textButton1);
        rightMicLanguage = view.findViewById(R.id.textButton2);
        settingsButton = view.findViewById(R.id.settingsButton);
        description.setText(R.string.description_walkie_talkie);
        deactivateInputs(DeactivableButton.DEACTIVATED);
        //container.setVisibility(View.INVISIBLE);  //we make the UI invisible until the restore of the attributes from the service (to avoid instant changes of the UI).
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
        final View.OnClickListener micMissingClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(activity, R.string.error_missing_mic_permissions, Toast.LENGTH_SHORT).show();
            }
        };
        Toolbar toolbar = activity.findViewById(R.id.toolbarWalkieTalkie);
        activity.setActionBar(toolbar);
        // we give the constraint layout the information on the system measures (status bar etc.), which has the fragmentContainer,
        // because they are not passed to it if started with a Transaction and therefore it overlaps the status bar because it fitsSystemWindows does not work
        WindowInsets windowInsets = activity.getFragmentContainer().getRootWindowInsets();
        if (windowInsets != null) {
            constraintLayout.dispatchApplyWindowInsets(windowInsets.replaceSystemWindowInsets(windowInsets.getSystemWindowInsetLeft(),windowInsets.getSystemWindowInsetTop(),windowInsets.getSystemWindowInsetRight(),0));
        }

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

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

        microphone.setOnClickListenerForActivated(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (microphone.getState() == ButtonMic.STATE_NORMAL) {
                    if(isMicAutomatic) {
                        if (microphone.isMute()) {
                            startMicrophone(true);
                        } else {
                            stopMicrophone(true);
                        }
                    }else{
                        switchMicMode(true);
                    }
                }
            }
        });
        microphone.setOnClickListenerForDeactivatedForMissingMicPermission(micMissingClickListener);
        microphone.setOnClickListenerForDeactivated(deactivatedClickListener);

        leftMicrophone.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:  // PRESSED
                        if (leftMicrophone.getActivationStatus() == DeactivableButton.ACTIVATED && leftMicrophone.getState() == ButtonMic.STATE_NORMAL) {
                            if(isMicAutomatic) {
                                switchMicMode(false);
                            }
                            if(!leftMicrophone.isListening()){
                                walkieTalkieServiceCommunicator.startRecognizingFirstLanguage();
                                //leftMicrophone.onVoiceStarted();
                            }else{
                                //leftMicrophone.onVoiceEnded();
                                walkieTalkieServiceCommunicator.stopRecognizingFirstLanguage();
                            }
                            lastPressedLeftMic = System.currentTimeMillis();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:  // RELEASED
                        if(leftMicrophone.getActivationStatus() == DeactivableButton.ACTIVATED){
                            if(leftMicrophone.getState() == ButtonMic.STATE_NORMAL && lastPressedLeftMic != -1){
                                if(System.currentTimeMillis() - lastPressedLeftMic <= LONG_PRESS_THRESHOLD_MS){  //short click release

                                }else{   //long click release
                                    if(leftMicrophone.isListening()){
                                        //leftMicrophone.onVoiceEnded();
                                        walkieTalkieServiceCommunicator.stopRecognizingFirstLanguage();
                                    }
                                }
                            }
                        }else{
                            leftMicrophone.performClick();
                        }
                        lastPressedLeftMic = -1;
                        return true;
                }
                return false;
            }
        });
        leftMicrophone.setOnClickListenerForDeactivatedForMissingMicPermission(micMissingClickListener);
        leftMicrophone.setOnClickListenerForDeactivated(deactivatedClickListener);

        rightMicrophone.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:  // PRESSED
                        if (rightMicrophone.getActivationStatus() == DeactivableButton.ACTIVATED && rightMicrophone.getState() == ButtonMic.STATE_NORMAL) {
                            if(isMicAutomatic) {
                                switchMicMode(false);
                            }
                            if(!rightMicrophone.isListening()){
                                //rightMicrophone.onVoiceStarted();
                                walkieTalkieServiceCommunicator.startRecognizingSecondLanguage();
                            }else{
                                walkieTalkieServiceCommunicator.stopRecognizingSecondLanguage();
                            }
                            lastPressedRightMic = System.currentTimeMillis();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:  // RELEASED
                        if(rightMicrophone.getActivationStatus() == DeactivableButton.ACTIVATED){
                            if(rightMicrophone.getState() == ButtonMic.STATE_NORMAL && lastPressedRightMic != -1){
                                if(System.currentTimeMillis() - lastPressedRightMic <= LONG_PRESS_THRESHOLD_MS){  //short click release

                                }else{   //long click release
                                    if(rightMicrophone.isListening()){
                                        walkieTalkieServiceCommunicator.stopRecognizingSecondLanguage();
                                    }
                                }
                            }
                        }else{
                            rightMicrophone.performClick();
                        }
                        lastPressedRightMic = -1;
                        return true;
                }
                return false;
            }
        });
        rightMicrophone.setOnClickListenerForDeactivatedForMissingMicPermission(micMissingClickListener);
        rightMicrophone.setOnClickListenerForDeactivated(deactivatedClickListener);

        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("button", "exitButton pressed");
                activity.onBackPressed();
            }
        });
    }

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
        activity.connectToWalkieTalkieService(walkieTalkieServiceCallback, new ServiceCommunicatorListener() {
            @Override
            public void onServiceCommunicator(ServiceCommunicator serviceCommunicator) {
                walkieTalkieServiceCommunicator = (WalkieTalkieService.WalkieTalkieServiceCommunicator) serviceCommunicator;
                restoreAttributesFromService();
                // listener setting for the two language selectors
                firstLanguageSelector.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showLanguageListDialog(1);
                    }
                });
                secondLanguageSelector.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showLanguageListDialog(2);
                    }
                });

                // setting of the selected languages
                walkieTalkieServiceCommunicator.getFirstLanguage(new WalkieTalkieService.LanguageListener() {
                    @Override
                    public void onLanguage(CustomLocale language) {
                        setFirstLanguage(language);
                    }
                });
                walkieTalkieServiceCommunicator.getSecondLanguage(new WalkieTalkieService.LanguageListener() {
                    @Override
                    public void onLanguage(CustomLocale language) {
                        setSecondLanguage(language);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                WalkieTalkieFragment.super.onFailureConnectingWithService(reasons, value);
            }
        });
    }

    @Override
    public void restoreAttributesFromService() {
        walkieTalkieServiceCommunicator.getAttributes(new VoiceTranslationService.AttributesListener() {
            @Override
            public void onSuccess(ArrayList<GuiMessage> messages, boolean isMicMute, boolean isAudioMute, boolean isTTSError, final boolean isEditTextOpen, boolean isBluetoothHeadsetConnected, boolean isMicAutomatic, boolean isMicActivated, long speakingUtteranceId, int listeningMic) {
                // initialization with service values
                //container.setVisibility(View.VISIBLE);
                mAdapter = new MessagesAdapter(messages, global, speakingUtteranceId, new MessagesAdapter.Callback() {
                    @Override
                    public void onFirstItemAdded() {
                        description.setVisibility(View.GONE);
                        mRecyclerView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onTTSButtonClick(final GuiMessage message, final boolean play) {
                        if(play){
                            final String textToSpeak = message.getMessage().getText();
                            final String utteranceId = String.valueOf(message.getMessageID());
                            String localeCode = message.isMine() ? global.getSecondLanguage(true).getCode() : global.getFirstLanguage(true).getCode();
                            walkieTalkieServiceCommunicator.speakText(textToSpeak, localeCode, utteranceId);
                        }else{
                            walkieTalkieServiceCommunicator.stopSpeakingText();
                        }
                    }
                });
                mRecyclerView.setAdapter(mAdapter);
                // restore microphone and sound status
                if(isMicAutomatic) {
                    microphone.setMute(isMicMute, false);
                    leftMicrophone.setMute(true, false);
                    rightMicrophone.setMute(true, false);
                    if(isMicActivated) {
                        if (listeningMic == VoiceTranslationService.AUTO_LANGUAGE) {
                            microphone.onVoiceStarted(false);
                        } else {
                            microphone.onVoiceEnded(false);
                        }
                    }else{
                        microphone.onVoiceEnded(false);
                    }
                    leftMicrophone.onVoiceEnded(false);
                    rightMicrophone.onVoiceEnded(false);
                }else{
                    WalkieTalkieFragment.this.isMicAutomatic = false;
                    microphone.setMute(true, false);
                    leftMicrophone.setMute(false, false);
                    rightMicrophone.setMute(false, false);
                    if(isMicActivated) {
                        if (listeningMic == VoiceTranslationService.FIRST_LANGUAGE) {
                            leftMicrophone.onVoiceStarted(false);
                        } else {
                            leftMicrophone.onVoiceEnded(false);
                        }
                        if (listeningMic == VoiceTranslationService.SECOND_LANGUAGE) {
                            rightMicrophone.onVoiceStarted(false);
                        } else {
                            rightMicrophone.onVoiceEnded(false);
                        }
                    }else{
                        leftMicrophone.onVoiceEnded(false);
                        rightMicrophone.onVoiceEnded(false);
                    }
                    microphone.onVoiceEnded(false);
                }

                sound.setMute(isAudioMute);
                if(isTTSError){
                    sound.deactivate(DeactivableButton.DEACTIVATED_FOR_TTS_ERROR);
                }

                if(isMicActivated){
                    if (!microphone.isMute()) {
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
        walkieTalkieServiceCommunicator.startMic();
    }

    @Override
    public void stopMicrophone(boolean changeAspect) {
        if (changeAspect) {
            microphone.setMute(true);
        }
        walkieTalkieServiceCommunicator.stopMic(changeAspect);
    }

    protected void startSound() {
        sound.setMute(false);
        walkieTalkieServiceCommunicator.startSound();
    }

    protected void stopSound() {
        sound.setMute(true);
        walkieTalkieServiceCommunicator.stopSound();
    }

    @Override
    protected void deactivateInputs(int cause) {
        microphone.deactivate(cause);
        leftMicrophone.deactivate(cause);
        rightMicrophone.deactivate(cause);
        if (cause == DeactivableButton.DEACTIVATED) {
            sound.deactivate(DeactivableButton.DEACTIVATED);
        } else {
            sound.activate(false);  // to activate the button sound which otherwise remains deactivated and when clicked it shows the message "wait for initialisation"
        }
    }

    @Override
    protected void activateInputs(boolean start) {
        Log.d("mic", "activatedInputs");
        microphone.activate(start);
        leftMicrophone.activate(false);
        rightMicrophone.activate(false);
        sound.activate(false);
    }

    private void switchMicMode(boolean automatic){
        if(isMicAutomatic != automatic){
            //walkieTalkieServiceCallback.onVoiceEnded();
            isMicAutomatic = automatic;
            if(!isMicAutomatic){  //we switched from automatic to manual
                microphone.setMute(true);
                leftMicrophone.setMute(false);
                rightMicrophone.setMute(false);
                walkieTalkieServiceCommunicator.startManualRecognition();
            }else{
                walkieTalkieServiceCommunicator.stopManualRecognition();
                microphone.setMute(false);
                leftMicrophone.setMute(true);
                rightMicrophone.setMute(true);
            }
        }
    }


    private void showLanguageListDialog(final int languageNumber) {
        //when the dialog is shown at the beginning the loading is shown, then once the list of languages​is obtained (within the showList)
        //the loading is replaced with the list of languages
        String title = "";
        switch (languageNumber) {
            case 1: {
                title = global.getResources().getString(R.string.dialog_select_first_language);
                break;
            }
            case 2: {
                title = global.getResources().getString(R.string.dialog_select_second_language);
                break;
            }
        }

        final View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_languages, null);

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle(title);

        dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 16), 0, 0);
        dialog.show();

        listViewGui = editDialogLayout.findViewById(R.id.list_view_dialog);
        progressBar = editDialogLayout.findViewById(R.id.progressBar3);
        reloadButton = editDialogLayout.findViewById(R.id.reloadButton);

        CustomLocale result;
        if (languageNumber == 1) {
            result = global.getFirstLanguage(false);
        } else {  //languageNumber == 2
            result = global.getSecondLanguage(false);
        }

        reloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showList(languageNumber, result);
            }
        });
        showList(languageNumber, result);
    }

    private void showList(final int languageNumber, final CustomLocale selectedLanguage) {
        reloadButton.setVisibility(View.GONE);
        //progressBar.setVisibility(View.VISIBLE);

        final ArrayList<CustomLocale> languages = global.getLanguages(true);
        //progressBar.setVisibility(View.GONE);
        listViewGui.setVisibility(View.VISIBLE);

        listView = new LanguageListAdapter(activity, languages, selectedLanguage);
        listViewGui.setAdapter(listView);
        listViewGui.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                if (languages.contains((CustomLocale) listView.getItem(position))) {
                    switch (languageNumber) {
                        case 1: {
                            setFirstLanguage((CustomLocale) listView.getItem(position));
                            break;
                        }
                        case 2: {
                            setSecondLanguage((CustomLocale) listView.getItem(position));
                            break;
                        }
                    }
                }
                dialog.dismiss();
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        mHandler.removeCallbacksAndMessages(null);
        firstLanguageSelector.setOnClickListener(null);
        secondLanguageSelector.setOnClickListener(null);
        activity.disconnectFromWalkieTalkieService(walkieTalkieServiceCommunicator);
    }

    private void setFirstLanguage(CustomLocale language) {
        // new language setting in the WalkieTalkieService
        walkieTalkieServiceCommunicator.changeFirstLanguage(language);
        // save firstLanguage selected
        global.setFirstLanguage(language, null);
        // change language displayed
        ((AnimatedTextView) firstLanguageSelector.findViewById(R.id.firstLanguageName)).setText(language.getDisplayNameWithoutTTS(), true);
        leftMicLanguage.setText(language.getDisplayNameWithoutTTS(), true);
    }

    private void setSecondLanguage(CustomLocale language) {
        // new language setting in the WalkieTalkieService
        walkieTalkieServiceCommunicator.changeSecondLanguage(language);
        // save secondLanguage selected
        global.setSecondLanguage(language, null);
        // change language displayed
        ((AnimatedTextView) secondLanguageSelector.findViewById(R.id.secondLanguageName)).setText(language.getDisplayNameWithoutTTS(), true);
        rightMicLanguage.setText(language.getDisplayNameWithoutTTS(), true);
    }

    private void onFailureShowingList(int[] reasons, long value) {
        progressBar.setVisibility(View.GONE);
        reloadButton.setVisibility(View.VISIBLE);
        for (int aReason : reasons) {
            switch (aReason) {
                case ErrorCodes.MISSED_ARGUMENT:
                case ErrorCodes.SAFETY_NET_EXCEPTION:
                case ErrorCodes.MISSED_CONNECTION:
                    Toast.makeText(activity, getResources().getString(R.string.error_internet_lack_loading_languages), Toast.LENGTH_LONG).show();
                    break;
                default:
                    activity.onError(aReason, value);
                    break;
            }
        }
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



    public class WalkieTalkieServiceCallback extends VoiceTranslationService.VoiceTranslationServiceCallback {
        @Override
        public void onTTSStarted(String utteranceId) {
            super.onTTSStarted(utteranceId);
            if(utteranceId != null) {
                long id = Long.parseLong(utteranceId);
                mAdapter.setPlayingMessageID(id);
            }
        }

        @Override
        public void onTTSDone(String utteranceId) {
            super.onTTSDone(utteranceId);
            if (utteranceId != null) {
                mAdapter.setPlayingMessageID(-1);
            }
        }

        @Override
        public void onVoiceStarted(int mode) {
            super.onVoiceStarted(mode);
            if(mode == VoiceTranslationService.AUTO_LANGUAGE && !microphone.isMute()) {
                microphone.onVoiceStarted(true);
                Log.e("onVoiceStart", "onVoiceStart center");
            }else if(mode == VoiceTranslationService.FIRST_LANGUAGE && !leftMicrophone.isMute()) {
                leftMicrophone.onVoiceStarted(true);
                Log.e("onVoiceStart", "onVoiceStart left");
            }else if(mode == VoiceTranslationService.SECOND_LANGUAGE && !rightMicrophone.isMute()) {
                rightMicrophone.onVoiceStarted(true);
                Log.e("onVoiceStart", "onVoiceStart right");
            }
        }

        @Override
        public void onVoiceEnded() {
            super.onVoiceEnded();
            microphone.onVoiceEnded(true);
            leftMicrophone.onVoiceEnded(true);
            rightMicrophone.onVoiceEnded(true);
        }

        @Override
        public void onVolumeLevel(float volumeLevel) {
            super.onVolumeLevel(volumeLevel);
            if(microphone.isListening()) {
                microphone.updateVolumeLevel(volumeLevel);
            }else if(leftMicrophone.isListening()){
                leftMicrophone.updateVolumeLevel(volumeLevel);
            }else if(rightMicrophone.isListening()){
                rightMicrophone.updateVolumeLevel(volumeLevel);
            }
        }

        @Override
        public void onMicActivated() {
            super.onMicActivated();
            Log.d("mic", "onMicActivated");
            if(!microphone.isActivated()) {
                microphone.activate(false);
            }
            if(!leftMicrophone.isActivated()) {
                leftMicrophone.activate(false);
            }
            if(!rightMicrophone.isActivated()) {
                rightMicrophone.activate(false);
            }
        }

        @Override
        public void onMicDeactivated() {
            super.onMicDeactivated();
            if (microphone.getState() == ButtonMic.STATE_NORMAL && microphone.isActivated()) {
                microphone.deactivate(DeactivableButton.DEACTIVATED);
            }
            if (leftMicrophone.getState() == ButtonMic.STATE_NORMAL && leftMicrophone.isActivated()) {
                leftMicrophone.deactivate(DeactivableButton.DEACTIVATED);
            }
            if (rightMicrophone.getState() == ButtonMic.STATE_NORMAL && rightMicrophone.isActivated()) {
                rightMicrophone.deactivate(DeactivableButton.DEACTIVATED);
            }
        }

        @Override
        public void onMessage(GuiMessage message) {
            super.onMessage(message);
            if (message != null) {
                int messageIndex = mAdapter.getMessageIndex(message.getMessageID());
                if(messageIndex != -1) {
                    if((!mRecyclerView.isAnimating() && !mRecyclerView.getLayoutManager().isSmoothScrolling()) || message.isFinal()) {
                        if(message.isFinal()){
                            if(mRecyclerView.getItemAnimator() != null) {
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