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

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Keep;
import androidx.constraintlayout.widget.ConstraintLayout;

public class ViewPropertiesAdapter {
    private View view;

    public ViewPropertiesAdapter(View view){
        this.view=view;
    }

    @Keep
    public void setWidth(int width){
        ViewGroup.LayoutParams layoutParams= view.getLayoutParams();
        layoutParams.width=width;
        view.setLayoutParams(layoutParams);
    }

    @Keep
    public void setHeight(int height){
        ViewGroup.LayoutParams layoutParams= view.getLayoutParams();
        layoutParams.height=height;
        view.setLayoutParams(layoutParams);
    }

    @Keep
    public void setHorizontalBias(float horizontalBias){
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) view.getLayoutParams();
        layoutParams.horizontalBias=horizontalBias;
        view.setLayoutParams(layoutParams);
    }

    @Keep
    public void setVerticalBias(float verticalBias){
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) view.getLayoutParams();
        layoutParams.verticalBias=verticalBias;
        view.setLayoutParams(layoutParams);
    }

    @Keep
    public void setTopMargin(int topMargin){
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) view.getLayoutParams();
        layoutParams.topMargin=topMargin;
        view.setLayoutParams(layoutParams);
    }

    @Keep
    public void setBottomMargin(int bottomMargin){
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) view.getLayoutParams();
        layoutParams.bottomMargin=bottomMargin;
        view.setLayoutParams(layoutParams);
    }

    public int getWidth(){
        return view.getWidth();
    }

    public int getHeight(){
        return view.getHeight();
    }

    public View getView() {
        return view;
    }
}
