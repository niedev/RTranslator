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
import android.text.Editable;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;
import nie.translator.rtranslatordevedition.R;


/**
 * Not used, this class is for the eventual use of accounts
 */
public class SecurityLevel extends AppCompatTextView {

    public SecurityLevel(Context context) {
        super(context);
    }

    public SecurityLevel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SecurityLevel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void createTextWatcher(){

    }

    public void removeTextWatcher(){

    }

    public void updateSecurityLevel(Editable text) {
        if (text != null && text.length() > 0) {
            if (text.length() <= 4 && !getText().equals("sicurezza molto bassa")) {  //da sostituire con le risorse string
                setText("sicurezza molto bassa");
                setTextColor(GuiTools.getColor(getContext(), R.color.red));
            } else if (text.length() > 4 && text.length() <= 9 && !getText().equals("sicurezza bassa")) {
                setText("sicurezza bassa");
                setTextColor(GuiTools.getColor(getContext(), R.color.red));
            } else if (text.length() > 9 && text.length() <= 13 && !getText().equals("sicurezza media")) {
                setText("sicurezza media");
                setTextColor(GuiTools.getColor(getContext(), R.color.yellow));
            } else if (text.length() > 13 && text.length() <= 16 && !getText().equals("sicurezza alta")) {
                setText("sicurezza alta");
                setTextColor(GuiTools.getColor(getContext(), R.color.primary_dark));
            } else if (text.length() > 16 && !getText().equals("sicurezza molto alta")) {
                setText("sicurezza molto alta");
                setTextColor(GuiTools.getColor(getContext(), R.color.primary_dark));
            }
        }
    }
}
