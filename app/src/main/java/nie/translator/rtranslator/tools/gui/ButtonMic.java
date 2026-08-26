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

package nie.translator.rtranslator.tools.gui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.AndroidResources;

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.tools.gui.animations.CustomAnimator;
import nie.translator.rtranslator.voice_translation.VoiceTranslationActivity;
import nie.translator.rtranslator.voice_translation._conversation_mode._conversation.main.ConversationMainFragment;
import nie.translator.rtranslator.voice_translation._walkie_talkie_mode._walkie_talkie.WalkieTalkieFragment;


public class ButtonMic extends DeactivableButton {
    public static final int SIZE_MUTED_DP = 56;
    public static final int SIZE_NORMAL_DP = 66;
    public static final int SIZE_LISTENING_DP = 76;
    public static final float SIZE_ICON_DP = 42;

    public static final int MAX_LENGTH_LEFT_LINE_DP = 21;
    public static final int MAX_LENGTH_CENTER_LINE_DP = 26;
    public static final int MAX_LENGTH_RIGHT_LINE_DP = 15;
    public static final int MIN_LINE_LENGTH_DP = 5;

    public static final int STATE_NORMAL = 0;
    public static final int STATE_RETURN = 1;
    public static final int STATE_SEND = 2;

    public static ButtonMicColor colorActivated;
    public static ButtonMicColor colorMutedActivated;
    public static ButtonMicColor colorDeactivated;
    public static ButtonMicColor colorMutedDeactivated;

    private boolean isMute = false;
    private int state = STATE_NORMAL;
    private boolean isListening = false;
    private TextView micInput;
    private EditText editText;

    @Nullable
    private MicrophoneComunicable fragment;
    private View leftLine;
    private View centerLine;
    private View rightLine;
    private Context context;
    private CustomAnimator animator = new CustomAnimator();
    private ButtonMicColor currentColor;
    private float volumeLevel = -1;
    private Animator animationVoice;


    public ButtonMic(Context context) {
        super(context);
        this.context = context;
        colorActivated = new ButtonMicColor(GuiTools.getColorStateList(context,R.color.accent_white), GuiTools.getColorStateList(context,R.color.primary));
        colorMutedActivated = new ButtonMicColor(GuiTools.getColorStateList(context,R.color.primary_very_dark), GuiTools.getColorStateList(context,R.color.primary_very_lite));
        colorDeactivated = new ButtonMicColor(GuiTools.getColorStateList(context,R.color.accent_white), GuiTools.getColorStateList(context,R.color.gray));
        colorMutedDeactivated = new ButtonMicColor(GuiTools.getColorStateList(context,R.color.very_very_dark_gray), GuiTools.getColorStateList(context,R.color.very_very_light_gray));
    }

    public ButtonMic(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        colorActivated = new ButtonMicColor(GuiTools.getColorStateList(context,R.color.accent_white), GuiTools.getColorStateList(context,R.color.primary));
        colorMutedActivated = new ButtonMicColor(GuiTools.getColorStateList(context,R.color.primary_very_dark), GuiTools.getColorStateList(context,R.color.primary_very_lite));
        colorDeactivated = new ButtonMicColor(GuiTools.getColorStateList(context,R.color.accent_white), GuiTools.getColorStateList(context,R.color.gray));
        colorMutedDeactivated = new ButtonMicColor(GuiTools.getColorStateList(context,R.color.very_very_dark_gray), GuiTools.getColorStateList(context,R.color.very_very_light_gray));
    }

    public ButtonMic(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        colorActivated = new ButtonMicColor(GuiTools.getColorStateList(context,R.color.accent_white), GuiTools.getColorStateList(context,R.color.primary));
        colorMutedActivated = new ButtonMicColor(GuiTools.getColorStateList(context,R.color.primary_very_dark), GuiTools.getColorStateList(context,R.color.primary_very_lite));
        colorDeactivated = new ButtonMicColor(GuiTools.getColorStateList(context,R.color.accent_white), GuiTools.getColorStateList(context,R.color.gray));
        colorMutedDeactivated = new ButtonMicColor(GuiTools.getColorStateList(context,R.color.very_very_dark_gray), GuiTools.getColorStateList(context,R.color.very_very_light_gray));
    }

    public void deleteEditText(VoiceTranslationActivity activity, final ConversationMainFragment fragment, final ButtonKeyboard buttonKeyboard, final EditText editText, ImageButton micPlaceHolder) {
        animator.animateDeleteEditText(activity, this, buttonKeyboard, editText, micPlaceHolder, new CustomAnimator.Listener() {
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
                if (!isMute && activationStatus == ACTIVATED && fragment != null) {
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
                if (!isMute && activationStatus == ACTIVATED && fragment != null) {
                    fragment.startMicrophone(false);
                }
                editText.setText(""); // do it without activating the listener
                if (micInput != null) {
                    // animation micInput (TextView under the mic) and enlargement of the microphone after the change of icon from send to mic
                    Drawable icon = getDrawable(R.drawable.mic_icon);
                    icon.setColorFilter(currentColor.iconColor.getDefaultColor(), PorterDuff.Mode.SRC_IN);
                    animator.animateIconToMicAndIconChange(context, this, micInput, icon);
                } else {
                    // microphone enlargement animation
                    Drawable icon = getDrawable(R.drawable.mic_icon);
                    icon.setColorFilter(currentColor.iconColor.getDefaultColor(), PorterDuff.Mode.SRC_IN);
                    animator.animateIconToMicAndIconChange(context, this, icon);
                }
            }
        } else if (state == STATE_RETURN) {
            if (oldState == STATE_NORMAL) {
                if(fragment != null) {
                    fragment.stopMicrophone(false);
                }
                if (micInput != null) {
                    // micInput appearance animation (TextView under mic) and microphone enlargement
                    animator.animateMicToIcon(context, this, micInput);
                } else {
                    // microphone enlargement animation
                    animator.animateMicToIcon(context, this);
                }

            } else if (oldState == STATE_SEND) {
                // change icon animation
                Drawable icon = getDrawable(R.drawable.mic_icon);
                icon.setColorFilter(currentColor.iconColor.getDefaultColor(), PorterDuff.Mode.SRC_IN);
                animator.animateIconChange(this, icon);
            }
        } else if (state == STATE_SEND) {
            // change icon animation
            Drawable icon = getDrawable(R.drawable.send_icon);
            icon.setColorFilter(currentColor.iconColor.getDefaultColor(), PorterDuff.Mode.SRC_IN);
            animator.animateIconChange(this, icon);
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

    public void setMute(boolean mute){
        setMute(mute, true);
    }

    public void setMute(boolean mute, boolean animate) {
        isMute = mute;
        if (state == STATE_NORMAL) {
            if (mute) {
                if(leftLine != null && centerLine != null && rightLine != null){
                    getDrawable().setColorFilter(currentColor.iconColor.getDefaultColor(), PorterDuff.Mode.SRC_IN);  //we make the mic icon visible
                    leftLine.setVisibility(GONE);
                    centerLine.setVisibility(GONE);
                    rightLine.setVisibility(GONE);
                    volumeLevel = -1;
                }
                animator.animateMute(context, this, !animate);
                currentColor = colorMutedActivated;   //setMute can be called only when the mic is activate
            } else {
                animator.animateUnmute(context, this, !animate);
                currentColor = colorActivated;     //setMute can be called only when the mic is activate
            }
        }
    }

    public void onVoiceStarted(boolean animate) {
        if (activationStatus == ACTIVATED) {  // see if it makes sense to keep this check
            isListening = true;
            if(!isMute) {
                if(leftLine != null && centerLine != null && rightLine != null){
                    getDrawable().setColorFilter(GuiTools.getColorStateList(context, android.R.color.transparent).getDefaultColor(), PorterDuff.Mode.SRC_IN);  //we make the mic icon invisible
                    leftLine.setVisibility(VISIBLE);
                    centerLine.setVisibility(VISIBLE);
                    rightLine.setVisibility(VISIBLE);
                }
                animator.animateOnVoiceStart(context, this, !animate);
            }
        }
    }

    public void onVoiceEnded(boolean animate) {
        isListening = false;
        if (!isMute) {
            if(leftLine != null && centerLine != null && rightLine != null){
                getDrawable().setColorFilter(currentColor.iconColor.getDefaultColor(), PorterDuff.Mode.SRC_IN);  //we make the mic icon visible
                leftLine.setVisibility(GONE);
                centerLine.setVisibility(GONE);
                rightLine.setVisibility(GONE);
                volumeLevel = -1;
            }
            animator.animateOnVoiceEnd(context, this, !animate);
        }
    }

    public void updateVolumeLevel(float volumeLevel){
        if(isListening){
            //we make the mic icon invisible (we do this also here because otherwise when the onVoiceStart is called during another animation the icon will not disappear)
            getDrawable().setColorFilter(GuiTools.getColorStateList(context, android.R.color.transparent).getDefaultColor(), PorterDuff.Mode.SRC_IN);
        }
        if(this.volumeLevel == -1 || this.animationVoice == null || !this.animationVoice.isRunning()) {
            animationVoice = updateVolumeLevel(volumeLevel, new CustomAnimator.EndListener() {
                @Override
                public void onAnimationEnd() {
                    if(isListening()){
                        animationVoice = updateVolumeLevel(ButtonMic.this.volumeLevel, this);
                    }else{
                        ButtonMic.this.volumeLevel = -1;
                        if(animationVoice != null){
                            animationVoice.cancel();
                            animationVoice = null;
                        }
                    }
                }
            });
        }
        this.volumeLevel = volumeLevel;
    }

    private Animator updateVolumeLevel(float volumeLevel, CustomAnimator.EndListener listener){
        if(leftLine != null && centerLine != null && rightLine != null && leftLine.getVisibility() == VISIBLE && centerLine.getVisibility() == VISIBLE && rightLine.getVisibility() == VISIBLE && volumeLevel > 0) {
            int duration_ms = 70;
            //Log.d("volume", "volume: " + volumeLevel);
            float maxLengthLeft = Tools.convertDpToPixels(context, MAX_LENGTH_LEFT_LINE_DP);
            float maxLengthCenter = Tools.convertDpToPixels(context, MAX_LENGTH_CENTER_LINE_DP);
            float maxLengthRight = Tools.convertDpToPixels(context, MAX_LENGTH_RIGHT_LINE_DP);
            float minLength = Tools.convertDpToPixels(context, MIN_LINE_LENGTH_DP);

            AnimatorSet animatorSet = new AnimatorSet();

            Animator leftAnimation = animator.createAnimatorHeight(leftLine, leftLine.getHeight(), (int) (minLength + (volumeLevel * (maxLengthLeft - minLength))), duration_ms);
            Animator centerAnimation = animator.createAnimatorHeight(centerLine, centerLine.getHeight(), (int) (minLength + (volumeLevel * (maxLengthCenter - minLength))), duration_ms);
            Animator rightAnimation = animator.createAnimatorHeight(rightLine, rightLine.getHeight(), (int) (minLength + (volumeLevel * (maxLengthRight - minLength))), duration_ms);

            animatorSet.play(leftAnimation).with(centerAnimation).with(rightAnimation);
            animatorSet.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(@NonNull Animator animation) {

                }

                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    if(listener != null){
                        listener.onAnimationEnd();
                    }
                }

                @Override
                public void onAnimationCancel(@NonNull Animator animation) {

                }

                @Override
                public void onAnimationRepeat(@NonNull Animator animation) {

                }
            });
            animatorSet.start();
            return animatorSet;
        }
        return null;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public void activate(boolean start) {
        super.activate(start);
        animator.animateActivation(context, this);
        currentColor = isMute ? colorMutedActivated : colorActivated;
        if (start && fragment != null) {
            fragment.startMicrophone(false);
        }
    }

    @Override
    public void deactivate(int reason) {
        super.deactivate(reason);
        switch (reason) {
            case DEACTIVATED_FOR_MISSING_MIC_PERMISSION:
                //setImageDrawable(getDrawable(R.drawable.mic));
                animator.animateDeactivation(context, this);
                currentColor = isMute ? colorMutedDeactivated : colorDeactivated;
                if(fragment != null) {
                    fragment.stopMicrophone(false);
                }
                break;
            case DEACTIVATED:
                if (currentColor == null) {  //for differentiating the deactivate at the start of WalkieTalkie mode (color == null) from the one caused by programmatic stop of mic
                    currentColor = isMute ? colorMutedDeactivated : colorDeactivated;
                    //setImageDrawable(getDrawable(R.drawable.mic));
                } else {
                    //setImageDrawable(getDrawable(R.drawable.mic));
                    animator.animateDeactivation(context, this);
                    currentColor = isMute ? colorMutedDeactivated : colorDeactivated;
                }
                break;
        }
    }

    public void initialize(@Nullable MicrophoneComunicable fragment) {  //da fare: cancellarlo quando avrò implementato l'animazione in base al volume anche in Conversation
        this.fragment = fragment;
    }

    public void initialize(@Nullable MicrophoneComunicable fragment, View leftLine, View centerLine, View rightLine) {
        this.fragment = fragment;
        this.leftLine = leftLine;
        this.centerLine = centerLine;
        this.rightLine = rightLine;
    }

    public Drawable getDrawable(int id) {
        Drawable drawable = getResources().getDrawable(id, null);
        drawable.setTintList(currentColor.iconColor);
        return drawable;
    }

    public ButtonMicColor getCurrentColor(){
        return currentColor;
    }

    public boolean isListening() {
        return isListening;
    }


    public static class ButtonMicColor {
        public ColorStateList iconColor;
        public ColorStateList backgroundColor;

        public ButtonMicColor(ColorStateList iconColor, ColorStateList backgroundColor){
            this.iconColor = iconColor;
            this.backgroundColor = backgroundColor;
        }
    }
}
