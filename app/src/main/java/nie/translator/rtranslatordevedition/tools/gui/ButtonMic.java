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

package nie.translator.rtranslatordevedition.tools.gui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.TextView;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.gui.animations.CustomAnimator;
import nie.translator.rtranslatordevedition.voice_translation.VoiceTranslationActivity;
import nie.translator.rtranslatordevedition.voice_translation.VoiceTranslationFragment;


public class ButtonMic extends DeactivableButton {
    public static final int STATE_NORMAL = 0;
    public static final int STATE_RETURN = 1;
    public static final int STATE_SEND = 2;
    private boolean isMute = false;
    private int state = STATE_NORMAL;
    private TextView micInput;
    private EditText editText;
    private MicrophoneComunicable fragment;
    private Context context;
    private CustomAnimator animator = new CustomAnimator();

    public ButtonMic(Context context) {
        super(context);
        this.context = context;
        deactivatedColor = GuiTools.getColorStateList(context, R.color.gray);
        activatedColor = GuiTools.getColorStateList(context, R.color.primary);
    }

    public ButtonMic(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        deactivatedColor = GuiTools.getColorStateList(context, R.color.gray);
        activatedColor = GuiTools.getColorStateList(context, R.color.primary);
    }

    public ButtonMic(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        deactivatedColor = GuiTools.getColorStateList(context, R.color.gray);
        activatedColor = GuiTools.getColorStateList(context, R.color.primary);
    }

    public void deleteEditText(VoiceTranslationActivity activity, final VoiceTranslationFragment fragment, final ButtonKeyboard buttonKeyboard, final EditText editText) {
        animator.animateDeleteEditText(activity, this, buttonKeyboard, editText, new CustomAnimator.Listener() {
            @Override
            public void onAnimationStart() {
                fragment.setInputActive(false);
                buttonKeyboard.setClickable(false);
            }

            @Override
            public void onAnimationEnd() {
                fragment.setInputActive(true);
                buttonKeyboard.setClickable(true);
            }
        });
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        int oldState = this.state;
        this.state = state;

        if (state == STATE_NORMAL) {
            if (oldState == STATE_RETURN) {
                if (!isMute && activationStatus == ACTIVATED) {
                    fragment.startMicrophone(false);
                }
                if (micInput != null) {
                    // micInput appearance animation (TextView under mic) and microphone enlargement
                    animator.animateIconToMic(context, this, micInput);
                } else {
                    // microphone enlargement animation
                    animator.animateIconToMic(context, this);
                }
            } else if (oldState == STATE_SEND) {
                // in this case first we switch to the microphone icon with the animation and then we start the animation to delete the editText
                if (!isMute && activationStatus == ACTIVATED) {
                    fragment.startMicrophone(false);
                }
                editText.setText(""); // do it without activating the listener
                if (micInput != null) {
                    // animation micInput (TextView under the mic) and enlargement of the microphone after the change of icon from send to mic
                    if (isMute) {
                        animator.animateIconToMicAndIconChange(context, this, micInput, getDrawable(R.drawable.mic_mute));
                    }else{
                        animator.animateIconToMicAndIconChange(context, this, micInput, getDrawable(R.drawable.mic));
                    }
                } else {
                    // microphone enlargement animation
                    if(isMute){
                        animator.animateIconToMicAndIconChange(context, this, getDrawable(R.drawable.mic_mute));
                    }else {
                        animator.animateIconToMicAndIconChange(context, this, getDrawable(R.drawable.mic));
                    }
                }
            }
        } else if (state == STATE_RETURN) {
            if (oldState == STATE_NORMAL) {
                fragment.stopMicrophone(false);
                if (micInput != null) {
                    // micInput appearance animation (TextView under mic) and microphone enlargement
                    animator.animateMicToIcon(context, this, micInput);
                } else {
                    // microphone enlargement animation
                    animator.animateMicToIcon(context, this);
                }

            } else if (oldState == STATE_SEND) {
                // change icon animation
                if(isMute){
                    animator.animateIconChange(this, getDrawable(R.drawable.mic_mute));
                }else{
                    animator.animateIconChange(this, getDrawable(R.drawable.mic));
                }
            }
        } else if (state == STATE_SEND) {
            // change icon animation
            animator.animateIconChange(this, getDrawable(R.drawable.send_icon));
        }
    }

    // to be set at the beginning only if micInput is present in the GUI
    public void setMicInput(TextView micInput) {
        this.micInput = micInput;
    }

    public void setEditText(EditText editText) {
        this.editText = editText;
    }

    public boolean isMute() {
        return isMute;
    }

    public void setMute(boolean mute) {
        isMute = mute;
        if (state == STATE_NORMAL) {
            if (mute) {
                setImageDrawable(getDrawable(R.drawable.mic_mute));
            } else {
                setImageDrawable(getDrawable(R.drawable.mic));
            }
        }
    }

    public void onVoiceStarted() {
        if (!isMute && activationStatus == ACTIVATED) {  // see if it makes sense to keep this check
            animator.animateOnVoiceStart(this);
        }
    }

    public void onVoiceEnded() {
        if (!isMute && activationStatus == ACTIVATED) {   // see if it makes sense to keep this check
            animator.animateOnVoiceEnd(this);
        }
    }

    @Override
    public void activate(boolean start) {
        super.activate(start);
        animator.createAnimatorColor(getDrawable(), color.getDefaultColor(), activatedColor.getDefaultColor(), getResources().getInteger(R.integer.durationShort) * 2).start();
        color = activatedColor;
        if (start) {
            fragment.startMicrophone(false);
        }
    }

    @Override
    public void deactivate(int reason) {
        super.deactivate(reason);
        switch (reason) {
            case DEACTIVATED_FOR_CREDIT_EXHAUSTED:
            case DEACTIVATED_FOR_MISSING_OR_WRONG_KEYFILE:
            case DEACTIVATED_FOR_MISSING_MIC_PERMISSION:
                setImageDrawable(getDrawable(R.drawable.mic));
                animator.createAnimatorColor(getDrawable(), color.getDefaultColor(), deactivatedColor.getDefaultColor(), getResources().getInteger(R.integer.durationShort) * 2).start();
                color = deactivatedColor;
                fragment.stopMicrophone(false);
                break;
            case DEACTIVATED:
                color = deactivatedColor;
                setImageDrawable(getDrawable(R.drawable.mic));
                break;
        }
    }

    public void setFragment(MicrophoneComunicable fragment) {
        this.fragment = fragment;
    }

    public Drawable getDrawable(int id) {
        Drawable drawable = getResources().getDrawable(id, null);
        drawable.setTintList(color);
        return drawable;
    }
}
