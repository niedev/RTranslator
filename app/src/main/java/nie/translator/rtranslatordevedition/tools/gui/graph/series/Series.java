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
package nie.translator.rtranslatordevedition.tools.gui.graph.series;

import android.graphics.Canvas;

import java.util.Iterator;

import nie.translator.rtranslatordevedition.tools.gui.graph.GraphView;

/**
 * Basis interface for series that can be plotted
 * on the graph.
 * You can implement this in order to create a completely
 * custom series type.
 * But it is recommended to extend {@link BaseSeries} or another
 * implemented Series class to save time.
 * Anyway this interface can make sense if you want to implement
 * a custom data provider, because BaseSeries uses a internal Array to store
 * the data.
 *
 * @author jjoe64
 */
public interface Series<E extends DataPointInterface> {
    /**
     * @return the lowest x-value of the data
     */
    public double getLowestValueX();

    /**
     * @return the highest x-value of the data
     */
    public double getHighestValueX();

    /**
     * @return the lowest y-value of the data
     */
    public double getLowestValueY();

    /**
     * @return the highest y-value of the data
     */
    public double getHighestValueY();

    /**
     * get the values for a specific range. It is
     * important that the data comes in the sorted order
     * (from lowest to highest x-value).
     *
     * @param from the minimal x-value
     * @param until the maximal x-value
     * @return  all datapoints between the from and until x-value
     *          including the from and until data points.
     */
    public Iterator<E> getValues(double from, double until);

    /**
     * Plots the series to the viewport.
     * You have to care about overdrawing.
     * This method may be called 2 times: one for
     * the default scale and one time for the
     * second scale.
     *
     * @param graphView corresponding graphview
     * @param canvas canvas to draw on
     * @param isSecondScale true if the drawing is for the second scale
     */
    public void draw(GraphView graphView, Canvas canvas, boolean isSecondScale);

    /**
     * @return the title of the series. Used in the legend
     */
    public String getTitle();

    /**
     * @return  the color of the series. Used in the legend and should
     *          be used for the plotted points or lines.
     */
    public int getColor();

    /**
     * set a listener for tap on a data point.
     *
     * @param l listener
     */
    public void setOnDataPointTapListener(OnDataPointTapListener l);

    /**
     * called by the tap detector in order to trigger
     * the on tap on datapoint event.
     *
     * @param x pixel
     * @param y pixel
     */
    void onTap(float x, float y);

    /**
     * called when the series was added to a graph
     *
     * @param graphView graphview
     */
    void onGraphViewAttached(GraphView graphView);

    /**
     * @return whether there are data points
     */
    boolean isEmpty();

    /**
     * clear reference to view and activity
     *
     * @param graphView
     */
    void clearReference(GraphView graphView);
}
