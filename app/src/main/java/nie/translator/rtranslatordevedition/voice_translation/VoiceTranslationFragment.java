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

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.api_management.ApiManagementActivity;
import nie.translator.rtranslatordevedition.tools.ErrorCodes;
import nie.translator.rtranslatordevedition.tools.gui.ButtonKeyboard;
import nie.translator.rtranslatordevedition.tools.gui.ButtonMic;
import nie.translator.rtranslatordevedition.tools.gui.ButtonSound;
import nie.translator.rtranslatordevedition.tools.gui.DeactivableButton;
import nie.translator.rtranslatordevedition.tools.gui.MicrophoneComunicable;
import nie.translator.rtranslatordevedition.tools.gui.messages.GuiMessage;
import nie.translator.rtranslatordevedition.tools.gui.messages.MessagesAdapter;

public abstract class VoiceTranslationFragment extends Fragment implements MicrophoneComunicable {
    //gui
    private boolean isEditTextOpen = false;
    private boolean isInputActive = true;
    protected VoiceTranslationActivity activity;
    protected Global global;
    private ButtonKeyboard keyboard;
    protected ButtonMic microphone;
    protected TextView description;
    private ButtonSound sound;
    private EditText editText;
    private MessagesAdapter mAdapter;
    private RecyclerView mRecyclerView;
    private RecyclerView.SmoothScroller smoothScroller;
    private View.OnClickListener micClickListener;
    //connection
    protected VoiceTranslationService.VoiceTranslationServiceCommunicator voiceTranslationServiceCommunicator;
    protected VoiceTranslationService.VoiceTranslationServiceCallback voiceTranslationServiceCallback;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRecyclerView = view.findViewById(R.id.recycler_view);
        description = view.findViewById(R.id.description);
        keyboard = view.findViewById(R.id.buttonKeyboard);
        microphone = view.findViewById(R.id.buttonMic);
        sound = view.findViewById(R.id.buttonSound);
        editText = view.findViewById(R.id.editText);
        microphone.setFragment(this);
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
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (VoiceTranslationActivity) requireActivity();
        global = (Global) activity.getApplication();
        LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
        layoutManager.setStackFromEnd(true);
        mRecyclerView.setLayoutManager(layoutManager);
        smoothScroller = new LinearSmoothScroller(activity) {
            @Override
            protected int calculateTimeForScrolling(int dx) {
                return 100;
            }
        };
        //smoothScroller = new LinearSmoothScroller(activity);
        final View.OnClickListener deactivatedClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(activity, getResources().getString(R.string.error_wait_initialization), Toast.LENGTH_SHORT).show();
            }
        };


        sound.setOnClickListenerForDeactivated(deactivatedClickListener);
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
                keyboard.generateEditText(activity, VoiceTranslationFragment.this, microphone, editText, true);
                voiceTranslationServiceCommunicator.setEditTextOpen(true);
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
                        voiceTranslationServiceCommunicator.setEditTextOpen(false);
                        microphone.deleteEditText(activity, VoiceTranslationFragment.this, keyboard, editText);
                        break;
                    case ButtonMic.STATE_SEND:
                        // sending the message to be translated to the service
                        voiceTranslationServiceCommunicator.receiveText(editText.getText().toString());
                        editText.setText("");
                        break;
                }
            }
        });
        microphone.setOnClickListenerForDeactivatedForCreditExhausted(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (microphone.getState() == ButtonMic.STATE_RETURN) {
                    isEditTextOpen = false;
                    voiceTranslationServiceCommunicator.setEditTextOpen(false);
                    microphone.deleteEditText(activity, VoiceTranslationFragment.this, keyboard, editText);
                }
            }
        });
        microphone.setOnClickListenerForDeactivatedForMissingMicPermission(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (microphone.getState() == ButtonMic.STATE_RETURN) {
                    isEditTextOpen = false;
                    voiceTranslationServiceCommunicator.setEditTextOpen(false);
                    microphone.deleteEditText(activity, VoiceTranslationFragment.this, keyboard, editText);
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
                    voiceTranslationServiceCommunicator.setEditTextOpen(false);
                    microphone.deleteEditText(activity, VoiceTranslationFragment.this, keyboard, editText);
                } else {
                    deactivatedClickListener.onClick(v);
                }
            }
        });
        microphone.setOnClickListenerForDeactivatedForMissingOrWrongKeyfile(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (microphone.getState() == ButtonMic.STATE_RETURN) {
                    isEditTextOpen = false;
                    voiceTranslationServiceCommunicator.setEditTextOpen(false);
                    microphone.deleteEditText(activity, VoiceTranslationFragment.this, keyboard, editText);
                } else {
                    Toast.makeText(activity, getResources().getString(R.string.error_invalid_key), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    protected void connectToService() {
    }

    @Override
    public void onStop() {
        super.onStop();
        deactivateInputs(DeactivableButton.DEACTIVATED);
        if (activity.getCurrentFragment() != VoiceTranslationActivity.DEFAULT_FRAGMENT) {
            Toast.makeText(activity, getResources().getString(R.string.toast_working_background), Toast.LENGTH_SHORT).show();
        }
    }

    public void restoreAttributesFromService() {
        voiceTranslationServiceCommunicator.getAttributes(new VoiceTranslationService.AttributesListener() {
            @Override
            public void onSuccess(ArrayList<GuiMessage> messages, boolean isMicMute, boolean isAudioMute, final boolean isEditTextOpen, boolean isBluetoothHeadsetConnected) {
                // initialization with service values
                mAdapter = new MessagesAdapter(messages, new MessagesAdapter.Callback() {
                    @Override
                    public void onFirstItemAdded() {
                        description.setVisibility(View.GONE);
                        mRecyclerView.setVisibility(View.VISIBLE);
                    }
                });
                mRecyclerView.setAdapter(mAdapter);
                // restore microphone and sound status
                microphone.setMute(isMicMute);
                sound.setMute(isAudioMute);
                // restore editText
                VoiceTranslationFragment.this.isEditTextOpen = isEditTextOpen;
                if (isEditTextOpen) {
                    keyboard.generateEditText(activity, VoiceTranslationFragment.this, microphone, editText, false);
                }
                if (isBluetoothHeadsetConnected) {
                    voiceTranslationServiceCallback.onBluetoothHeadsetConnected();
                } else {
                    voiceTranslationServiceCallback.onBluetoothHeadsetDisconnected();
                }

                if (!microphone.isMute() && !isEditTextOpen) {
                    activateInputs(true);
                } else {
                    activateInputs(false);
                }
            }
        });
    }

    @Override
    public void startMicrophone(boolean changeAspect) {
        if (changeAspect) {
            microphone.setMute(false);
        }
        voiceTranslationServiceCommunicator.startMic();
    }

    @Override
    public void stopMicrophone(boolean changeAspect) {
        if (changeAspect) {
            microphone.setMute(true);
        }
        voiceTranslationServiceCommunicator.stopMic(changeAspect);
    }

    protected void startSound() {
        sound.setMute(false);
        voiceTranslationServiceCommunicator.startSound();
    }

    protected void stopSound() {
        sound.setMute(true);
        voiceTranslationServiceCommunicator.stopSound();
    }

    protected void deactivateInputs(int cause) {
        microphone.deactivate(cause);
        if (cause == DeactivableButton.DEACTIVATED) {
            sound.deactivate(DeactivableButton.DEACTIVATED);
        } else {
            sound.activate(false);  // to activate the button sound which otherwise remains deactivated and when clicked it shows the message "wait for initialisation"
        }
    }

    protected void activateInputs(boolean start) {
        microphone.activate(start);
        sound.activate(start);
    }

    public boolean isEditTextOpen() {
        return isEditTextOpen;
    }

    public void deleteEditText() {
        isEditTextOpen = false;
        voiceTranslationServiceCommunicator.setEditTextOpen(false);
        microphone.deleteEditText(activity, VoiceTranslationFragment.this, keyboard, editText);
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

    public class VoiceTranslationServiceCallback extends VoiceTranslationService.VoiceTranslationServiceCallback {
        @Override
        public void onVoiceStarted() {
            super.onVoiceStarted();
            microphone.onVoiceStarted();
        }

        @Override
        public void onVoiceEnded() {
            super.onVoiceEnded();
            microphone.onVoiceEnded();
        }

        @Override
        public void onMessage(GuiMessage message) {
            super.onMessage(message);
            if (message != null) {
                if (message.isFinal()) {
                    if (message.isMine()) {
                        int previewIndex = mAdapter.getPreviewIndex();
                        if (previewIndex != -1) {
                            mAdapter.setMessage(previewIndex, message);
                        } else {
                            mAdapter.addMessage(message);
                            //smooth scroll
                            smoothScroller.setTargetPosition(mAdapter.getItemCount() - 1);
                            mRecyclerView.getLayoutManager().startSmoothScroll(smoothScroller);
                        }
                    } else {
                        mAdapter.addMessage(message);
                        //smooth scroll
                        int previewIndex = mAdapter.getPreviewIndex();
                        if (previewIndex == -1) {
                            smoothScroller.setTargetPosition(mAdapter.getItemCount() - 1);
                        } else {
                            smoothScroller.setTargetPosition(mAdapter.getItemCount() - 2);  // because the message is added in the penultimate position, not in the last one, because there is a preview
                        }
                        mRecyclerView.getLayoutManager().startSmoothScroll(smoothScroller);
                    }
                } else {
                    GuiMessage preview = mAdapter.getPreview();
                    if (preview != null) {
                        // update the component_message_preview
                        mAdapter.setPreviewText(message.getMessage().getText());

                    } else {
                        //add the component_message_preview
                        mAdapter.addMessage(message);
                        //smooth scroll
                        smoothScroller.setTargetPosition(mAdapter.getItemCount() - 1);
                        mRecyclerView.getLayoutManager().startSmoothScroll(smoothScroller);
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
                        sound.setMute(true);
                        activity.showMissingGoogleTTSDialog();
                        break;
                    case ErrorCodes.GOOGLE_TTS_ERROR:
                        sound.setMute(true);
                        activity.showGoogleTTSErrorDialog();
                        break;
                    case VoiceTranslationService.MISSING_MIC_PERMISSION: {
                        requestPermissions(VoiceTranslationService.REQUIRED_PERMISSIONS, VoiceTranslationService.REQUEST_CODE_REQUIRED_PERMISSIONS);
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

    protected void onFailureConnectingWithService(int[] reasons, long value) {
        for (int aReason : reasons) {
            switch (aReason) {
                case ErrorCodes.MISSED_ARGUMENT:
                case ErrorCodes.SAFETY_NET_EXCEPTION:
                case ErrorCodes.MISSED_CONNECTION:
                    //creation of the dialog.
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setMessage(R.string.error_internet_lack_accessing);
                    builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            activity.exitFromVoiceTranslation();
                        }
                    });
                    builder.setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            connectToService();
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                    break;
                case ErrorCodes.MISSING_GOOGLE_TTS:
                    activity.showMissingGoogleTTSDialog();
                    break;
                case ErrorCodes.GOOGLE_TTS_ERROR:
                    activity.showGoogleTTSErrorDialog(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            connectToService();
                        }
                    });
                    break;
                case ErrorCodes.MISSING_API_KEY: {
                    deactivateInputs(DeactivableButton.DEACTIVATED_FOR_MISSING_OR_WRONG_KEYFILE);
                    activity.showApiKeyFileErrorDialog(R.string.error_missing_api_key_file, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(activity, ApiManagementActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            activity.exitFromVoiceTranslation();
                        }
                    });
                    break;
                }
                case ErrorCodes.WRONG_API_KEY: {
                    deactivateInputs(DeactivableButton.DEACTIVATED_FOR_MISSING_OR_WRONG_KEYFILE);
                    activity.showApiKeyFileErrorDialog(R.string.error_wrong_api_key_file, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(activity, ApiManagementActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            activity.exitFromVoiceTranslation();
                        }
                    });
                    break;
                }
                default:
                    activity.onError(aReason, value);
                    break;
            }
        }
    }
}
