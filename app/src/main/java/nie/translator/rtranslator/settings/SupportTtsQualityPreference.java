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
import android.content.SharedPreferences;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;
import java.util.ArrayList;
import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.ErrorCodes;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;

public class SupportTtsQualityPreference extends SwitchPreference {
    private SettingsFragment fragment;
    private Translator translator;
    private Global global;
    private SettingsActivity activity;

    public SupportTtsQualityPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public SupportTtsQualityPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SupportTtsQualityPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SupportTtsQualityPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        //translator = new Translator((Global) fragment.requireActivity().getApplication(), Translator.MADLAD_CACHE);
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(global != null) {
                    final SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean("languagesQualityLow", (Boolean) newValue);
                    editor.apply();
                }
                if (fragment != null) {
                    //download new languages
                    downloadLanguages();
                }
                return true;
            }
        });
    }

    public void downloadLanguages() {
        if(global != null && fragment != null) {
            fragment.addDownload();
            global.getLanguages(Global.RTranslatorMode.TEXT_TRANSLATION_MODE, false);
            fragment.removeDownload();
            fragment.getLanguagePreference().initializeLanguagesList();
        }
    }

    public void setFragment(@NonNull SettingsFragment fragment) {
        this.fragment = fragment;
        this.activity = (SettingsActivity) fragment.requireActivity();
        this.global = (Global) activity.getApplication();
    }
}
