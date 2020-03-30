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
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatSpinner;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.Tools;


public class DynamicWidthSpinner extends AppCompatSpinner {

    public DynamicWidthSpinner(Context context) {
        super(context);
    }

    public DynamicWidthSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DynamicWidthSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public void setAdapter(SpinnerAdapter adapter) {
        super.setAdapter(adapter != null ? new WrapperSpinnerAdapter(adapter) : null);
    }

    @Override
    public void setDropDownWidth(int pixels) {
        pixels+= Tools.convertDpToPixels(getContext(),44);
        int minPixels=Tools.convertSpToPixels(getContext(),130);
        if(pixels!=0 && pixels<minPixels){
            pixels=minPixels;
        }
        super.setDropDownWidth(pixels);
    }

    public final class WrapperSpinnerAdapter implements SpinnerAdapter {

        private final SpinnerAdapter mBaseAdapter;


        WrapperSpinnerAdapter(SpinnerAdapter baseAdapter) {
            mBaseAdapter = baseAdapter;
        }

        protected void onMeasure(){}

        public View getView(int position, View convertView, ViewGroup parent) {
            TextView returnedView= (TextView) mBaseAdapter.getView(getSelectedItemPosition(), convertView, parent);
            if(returnedView!=null) {
                String text=returnedView.getText().toString();
                returnedView.setTextSize(15);
                returnedView.setPaddingRelative(Tools.convertDpToPixels(getContext(),8),0,0,0);
                returnedView.setTextColor(GuiTools.getColor(getContext(), R.color.very_dark_gray));
                setDropDownWidth(returnedView.getMeasuredWidth());
            }
            return returnedView;
        }

        public final SpinnerAdapter getBaseAdapter() {
            return mBaseAdapter;
        }

        public int getCount() {
            return mBaseAdapter.getCount();
        }

        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View view=mBaseAdapter.getDropDownView(position, convertView, parent);
            ((TextView)view).setGravity(Gravity.CENTER);
            return view;
        }

        public Object getItem(int position) {
            return mBaseAdapter.getItem(position);
        }

        public long getItemId(int position) {
            return mBaseAdapter.getItemId(position);
        }

        public int getItemViewType(int position) {
            return mBaseAdapter.getItemViewType(position);
        }

        public int getViewTypeCount() {
            return mBaseAdapter.getViewTypeCount();
        }

        public boolean hasStableIds() {
            return mBaseAdapter.hasStableIds();
        }

        public boolean isEmpty() {
            return mBaseAdapter.isEmpty();
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            mBaseAdapter.registerDataSetObserver(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            mBaseAdapter.unregisterDataSetObserver(observer);
        }
    }
}

