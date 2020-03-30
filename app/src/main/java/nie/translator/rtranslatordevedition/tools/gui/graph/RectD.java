/**
 * GraphView
 * Copyright 2016 Jonas Gehring
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
package nie.translator.rtranslatordevedition.tools.gui.graph;

import android.graphics.RectF;

/**
 * Created by jonas on 05.06.16.
 */
public class RectD {
    public double left;
    public double right;
    public double top;
    public double bottom;

    public RectD() {
    }

    public RectD(double lLeft, double lTop, double lRight, double lBottom) {
        set(lLeft, lTop, lRight, lBottom);
    }

    public double width() {
        return right-left;
    }

    public double height() {
        return bottom-top;
    }

    public void set(double lLeft, double lTop, double lRight, double lBottom) {
        left = lLeft;
        right = lRight;
        top = lTop;
        bottom = lBottom;
    }

    public RectF toRectF() {
        return new RectF((float) left, (float) top, (float) right, (float) bottom);
    }
}
