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
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageButton;
import javax.annotation.Nullable;

public class DeactivableButton extends AppCompatImageButton {
    public static final int ACTIVATED=0;
    public static final int DEACTIVATED=1;
    public static final int DEACTIVATED_FOR_CREDIT_EXHAUSTED =2;
    public static final int DEACTIVATED_FOR_MISSING_MIC_PERMISSION =3;
    public static final int DEACTIVATED_FOR_MISSING_OR_WRONG_KEYFILE =4;
    protected int activationStatus=0;
    private OnClickListener activatedClickListener;
    private OnClickListener deactivatedClickListener;
    private OnClickListener deactivatedForCreditExhaustedClickListener;
    private OnClickListener deactivatedForMissingMicPermissionClickListener;
    private OnClickListener deactivatedForMissingOrWrongKeyfileClickListener;
    protected static ColorStateList deactivatedColor;
    protected static ColorStateList activatedColor;
    protected ColorStateList color;

    public DeactivableButton(Context context) {
        super(context);
    }

    public DeactivableButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DeactivableButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnClickListenerForActivated(@Nullable OnClickListener l) {
        activatedClickListener =l;
        if(activationStatus==ACTIVATED) {
            super.setOnClickListener(l);
        }
    }

    public void setOnClickListenerForDeactivated(@Nullable OnClickListener l) {
        deactivatedClickListener =l;
        if(activationStatus==DEACTIVATED) {
            super.setOnClickListener(l);
        }
    }

    public void setOnClickListenerForDeactivatedForCreditExhausted(@Nullable OnClickListener l) {
        deactivatedForCreditExhaustedClickListener =l;
        if(activationStatus== DEACTIVATED_FOR_CREDIT_EXHAUSTED) {
            super.setOnClickListener(l);
        }
    }

    public void setOnClickListenerForDeactivatedForMissingMicPermission(@Nullable OnClickListener l) {
        deactivatedForMissingMicPermissionClickListener =l;
        if(activationStatus== DEACTIVATED_FOR_MISSING_MIC_PERMISSION) {
            super.setOnClickListener(l);
        }
    }

    public void setOnClickListenerForDeactivatedForMissingOrWrongKeyfile(@Nullable OnClickListener l) {
        deactivatedForMissingOrWrongKeyfileClickListener =l;
        if(activationStatus== DEACTIVATED_FOR_MISSING_OR_WRONG_KEYFILE) {
            super.setOnClickListener(l);
        }
    }

    public void deactivate(int reason){
        activationStatus=reason;
        switch (reason){
            case DEACTIVATED:{
                super.setOnClickListener(deactivatedClickListener);
                break;
            }
            case DEACTIVATED_FOR_CREDIT_EXHAUSTED:{
                super.setOnClickListener(deactivatedForCreditExhaustedClickListener);
                break;
            }
            case DEACTIVATED_FOR_MISSING_MIC_PERMISSION:{
                super.setOnClickListener(deactivatedForMissingMicPermissionClickListener);
                break;
            }
            case DEACTIVATED_FOR_MISSING_OR_WRONG_KEYFILE:{
                super.setOnClickListener(deactivatedForMissingOrWrongKeyfileClickListener);
                break;
            }
        }
    }

    public void activate(boolean start){
        activationStatus=ACTIVATED;
        super.setOnClickListener(activatedClickListener);
    }


    public int getActivationStatus() {
        return activationStatus;
    }
}
