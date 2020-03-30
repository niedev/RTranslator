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


/**
 * Interface to use as label formatter.
 * Implement this in order to generate
 * your own labels format.
 * It is recommended to override {@link DefaultLabelFormatter}.
 *
 * @author jjoe64
 */
public interface LabelFormatter {
    /**
     * converts a raw number as input to
     * a formatted string for the label.
     *
     * @param value raw input number
     * @param isValueX  true if it is a value for the x axis
     *                  false if it is a value for the y axis
     * @return the formatted number as string
     */
    public String formatLabel(double value, boolean isValueX);

    /**
     * will be called in order to have a
     * reference to the current viewport.
     * This is useful if you need the bounds
     * to generate your labels.
     * You store this viewport in as member variable
     * and access it e.g. in the {@link #formatLabel(double, boolean)}
     * method.
     *
     * @param viewport the used viewport
     */
    public void setViewport(Viewport viewport);
}
