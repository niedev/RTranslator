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
import android.util.AttributeSet;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.gui.animations.CustomAnimator;

public class KeyFileSelectorButton extends DeactivableButton{
    private Context context;
    private CustomAnimator animator = new CustomAnimator();

    public KeyFileSelectorButton(Context context) {
        super(context);
        this.context = context;
        deactivatedColor = GuiTools.getColorStateList(context, R.color.gray);
        activatedColor = GuiTools.getColorStateList(context, R.color.primary);
        color = activatedColor;
    }

    public KeyFileSelectorButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        deactivatedColor = GuiTools.getColorStateList(context, R.color.gray);
        activatedColor = GuiTools.getColorStateList(context, R.color.primary);
        color = activatedColor;
    }

    public KeyFileSelectorButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        deactivatedColor = GuiTools.getColorStateList(context, R.color.gray);
        activatedColor = GuiTools.getColorStateList(context, R.color.primary);
        color = activatedColor;
    }

    @Override
    public void activate(boolean start) {
        super.activate(start);
        animator.createAnimatorColor(getDrawable(), color.getDefaultColor(), activatedColor.getDefaultColor(), getResources().getInteger(R.integer.durationShort) * 2).start();
        color = activatedColor;
    }

    @Override
    public void deactivate(int reason) {
        super.deactivate(reason);
        animator.createAnimatorColor(getDrawable(), color.getDefaultColor(), deactivatedColor.getDefaultColor(), getResources().getInteger(R.integer.durationShort) * 2).start();
        color = deactivatedColor;
    }
}
