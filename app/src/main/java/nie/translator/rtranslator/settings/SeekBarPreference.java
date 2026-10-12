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

package nie.translator.rtranslator.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;

import java.util.Locale;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.voice_translation._text_translation.TranslationFragment;
import nie.translator.rtranslator.voice_translation.neural_networks.voice.Recorder;


public class SeekBarPreference extends Preference {
    public static final int MIC_SENSIBILITY_MODE = 0;
    public static final int SPEECH_TIMEOUT_MODE = 1;
    public static final int PREV_VOICE_DURATION_MODE = 2;
    public static final int BEAM_SIZE_MODE = 3;
    private int mode;
    private float defaultValue = 50;
    private Slider slider;
    private TextView value;
    private TextView title;
    private TextView summary;
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
        title = (TextView) holder.findViewById(R.id.title);
        summary = (TextView) holder.findViewById(R.id.summary);
        slider = (Slider) holder.findViewById(R.id.seekBar);
        button = (ImageButton) holder.findViewById(R.id.buttonRestore);

        slider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(Slider seekBar, float seekBarValue, boolean b) {
                switch (mode) {
                    case MIC_SENSIBILITY_MODE:
                        value.setText(String.format(Locale.US, "%d", (int) seekBarValue));
                        break;
                    case SPEECH_TIMEOUT_MODE:
                        value.setText(String.format(Locale.US, "%.2f s", seekBarValue));
                        break;
                    case PREV_VOICE_DURATION_MODE:
                        value.setText(String.format(Locale.US, "%.2f s", seekBarValue));
                        break;
                    case BEAM_SIZE_MODE:
                        value.setText(String.format(Locale.US, "%d", (int) seekBarValue));
                        break;
                }
            }
        });

        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {

            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                saveValue();
            }
        });

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                slider.setValue(defaultValue);
                saveValue();
            }
        });

        if (global != null) { //if initialize was run before (and therefore failed to execute the code inside if(seekBar!=null){} )
            initialize();
        }
    }

    public void initialize(SettingsActivity activity, int mode) {
        global = (Global) activity.getApplication();
        this.mode = mode;
        initialize();
    }

    private void initialize() {
        if (slider != null) {  //if onBindViewHolder was run before (and therefore failed to execute the code inside if(global!=null){} )
            switch (mode) {
                case MIC_SENSIBILITY_MODE:
                    defaultValue = 50;
                    slider.setStepSize(1);
                    slider.setValueTo(100);
                    title.setText(R.string.preference_title_mic_sensitivity);
                    summary.setText(R.string.preference_description_mic_sensitivity);
                    break;

                case SPEECH_TIMEOUT_MODE:
                    defaultValue = Recorder.DEFAULT_SPEECH_TIMEOUT_MILLIS / 1000f;
                    slider.setValueFrom(Recorder.MIN_SPEECH_TIMEOUT_MILLIS / 1000f);
                    slider.setValueTo(Recorder.MAX_SPEECH_TIMEOUT_MILLIS / 1000f);  //we not use only MAX_SPEECH_TIMEOUT_MILLIS because we can't set the min value, so we set a (MAX - MIN) Max and we add MIN to the value of the SeekBar
                    title.setText(R.string.preference_title_speech_timeout);
                    summary.setText(R.string.preference_description_speech_timeout);
                    break;

                case PREV_VOICE_DURATION_MODE:
                    defaultValue = Recorder.DEFAULT_PREV_VOICE_DURATION / 1000f;
                    slider.setValueFrom(Recorder.MIN_PREV_VOICE_DURATION / 1000f);
                    slider.setValueTo(Recorder.MAX_PREV_VOICE_DURATION / 1000f);
                    title.setText(R.string.preference_title_prev_voice_duration);
                    summary.setText(R.string.preference_description_prev_voice_duration);
                    break;
                case BEAM_SIZE_MODE:
                    defaultValue = TranslationFragment.DEFAULT_BEAM_SIZE;
                    slider.setStepSize(1);
                    slider.setValueFrom(1);
                    slider.setValueTo(TranslationFragment.MAX_BEAM_SIZE);
                    title.setText(R.string.preference_title_beam_size);
                    summary.setText(R.string.preference_description_beam_size);
                    break;
            }

            setSeekBarValue();
        }
    }

    public void setSeekBarValue() {
        switch (mode) {
            case MIC_SENSIBILITY_MODE: {
                int value = global.getMicSensitivity();
                slider.setValue(value);
            }
            break;

            case SPEECH_TIMEOUT_MODE: {
                float value = global.getSpeechTimeout() / 1000f;
                slider.setValue(value);
            }
            break;

            case PREV_VOICE_DURATION_MODE: {
                float value = global.getPrevVoiceDuration() / 1000f;
                slider.setValue(value);
            }
            break;

            case BEAM_SIZE_MODE: {
                int value = global.getBeamSize();
                slider.setValue(value);
            }
            break;
        }
    }

    private void saveValue() {
        if (global != null) {
            // saving the value of the textView value
            switch (mode) {
                case MIC_SENSIBILITY_MODE:
                    global.setMicSensitivity(Integer.parseInt(value.getText().toString()));
                    break;
                case SPEECH_TIMEOUT_MODE:
                    global.setSpeechTimeout((int) (Float.parseFloat(value.getText().toString().replace(" s", "")) * 1000));
                    break;
                case PREV_VOICE_DURATION_MODE:
                    global.setPrevVoiceDuration((int) (Float.parseFloat(value.getText().toString().replace(" s", "")) * 1000));
                    break;
                case BEAM_SIZE_MODE:
                    global.setBeamSize(Integer.parseInt(value.getText().toString()));
                    break;
            }
        }
    }
}