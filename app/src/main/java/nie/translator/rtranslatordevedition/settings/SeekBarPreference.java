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

package nie.translator.rtranslatordevedition.settings;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.R;


public class SeekBarPreference extends Preference {
    private SeekBar seekBar;
    private TextView value;
    private ImageButton button;
    private Global global;

    public SeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public SeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SeekBarPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        value = (TextView) holder.findViewById(R.id.textViewValue);
        seekBar = (SeekBar) holder.findViewById(R.id.seekBar);
        button = (ImageButton) holder.findViewById(R.id.buttonRestore);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int seekbarValue, boolean b) {
                value.setText(Integer.toString(seekbarValue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (global != null) {
                    // saving the value of the textView value
                    global.setMicSensitivity(Integer.parseInt(value.getText().toString()));
                }
            }
        });

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    seekBar.setProgress(50, true);
                } else {
                    seekBar.setProgress(50);
                }
                // saving the value of the textView value
                global.setMicSensitivity(Integer.parseInt(value.getText().toString()));
            }
        });

        if(global!=null){
            setSeekebarValue();
        }
    }

    public void initialize(SettingsActivity activity) {
        global = (Global) activity.getApplication();
        if(seekBar!=null){
            setSeekebarValue();
        }
    }

    public void setSeekebarValue(){
        int value=global.getMicSensitivity();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            seekBar.setProgress(value, true);
        } else {
            seekBar.setProgress(value);
        }
    }
}
