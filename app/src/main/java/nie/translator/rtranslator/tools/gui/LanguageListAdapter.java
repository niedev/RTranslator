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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.CustomLocale;

public class LanguageListAdapter extends BaseAdapter implements Filterable {
    private ArrayList<CustomLocalePlus> languages;
    private ArrayList<CustomLocalePlus> filteredLanguages;
    private ArrayList<CustomLocale> highQualityLanguages;
    private LanguageFilter filter;
    private CustomLocale selectedLanguage;
    private Activity activity;
    private LayoutInflater inflater;
    private final boolean showTTSInfo;
    private final boolean showLowQualityInfo;
    private ArrayList<CustomLocale> ttsLanguages = new ArrayList<>();
    private boolean ttsLanguagesInitialized = false;
    private volatile boolean includeLowQuality;

    public LanguageListAdapter(Activity activity, boolean showTTSInfo, boolean showLowQualityInfo, ArrayList<CustomLocale> languages, ArrayList<CustomLocale> highQualityLanguages, CustomLocale selectedLanguage, boolean includeLowQuality) {
        this.activity = activity;
        this.showTTSInfo = showTTSInfo;
        this.showLowQualityInfo = showLowQualityInfo;
        this.includeLowQuality = includeLowQuality;
        languages.sort(new Comparator<CustomLocale>() {
            @Override
            public int compare(final CustomLocale locale1, CustomLocale locale2) {
                return locale1.getDisplayName().compareTo(locale2.getDisplayName());
            }
        });
        this.languages = new ArrayList<>(languages.size());
        this.filteredLanguages = new ArrayList<>(languages.size());
        CustomLocale english = new CustomLocale(activity, "en");
        for (CustomLocale language : languages) {
            boolean lowQuality = !CustomLocale.containsLanguage(highQualityLanguages, language) && !language.equals(english);
            CustomLocalePlus item = new CustomLocalePlus(language, lowQuality);  //using the same object for the languages and filteredLanguages will replicate the status change of a language in initializeTTSLanguageList for both the language in languages and filteredLanguages, as we expect
            this.languages.add(item);
            if (includeLowQuality || !lowQuality) {
                this.filteredLanguages.add(item);
            }
        }
        this.highQualityLanguages = highQualityLanguages;
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
        CustomLocalePlus item = filteredLanguages.get(position);

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
        final CustomLocalePlus item = (CustomLocalePlus) getItem(position);
        if (view == null) {
            view = inflater.inflate(R.layout.component_row_language, container, false);
        }
        if (item.equals(selectedLanguage)) {
            view.findViewById(R.id.isSelected).setVisibility(View.VISIBLE);
        } else {
            view.findViewById(R.id.isSelected).setVisibility(View.INVISIBLE);
        }
        if(showTTSInfo && ttsLanguagesInitialized) {
            if(!item.supportsTTS){
                ImageView noTTSIcon = view.findViewById(R.id.no_tts_icon);
                noTTSIcon.setVisibility(View.VISIBLE);
                noTTSIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(activity, "TTS not supported for this language", Toast.LENGTH_LONG).show();  //todo: convert text
                    }
                });
            }else{
                view.findViewById(R.id.no_tts_icon).setVisibility(View.INVISIBLE);
            }
        }
        if(showLowQualityInfo) {
            if(item.lowQuality){
                TextView lowQualityIcon = view.findViewById(R.id.low_quality_icon);
                lowQualityIcon.setVisibility(View.VISIBLE);
                lowQualityIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(activity, "Low quality language", Toast.LENGTH_LONG).show();  //todo: convert text
                    }
                });
            }else{
                view.findViewById(R.id.low_quality_icon).setVisibility(includeLowQuality ? View.INVISIBLE : View.GONE);
            }
        }
        ((TextView) view.findViewById(R.id.languageName)).setText(item.getDisplayName());
        return view;
    }

    private void initializeTTSLanguageList(Activity activity) {
        Global global = (Global) activity.getApplication();
        global.getTTSLanguages(true, true, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                LanguageListAdapter.this.ttsLanguages = ttsLanguages;
                for(CustomLocalePlus language: languages) {
                    if (!CustomLocale.containsLanguage(ttsLanguages, language)) {
                        language.supportsTTS = false;
                    }
                }
                ttsLanguagesInitialized = true;
                notifyDataSetChanged();
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                //never called in this case
            }
        });
    }

    public int getSelectedPosition() {
        return filteredLanguages.indexOf(selectedLanguage);
    }

    //this class is used to apply filters to the language list (for now it is used for search)
    private class LanguageFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            ArrayList<CustomLocalePlus> filteredItems = new ArrayList<>();
            String filterPattern = constraint.toString().toLowerCase().trim();
            boolean allowLowQuality = includeLowQuality;

            for (CustomLocalePlus item : languages) {
                if(allowLowQuality || !item.lowQuality) {  // low quality filter
                    if (filterPattern.isEmpty() || item.getDisplayName().toLowerCase().contains(filterPattern)) {  //search query filter (it filters nothing if the query is empty)
                        filteredItems.add(item);
                    }
                }
            }
            results.values = filteredItems;
            results.count = filteredItems.size();
            return results; // Runs on background thread automatically!
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            // This runs on the UI thread automatically
            filteredLanguages = (ArrayList<CustomLocalePlus>) results.values;
            notifyDataSetChanged(); // Refresh the ListView
        }
    }

    public void setIncludeLowQuality(boolean include, CharSequence currentQuery) {
        includeLowQuality = include;
        getFilter().filter(currentQuery);
    }

    private static class CustomLocalePlus extends CustomLocale {
        public boolean lowQuality;
        public boolean supportsTTS = true;

        public CustomLocalePlus(CustomLocale language, boolean lowQuality) {
            super(language);
            this.lowQuality = lowQuality;
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
    }
}
