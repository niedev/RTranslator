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

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.CustomLocale;

public class LanguageListAdapter extends BaseAdapter {
    private ArrayList<CustomLocale> languages;
    private CustomLocale selectedLanguage;
    private Activity activity;
    private LayoutInflater inflater;

    public LanguageListAdapter(Activity activity, ArrayList<CustomLocale> languages, CustomLocale selectedLanguage) {
        this.activity = activity;
        this.languages = languages;
        this.selectedLanguage = selectedLanguage;
        notifyDataSetChanged();
        inflater = activity.getLayoutInflater();
    }

    @Override
    public int getCount() {
        return languages.size();
    }

    @Override
    public Object getItem(int position) {
        return languages.get(position);
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
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        final CustomLocale item = (CustomLocale) getItem(position);
        if (view == null) {
            view = inflater.inflate(R.layout.component_row_language, null);
        }
        if (item.equals(selectedLanguage)) {
            view.findViewById(R.id.isSelected).setVisibility(View.VISIBLE);
        } else {
            view.findViewById(R.id.isSelected).setVisibility(View.GONE);
        }
        ((TextView) view.findViewById(R.id.languageName)).setText(item.getDisplayName());
        return view;
    }
}
