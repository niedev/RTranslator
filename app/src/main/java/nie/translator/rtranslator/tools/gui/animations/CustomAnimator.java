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

package nie.translator.rtranslator.tools.gui.animations;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.tools.gui.ButtonKeyboard;
import nie.translator.rtranslator.tools.gui.ButtonMic;
import nie.translator.rtranslator.tools.gui.GuiTools;
import nie.translator.rtranslator.voice_translation.VoiceTranslationActivity;
import nie.translator.rtranslator.voice_translation._text_translation.TranslationFragment;

public class CustomAnimator {
    private final float iconSizeInDp=24;
    private final float micSizeInDp=50;
    private Handler eventHandler= new Handler();
    private Interpolator reverseInterpolator=new Interpolator() {
        private AccelerateDecelerateInterpolator interpolator= new AccelerateDecelerateInterpolator();
        @Override
        public float getInterpolation(float v) {
            return Math.abs(interpolator.getInterpolation(v) -1f);
        }
    };
    private ValueAnimator selectionAnimator= new ValueAnimator();

    public void animateIconToMic(Context context, ButtonMic buttonMic, TextView micInput){
        AnimatorSet animatorSet= new AnimatorSet();
        int duration=context.getResources().getInteger(R.integer.durationStandard);
        int durationShort=buttonMic.getResources().getInteger(R.integer.durationShort);
        micInput.measure(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        final int micFinalSize;
        if(buttonMic.isMute()) {
            micFinalSize = Tools.convertDpToPixels(context, ButtonMic.SIZE_MUTED_DP);
        }else{
            micFinalSize = Tools.convertDpToPixels(context, ButtonMic.SIZE_NORMAL_DP);
        }
        final int micInputFinalWidth = micInput.getMeasuredWidth();
        final int micInputFinalHeight = micInput.getMeasuredHeight();
        Animator micAnimator = createAnimatorSize(buttonMic,buttonMic.getWidth(),buttonMic.getHeight(),micFinalSize,micFinalSize,duration);
        Animator micInputAnimator = createAnimatorSize(micInput,micInput.getWidth(),micInput.getHeight(),micInputFinalWidth,micInputFinalHeight,durationShort,duration);
        animatorSet.play(micAnimator).with(micInputAnimator);
        animatorSet.start();
    }

    public void animateIconToMic(Context context, ButtonMic buttonMic){
        final int micFinalSize;
        if(buttonMic.isMute()) {
            micFinalSize = Tools.convertDpToPixels(context, ButtonMic.SIZE_MUTED_DP);
        } else {
            micFinalSize = Tools.convertDpToPixels(context, ButtonMic.SIZE_NORMAL_DP);
        }
        Animator micAnimator = createAnimatorSize(buttonMic, buttonMic.getWidth(), buttonMic.getHeight(), micFinalSize, micFinalSize, context.getResources().getInteger(R.integer.durationStandard));
        micAnimator.start();
    }

    public void animateMicToIcon(Context context, ButtonMic buttonMic, TextView micInput){
        int duration=context.getResources().getInteger(R.integer.durationStandard);
        AnimatorSet animatorSet= new AnimatorSet();
        final int micFinalSize = Tools.convertDpToPixels(context, ButtonMic.SIZE_ICON_DP);
        final int micInputFinalSize = 1;
        Animator micAnimator = createAnimatorSize(buttonMic, buttonMic.getWidth(), buttonMic.getHeight(), micFinalSize, micFinalSize,duration);
        Animator micInputAnimator = createAnimatorSize(micInput, micInput.getWidth(), micInput.getHeight(), micInputFinalSize, micInputFinalSize, duration);
        animatorSet.play(micAnimator).with(micInputAnimator);
        animatorSet.start();
    }

    public void animateMicToIcon(Context context, ButtonMic buttonMic){
        final int micFinalSize = Tools.convertDpToPixels(context, ButtonMic.SIZE_ICON_DP);
        Animator micAnimator = createAnimatorSize(buttonMic, buttonMic.getWidth(), buttonMic.getHeight(), micFinalSize, micFinalSize, context.getResources().getInteger(R.integer.durationStandard));
        micAnimator.start();
    }

    /*public void animateIconToMicInstantly(Context context, ButtonMic buttonMic){
        new AnimationIconToMic(context,new ViewPropertiesAdapter(buttonMic)).startInstantly();
    }*/

    public void animateIconChange(final ImageView view, final Drawable newIcon){
        animateIconChange(view,newIcon,null);
    }

    public void animateIconChange(final ImageView view, final Drawable newIcon, @Nullable final EndListener responseListener){  // check that the fact that the view gets smaller does not affect the rest of the graphics
        Animation dwindleAnimation=AnimationUtils.loadAnimation(view.getContext(),R.anim.dwindle_icon);
        dwindleAnimation.setDuration(view.getResources().getInteger(R.integer.durationShort)/2);
        final Animation enlargeAnimation=AnimationUtils.loadAnimation(view.getContext(),R.anim.enlarge_icon);
        enlargeAnimation.setDuration(view.getResources().getInteger(R.integer.durationShort)/2);
        view.startAnimation(dwindleAnimation);
        view.getAnimation().setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                view.setImageDrawable(newIcon);
                view.startAnimation(enlargeAnimation);
                if(responseListener!=null){
                    view.getAnimation().setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {}

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            responseListener.onAnimationEnd();
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {}
                    });
                }
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }

    public void animateIconToMicAndIconChange(final Context context, final ButtonMic buttonMic, final TextView micInput, final Drawable newIcon){
        int durationStandard= context.getResources().getInteger(R.integer.durationStandard);
        int durationShort=buttonMic.getResources().getInteger(R.integer.durationShort);
        // change icon horizontally
        Animation dwindleAnimation=AnimationUtils.loadAnimation(buttonMic.getContext(),R.anim.horizontal_dwindle_icon);
        dwindleAnimation.setDuration(durationShort/2);
        final Animation enlargeAnimation=AnimationUtils.loadAnimation(buttonMic.getContext(),R.anim.horizontal_enlarge_icon);
        enlargeAnimation.setDuration(durationShort/2);
        buttonMic.startAnimation(dwindleAnimation);
        buttonMic.getAnimation().setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                buttonMic.setImageDrawable(newIcon);
                buttonMic.startAnimation(enlargeAnimation);
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        // enlargement icon and micInput
        AnimatorSet animatorSet= new AnimatorSet();
        micInput.measure(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        final int micFinalSize=Tools.convertDpToPixels(context,micSizeInDp);
        final int micInputFinalWidth= micInput.getMeasuredWidth();
        final int micInputFinalHeight= micInput.getMeasuredHeight();
        Animator micAnimator=createAnimatorSize(buttonMic,buttonMic.getWidth(),buttonMic.getHeight(),micFinalSize,micFinalSize,durationStandard);
        Animator micInputAnimator=createAnimatorSize(micInput,micInput.getWidth(),micInput.getHeight(),micInputFinalWidth,micInputFinalHeight,durationShort,durationStandard);

        animatorSet.play(micAnimator).with(micInputAnimator);
        animatorSet.start();
    }

    public void animateIconToMicAndIconChange(final Context context, final ButtonMic buttonMic, final Drawable newIcon){
        int durationShort=buttonMic.getResources().getInteger(R.integer.durationShort);
        // change icon horizontally
        Animation dwindleAnimation=AnimationUtils.loadAnimation(buttonMic.getContext(),R.anim.horizontal_dwindle_icon);
        dwindleAnimation.setDuration(durationShort/2);
        final Animation enlargeAnimation=AnimationUtils.loadAnimation(buttonMic.getContext(),R.anim.horizontal_enlarge_icon);
        enlargeAnimation.setDuration(durationShort/2);
        buttonMic.startAnimation(dwindleAnimation);
        buttonMic.getAnimation().setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                buttonMic.setImageDrawable(newIcon);
                buttonMic.startAnimation(enlargeAnimation);
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        //enlargement icon
        final int micFinalSize=Tools.convertDpToPixels(context,micSizeInDp);
        Animator micAnimator=createAnimatorSize(buttonMic,buttonMic.getWidth(),buttonMic.getHeight(),micFinalSize,micFinalSize,context.getResources().getInteger(R.integer.durationStandard));
        micAnimator.start();
    }

    public void animateOnVoiceStart(Context context, final ButtonMic buttonMic, boolean instant){
        int duration=buttonMic.getResources().getInteger(R.integer.durationStandard);
        int finalSizeInPixels = Tools.convertDpToPixels(context, ButtonMic.SIZE_LISTENING_DP);

        if(!instant) {
            // enlargement animation
            Animator enlargeAnimation = createAnimatorSize(buttonMic, buttonMic.getWidth(), buttonMic.getHeight(), finalSizeInPixels, finalSizeInPixels, duration);
            enlargeAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
            enlargeAnimation.start();
        }else{
            ViewGroup.LayoutParams layoutParams = buttonMic.getLayoutParams();
            layoutParams.width = finalSizeInPixels;
            layoutParams.height = finalSizeInPixels;
            buttonMic.setLayoutParams(layoutParams);
        }
    }

    public void animateOnVoiceEnd(Context context, final ButtonMic buttonMic, boolean instant){
        int duration=buttonMic.getResources().getInteger(R.integer.durationStandard);
        int finalSizeInPixels = Tools.convertDpToPixels(context, ButtonMic.SIZE_NORMAL_DP);

        if(!instant) {
            //reduce animation
            Animator reduceAnimation = createAnimatorSize(buttonMic, buttonMic.getWidth(), buttonMic.getHeight(), finalSizeInPixels, finalSizeInPixels, duration);
            reduceAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
            reduceAnimation.start();
        }else{
            ViewGroup.LayoutParams layoutParams = buttonMic.getLayoutParams();
            layoutParams.width = finalSizeInPixels;
            layoutParams.height = finalSizeInPixels;
            buttonMic.setLayoutParams(layoutParams);
        }
    }

    public void animateMute(Context context, final ButtonMic buttonMic, boolean instant){
        int duration = buttonMic.getResources().getInteger(R.integer.durationStandard);
        int finalSizeInPixels = Tools.convertDpToPixels(context, ButtonMic.SIZE_MUTED_DP);

        int initialColorIcon = GuiTools.getColor(buttonMic.getContext(), R.color.white);
        int finalColorIcon = GuiTools.getColor(buttonMic.getContext(), R.color.primary_very_dark);
        int initialColor = GuiTools.getColor(buttonMic.getContext(), R.color.primary);
        int finalColor = GuiTools.getColor(buttonMic.getContext(), R.color.primary_very_lite);

        if(!instant) {
            AnimatorSet animatorSet = new AnimatorSet();

            //reduce animation
            Animator reduceAnimation = createAnimatorSize(buttonMic, buttonMic.getWidth(), buttonMic.getHeight(), finalSizeInPixels, finalSizeInPixels, duration);

            // change color of icon animation
            Animator animatorIconColor = createAnimatorColor(buttonMic.getDrawable(), initialColorIcon, finalColorIcon, duration);

            // change color of background animation
            Animator animatorBackgroundColor = createAnimatorColor(buttonMic.getBackground(), initialColor, finalColor, duration);

            animatorSet.play(reduceAnimation).with(animatorIconColor).with(animatorBackgroundColor);
            animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
            animatorSet.start();

        }else{
            //reduce size
            ViewGroup.LayoutParams layoutParams = buttonMic.getLayoutParams();
            layoutParams.width = finalSizeInPixels;
            layoutParams.height = finalSizeInPixels;
            buttonMic.setLayoutParams(layoutParams);

            // change color of icon
            buttonMic.getDrawable().setColorFilter(finalColorIcon, PorterDuff.Mode.SRC_IN);

            // change color of background
            buttonMic.getBackground().setColorFilter(finalColor, PorterDuff.Mode.SRC_IN);
        }
    }

    public void animateUnmute(Context context, final ButtonMic buttonMic, boolean instant){
        int duration = buttonMic.getResources().getInteger(R.integer.durationStandard);
        int finalSizeInPixels = Tools.convertDpToPixels(context, ButtonMic.SIZE_NORMAL_DP);

        int initialColorIcon = GuiTools.getColor(buttonMic.getContext(),R.color.primary_very_dark);
        int finalColorIcon = GuiTools.getColor(buttonMic.getContext(),R.color.white);
        int initialColor = GuiTools.getColor(buttonMic.getContext(),R.color.primary_very_lite);
        int finalColor = GuiTools.getColor(buttonMic.getContext(),R.color.primary);

        if(!instant) {
            AnimatorSet animatorSet = new AnimatorSet();

            //enlarge animation
            Animator enlargeAnimation = createAnimatorSize(buttonMic, buttonMic.getWidth(), buttonMic.getHeight(), finalSizeInPixels, finalSizeInPixels, duration);

            // change color of icon animation
            Animator animatorIconColor = createAnimatorColor(buttonMic.getDrawable(), initialColorIcon, finalColorIcon, duration);

            // change color of background animation
            Animator animatorBackgroundColor = createAnimatorColor(buttonMic.getBackground(), initialColor, finalColor, duration);

            animatorSet.play(enlargeAnimation).with(animatorIconColor).with(animatorBackgroundColor);
            animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
            animatorSet.start();

        } else {
            //increase size
            ViewGroup.LayoutParams layoutParams = buttonMic.getLayoutParams();
            layoutParams.width = finalSizeInPixels;
            layoutParams.height = finalSizeInPixels;
            buttonMic.setLayoutParams(layoutParams);

            // change color of icon
            buttonMic.getDrawable().setColorFilter(finalColorIcon, PorterDuff.Mode.SRC_IN);

            // change color of background
            buttonMic.getBackground().setColorFilter(finalColor, PorterDuff.Mode.SRC_IN);
        }
    }

    public void animateDeactivation(Context context, final ButtonMic buttonMic){
        int duration = buttonMic.getResources().getInteger(R.integer.durationStandard);

        AnimatorSet animatorSet = new AnimatorSet();
        int finalColorIcon;
        int finalColorBackground;
        if(buttonMic.isMute()){
            finalColorIcon = ButtonMic.colorMutedDeactivated.iconColor.getDefaultColor();
            finalColorBackground = ButtonMic.colorMutedDeactivated.backgroundColor.getDefaultColor();
        }else{
            finalColorIcon = ButtonMic.colorDeactivated.iconColor.getDefaultColor();
            finalColorBackground = ButtonMic.colorDeactivated.backgroundColor.getDefaultColor();
        }

        // change color of icon animation
        Animator animatorIconColor = createAnimatorColor(buttonMic.getDrawable(), buttonMic.getCurrentColor().iconColor.getDefaultColor(), finalColorIcon, duration);

        // change color of background animation
        Animator animatorBackgroundColor = createAnimatorColor(buttonMic.getBackground(), buttonMic.getCurrentColor().backgroundColor.getDefaultColor(), finalColorBackground, duration);

        animatorSet.play(animatorIconColor).with(animatorBackgroundColor);
        animatorSet.start();
    }

    public void animateActivation(Context context, final ButtonMic buttonMic){
        int duration = buttonMic.getResources().getInteger(R.integer.durationStandard);

        AnimatorSet animatorSet = new AnimatorSet();
        int finalColorIcon;
        int finalColorBackground;
        if(buttonMic.isMute()){
            finalColorIcon = ButtonMic.colorMutedActivated.iconColor.getDefaultColor();
            finalColorBackground = ButtonMic.colorMutedActivated.backgroundColor.getDefaultColor();
        }else{
            finalColorIcon = ButtonMic.colorActivated.iconColor.getDefaultColor();
            finalColorBackground = ButtonMic.colorActivated.backgroundColor.getDefaultColor();
        }

        // change color of icon animation
        Animator animatorIconColor = createAnimatorColor(buttonMic.getDrawable(), buttonMic.getCurrentColor().iconColor.getDefaultColor(), finalColorIcon, duration);

        // change color of background animation
        Animator animatorBackgroundColor = createAnimatorColor(buttonMic.getBackground(), buttonMic.getCurrentColor().backgroundColor.getDefaultColor(), finalColorBackground, duration);

        animatorSet.play(animatorIconColor).with(animatorBackgroundColor);
        animatorSet.start();
    }

    public Animator animateTranslationButtonsCompress(final VoiceTranslationActivity activity, TranslationFragment translationFragment, FloatingActionButton walkieTalkieButton, TextView walkieTalkieText, FloatingActionButton conversationButton, TextView conversationText, FloatingActionButton walkieTalkieButtonSmall, FloatingActionButton conversationButtonSmall, boolean hideActionButtons, Listener listener){
        int duration = activity.getResources().getInteger(R.integer.durationStandard);
        int durationShort = activity.getResources().getInteger(R.integer.durationShort);
        int durationInstant = 0;

        int textActionButtonHeight = walkieTalkieText.getHeight();
        int textActionButtonBottomMargin = ((ConstraintLayout.LayoutParams) walkieTalkieText.getLayoutParams()).bottomMargin;
        int actionButtonHeight = walkieTalkieButton.getHeight();
        int translateButtonHeight = translationFragment.getTranslateButtonHeight();  //143
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) walkieTalkieButton.getLayoutParams();
        int actionButtonTopMargin = layoutParams.topMargin;
        int actionButtonBottomMargin = layoutParams.bottomMargin;

        walkieTalkieButton.setClickable(false);
        conversationButton.setClickable(false);

        Animator animationDisappear = createAnimatorAlpha(new View[]{walkieTalkieButton, walkieTalkieText, conversationButton, conversationText}, walkieTalkieButton.getAlpha(), 0, durationInstant);

        Animator animationTextMargin = createAnimatorBottomMargin(new View[]{walkieTalkieText, conversationText}, textActionButtonBottomMargin, Tools.convertDpToPixels(activity,6), durationShort);
        Animator animationButtonBottomMargin = createAnimatorBottomMargin(new View[]{walkieTalkieButton, conversationButton}, actionButtonBottomMargin, 0, durationShort);
        Animator animationButtonTopMargin = createAnimatorTopMargin(new View[]{walkieTalkieButton, conversationButton}, actionButtonTopMargin, Tools.convertDpToPixels(activity,8), durationShort);
        Animator animationButtonHeight = createAnimatorHeight(new View[]{walkieTalkieButton, conversationButton}, actionButtonHeight, translateButtonHeight, durationShort);
        Animator animationTextHeight = createAnimatorHeight(new View[]{walkieTalkieText, conversationText}, textActionButtonHeight, Tools.convertDpToPixels(activity,2), durationShort);   //we set final size to 2dp because too small size causes UI problems

        Animator animationAppear = createAnimatorAlpha(new View[]{walkieTalkieButtonSmall, conversationButtonSmall}, walkieTalkieButtonSmall.getAlpha(), 1, duration);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(animationTextMargin).after(animationDisappear);
        animatorSet.play(animationTextMargin).with(animationButtonBottomMargin).with(animationButtonTopMargin).with(animationButtonHeight).with(animationTextHeight);
        if(!hideActionButtons) {
            animatorSet.play(animationAppear).after(animationTextMargin);
        }

        animatorSet.setInterpolator(new DecelerateInterpolator(2));

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {
                if(listener != null){
                    listener.onAnimationStart();
                }
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                walkieTalkieButtonSmall.setClickable(true);
                conversationButtonSmall.setClickable(true);
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

    public Animator animateTranslationButtonsEnlarge(final VoiceTranslationActivity activity, TranslationFragment translationFragment, FloatingActionButton walkieTalkieButton, TextView walkieTalkieText, FloatingActionButton conversationButton, TextView conversationText, FloatingActionButton walkieTalkieButtonSmall, FloatingActionButton conversationButtonSmall, Listener listener){
        int duration = activity.getResources().getInteger(R.integer.durationStandard);
        int durationShort = activity.getResources().getInteger(R.integer.durationShort);
        int durationInstant = 0;

        int textActionButtonHeightInit = walkieTalkieText.getHeight();
        int textActionButtonBottomMarginInit = ((ConstraintLayout.LayoutParams) walkieTalkieText.getLayoutParams()).bottomMargin;
        int actionButtonHeightInit = walkieTalkieButton.getHeight();
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) walkieTalkieButton.getLayoutParams();
        int actionButtonTopMarginInit = layoutParams.topMargin;
        int actionButtonBottomMarginInit = layoutParams.bottomMargin;

        int textActionButtonHeight = translationFragment.getTextActionButtonHeight();
        int textActionButtonBottomMargin = translationFragment.getTextActionButtonBottomMargin();
        int actionButtonHeight = translationFragment.getActionButtonHeight();
        int actionButtonTopMargin = translationFragment.getActionButtonTopMargin();
        int actionButtonBottomMargin = translationFragment.getActionButtonBottomMargin();

        walkieTalkieButtonSmall.setClickable(false);
        conversationButtonSmall.setClickable(false);

        Animator animationDisappear = createAnimatorAlpha(new View[]{walkieTalkieButtonSmall, conversationButtonSmall}, walkieTalkieButtonSmall.getAlpha(), 0, durationInstant);

        Animator animationTextMargin = createAnimatorBottomMargin(new View[]{walkieTalkieText, conversationText}, textActionButtonBottomMarginInit, textActionButtonBottomMargin, durationShort);
        Animator animationButtonBottomMargin = createAnimatorBottomMargin(new View[]{walkieTalkieButton, conversationButton}, actionButtonBottomMarginInit, actionButtonBottomMargin, durationShort);
        Animator animationButtonTopMargin = createAnimatorTopMargin(new View[]{walkieTalkieButton, conversationButton}, actionButtonTopMarginInit, actionButtonTopMargin, durationShort);
        Animator animationButtonHeight = createAnimatorHeight(new View[]{walkieTalkieButton, conversationButton}, actionButtonHeightInit, actionButtonHeight, durationShort);
        Animator animationTextHeight = createAnimatorHeight(new View[]{walkieTalkieText, conversationText}, textActionButtonHeightInit, textActionButtonHeight, durationShort);

        Animator animationAppear = createAnimatorAlpha(new View[]{walkieTalkieButton, walkieTalkieText, conversationButton, conversationText}, walkieTalkieButton.getAlpha(), 1, duration);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(animationTextMargin).after(animationDisappear);
        animatorSet.play(animationTextMargin).with(animationButtonBottomMargin).with(animationButtonTopMargin).with(animationButtonHeight).with(animationTextHeight);
        animatorSet.play(animationAppear).after(animationTextMargin);

        animatorSet.setInterpolator(new DecelerateInterpolator(2));

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {
                if(listener != null){
                    listener.onAnimationStart();
                }
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                walkieTalkieButton.setClickable(true);
                conversationButton.setClickable(true);
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

    public Animator animateCompressActionBar(final VoiceTranslationActivity activity, ConstraintLayout toolbarContainer, TextView title, AppCompatImageButton settingsButton, AppCompatImageButton settingsButtonReduced, AppCompatImageButton backButton, Listener listener){
        int duration = activity.getResources().getInteger(R.integer.durationStandard);
        int durationShort = activity.getResources().getInteger(R.integer.durationShort);

        settingsButton.setClickable(false);

        Animator animatorToolbar = createAnimatorHeight(toolbarContainer, toolbarContainer.getHeight(), Tools.convertDpToPixels(activity,2), duration);
        Animator animatorTranslation = createAnimatorTranslationY(new View[]{title, settingsButton}, (int) title.getTranslationY(), -Tools.convertDpToPixels(activity,56), duration);

        Animator animationAppear = createAnimatorAlpha(new View[]{settingsButtonReduced, backButton}, settingsButtonReduced.getAlpha(), 1, duration);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(animatorToolbar).with(animatorTranslation)/*.with(animationAppear)*/;
        animatorSet.play(animationAppear).after(600).after(animatorToolbar);

        animatorSet.setInterpolator(new DecelerateInterpolator(2));

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {
                if(listener != null){
                    listener.onAnimationStart();
                }
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                settingsButtonReduced.setClickable(true);
                backButton.setClickable(true);
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

    public Animator animateEnlargeActionBar(final VoiceTranslationActivity activity, ConstraintLayout toolbarContainer, TextView title, AppCompatImageButton settingsButton, AppCompatImageButton settingsButtonReduced, AppCompatImageButton backButton, Listener listener){
        int duration = activity.getResources().getInteger(R.integer.durationStandard);
        int durationShort = activity.getResources().getInteger(R.integer.durationShort);

        settingsButtonReduced.setClickable(false);
        backButton.setClickable(false);
        settingsButton.setAlpha((float) 0);

        Animator animationDisappear = createAnimatorAlpha(new View[]{settingsButtonReduced, backButton}, settingsButtonReduced.getAlpha(), 0, duration);

        Animator animatorToolbar = createAnimatorHeight(toolbarContainer, toolbarContainer.getHeight(), Tools.convertDpToPixels(activity,56), duration);
        Animator animatorTranslation = createAnimatorTranslationY(new View[]{title, settingsButton}, (int) title.getTranslationY(), 0, duration);

        Animator animationAppear = createAnimatorAlpha(settingsButton, settingsButton.getAlpha(), 1, duration);

        AnimatorSet animatorSet = new AnimatorSet();
        //animatorSet.play(animatorToolbar).after(animationDisappear);
        animatorSet.play(animatorToolbar).with(animatorTranslation).with(animationDisappear);
        animatorSet.play(animationAppear).after(700).after(animatorToolbar);

        animatorSet.setInterpolator(new DecelerateInterpolator(2));

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {
                if(listener != null){
                    listener.onAnimationStart();
                }
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                settingsButton.setClickable(true);
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

    public Animator animateInputAppearance(final VoiceTranslationActivity activity, FloatingActionButton ttsInputButton, FloatingActionButton copyInputButton, FloatingActionButton cancelInputButton, Listener listener){
        int duration = activity.getResources().getInteger(R.integer.durationStandard);

        ttsInputButton.setVisibility(View.VISIBLE);
        copyInputButton.setVisibility(View.VISIBLE);
        cancelInputButton.setVisibility(View.VISIBLE);

        final Animator animationAppearance = createAnimatorAlpha(new View[]{ttsInputButton, copyInputButton, cancelInputButton}, ttsInputButton.getAlpha(), 1, duration);

        animationAppearance.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                if (listener != null) {
                    listener.onAnimationStart();
                }
            }
            @Override
            public void onAnimationEnd(Animator animator) {
                if (listener != null) {
                    listener.onAnimationEnd();
                }
            }
            @Override
            public void onAnimationCancel(Animator animator) {}
            @Override
            public void onAnimationRepeat(Animator animator) {}
        });
        animationAppearance.start();
        return animationAppearance;
    }

    public Animator animateOutputAppearance(final VoiceTranslationActivity activity, ConstraintLayout outputContainer, View lineSeparator, Listener listener){
        int duration = activity.getResources().getInteger(R.integer.durationStandard);

        outputContainer.setVisibility(View.VISIBLE);
        lineSeparator.setVisibility(View.VISIBLE);

        final Animator animationAppearance = createAnimatorAlpha(new View[]{outputContainer, lineSeparator}, outputContainer.getAlpha(), 1, duration);

        animationAppearance.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                if (listener != null) {
                    listener.onAnimationStart();
                }
            }
            @Override
            public void onAnimationEnd(Animator animator) {
                if (listener != null) {
                    listener.onAnimationEnd();
                }
            }
            @Override
            public void onAnimationCancel(Animator animator) {}
            @Override
            public void onAnimationRepeat(Animator animator) {}
        });
        animationAppearance.start();
        return animationAppearance;
    }

    public Animator animateInputDisappearance(final VoiceTranslationActivity activity, FloatingActionButton ttsInputButton, FloatingActionButton copyInputButton, FloatingActionButton cancelInputButton, Listener listener){
        int duration = activity.getResources().getInteger(R.integer.durationStandard);

        final Animator animationAppearance = createAnimatorAlpha(new View[]{ttsInputButton, copyInputButton, cancelInputButton}, ttsInputButton.getAlpha(), 0, duration);

        animationAppearance.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                if (listener != null) {
                    listener.onAnimationStart();
                }
            }
            @Override
            public void onAnimationEnd(Animator animator) {
                ttsInputButton.setVisibility(View.INVISIBLE);
                copyInputButton.setVisibility(View.INVISIBLE);
                cancelInputButton.setVisibility(View.INVISIBLE);
                if (listener != null) {
                    listener.onAnimationEnd();
                }
            }
            @Override
            public void onAnimationCancel(Animator animator) {}
            @Override
            public void onAnimationRepeat(Animator animator) {}
        });
        animationAppearance.start();
        return animationAppearance;
    }

    public Animator animateOutputDisappearance(final VoiceTranslationActivity activity, ConstraintLayout outputContainer, View lineSeparator, Listener listener){
        int duration = activity.getResources().getInteger(R.integer.durationStandard);

        final Animator animationAppearance = createAnimatorAlpha(new View[]{outputContainer, lineSeparator}, outputContainer.getAlpha(), 0, duration);

        animationAppearance.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                if (listener != null) {
                    listener.onAnimationStart();
                }
            }
            @Override
            public void onAnimationEnd(Animator animator) {
                outputContainer.setVisibility(View.INVISIBLE);
                lineSeparator.setVisibility(View.INVISIBLE);
                if (listener != null) {
                    listener.onAnimationEnd();
                }
            }
            @Override
            public void onAnimationCancel(Animator animator) {}
            @Override
            public void onAnimationRepeat(Animator animator) {}
        });
        animationAppearance.start();
        return animationAppearance;
    }

    public Animator animateViewAppearance(final VoiceTranslationActivity activity, View view, Listener listener){
        int duration = activity.getResources().getInteger(R.integer.durationStandard);

        view.setVisibility(View.VISIBLE);

        final Animator animationAppearance = createAnimatorAlpha(new View[]{view}, view.getAlpha(), 1, duration);

        animationAppearance.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animator) {
                if (listener != null) {
                    listener.onAnimationStart();
                }
            }
            @Override
            public void onAnimationEnd(@NonNull Animator animator) {
                if (listener != null) {
                    listener.onAnimationEnd();
                }
            }
            @Override
            public void onAnimationCancel(@NonNull Animator animator) {}
            @Override
            public void onAnimationRepeat(@NonNull Animator animator) {}
        });
        animationAppearance.start();
        return animationAppearance;
    }

    public Animator animateViewDisappearance(final VoiceTranslationActivity activity, View view, Listener listener){
        int duration = activity.getResources().getInteger(R.integer.durationStandard);

        final Animator animationAppearance = createAnimatorAlpha(new View[]{view}, view.getAlpha(), 0, duration);

        animationAppearance.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animator) {
                if (listener != null) {
                    listener.onAnimationStart();
                }
            }
            @Override
            public void onAnimationEnd(@NonNull Animator animator) {
                view.setVisibility(View.INVISIBLE);
                if (listener != null) {
                    listener.onAnimationEnd();
                }
            }
            @Override
            public void onAnimationCancel(@NonNull Animator animator) {}
            @Override
            public void onAnimationRepeat(@NonNull Animator animator) {}
        });
        animationAppearance.start();
        return animationAppearance;
    }

    public Animator animateSynonymsTextEnlargement(Context context, TextView textView, int topMarginDp, Listener listener){
        AnimatorSet animatorSet= new AnimatorSet();
        int duration=context.getResources().getInteger(R.integer.durationStandard);
        int durationShort=context.getResources().getInteger(R.integer.durationShort);

        textView.setVisibility(View.VISIBLE);

        int width = textView.getWidth();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);  //WRAP_CONTENT
        textView.measure(widthSpec, heightSpec);
        final int textViewFinalHeight = textView.getMeasuredHeight();
        Animator textHeightAnimator = createAnimatorHeight(textView, textView.getHeight(), textViewFinalHeight, duration);
        Animator textMarginAnimator = createAnimatorTopMarginLinear(textView, 0, Tools.convertDpToPixels(context, topMarginDp), duration);
        Animator textAlphaAnimator = createAnimatorAlpha(textView, textView.getAlpha(), 1, duration);
        animatorSet.play(textHeightAnimator).with(textMarginAnimator).with(textAlphaAnimator);
        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animator) {
                if (listener != null) {
                    listener.onAnimationStart();
                }
            }
            @Override
            public void onAnimationEnd(@NonNull Animator animator) {
                if (listener != null) {
                    listener.onAnimationEnd();
                }
            }
            @Override
            public void onAnimationCancel(@NonNull Animator animator) {}
            @Override
            public void onAnimationRepeat(@NonNull Animator animator) {}
        });
        animatorSet.start();
        return animatorSet;
    }

    public Animator animateSynonymsTextReduction(Context context, TextView textView, int topMarginDp, Listener listener){
        AnimatorSet animatorSet= new AnimatorSet();
        int duration=context.getResources().getInteger(R.integer.durationStandard);
        int durationShort=context.getResources().getInteger(R.integer.durationShort);

        Animator textHeightAnimator = createAnimatorHeight(textView, textView.getHeight(), 0, duration);
        Animator textMarginAnimator = createAnimatorTopMarginLinear(textView, Tools.convertDpToPixels(context, topMarginDp), 0, duration);
        Animator textAlphaAnimator = createAnimatorAlpha(textView, textView.getAlpha(), 0, duration);
        animatorSet.play(textHeightAnimator).with(textMarginAnimator).with(textAlphaAnimator);
        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animator) {
                if (listener != null) {
                    listener.onAnimationStart();
                }
            }
            @Override
            public void onAnimationEnd(@NonNull Animator animator) {
                textView.setVisibility(View.INVISIBLE);
                if (listener != null) {
                    listener.onAnimationEnd();
                }
            }
            @Override
            public void onAnimationCancel(@NonNull Animator animator) {}
            @Override
            public void onAnimationRepeat(@NonNull Animator animator) {}
        });
        animatorSet.start();
        return animatorSet;
    }

    public void animateSwitchLanguages(Context context, CardView firstLanguageContainer, CardView secondLanguageContainer, AppCompatImageButton invertLanguagesButton, Listener listener){
        int duration = context.getResources().getInteger(R.integer.durationStandard);

        firstLanguageContainer.setClickable(false);
        secondLanguageContainer.setClickable(false);
        invertLanguagesButton.setClickable(false);

        float finalFirstLanguageX = secondLanguageContainer.getX()-secondLanguageContainer.getTranslationX();
        float finalSecondLanguageX = firstLanguageContainer.getX()-firstLanguageContainer.getTranslationX();

        float finalFirstTranslationX = finalFirstLanguageX - (firstLanguageContainer.getX()-firstLanguageContainer.getTranslationX());
        float finalSecondTranslationX = finalSecondLanguageX - (secondLanguageContainer.getX()-secondLanguageContainer.getTranslationX());

        AnimatorSet animatorSet = new AnimatorSet();

        Animator firstLanguageTranslationAnimator = createAnimatorTranslationX(new View[]{firstLanguageContainer}, firstLanguageContainer.getTranslationX(), finalFirstTranslationX, duration);
        Animator secondLanguageTranslationAnimator = createAnimatorTranslationX(new View[]{secondLanguageContainer}, secondLanguageContainer.getTranslationX(), finalSecondTranslationX, duration);

        animatorSet.play(firstLanguageTranslationAnimator).with(secondLanguageTranslationAnimator);

        animatorSet.setInterpolator(new DecelerateInterpolator(2));

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {
                if (listener != null) {
                    listener.onAnimationStart();
                }
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                firstLanguageContainer.setTranslationX(0);
                secondLanguageContainer.setTranslationX(0);
                firstLanguageContainer.setClickable(true);
                secondLanguageContainer.setClickable(true);
                invertLanguagesButton.setClickable(true);
                if (listener != null) {
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
    }


    public Animator animateTabSelection(final VoiceTranslationActivity activity, MaterialCardView containerConversation, TextView titleConversation, MaterialCardView containerConnection, TextView titleConnection, int tabSelected){
        int duration = activity.getResources().getInteger(R.integer.durationStandard);
        AnimatorSet animatorSet = new AnimatorSet();

        int deselectedColorText = GuiTools.getColorStateList(activity, R.color.primary_very_dark).getDefaultColor();
        int deselectedColorBackground = GuiTools.getColorStateList(activity, R.color.accent_white).getDefaultColor();
        int selectedColorText = GuiTools.getColorStateList(activity, R.color.accent_white).getDefaultColor();
        int selectedColorBackground = GuiTools.getColorStateList(activity, R.color.primary_very_dark).getDefaultColor();

        Animator animatorBackgroundColorConversation;
        Animator animatorTextColorConversation;
        Animator animatorBackgroundColorConnection;
        Animator animatorTextColorConnection;

        if(tabSelected == 0){
            animatorBackgroundColorConversation = createAnimatorColor(containerConversation, deselectedColorBackground, selectedColorBackground, duration);
            animatorTextColorConversation = createAnimatorColor(titleConversation, deselectedColorText, selectedColorText, duration);
            animatorBackgroundColorConnection = createAnimatorColor(containerConnection, selectedColorBackground, deselectedColorBackground, duration);
            animatorTextColorConnection = createAnimatorColor(titleConnection, selectedColorText, deselectedColorText, duration);
        }else{  //tabSelected == 1
            animatorBackgroundColorConversation = createAnimatorColor(containerConversation, selectedColorBackground, deselectedColorBackground, duration);
            animatorTextColorConversation = createAnimatorColor(titleConversation, selectedColorText, deselectedColorText, duration);
            animatorBackgroundColorConnection = createAnimatorColor(containerConnection, deselectedColorBackground, selectedColorBackground, duration);
            animatorTextColorConnection = createAnimatorColor(titleConnection, deselectedColorText, selectedColorText, duration);
        }

        animatorSet.play(animatorBackgroundColorConversation).with(animatorTextColorConversation).with(animatorBackgroundColorConnection).with(animatorTextColorConnection);

        animatorSet.start();
        return animatorSet;
    }




    public void animateGenerateEditText(final VoiceTranslationActivity activity, final ButtonKeyboard buttonKeyboard, final ButtonMic buttonMic, final EditText editText, final ImageButton micPlaceHolder, final Listener listener){
        Point point= new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(point);
        int durationStandard = activity.getResources().getInteger(R.integer.durationStandard);
        int margin = Tools.convertDpToPixels(activity,16);
        int iconSize = Tools.convertDpToPixels(activity, 40);
        int micReducedSize = Tools.convertDpToPixels(activity,ButtonMic.SIZE_ICON_DP);
        int screenWidth=point.x;
        int expandedEditTextWidth=screenWidth-(margin + micReducedSize + margin + iconSize + margin);

        AnimatorSet animatorSet= new AnimatorSet();
        // disappearance keyboard button animation
        final Animator animation1 = createAnimatorAlpha(buttonKeyboard,1f,0f,50);
        // appearance of the editText animation
        final Animator animation2 = createAnimatorAlpha(editText,0f,1f,50);
        // enlargement of the editText animation
        Animator animation3 = createAnimatorWidth(editText, iconSize, expandedEditTextWidth,durationStandard-50);
        // shrinkage of the micPlaceHolder (to reduce size of the entire container of editText)
        Animator animation4 = createAnimatorHeight(micPlaceHolder, micPlaceHolder.getHeight(), Tools.convertDpToPixels(activity, 1), durationStandard);

        animatorSet.play(animation3).with(animation4).after(animation2);
        animatorSet.play(animation2).with(animation1);

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                if (listener != null) {
                    listener.onAnimationStart();
                }
                //change of buttonMic
                buttonMic.setState(ButtonMic.STATE_RETURN);
                editText.setVisibility(EditText.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                buttonKeyboard.setVisibility(View.GONE);
                editText.requestFocus();
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                }
                if (listener != null) {
                    listener.onAnimationEnd();
                }
            }

            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });
        animatorSet.start();
    }

    public void animateDeleteEditText(final VoiceTranslationActivity activity, final ButtonMic buttonMic, final ButtonKeyboard buttonKeyboard, final EditText editText, final ImageButton micPlaceHolder, final Listener listener){
        Point point= new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(point);
        int durationStandard = activity.getResources().getInteger(R.integer.durationStandard);
        int margin = Tools.convertDpToPixels(activity,16);
        int iconSize = Tools.convertDpToPixels(activity, 40);
        int micReducedSize = Tools.convertDpToPixels(activity,ButtonMic.SIZE_ICON_DP);
        int screenWidth = point.x;
        int expandedEditTextWidth = screenWidth-(margin + micReducedSize + margin + iconSize + margin);

        AnimatorSet animatorSet= new AnimatorSet();
        // appearance keyboard button animation
        final Animator animation1 = createAnimatorAlpha(buttonKeyboard,0f,1f,50);
        // disappearance of the editText animation
        final Animator animation2 = createAnimatorAlpha(editText,1f,0f,50);
        // shrinkage of the editText animation
        Animator animation3 = createAnimatorWidth(editText, expandedEditTextWidth, iconSize,durationStandard-50);
        // enlargement of the micPlaceHolder (to increase the size of the entire container of the mic)
        Animator animation4 = createAnimatorHeight(micPlaceHolder, micPlaceHolder.getHeight(), Tools.convertDpToPixels(activity,ButtonMic.SIZE_LISTENING_DP), durationStandard);

        animatorSet.play(animation1).with(animation2);
        animatorSet.play(animation2).after(animation3);
        animatorSet.play(animation3).with(animation4);

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                if (listener != null) {
                    listener.onAnimationStart();
                }
                buttonKeyboard.setVisibility(View.VISIBLE);
                buttonKeyboard.setAlpha(0f);
                buttonMic.setState(ButtonMic.STATE_NORMAL);
            }
            @Override
            public void onAnimationEnd(Animator animator) {
                editText.setVisibility(View.INVISIBLE);
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if(imm!=null) {
                    imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                }
                if (listener != null) {
                    listener.onAnimationEnd();
                }
            }
            @Override
            public void onAnimationCancel(Animator animator) {}
            @Override
            public void onAnimationRepeat(Animator animator) {}
        });
        animatorSet.start();
    }

    public void animateSelection(Context context,View view, int x,int y,ValueAnimator.AnimatorListener listener){
        selectionAnimator.cancel();
        // animation preparation
        CustomDrawable customDrawable;
        if(view.getBackground()==null) {
            customDrawable = new CustomDrawable(context, Tools.convertDpToPixels(context, 6));
            view.setBackground(customDrawable);
        }else{
            customDrawable= (CustomDrawable) view.getBackground();
        }

        final CustomDrawable customDrawableFinal=customDrawable;

        customDrawable.setPosition(x,y);

        // selection animation
        selectionAnimator = ValueAnimator.ofFloat(customDrawable.getSize(), view.getWidth() + 100);
        selectionAnimator.setDuration(350);
        selectionAnimator.setInterpolator(new AccelerateInterpolator());
        selectionAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                customDrawableFinal.setSize((Float) valueAnimator.getAnimatedValue());
            }
        });
        selectionAnimator.addListener(listener);
        selectionAnimator.start();
    }

    public void animateDeselection(View view,int x,int y,ValueAnimator.AnimatorListener listener){
        selectionAnimator.cancel();
        // animation preparation
        final CustomDrawable customDrawable= (CustomDrawable) view.getBackground();
        customDrawable.setPosition(x,y);

        // selection animation
        selectionAnimator= ValueAnimator.ofFloat(customDrawable.getSize(),0);  //vedere se si può fare senza reverse interpolator, ma invertendo i valori
        selectionAnimator.setDuration(350);
                /*valueAnimator.setInterpolator(new Interpolator() {
                    @Override
                    public float getInterpolation(float v) {
                        return Math.abs(v-1);
                    }
                });*/
        selectionAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                customDrawable.setSize((Float) valueAnimator.getAnimatedValue());
            }
        });
        selectionAnimator.addListener(listener);
        selectionAnimator.start();
    }

    public void appearVertically(final View view, int finalHeight){
        createAnimatorHeight(view,view.getHeight(),finalHeight,view.getResources().getInteger(R.integer.durationShort)).start();
    }

    public void disappearVertically(final View view){
        createAnimatorHeight(view,view.getHeight(),1,view.getResources().getInteger(R.integer.durationShort)).start();
    }

    public void appearSecurityLevel(Context context, int duration, final TextView textSecurity, ImageButton securityInfo, final EditText inputRepeatPassword){
        AnimatorSet animatorSet= new AnimatorSet();
        // textSecurity animation
        int finalTextHeight= Tools.convertSpToPixels(context,18);
        int finalTextMargin= Tools.convertDpToPixels(context,12);
        Animator textSecurityAnimation= createAppearFromTopAnimator(duration/2,finalTextHeight,finalTextMargin,textSecurity,new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                if(inputRepeatPassword!=null) {
                    ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) inputRepeatPassword.getLayoutParams();
                    layoutParams.topToBottom = textSecurity.getId();
                    inputRepeatPassword.setLayoutParams(layoutParams);
                }
            }
            @Override
            public void onAnimationEnd(Animator animator) {}
            @Override
            public void onAnimationCancel(Animator animator) {}
            @Override
            public void onAnimationRepeat(Animator animator) {}
        });

        // securityInfo animation
        int finalInfoHeight= Tools.convertSpToPixels(context,16);
        int finalInfoMargin= 0;
        Animator securityInfoAnimation= createAppearFromTopAnimator(duration/2,finalInfoHeight,finalInfoMargin,securityInfo,null);

        animatorSet.play(textSecurityAnimation).with(securityInfoAnimation);
        animatorSet.start();

    }

    public void disappearSecurityLevel(Context context, int duration, TextView textSecurity, ImageButton securityInfo, final EditText inputPassword, final EditText inputRepeatPassword){
        AnimatorSet animatorSet= new AnimatorSet();
        int initialTextMargin=Tools.convertDpToPixels(context,12);
        // textSecurity animation
        Animator textSecurityAnimation= createDisappearFromTopAnimator(context,duration/2,initialTextMargin,View.GONE,textSecurity);

        // securityInfo animation
        int initialInfoMargin=0;
        Animator securityInfoAnimation= createDisappearFromTopAnimator(context,duration/2,initialInfoMargin,View.GONE,securityInfo);

        animatorSet.play(textSecurityAnimation).with(securityInfoAnimation);
        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {}
            @Override
            public void onAnimationEnd(Animator animator) {
                if(inputRepeatPassword!=null) {
                    ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) inputRepeatPassword.getLayoutParams();
                    layoutParams.topToBottom = inputPassword.getId();
                    inputRepeatPassword.setLayoutParams(layoutParams);
                }
            }
            @Override
            public void onAnimationCancel(Animator animator) {}
            @Override
            public void onAnimationRepeat(Animator animator) {}
        });
        animatorSet.start();

    }

    public void appearFromBottom(Context context, int duration, int finalSize, View...view){
        AnimatorSet animatorSet= new AnimatorSet();
        Animator[] animators= new Animator[view.length];
        for(int i=0;i<view.length;i++){
            animators[i]=createAppearFromBottomAnimator(context,duration,finalSize,view[i]);
        }
        if(animators.length>1) {
            for (int i = 1; i < animators.length; i++) {
                animatorSet.play(animators[i - 1]).with(animators[i]);
            }
        }else if(animators.length==1){
            animatorSet.play(animators[0]);
        }
        animatorSet.start();
    }

    public void disappearFromBottom(Context context, int duration, int finalVisibility, View...view){
        AnimatorSet animatorSet= new AnimatorSet();
        Animator[] animators= new Animator[view.length];
        for(int i=0;i<view.length;i++){
            animators[i]=createDisappearFromBottomAnimator(context,duration,finalVisibility,view[i]);
        }
        if(animators.length>1) {
            for (int i = 1; i < animators.length; i++) {
                animatorSet.play(animators[i - 1]).with(animators[i]);
            }
        }else if(animators.length==1){
            animatorSet.play(animators[0]);
        }
        animatorSet.start();
    }

    public Animator createAppearFromTopAnimator(int duration, int finalHeight, int finalMargin, final View view, final Animator.AnimatorListener animatorListener){
        AnimatorSet animatorSet= new AnimatorSet();

        // bottom margin enlargement animation
        Animator animationTopMargin=null;
        if(finalMargin>0) {
            animationTopMargin = createAnimatorTopMargin(view, 0, finalMargin, duration/2);
        }

        // height increase animation
        Animator animationHeight= createAnimatorHeight(view,1,finalHeight,duration/2);

        // appearance animation
        Animator animationAppareance =createAnimatorAlpha(view,0f,1f,duration/2);

        if(animationTopMargin!=null) {
            animatorSet.play(animationTopMargin).with(animationHeight);
        }
        animatorSet.play(animationAppareance).after(animationHeight);

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(final Animator animator) {
                view.setVisibility(View.VISIBLE);
                view.setAlpha(0f);
                if(animatorListener!=null)
                    animatorListener.onAnimationStart(animator);
            }
            @Override
            public void onAnimationEnd(Animator animator) {
                if(animatorListener!=null)
                    animatorListener.onAnimationEnd(animator);
            }
            @Override
            public void onAnimationCancel(Animator animator) {
                if(animatorListener!=null)
                    animatorListener.onAnimationCancel(animator);
            }
            @Override
            public void onAnimationRepeat(Animator animator) {
                if(animatorListener!=null)
                    animatorListener.onAnimationCancel(animator);
            }
        });
        return animatorSet;
    }
    
    public Animator createDisappearFromTopAnimator(final Context context, int duration, int initialMargin, final int finalVisibility, final View view){
        final AnimatorSet animatorSet= new AnimatorSet();
        final int initialSize=view.getHeight();

        // shrinkage top margin animation
        Animator animationTopMargin=null;
        if(initialMargin>0) {
            animationTopMargin = createAnimatorTopMargin(view, initialMargin, 0, duration/2);
        }

        // height decrease animation
        Animator animationHeight= createAnimatorHeight(view,initialSize,1,duration/2);

        // disappearance animation
        Animator animationDisappareance =createAnimatorAlpha(view,1f,0f,duration/2);

        if(animationTopMargin!=null) {
            animatorSet.play(animationTopMargin).with(animationHeight);
        }
        animatorSet.play(animationHeight).after(animationDisappareance);

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {}
            @Override
            public void onAnimationEnd(Animator animator) {
                view.setVisibility(finalVisibility);
                //ripristino delle condizioni iniziali della view
                /*view.setAlpha(1f);
                ViewPropertiesAdapter adaptedView=new ViewPropertiesAdapter(view);
                adaptedView.setTopMargin(Tools.convertDpToPixels(context,12));
                adaptedView.setHeight(initialSize);*/
            }
            @Override
            public void onAnimationCancel(Animator animator) {}
            @Override
            public void onAnimationRepeat(Animator animator) {}
        });
        return animatorSet;
    }

    public Animator createAppearFromBottomAnimator(final Context context, int duration, int finalSize, final View view){
        AnimatorSet animatorSet= new AnimatorSet();

        // bottom margin enlargement animation
        Animator animationBottomMargin=createAnimatorBottomMargin(view,0,Tools.convertDpToPixels(context,16),duration/2);

        // height increase animation
        Animator animationHeight= createAnimatorHeight(view,1,finalSize,duration/2);

        // appearance animation
        Animator animationAppareance =createAnimatorAlpha(view,0f,1f,duration/2);

        animatorSet.play(animationBottomMargin).with(animationHeight);
        animatorSet.play(animationAppareance).after(animationBottomMargin);

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                view.setVisibility(View.VISIBLE);
                view.setAlpha(0f);
            }
            @Override
            public void onAnimationEnd(Animator animator) {}
            @Override
            public void onAnimationCancel(Animator animator) {}
            @Override
            public void onAnimationRepeat(Animator animator) {}
        });
        return animatorSet;
    }

    public Animator createDisappearFromBottomAnimator(final Context context, int duration, final int finalVisibility, final View view){
        final AnimatorSet animatorSet= new AnimatorSet();
        final int initialSize=view.getHeight();

        // bottom margin shrinkage animation
        Animator animationBottomMargin=createAnimatorBottomMargin(view,Tools.convertDpToPixels(context,12),0,duration/2);

        // height decrease animation
        Animator animationHeight= createAnimatorHeight(view,initialSize,1,duration/2);

        // disappearance animation
        Animator animationDisappareance =createAnimatorAlpha(view,1f,0f,duration/2);

        animatorSet.play(animationBottomMargin).with(animationHeight);
        animatorSet.play(animationBottomMargin).after(animationDisappareance);

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {}
            @Override
            public void onAnimationEnd(Animator animator) {
                view.setVisibility(finalVisibility);
                //ripristino delle condizioni iniziali della view
                /*view.setAlpha(1f);
                ViewPropertiesAdapter adaptedView=new ViewPropertiesAdapter(view);
                adaptedView.setTopMargin(Tools.convertDpToPixels(context,12));
                adaptedView.setHeight(initialSize);*/
            }
            @Override
            public void onAnimationCancel(Animator animator) {}
            @Override
            public void onAnimationRepeat(Animator animator) {}
        });
        return animatorSet;
    }

    private Animator createAnimatorBottomMargin(final View view, int initialPixels, final int finalPixels, int duration){
        ValueAnimator animator= ValueAnimator.ofInt(initialPixels,finalPixels);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) view.getLayoutParams();
                layoutParams.bottomMargin=(int)valueAnimator.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    private Animator createAnimatorBottomMargin(final View[] views, int initialPixels, final int finalPixels, int duration){
        ValueAnimator animator= ValueAnimator.ofInt(initialPixels,finalPixels);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                for (View view: views) {
                    ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) view.getLayoutParams();
                    layoutParams.bottomMargin=(int)valueAnimator.getAnimatedValue();
                    view.setLayoutParams(layoutParams);
                }
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    private Animator createAnimatorTopMarginLinear(final View view, int initialPixels, int finalPixels, int duration){
        ValueAnimator animator= ValueAnimator.ofInt(initialPixels,finalPixels);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) view.getLayoutParams();
                layoutParams.topMargin=(int)valueAnimator.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    private Animator createAnimatorTopMargin(final View view, int initialPixels, int finalPixels, int duration){
        ValueAnimator animator= ValueAnimator.ofInt(initialPixels,finalPixels);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) view.getLayoutParams();
                layoutParams.topMargin=(int)valueAnimator.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    private Animator createAnimatorTopMargin(final View[] views, int initialPixels, int finalPixels, int duration){
        ValueAnimator animator= ValueAnimator.ofInt(initialPixels,finalPixels);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                for (View view: views) {
                    ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) view.getLayoutParams();
                    layoutParams.topMargin = (int) valueAnimator.getAnimatedValue();
                    view.setLayoutParams(layoutParams);
                }
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    public Animator createAnimatorElevation(View view, int initialPixels, int finalPixels, int duration){
        Animator animationBottomMargin = ObjectAnimator.ofFloat(view, "elevation", initialPixels, finalPixels);
        animationBottomMargin.setDuration(duration);
        return animationBottomMargin;
    }

    public Animator createAnimatorTranslationX(View view, float initialPixels, float finalPixels, int duration){
        Animator animationBottomMargin = ObjectAnimator.ofFloat(view, "translationX", initialPixels, finalPixels);
        animationBottomMargin.setDuration(duration);
        return animationBottomMargin;
    }

    public Animator createAnimatorTranslationX(View[] views, float initialPixels, float finalPixels, int duration){
        ValueAnimator animation = ValueAnimator.ofFloat(initialPixels, finalPixels);
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                for (View view : views) {
                    view.setTranslationX((float) animation.getAnimatedValue());
                }
            }
        });
        animation.setDuration(duration);
        return animation;
    }

    public Animator createAnimatorTranslationY(View view, int initialPixels, int finalPixels, int duration){
        Animator animationBottomMargin = ObjectAnimator.ofFloat(view, "translationY", initialPixels, finalPixels);
        animationBottomMargin.setDuration(duration);
        return animationBottomMargin;
    }

    public Animator createAnimatorTranslationY(View[] views, int initialPixels, int finalPixels, int duration){
        ValueAnimator animation = ValueAnimator.ofFloat(initialPixels, finalPixels);
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                for (View view : views) {
                    view.setTranslationY((float) animation.getAnimatedValue());
                }
            }
        });
        animation.setDuration(duration);
        return animation;
    }

    public Animator createAnimatorWidth(final View view, int initialPixels, int finalPixels, int duration){
        ValueAnimator animator= ValueAnimator.ofInt(initialPixels,finalPixels);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                layoutParams.width=(int)valueAnimator.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    public Animator createAnimatorHeight(final View view, int initialPixels, int finalPixels, int duration){
        ValueAnimator animator= ValueAnimator.ofInt(initialPixels,finalPixels);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                layoutParams.height=(int)valueAnimator.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    public Animator createAnimatorHeight(final View[] views, int initialPixels, int finalPixels, int duration){
        ValueAnimator animator= ValueAnimator.ofInt(initialPixels,finalPixels);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                for (View view: views) {
                    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                    layoutParams.height = (int) valueAnimator.getAnimatedValue();
                    view.setLayoutParams(layoutParams);
                }
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    public Animator createAnimatorSize(final View view, int initialWidthInPixels, int initialHeightInPixels, int finalWidthInPixels, int finalHeightInPixels, int duration){
        AnimatorSet animatorSet= new AnimatorSet();
        Animator animatorWidth=createAnimatorWidth(view, initialWidthInPixels, finalWidthInPixels, duration);
        Animator animatorHeight=createAnimatorHeight(view, initialHeightInPixels, finalHeightInPixels, duration);
        animatorSet.play(animatorWidth).with(animatorHeight);

        return animatorSet;
    }

    public Animator createAnimatorSize(final View view, int initialWidthInPixels, int initialHeightInPixels, int finalWidthInPixels, int finalHeightInPixels, int durationWidth,int durationHeight){
        AnimatorSet animatorSet= new AnimatorSet();
        Animator animatorWidth=createAnimatorWidth(view, initialWidthInPixels, finalWidthInPixels, durationWidth);
        Animator animatorHeight=createAnimatorHeight(view, initialHeightInPixels, finalHeightInPixels, durationHeight);
        animatorSet.play(animatorWidth).with(animatorHeight);

        return animatorSet;
    }

    public Animator createAnimatorScale(final View view, int initialPixels, int finalPixels, int duration){
        ValueAnimator animator= ValueAnimator.ofInt(initialPixels,finalPixels);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                layoutParams.height=(int)valueAnimator.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    public Animator createAnimatorAlpha(View view, float initialValue, float finalValue, int duration){
        Animator animation = ObjectAnimator.ofFloat(view, "alpha", initialValue, finalValue);
        animation.setDuration(duration);
        return animation;
    }

    public Animator createAnimatorAlpha(View[] views, float initialValue, float finalValue, int duration){
        ValueAnimator animation = ValueAnimator.ofFloat(initialValue, finalValue);
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                for (View view : views) {
                    view.setAlpha((float) animation.getAnimatedValue());
                }
            }
        });
        animation.setDuration(duration);
        return animation;
    }

    public Animator createAnimatorColor(final Drawable drawable, int initialColor, final int finalColor, int duration){
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), initialColor, finalColor);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                drawable.setColorFilter((int) animator.getAnimatedValue(),PorterDuff.Mode.SRC_IN);
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    public Animator createAnimatorColor(final TextView textView, int initialColor, final int finalColor, int duration){
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), initialColor, finalColor);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                textView.setTextColor((int) animator.getAnimatedValue());
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    public Animator createAnimatorColor(final MaterialCardView card, int initialColor, final int finalColor, int duration){
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), initialColor, finalColor);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                card.setCardBackgroundColor((int) animator.getAnimatedValue());
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    public Animator createAnimatorColor(final MaterialButton drawable, int initialColor, int finalColor, int duration){
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), initialColor, finalColor);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                drawable.setBackgroundColor((int) animator.getAnimatedValue());
            }
        });
        animator.setDuration(duration);

        return animator;
    }

    public class CustomDrawable extends Drawable {

        private Paint paint;
        private float cx,cy;
        private float size;


        CustomDrawable(Context context,float size){
            this.size=size;
            paint=new Paint();
            paint.setColor(GuiTools.getColor(context,R.color.pressed_color));
        }

        public void setSize(float size){
            this.size=size;
            invalidateSelf();
        }

        private void setPosition(float x, float y){
            this.cx=x;
            this.cy=y;
        }

        @Override
        public void draw(@NonNull Canvas canvas){
            canvas.save();
            canvas.drawCircle(cx,cy,size,paint);
        }

        @Override
        public void setAlpha(int alpha) {
            // Has no effect
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            // Has no effect
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            // Not Implemented
            return PixelFormat.OPAQUE;
        }

        public float getSize() {
            return size;
        }
    }

    /*private class AnimationIconToMic {
        private ValueAnimator valueAnimator;

        private AnimationIconToMic(Context context, final ViewPropertiesAdapter buttonMic, final ViewPropertiesAdapter micInput){
            final float[] micAnimationValues={Tools.convertDpToPixels(context,24),Tools.convertDpToPixels(context,38)};
            final float[] micInputAnimationValues={Tools.convertDpToPixels(context,0),Tools.convertDpToPixels(context,11)};
            final float micInputAnimationStartTime= (float) 0.6;
            final float micInputAnimationDuration= (1 - micInputAnimationStartTime);

            valueAnimator = ValueAnimator.ofObject(new FloatArrayEvaluator(),micAnimationValues,micInputAnimationValues);
            valueAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            valueAnimator.setDuration(250);
            valueAnimator.setEvaluator(new FloatArrayEvaluator() {
                @Override
                public float[] evaluate(float fraction, float[] micValues, float[] micInputValues) {
                    //l' evaluator ritornerà un array di due interi, nel primo ci inseriamo il valore dell' animazione del microfono
                    //e nel secondo 0 prima che l' animazione parta e il valore dell' animazione dell' anim. del micInput una volta partita

                    float micAnimationValue= micValues[0]+(micValues[1]-micValues[0])*fraction; //verificare se v parte da 0
                    float micInputAnimationValue=-1;
                    if(fraction > micInputAnimationStartTime){
                        float subFraction= (fraction-micInputAnimationStartTime)*(1/micInputAnimationDuration);
                        micInputAnimationValue= micInputValues[0]+(micInputValues[1]-micInputValues[0])*subFraction;
                    }

                    return new float[]{micAnimationValue,micInputAnimationValue};
                }
            });
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float[] values= (float[]) valueAnimator.getAnimatedValue();
                    buttonMic.setWidth((int) values[0]);
                    buttonMic.setHeight((int) values[0]);
                    if(values[1]!=-1){
                        micInput.setHeight((int) values[1]);
                    }
                }
            });
            valueAnimator.addCallback(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {micInput.getView().setVisibility(View.VISIBLE);}
                @Override
                public void onAnimationEnd(Animator animator) {}
                @Override
                public void onAnimationCancel(Animator animator) {}
                @Override
                public void onAnimationRepeat(Animator animator) {}
            });
        }

        private AnimationIconToMic(Context context,final ViewPropertiesAdapter buttonMic){
            final int[] micAnimationValues={Tools.convertDpToPixels(context,24),Tools.convertDpToPixels(context,38)};
            valueAnimator = ValueAnimator.ofInt(micAnimationValues[0],micAnimationValues[1]);
            valueAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            valueAnimator.setDuration(250);
            valueAnimator.setEvaluator(new TypeEvaluator() {
                @Override
                public Object evaluate(float v, Object initialValue, Object finalValue) {
                    return (int)initialValue+((int)finalValue-(int)initialValue)*v; //verificare se v parte da 0
                }
            });
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int value= (int) valueAnimator.getAnimatedValue();
                    buttonMic.setWidth(value);
                    buttonMic.setHeight(value);
                }
            });
        }

        public void start(){
            eventHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    valueAnimator.start();
                }
            },50);
        }

        public void startInstantly(){
            valueAnimator.start();
        }
    }

    private class AnimationMicToIcon {
        private ValueAnimator animator;

        private AnimationMicToIcon(Context context,final ViewPropertiesAdapter buttonMic, final ViewPropertiesAdapter micInput){
            final float[] micAnimationValues={Tools.convertDpToPixels(context,38),Tools.convertDpToPixels(context,24)};
            final float[] micInputAnimationValues={Tools.convertDpToPixels(context,11),Tools.convertDpToPixels(context,0)};
            final float micInputAnimationDuration= (float) 0.4;

            animator= ValueAnimator.ofObject(new FloatArrayEvaluator(),micAnimationValues,micInputAnimationValues);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.setDuration(250);
            animator.setEvaluator(new FloatArrayEvaluator() {
                @Override
                public float[] evaluate(float fraction, float[] micValues, float[] micInputValues) {
                    //l' evaluator ritornerà un array di due interi, nel primo ci inseriamo il valore dell' animazione del microfono
                    //e nel secondo 0 prima che l' animazione parta e il valore dell' animazione dell' anim. del micInput una volta partita

                    float micAnimationValue= micValues[0]+(micValues[1]-micValues[0])*fraction; //verificare se v parte da 0
                    float micInputAnimationValue=-1;
                    if(fraction < micInputAnimationDuration){
                        float subFraction= fraction*(1/micInputAnimationDuration);
                        micInputAnimationValue= micInputValues[0]+(micInputValues[1]-micInputValues[0])*subFraction;
                    }

                    return new float[]{micAnimationValue,micInputAnimationValue};
                }
            });
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float[] values= (float[]) valueAnimator.getAnimatedValue();
                    buttonMic.setWidth((int) values[0]);
                    buttonMic.setHeight((int) values[0]);
                    if(values[1]!=-1){
                        micInput.setHeight((int) values[1]);
                    }
                }
            });
            animator.addCallback(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {}
                @Override
                public void onAnimationEnd(Animator animator) {micInput.getView().setVisibility(View.GONE);}
                @Override
                public void onAnimationCancel(Animator animator) {}
                @Override
                public void onAnimationRepeat(Animator animator) {}
            });
        }

        private AnimationMicToIcon(Context context,final ViewPropertiesAdapter buttonMic){
            final int[] micAnimationValues={Tools.convertDpToPixels(context,38),Tools.convertDpToPixels(context,24)};
            animator= ValueAnimator.ofInt(micAnimationValues[0],micAnimationValues[1]);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.setDuration(250);
            animator.setEvaluator(new TypeEvaluator() {
                @Override
                public Object evaluate(float v, Object initialValue, Object finalValue) {
                    return (int)initialValue+((int)finalValue-(int)initialValue)*v; //verificare se v parte da 0
                }
            });
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int value= (int) valueAnimator.getAnimatedValue();
                    buttonMic.setWidth(value);
                    buttonMic.setHeight(value);
                }
            });
        }

        public void start(){
            animator.start();
        }
    }*/

    public abstract static class Listener {
        public void onAnimationStart(){}
        public void onAnimationEnd(){}
    }

    public abstract static class EndListener {
        public abstract void onAnimationEnd();
    }
}