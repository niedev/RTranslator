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

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.List;

import nie.translator.rtranslatordevedition.tools.gui.graph.series.Series;

/**
 * To be used to plot a second scale
 * on the graph.
 * The second scale has always to have
 * manual bounds.
 * Use {@link #setMinY(double)} and {@link #setMaxY(double)}
 * to set them.
 * The second scale has it's own array of series.
 *
 * @author jjoe64
 */
public class SecondScale {
    /**
     * reference to the graph
     */
    protected final GraphView mGraph;

    /**
     * array of series for the second
     * scale
     */
    protected List<Series> mSeries;

    /**
     * flag whether the y axis bounds
     * are manual.
     * For the current version this is always
     * true.
     */
    private boolean mYAxisBoundsManual = true;

    /**
     *
     */
    protected RectD mCompleteRange = new RectD();

    protected RectD mCurrentViewport = new RectD();

    /**
     * label formatter for the y labels
     * on the right side
     */
    protected LabelFormatter mLabelFormatter;

    protected double mReferenceY = Double.NaN;

    /**
     * the paint to draw axis titles
     */
    private Paint mPaintAxisTitle;

    /**
     * the title of the vertical axis
     */
    private String mVerticalAxisTitle;

    /**
     * font size of the vertical axis title
     */
    public float mVerticalAxisTitleTextSize;

    /**
     * font color of the vertical axis title
     */
    public int mVerticalAxisTitleColor;

    /**
     * creates the second scale.
     * normally you do not call this contructor.
     * Use {@link com.jjoe64.graphview.GraphView#getSecondScale()}
     * in order to get the instance.
     */
    SecondScale(GraphView graph) {
        mGraph = graph;
        mSeries = new ArrayList<Series>();
        mLabelFormatter = new DefaultLabelFormatter();
        mLabelFormatter.setViewport(mGraph.getViewport());
    }

    /**
     * add a series to the second scale.
     * Don't add this series also to the GraphView
     * object.
     *
     * @param s the series
     */
    public void addSeries(Series s) {
        s.onGraphViewAttached(mGraph);
        mSeries.add(s);
        mGraph.onDataChanged(false, false);
    }

    //public void setYAxisBoundsManual(boolean mYAxisBoundsManual) {
    //    this.mYAxisBoundsManual = mYAxisBoundsManual;
    //}

    /**
     * set the min y bounds
     *
     * @param d min y value
     */
    public void setMinY(double d) {
        mReferenceY = d;
        mCurrentViewport.bottom = d;
    }

    /**
     * set the max y bounds
     *
     * @param d max y value
     */
    public void setMaxY(double d) {
        mCurrentViewport.top = d;
    }

    /**
     * @return the series of the second scale
     */
    public List<Series> getSeries() {
        return mSeries;
    }

    /**
     * @return min y bound
     */
    public double getMinY(boolean completeRange) {
        return completeRange ? mCompleteRange.bottom : mCurrentViewport.bottom;
    }

    /**
     * @return max y bound
     */
    public double getMaxY(boolean completeRange) {
        return completeRange ? mCompleteRange.top : mCurrentViewport.top;
    }

    /**
     * @return always true for the current implementation
     */
    public boolean isYAxisBoundsManual() {
        return mYAxisBoundsManual;
    }

    /**
     * @return label formatter for the y labels on the right side
     */
    public LabelFormatter getLabelFormatter() {
        return mLabelFormatter;
    }

    /**
     * Set a custom label formatter that is used
     * for the y labels on the right side.
     *
     * @param formatter label formatter for the y labels
     */
    public void setLabelFormatter(LabelFormatter formatter) {
        mLabelFormatter = formatter;
        mLabelFormatter.setViewport(mGraph.getViewport());
    }

    /**
     * Removes all series of the graph.
     */
    public void removeAllSeries() {
        mSeries.clear();
        mGraph.onDataChanged(false, false);
    }

    /**
     * Remove a specific series of the
     * second scale.
     *
     * @param series
     */
    public void removeSeries(Series series) {
        mSeries.remove(series);
        mGraph.onDataChanged(false, false);
    }

    /**
     * caches the complete range (minX, maxX, minY, maxY)
     * by iterating all series and all datapoints and
     * stores it into #mCompleteRange
     *
     * for the x-range it will respect the series on the
     * second scale - not for y-values
     */
    public void calcCompleteRange() {
        List<Series> series = getSeries();
        mCompleteRange.set(0d, 0d, 0d, 0d);
        if (!series.isEmpty() && !series.get(0).isEmpty()) {
            double d = series.get(0).getLowestValueX();
            for (Series s : series) {
                if (!s.isEmpty() && d > s.getLowestValueX()) {
                    d = s.getLowestValueX();
                }
            }
            mCompleteRange.left = d;

            d = series.get(0).getHighestValueX();
            for (Series s : series) {
                if (!s.isEmpty() && d < s.getHighestValueX()) {
                    d = s.getHighestValueX();
                }
            }
            mCompleteRange.right = d;

            if (!series.isEmpty() && !series.get(0).isEmpty()) {
                d = series.get(0).getLowestValueY();
                for (Series s : series) {
                    if (!s.isEmpty() && d > s.getLowestValueY()) {
                        d = s.getLowestValueY();
                    }
                }
                mCompleteRange.bottom = d;

                d = series.get(0).getHighestValueY();
                for (Series s : series) {
                    if (!s.isEmpty() && d < s.getHighestValueY()) {
                        d = s.getHighestValueY();
                    }
                }
                mCompleteRange.top = d;
            }
        }
    }

    /**
     * @return the title of the vertical axis
     */
    public String getVerticalAxisTitle() {
        return mVerticalAxisTitle;
    }

    /**
     * @param mVerticalAxisTitle the title of the vertical axis
     */
    public void setVerticalAxisTitle(String mVerticalAxisTitle) {
        if(mPaintAxisTitle==null) {
            mPaintAxisTitle = new Paint();
            mPaintAxisTitle.setTextSize(getVerticalAxisTitleTextSize());
            mPaintAxisTitle.setTextAlign(Paint.Align.CENTER);
        }
        this.mVerticalAxisTitle = mVerticalAxisTitle;
    }

    /**
     * @return font size of the vertical axis title
     */
    public float getVerticalAxisTitleTextSize() {
        if (getVerticalAxisTitle() == null || getVerticalAxisTitle().length() == 0) {
            return 0;
        }
        return mVerticalAxisTitleTextSize;
    }

    /**
     * @param verticalAxisTitleTextSize font size of the vertical axis title
     */
    public void setVerticalAxisTitleTextSize(float verticalAxisTitleTextSize) {
        mVerticalAxisTitleTextSize = verticalAxisTitleTextSize;
    }

    /**
     * @return font color of the vertical axis title
     */
    public int getVerticalAxisTitleColor() {
        return mVerticalAxisTitleColor;
    }

    /**
     * @param verticalAxisTitleColor font color of the vertical axis title
     */
    public void setVerticalAxisTitleColor(int verticalAxisTitleColor) {
        mVerticalAxisTitleColor = verticalAxisTitleColor;
    }

    /**
     * draws the vertical axis title if
     * it is set
     * @param canvas canvas
     */
    protected void drawVerticalAxisTitle(Canvas canvas) {
        if (mVerticalAxisTitle != null && mVerticalAxisTitle.length() > 0) {
            mPaintAxisTitle.setColor(getVerticalAxisTitleColor());
            mPaintAxisTitle.setTextSize(getVerticalAxisTitleTextSize());
            float x = canvas.getWidth() - getVerticalAxisTitleTextSize()/2;
            float y = canvas.getHeight() / 2;
            canvas.save();
            canvas.rotate(-90, x, y);
            canvas.drawText(mVerticalAxisTitle, x, y, mPaintAxisTitle);
            canvas.restore();
        }
    }
}
