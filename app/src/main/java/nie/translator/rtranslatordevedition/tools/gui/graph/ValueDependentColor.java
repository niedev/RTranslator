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

import nie.translator.rtranslatordevedition.tools.gui.graph.series.BarGraphSeries;
import nie.translator.rtranslatordevedition.tools.gui.graph.series.DataPointInterface;


/**
 * you can change the color depending on the value.
 * takes only effect for BarGraphSeries.
 *
 * @see BarGraphSeries#setValueDependentColor(ValueDependentColor)
 */
public interface ValueDependentColor<T extends DataPointInterface> {
    /**
     * this is called when a bar is about to draw
     * and the color is be loaded.
     *
     * @param data the current input value
     * @return  the color that the bar should be drawn with
     *          Generate the int via the android.graphics.Color class.
     */
    public int get(T data);
}
