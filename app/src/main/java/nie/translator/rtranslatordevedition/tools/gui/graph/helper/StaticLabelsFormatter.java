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
package nie.translator.rtranslatordevedition.tools.gui.graph.helper;

import nie.translator.rtranslatordevedition.tools.gui.graph.DefaultLabelFormatter;
import nie.translator.rtranslatordevedition.tools.gui.graph.GraphView;
import nie.translator.rtranslatordevedition.tools.gui.graph.LabelFormatter;
import nie.translator.rtranslatordevedition.tools.gui.graph.Viewport;

/**
 * Use this label formatter to show static labels.
 * Static labels are not bound to the data. It is typical used
 * for show text like "low", "middle", "high".
 *
 * You can set the static labels for vertical or horizontal
 * individually and you can define a label formatter that
 * is to be used if you don't define static labels.
 *
 * For example if you only use static labels for horizontal labels,
 * graphview will use the dynamicLabelFormatter for the vertical labels.
 */
public class StaticLabelsFormatter implements LabelFormatter {
    /**
     * reference to the viewport
     */
    protected Viewport mViewport;

    /**
     * the vertical labels, ordered from bottom to the top
     * if it is null, the labels will be generated via the #dynamicLabelFormatter
     */
    protected String[] mVerticalLabels;

    /**
     * the horizontal labels, ordered form the left to the right
     * if it is null, the labels will be generated via the #dynamicLabelFormatter
     */
    protected String[] mHorizontalLabels;

    /**
     * the label formatter that will format the labels
     * for that there are no static labels defined.
     */
    protected LabelFormatter mDynamicLabelFormatter;

    /**
     * reference to the graphview
     */
    protected final GraphView mGraphView;

    /**
     * creates the formatter without any static labels
     * define your static labels via {@link #setHorizontalLabels(String[])} and {@link #setVerticalLabels(String[])}
     *
     * @param graphView reference to the graphview
     */
    public StaticLabelsFormatter(GraphView graphView) {
        mGraphView = graphView;
        init(null, null, null);
    }

    /**
     * creates the formatter without any static labels.
     * define your static labels via {@link #setHorizontalLabels(String[])} and {@link #setVerticalLabels(String[])}
     *
     * @param graphView reference to the graphview
     * @param dynamicLabelFormatter     the label formatter that will format the labels
     *                                  for that there are no static labels defined.
     */
    public StaticLabelsFormatter(GraphView graphView, LabelFormatter dynamicLabelFormatter) {
        mGraphView = graphView;
        init(null, null, dynamicLabelFormatter);
    }

    /**
     * creates the formatter with static labels defined.
     *
     * @param graphView reference to the graphview
     * @param horizontalLabels  the horizontal labels, ordered form the left to the right
     *                          if it is null, the labels will be generated via the #dynamicLabelFormatter
     * @param verticalLabels    the vertical labels, ordered from bottom to the top
     *                          if it is null, the labels will be generated via the #dynamicLabelFormatter
     */
    public StaticLabelsFormatter(GraphView graphView, String[] horizontalLabels, String[] verticalLabels) {
        mGraphView = graphView;
        init(horizontalLabels, verticalLabels, null);
    }

    /**
     * creates the formatter with static labels defined.
     *
     * @param graphView reference to the graphview
     * @param horizontalLabels  the horizontal labels, ordered form the left to the right
     *                          if it is null, the labels will be generated via the #dynamicLabelFormatter
     * @param verticalLabels    the vertical labels, ordered from bottom to the top
     *                          if it is null, the labels will be generated via the #dynamicLabelFormatter
     * @param dynamicLabelFormatter     the label formatter that will format the labels
     *                                  for that there are no static labels defined.
     */
    public StaticLabelsFormatter(GraphView graphView, String[] horizontalLabels, String[] verticalLabels, LabelFormatter dynamicLabelFormatter) {
        mGraphView = graphView;
        init(horizontalLabels, verticalLabels, dynamicLabelFormatter);
    }

    /**
     * @param horizontalLabels  the horizontal labels, ordered form the left to the right
     *                          if it is null, the labels will be generated via the #dynamicLabelFormatter
     * @param verticalLabels    the vertical labels, ordered from bottom to the top
     *                          if it is null, the labels will be generated via the #dynamicLabelFormatter
     * @param dynamicLabelFormatter     the label formatter that will format the labels
     *                                  for that there are no static labels defined.
     */
    protected void init(String[] horizontalLabels, String[] verticalLabels, LabelFormatter dynamicLabelFormatter) {
        mDynamicLabelFormatter = dynamicLabelFormatter;
        if (mDynamicLabelFormatter == null) {
            mDynamicLabelFormatter = new DefaultLabelFormatter();
        }

        mHorizontalLabels = horizontalLabels;
        mVerticalLabels = verticalLabels;
    }

    /**
     * Set a label formatter that will be used for the labels
     * that don't have static labels.
     *
     * For example if you only use static labels for horizontal labels,
     * graphview will use the dynamicLabelFormatter for the vertical labels.
     *
     * @param dynamicLabelFormatter     the label formatter that will format the labels
     *                                  for that there are no static labels defined.
     */
    public void setDynamicLabelFormatter(LabelFormatter dynamicLabelFormatter) {
        this.mDynamicLabelFormatter = dynamicLabelFormatter;
        adjust();
    }

    /**
     * @param horizontalLabels  the horizontal labels, ordered form the left to the right
     *                          if it is null, the labels will be generated via the #dynamicLabelFormatter
     */
    public void setHorizontalLabels(String[] horizontalLabels) {
        this.mHorizontalLabels = horizontalLabels;
        adjust();
    }

    /**
     * @param verticalLabels    the vertical labels, ordered from bottom to the top
     *                          if it is null, the labels will be generated via the #dynamicLabelFormatter
     */
    public void setVerticalLabels(String[] verticalLabels) {
        this.mVerticalLabels = verticalLabels;
        adjust();
    }

    /**
     *
     * @param value raw input number
     * @param isValueX  true if it is a value for the x axis
     *                  false if it is a value for the y axis
     * @return
     */
    @Override
    public String formatLabel(double value, boolean isValueX) {
        if (isValueX && mHorizontalLabels != null) {
            double minX = mViewport.getMinX(false);
            double maxX = mViewport.getMaxX(false);
            double range = maxX - minX;
            value = value-minX;
            int idx = (int)((value/range) * (mHorizontalLabels.length-1));
            return mHorizontalLabels[idx];
        } else if (!isValueX && mVerticalLabels != null) {
            double minY = mViewport.getMinY(false);
            double maxY = mViewport.getMaxY(false);
            double range = maxY - minY;
            value = value-minY;
            int idx = (int)((value/range) * (mVerticalLabels.length-1));
            return mVerticalLabels[idx];
        } else {
            return mDynamicLabelFormatter.formatLabel(value, isValueX);
        }
    }

    /**
     * @param viewport the used viewport
     */
    @Override
    public void setViewport(Viewport viewport) {
        mViewport = viewport;
        adjust();
    }

    /**
     * adjusts the number of vertical/horizontal labels
     */
    protected void adjust() {
        mDynamicLabelFormatter.setViewport(mViewport);
        if (mVerticalLabels != null) {
            if (mVerticalLabels.length < 2) {
                throw new IllegalStateException("You need at least 2 vertical labels if you use static label formatter.");
            }
            mGraphView.getGridLabelRenderer().setNumVerticalLabels(mVerticalLabels.length);
        }
        if (mHorizontalLabels != null) {
            if (mHorizontalLabels.length < 2) {
                throw new IllegalStateException("You need at least 2 horizontal labels if you use static label formatter.");
            }
            mGraphView.getGridLabelRenderer().setNumHorizontalLabels(mHorizontalLabels.length);
        }
    }
}
