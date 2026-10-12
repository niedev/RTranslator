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

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.tools.gui.animations.CustomAnimator;
import nie.translator.rtranslator.voice_translation.VoiceTranslationActivity;
import nie.translator.rtranslator.voice_translation._conversation_mode._conversation.main.ConversationMainFragment;

public class ButtonKeyboard extends DeactivableButton {
    private Context context;
    private CustomAnimator animator= new CustomAnimator();

    public ButtonKeyboard(Context context) {
        super(context);
        this.context=context;
    }

    public ButtonKeyboard(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context=context;
    }

    public ButtonKeyboard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context=context;
    }

    public void generateEditText(VoiceTranslationActivity activity, final ConversationMainFragment voiceTranslationFragment, final ButtonMic buttonMic, final EditText editText, ImageButton micPlaceHolder, boolean animated){
        if(animated) {
            animator.animateGenerateEditText(activity, this, buttonMic, editText, micPlaceHolder, new CustomAnimator.Listener() {
                @Override
                public void onAnimationStart() {
                    buttonMic.setClickable(false);
                    voiceTranslationFragment.setInputActive(false);
                }

                @Override
                public void onAnimationEnd() {
                    voiceTranslationFragment.setInputActive(true);
                    buttonMic.setClickable(true);
                }
            });
        }else{
            Point point= new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(point);
            int margin = Tools.convertDpToPixels(activity,16);
            int iconSize = Tools.convertDpToPixels(activity, 40);
            int micReducedSize = Tools.convertDpToPixels(activity,ButtonMic.SIZE_ICON_DP);
            int screenWidth=point.x;
            int expandedEditTextWidth=screenWidth-(margin + micReducedSize + margin + iconSize + margin);

            //shrinkage of the micPlaceHolder (to reduce size of the entire container of editText)
            ViewGroup.LayoutParams layoutParams = micPlaceHolder.getLayoutParams();
            layoutParams.height = Tools.convertDpToPixels(activity, 1);
            micPlaceHolder.setLayoutParams(layoutParams);

            //buttonMic change
            //at this point the buttonMic state is normal
            buttonMic.setState(ButtonMic.STATE_RETURN);

            editText.setVisibility(EditText.VISIBLE);
            this.setVisibility(GONE);

            ViewPropertiesAdapter adapter=new ViewPropertiesAdapter(editText);
            adapter.setWidth(expandedEditTextWidth);
        }
    }
}
