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


public class ButtonSound extends DeactivableButton {
    private boolean isMute=false;

    public ButtonSound(Context context) {
        super(context);
    }

    public ButtonSound(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ButtonSound(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public boolean isMute() {
        return isMute;
    }

    public void setMute(boolean mute) {
        if(isMute!=mute){
            if(mute){
                setImageDrawable(getResources().getDrawable(R.drawable.sound_mute_icon,null));
            }else{
                setImageDrawable(getResources().getDrawable(R.drawable.sound_icon,null));
            }
        }
        isMute = mute;
    }
}
