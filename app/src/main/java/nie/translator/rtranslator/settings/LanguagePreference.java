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
import android.widget.AdapterView;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import java.util.ArrayList;
import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.gui.GuiTools;

public class LanguagePreference extends Preference {
    private SettingsFragment fragment;
    private SettingsActivity activity;
    private Global global;

    public LanguagePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LanguagePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LanguagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LanguagePreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
    }

    public void initializeLanguagesList() {
        Global global = (Global) fragment.requireActivity().getApplication();
        final String summary = (String) getSummary();
        CustomLocale result = global.getLanguage(false);
        global.getTTSLanguages(false, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                if (getSummary() == null || "".equals((String) getSummary())) {    // to avoid changing the summary after a language change after the initializeLanguageList () call
                    setSummary(result.getDisplayName(ttsLanguages));  // if we have an error of lack of internet we simply don't insert the summary
                } else if (summary != null && summary.equals((String) getSummary())) {
                    setSummary(result.getDisplayName(ttsLanguages));  // if we have an error of lack of internet we simply don't insert the summary
                }
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                //never called in this case
            }
        });

        setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (activity != null) {
                    showListDialog();
                }
                return false;
            }
        });
    }

    private void showListDialog() {
        String title = global.getResources().getString(R.string.dialog_select_personal_language);
        //todo: evaluate if here we need recycleResult = false to update language in case of changes to the preferences outside of model management that change the languages list
        // (in model management we update the languages list when we exit, in the other options I need to verify it).
        ArrayList<CustomLocale> languages = global.getLanguages(Global.RTranslatorMode.CONVERSATION_MODE, true);
        CustomLocale selectedLanguage = global.getLanguage(false);

        GuiTools.showLanguageListDialog(activity, title, languages, selectedLanguage, true, new GuiTools.OnLanguageClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id, CustomLocale item) {
                global.getTTSLanguages(true, new Global.GetLocalesListListener() {
                    @Override
                    public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                        if (item != null && languages.contains(item)) {
                            global.setLanguage(item);
                            setSummary(item.getDisplayName(ttsLanguages));
                        }
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        //never called in this case
                    }
                });
            }
        });
    }

    public void setFragment(@NonNull SettingsFragment fragment) {
        this.activity = (SettingsActivity) fragment.requireActivity();
        this.fragment = fragment;
        this.global = (Global) activity.getApplication();
    }

    private void onFailureUpdatingSummary(int[] reasons, long value) {
        for (int aReason : reasons) {
            switch (aReason) {
                // decide if errors need to be handled here (I don't think)
            }
        }
    }
}
