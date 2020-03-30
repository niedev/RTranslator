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
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import nie.translator.rtranslatordevedition.R;

public class FileListAdapter extends BaseAdapter {
    private ArrayList<File> files;
    private Activity activity;
    private LayoutInflater inflater;

    public FileListAdapter(Activity activity, ArrayList<File> files) {
        this.activity = activity;
        this.files = files;
        this.inflater = activity.getLayoutInflater();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return files.size();
    }

    @Override
    public Object getItem(int position) {
        return files.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        final Object item = getItem(position);
        File file = ((File) item);
        if (view == null) {
            view = inflater.inflate(R.layout.component_row_key_file, null);
        }
        ((TextView) view.findViewById(R.id.file_name)).setText(file.getName());
        try {
            ((TextView) view.findViewById(R.id.path)).setText(file.getCanonicalPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        //date set
        String dateAndTimeString = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(file.lastModified()));
        String dateString = dateAndTimeString.substring(0, dateAndTimeString.length() - 10);
        String timeString = dateAndTimeString.substring(dateAndTimeString.length() - 5);
        dateAndTimeString = dateString + timeString;

        ((TextView) view.findViewById(R.id.time)).setText(dateAndTimeString);


        return view;
    }
}
