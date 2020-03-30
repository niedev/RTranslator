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

package nie.translator.rtranslatordevedition.tools.gui.animations;

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
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.kyleduo.switchbutton.SwitchButton;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.Tools;
import nie.translator.rtranslatordevedition.tools.gui.ButtonKeyboard;
import nie.translator.rtranslatordevedition.tools.gui.ButtonMic;
import nie.translator.rtranslatordevedition.tools.gui.GuiTools;
import nie.translator.rtranslatordevedition.voice_translation.VoiceTranslationActivity;

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
        final int micFinalSize= Tools.convertDpToPixels(context,micSizeInDp);
        final int micInputFinalWidth= micInput.getMeasuredWidth();
        final int micInputFinalHeight= micInput.getMeasuredHeight();
        Animator micAnimator=createAnimatorSize(buttonMic,buttonMic.getWidth(),buttonMic.getHeight(),micFinalSize,micFinalSize,duration);
        Animator micInputAnimator=createAnimatorSize(micInput,micInput.getWidth(),micInput.getHeight(),micInputFinalWidth,micInputFinalHeight,durationShort,duration);
        animatorSet.play(micAnimator).with(micInputAnimator);
        animatorSet.start();
    }

    public void animateIconToMic(Context context, ButtonMic buttonMic){
        final int micFinalSize=Tools.convertDpToPixels(context,micSizeInDp);
        Animator micAnimator=createAnimatorSize(buttonMic,buttonMic.getWidth(),buttonMic.getHeight(),micFinalSize,micFinalSize,context.getResources().getInteger(R.integer.durationStandard));
        micAnimator.start();
    }

    public void animateMicToIcon(Context context, ButtonMic buttonMic, TextView micInput){
        AnimatorSet animatorSet= new AnimatorSet();
        int duration=context.getResources().getInteger(R.integer.durationStandard);
        final int micFinalSize=Tools.convertDpToPixels(context,iconSizeInDp);
        final int micInputFinalSize= 1;
        Animator micAnimator=createAnimatorSize(buttonMic,buttonMic.getWidth(),buttonMic.getHeight(),micFinalSize,micFinalSize,duration);
        Animator micInputAnimator=createAnimatorSize(micInput,micInput.getWidth(),micInput.getHeight(),micInputFinalSize,micInputFinalSize,duration);
        animatorSet.play(micAnimator).with(micInputAnimator);
        animatorSet.start();
    }

    public void animateMicToIcon(Context context, ButtonMic buttonMic){
        final int micFinalSize=Tools.convertDpToPixels(context,iconSizeInDp);
        Animator micAnimator=createAnimatorSize(buttonMic,buttonMic.getWidth(),buttonMic.getHeight(),micFinalSize,micFinalSize,context.getResources().getInteger(R.integer.durationStandard));
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

    public void animatePairingSwitchActivation(SwitchButton switchButton){
        AnimatorSet animatorSet= new AnimatorSet();
        int duration=switchButton.getResources().getInteger(R.integer.durationLong);
        Animator animatorThumb=createAnimatorColor(switchButton,switchButton.getThumbColor().getDefaultColor(), GuiTools.getColor(switchButton.getContext(),R.color.switch_color),duration,false);
        Animator animatorBack=createAnimatorColor(switchButton,switchButton.getBackColor().getDefaultColor(),GuiTools.getColor(switchButton.getContext(),R.color.switch_background_color),duration,true);
        animatorSet.play(animatorThumb).with(animatorBack);
        animatorSet.start();
    }

    public void animatePairingSwitchDeactivation(SwitchButton switchButton){
        AnimatorSet animatorSet= new AnimatorSet();
        int duration=switchButton.getResources().getInteger(R.integer.durationLong);
        Animator animatorThumb=createAnimatorColor(switchButton,switchButton.getThumbColor().getDefaultColor(),GuiTools.getColor(switchButton.getContext(),R.color.gray),duration,false);
        Animator animatorBack=createAnimatorColor(switchButton,switchButton.getBackColor().getDefaultColor(),GuiTools.getColor(switchButton.getContext(),R.color.very_light_gray),duration,true);
        animatorSet.play(animatorThumb).with(animatorBack);
        animatorSet.start();
    }

    public void animateOnVoiceStart(final ButtonMic buttonMic){
        int duration=buttonMic.getResources().getInteger(R.integer.durationShort);

        // enlargement animation
        /*buttonMic.setImageDrawable(buttonMic.getDrawable(R.drawable.enlargable_from_voice_mic));
        ((AnimatedVectorDrawable)buttonMic.getDrawable()).start();*/
        final Animation enlargeAnimation=AnimationUtils.loadAnimation(buttonMic.getContext(),R.anim.partial_enlarge_icon);
        enlargeAnimation.setDuration(duration);
        buttonMic.startAnimation(enlargeAnimation);

        // change color animation
        int initialColor=GuiTools.getColor(buttonMic.getContext(),R.color.primary);
        int finalColor=GuiTools.getColor(buttonMic.getContext(),R.color.accent);
        Animator animatorColor=createAnimatorColor(buttonMic.getDrawable(),initialColor,finalColor,duration);
        animatorColor.start();
    }

    public void animateOnVoiceEnd(final ButtonMic buttonMic){
        int duration=buttonMic.getResources().getInteger(R.integer.durationShort);
        //dwindle animation
        /*buttonMic.setImageDrawable(buttonMic.getDrawable(R.drawable.dwindable_from_voice_mic));
        ((AnimatedVectorDrawable)buttonMic.getDrawable()).start();*/
        final Animation enlargeAnimation=AnimationUtils.loadAnimation(buttonMic.getContext(),R.anim.partial_dwindle_icon);
        enlargeAnimation.setDuration(duration);
        buttonMic.startAnimation(enlargeAnimation);

        // change color animation
        int initialColor=GuiTools.getColor(buttonMic.getContext(),R.color.accent);
        int finalColor=GuiTools.getColor(buttonMic.getContext(),R.color.primary);
        Animator animatorColor=createAnimatorColor(buttonMic.getDrawable(),initialColor,finalColor,duration);
        animatorColor.start();
    }

    public void animateGenerateEditText(final VoiceTranslationActivity activity, final ButtonKeyboard buttonKeyboard, final ButtonMic buttonMic, final EditText editText, final Listener listener){
        Point point= new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(point);
        int margin= Tools.convertDpToPixels(activity,28);
        int buttonSize=Tools.convertDpToPixels(activity,24);
        int screenWidth=point.x;
        int expandedEditTextWidth=screenWidth-(margin + buttonSize + margin + buttonSize + margin);

        AnimatorSet animatorSet= new AnimatorSet();
        // disappearance keyboard button animation
        final Animator animation1= createAnimatorAlpha(buttonKeyboard,1f,0f,50);
        // appearance of the editText animation
        final Animator animation2= createAnimatorAlpha(editText,0f,1f,50);
        // enlargement of the editText animation
        Animator animation3= createAnimatorWidth(editText,buttonSize,expandedEditTextWidth,activity.getResources().getInteger(R.integer.durationStandard)-50);

        animatorSet.play(animation3).after(animation2);
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

    public void animateDeleteEditText(final VoiceTranslationActivity activity, final ButtonMic buttonMic, final ButtonKeyboard buttonKeyboard, final EditText editText, final Listener listener){
        Point point= new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(point);
        int margin= Tools.convertDpToPixels(activity,28);
        int buttonSize= Tools.convertDpToPixels(activity,24);
        int screenWidth=point.x;
        int expandedEditTextWidth=screenWidth-(margin + buttonSize + margin + buttonSize + margin);

        AnimatorSet animatorSet= new AnimatorSet();
        // appearance keyboard button animation
        final Animator animation1= createAnimatorAlpha(buttonKeyboard,0f,1f,50);
        // disappearance of the editText animation
        final Animator animation2= createAnimatorAlpha(editText,1f,0f,50);
        // shrinkage of the editText animation
        Animator animation3= createAnimatorWidth(editText,expandedEditTextWidth,buttonSize,activity.getResources().getInteger(R.integer.durationStandard)-50);

        animatorSet.play(animation1).with(animation2);
        animatorSet.play(animation2).after(animation3);

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

    public Animator createAnimatorElevation(View view, int initialPixels, int finalPixels, int duration){
        Animator animationBottomMargin = ObjectAnimator.ofFloat(view, "elevation", initialPixels, finalPixels);
        animationBottomMargin.setDuration(duration);
        return animationBottomMargin;
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

    public Animator createAnimatorAlpha(View view,float initialValue, float finalValue, int duration){
        Animator animation = ObjectAnimator.ofFloat(view, "alpha", initialValue, finalValue);
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

    public Animator createAnimatorColor(final SwitchButton drawable, int initialColor, int finalColor, int duration, boolean background){
        final int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked}, // checked
                new int[] {-android.R.attr.state_checked}, // unchecked
        };
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), initialColor, finalColor);
        if(background) {
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    drawable.setBackColor(new ColorStateList(states,new int[]{(int) animator.getAnimatedValue(),(int) animator.getAnimatedValue()}));
                }
            });
        }else{
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    drawable.setThumbColor(new ColorStateList(states,new int[]{(int) animator.getAnimatedValue(),(int) animator.getAnimatedValue()}));
                }
            });
        }
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