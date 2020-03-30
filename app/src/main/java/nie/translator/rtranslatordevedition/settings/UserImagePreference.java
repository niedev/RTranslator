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
import android.widget.ImageView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import nie.translator.rtranslatordevedition.R;


public class UserImagePreference extends Preference {
    private ImageView image;
    private LifecycleListener lifecycleListener;

    public UserImagePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public UserImagePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public UserImagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public UserImagePreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        image= (ImageView) holder.findViewById(R.id.user_image_settings);
        if(lifecycleListener!=null){
            lifecycleListener.onBindViewHolder();
        }
    }

    public ImageView getImage(){
        return image;
    }

    public void setLifecycleListener(LifecycleListener lifecycleListener) {
        this.lifecycleListener=lifecycleListener;
    }

    public interface LifecycleListener{
        void onBindViewHolder();
    }
}
