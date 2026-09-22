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

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Comparator;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.CustomLocale;

public class LanguageListAdapter extends BaseAdapter implements Filterable {
    private ArrayList<CustomLocale> languages;
    private ArrayList<CustomLocale> filteredLanguages;
    private LanguageFilter filter;
    private CustomLocale selectedLanguage;
    private Activity activity;
    private LayoutInflater inflater;
    private boolean showTTSInfo;
    private ArrayList<CustomLocale> ttsLanguages = new ArrayList<>();
    private boolean ttsLanguagesInitialized = false;

    public LanguageListAdapter(Activity activity, boolean showTTSInfo, ArrayList<CustomLocale> languages, CustomLocale selectedLanguage) {
        this.activity = activity;
        this.showTTSInfo = showTTSInfo;
        languages.sort(new Comparator<CustomLocale>() {
            @Override
            public int compare(final CustomLocale locale1, CustomLocale locale2) {
                return locale1.getDisplayNameWithoutTTS().compareTo(locale2.getDisplayNameWithoutTTS());
            }
        });
        this.languages = languages;
        this.filteredLanguages = languages;
        this.selectedLanguage = selectedLanguage;
        notifyDataSetChanged();
        inflater = activity.getLayoutInflater();
        initializeTTSLanguageList(activity);
    }

    @Override
    public int getCount() {
        return filteredLanguages.size();
    }

    @Override
    public Object getItem(int position) {
        return filteredLanguages.get(position);
    }

    /*public String getItemCode(int position) {
        return languageCodes.get(position);
    }

    public String getItemCode(String item) {
        int index = languageNames.indexOf(item);
        if (index != -1) {
            return languageCodes.get(index);
        } else {
            return "";
        }
    }*/

    @Override
    public long getItemId(int position) {
        CustomLocale item = filteredLanguages.get(position);

        // Use the unique language code's hash as the ID  (we use the code, not the name, because names might be identical in rare cases)
        if (item != null && item.getISO3Language() != null) {
            return item.getISO3Language().hashCode();
        }
        return position; // Fallback
    }

    @Override
    public boolean hasStableIds() {
        // This makes filtering smoother and prevents selection glitches
        return true;
    }

    @Override
    public Filter getFilter() {
        if (filter == null) {
            filter = new LanguageFilter();
        }
        return filter;
    }

    @Override
    public View getView(int position, View view, ViewGroup container) {
        final CustomLocale item = (CustomLocale) getItem(position);
        if (view == null) {
            view = inflater.inflate(R.layout.component_row_language, container, false);
        }
        if (item.equals(selectedLanguage)) {
            view.findViewById(R.id.isSelected).setVisibility(View.VISIBLE);
        } else {
            view.findViewById(R.id.isSelected).setVisibility(View.GONE);
        }
        if(showTTSInfo && ttsLanguagesInitialized) {
            ((TextView) view.findViewById(R.id.languageName)).setText(item.getDisplayName(ttsLanguages));
        }else {
            ((TextView) view.findViewById(R.id.languageName)).setText(item.getDisplayNameWithoutTTS());
        }
        return view;
    }

    private void initializeTTSLanguageList(Activity activity) {
        Global global = (Global) activity.getApplication();
        global.getTTSLanguages(true, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                LanguageListAdapter.this.ttsLanguages = ttsLanguages;
                ttsLanguagesInitialized = true;
                notifyDataSetChanged();
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                //never called in this case
            }
        });
    }

    //this class is used to apply filters to the language list (for now it is used for search)
    private class LanguageFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();

            // If search is empty, show original list
            if (constraint == null || constraint.length() == 0) {
                results.values = languages;
                results.count = languages.size();
            } else {
                ArrayList<CustomLocale> filteredItems = new ArrayList<>();
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (CustomLocale item : languages) {
                    if (item.getDisplayNameWithoutTTS().toLowerCase().contains(filterPattern)) {
                        filteredItems.add(item);
                    }
                }
                results.values = filteredItems;
                results.count = filteredItems.size();
            }
            return results; // Runs on background thread automatically!
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            // This runs on the UI thread automatically
            filteredLanguages = (ArrayList<CustomLocale>) results.values;
            notifyDataSetChanged(); // Refresh the ListView
        }
    }
}
