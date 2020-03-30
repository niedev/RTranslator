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
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.CustomLocale;
import nie.translator.rtranslatordevedition.tools.ErrorCodes;
import nie.translator.rtranslatordevedition.tools.Tools;
import nie.translator.rtranslatordevedition.tools.gui.LanguageListAdapter;

public class LanguagePreference extends Preference {
    private SettingsFragment fragment;
    private SettingsActivity activity;
    private Global global;
    private LanguageListAdapter listView;
    private ListView listViewGui;
    private ProgressBar progressBar;
    private ImageButton reloadButton;
    private AlertDialog dialog;

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
        global.getLanguage(false, new Global.GetLocaleListener() {
            @Override
            public void onSuccess(CustomLocale result) {
                if (getSummary() == null || "".equals((String) getSummary())) {    // to avoid changing the summary after a language change after the initializeLanguageList () call
                    setSummary(result.getDisplayName());  // if we have an error of lack of internet we simply don't insert the summary
                } else if (summary != null && summary.equals((String) getSummary())) {
                    setSummary(result.getDisplayName());  // if we have an error of lack of internet we simply don't insert the summary
                }
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                if (activity != null) {
                    onFailureUpdatingSummary(reasons, value);
                }
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

    /**
     *  when the dialog is shown at the beginning the loading is shown, then once the list of languages​is obtained (within the showList)
     *  the loading is replaced with the list of languages
     */
    private void showListDialog() {
        final View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_languages, null);

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle(global.getResources().getString(R.string.dialog_select_personal_language));

        dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 16), 0, 0);
        dialog.show();

        listViewGui = editDialogLayout.findViewById(R.id.list_view_dialog);
        progressBar = editDialogLayout.findViewById(R.id.progressBar3);
        reloadButton = editDialogLayout.findViewById(R.id.reloadButton);

        reloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showList();
            }
        });
        showList();
    }

    private void showList() {
        reloadButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        global.getLanguages(true, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(final ArrayList<CustomLocale> languages) {
                global.getLanguage(false, new Global.GetLocaleListener() {
                    @Override
                    public void onSuccess(CustomLocale selectedLanguage) {
                        progressBar.setVisibility(View.GONE);
                        listViewGui.setVisibility(View.VISIBLE);

                        listView = new LanguageListAdapter(activity, languages, selectedLanguage);
                        listViewGui.setAdapter(listView);
                        listViewGui.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                                global.getLanguages(true, new Global.GetLocalesListListener() {
                                    @Override
                                    public void onSuccess(ArrayList<CustomLocale> result) {
                                        if (result.contains((CustomLocale) listView.getItem(position))) {
                                            global.setLanguage((CustomLocale) listView.getItem(position));
                                            CustomLocale item=(CustomLocale) listView.getItem(position);
                                            setSummary(item.getDisplayName());
                                        }
                                    }

                                    @Override
                                    public void onFailure(int[] reasons, long value) {
                                        onFailureUpdatingSummary(reasons, value);
                                    }
                                });
                                dialog.dismiss();
                            }
                        });
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        onFailureShowingList(reasons, value);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                onFailureShowingList(reasons, value);
            }
        });
    }


    public void setFragment(@NonNull SettingsFragment fragment) {
        this.activity = (SettingsActivity) fragment.requireActivity();
        this.fragment = fragment;
        this.global = (Global) activity.getApplication();
    }

    private void onFailureShowingList(int[] reasons, long value) {
        progressBar.setVisibility(View.GONE);
        reloadButton.setVisibility(View.VISIBLE);
        for (int aReason : reasons) {
            switch (aReason) {
                case ErrorCodes.MISSED_ARGUMENT:
                case ErrorCodes.SAFETY_NET_EXCEPTION:
                case ErrorCodes.MISSED_CONNECTION:
                    Toast.makeText(activity, activity.getResources().getString(R.string.error_internet_lack_loading_languages), Toast.LENGTH_LONG).show();
                    break;
                default:
                    activity.onError(aReason, value);
                    break;
            }
        }
    }

    private void onFailureUpdatingSummary(int[] reasons, long value) {
        for (int aReason : reasons) {
            switch (aReason) {
                // decide if errors need to be handled here (I don't think)
            }
        }
    }
}
