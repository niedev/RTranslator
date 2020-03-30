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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import androidx.appcompat.widget.AppCompatTextView;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.gui.animations.CustomAnimator;

public class AnimatedTextView extends AppCompatTextView {
    CustomAnimator animator=new CustomAnimator();

    public AnimatedTextView(Context context) {
        super(context);
        setSingleLine(true);
    }

    public AnimatedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSingleLine(true);
    }

    public AnimatedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setSingleLine(true);
    }

    public void setText(final String text, boolean animated){
        if(animated){
            AnimatorSet animatorSet= new AnimatorSet();

            final int duration=getResources().getInteger(R.integer.durationStandard);
            String oldText=getText().toString();
            setText(text);
            measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            final int newWidth=getMeasuredWidth();
            setText(oldText);

            Animator disappearAnimator=animator.createAnimatorAlpha(this,1f,0f,duration);
            disappearAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {}
                @Override
                public void onAnimationEnd(Animator animation) {
                    AnimatedTextView.super.setText(text);
                    animator.createAnimatorAlpha(AnimatedTextView.this,0f,1f,duration).start();
                }
                @Override
                public void onAnimationCancel(Animator animation) {}
                @Override
                public void onAnimationRepeat(Animator animation) {}
            });
            Animator enlargeAnimator=animator.createAnimatorWidth(this,getWidth(),newWidth,duration*2);

            animatorSet.play(disappearAnimator).with(enlargeAnimator);
            animatorSet.start();
        }else {
            setText(text);
        }
    }
}
