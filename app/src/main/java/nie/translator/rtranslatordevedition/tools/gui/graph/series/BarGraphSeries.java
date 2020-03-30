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
import android.graphics.Paint;
import android.view.animation.AccelerateInterpolator;

import androidx.core.view.ViewCompat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import nie.translator.rtranslatordevedition.tools.gui.graph.GraphView;
import nie.translator.rtranslatordevedition.tools.gui.graph.RectD;
import nie.translator.rtranslatordevedition.tools.gui.graph.ValueDependentColor;

/**
 * Series with Bars to visualize the data.
 * The Bars are always vertical.
 *
 * @author jjoe64
 */
public class BarGraphSeries<E extends DataPointInterface> extends BaseSeries<E> {
    private static final long ANIMATION_DURATION = 333;

    /**
     * paint to do drawing on canvas
     */
    private Paint mPaint;

    /**
     * custom paint that can be used.
     * this will ignore the value dependent color.
     */
    private Paint mCustomPaint;

    /**
     * spacing between the bars in percentage.
     * 0 => no spacing
     * 100 => the space between the bars is as big as the bars itself
     */
    private int mSpacing;

    /**
     * width of a data point
     * 0 => no prior knowledge of sampling period, interval between bars will be calculated automatically
     * >0 => value is the total distance from one bar to another
     */
    private double mDataWidth;

    /**
     * callback to generate value-dependent colors
     * of the bars
     */
    private ValueDependentColor<E> mValueDependentColor;

    /**
     * flag whether the values should drawn
     * above the bars as text
     */
    private boolean mDrawValuesOnTop;

    /**
     * color of the text above the bars.
     *
     * @see #mDrawValuesOnTop
     */
    private int mValuesOnTopColor;

    /**
     * font size of the text above the bars.
     *
     * @see #mDrawValuesOnTop
     */
    private float mValuesOnTopSize;

    /**
     * stores the coordinates of the bars to
     * trigger tap on series events.
     */
    private Map<RectD, E> mDataPoints = new HashMap<RectD, E>();

    /**
     * flag for animated rendering
     */
    private boolean mAnimated;

    /**
     * store the last value that was animated
     */
    private double mLastAnimatedValue = Double.NaN;

    /**
     * time of start animation
     */
    private long mAnimationStart;

    /**
     * animation interpolator
     */
    private AccelerateInterpolator mAnimationInterpolator;

    /**
     * frame number of animation to avoid lagging
     */
    private int mAnimationStartFrameNo;


    /**
     * creates bar series without any data
     */
    public BarGraphSeries() {
        mPaint = new Paint();
    }

    /**
     * creates bar series with data
     *
     * @param data  data points
     *              important: array has to be sorted from lowest x-value to the highest
     */
    public BarGraphSeries(E[] data) {
        super(data);
        mPaint = new Paint();
        mAnimationInterpolator = new AccelerateInterpolator(2f);
    }

    /**
     * draws the bars on the canvas
     *
     * @param graphView corresponding graphview
     * @param canvas canvas
     * @param isSecondScale whether we are plotting the second scale or not
     */
    @Override
    public void draw(GraphView graphView, Canvas canvas, boolean isSecondScale) {
        mPaint.setTextAlign(Paint.Align.CENTER);
        if (mValuesOnTopSize == 0) {
            mValuesOnTopSize = graphView.getGridLabelRenderer().getTextSize();
        }
        mPaint.setTextSize(mValuesOnTopSize);

        resetDataPoints();
        
        // get data
        double maxX = graphView.getViewport().getMaxX(false);
        double minX = graphView.getViewport().getMinX(false);

        double maxY;
        double minY;
        if (isSecondScale) {
            maxY = graphView.getSecondScale().getMaxY(false);
            minY = graphView.getSecondScale().getMinY(false);
        } else {
            maxY = graphView.getViewport().getMaxY(false);
            minY = graphView.getViewport().getMinY(false);
        }

        // Iterate through all bar graph series
        // so we know how wide to make our bar,
        // and in what position to put it in
        int numBarSeries = 0;
        int currentSeriesOrder = 0;
        int numValues = 0;
        boolean isCurrentSeries;
        SortedSet<Double> xVals = new TreeSet<Double>();
        for(Series inspectedSeries: graphView.getSeries()) {
            if(inspectedSeries instanceof BarGraphSeries) {
                isCurrentSeries = (inspectedSeries == this);
                if(isCurrentSeries) {
                    currentSeriesOrder = numBarSeries;
                }
                numBarSeries++;

                // calculate the number of slots for bars based on the minimum distance between
                // x coordinates in the series.  This is divided into the range to find
                // the placement and width of bar slots
                // (sections of the x axis for each bar or set of bars)
                // TODO: Move this somewhere more general and cache it, so we don't recalculate it for each series
                Iterator<E> curValues = inspectedSeries.getValues(minX, maxX);
                if (curValues.hasNext()) {
                    xVals.add(curValues.next().getX());
                    if(isCurrentSeries) { numValues++; }
                    while (curValues.hasNext()) {
                        xVals.add(curValues.next().getX());
                        if(isCurrentSeries) { numValues++; }
                    }
                }
            }
        }
        if (numValues == 0) {
            return;
        }

        double minGap = 0;

        if(mDataWidth > 0.0) {
            minGap = mDataWidth;
        } else {
            Double lastVal = null;

            for(Double curVal: xVals) {
                if(lastVal != null) {
                    double curGap = Math.abs(curVal - lastVal);
                    if (minGap == 0 || (curGap > 0 && curGap < minGap)) {
                        minGap = curGap;
                    }
                }
                lastVal = curVal;
            }
        }

        int numBarSlots = (minGap == 0) ? 1 : (int)Math.round((maxX - minX)/minGap) + 1;

        Iterator<E> values = getValues(minX, maxX);

        // Calculate the overall bar slot width - this includes all bars across
        // all series, and any spacing between sets of bars
        int barSlotWidth = numBarSlots == 1
            ? graphView.getGraphContentWidth()
            : graphView.getGraphContentWidth() / (numBarSlots-1);

        // Total spacing (both sides) between sets of bars
        double spacing = Math.min(barSlotWidth*mSpacing/100, barSlotWidth*0.98f);
        // Width of an individual bar
        double barWidth = (barSlotWidth - spacing) / numBarSeries;
        // Offset from the center of a given bar to start drawing
        double offset = barSlotWidth/2;

        double diffY = maxY - minY;
        double diffX = maxX - minX;
        double contentHeight = graphView.getGraphContentHeight();
        double contentWidth = graphView.getGraphContentWidth();
        double contentLeft = graphView.getGraphContentLeft();
        double contentTop = graphView.getGraphContentTop();

        // draw data
        int i=0;
        while (values.hasNext()) {
            E value = values.next();

            double valY = value.getY() - minY;
            double ratY = valY / diffY;
            double y = contentHeight * ratY;

            double valY0 = 0 - minY;
            double ratY0 = valY0 / diffY;
            double y0 = contentHeight * ratY0;

            double valueX = value.getX();
            double valX = valueX - minX;
            double ratX = valX / diffX;
            double x = contentWidth * ratX;

            // hook for value dependent color
            if (getValueDependentColor() != null) {
                mPaint.setColor(getValueDependentColor().get(value));
            } else {
                mPaint.setColor(getColor());
            }

            double left = x + contentLeft - offset + spacing/2 + currentSeriesOrder*barWidth;
            double top = (contentTop - y) + contentHeight;
            double right = left + barWidth;
            double bottom = (contentTop - y0) + contentHeight - (graphView.getGridLabelRenderer().isHighlightZeroLines()?4:1);

            boolean reverse = top > bottom;

            if (mAnimated) {
                if ((Double.isNaN(mLastAnimatedValue) || mLastAnimatedValue < valueX)) {
                    long currentTime = System.currentTimeMillis();
                    if (mAnimationStart == 0) {
                        // start animation
                        mAnimationStart = currentTime;
                        mAnimationStartFrameNo = 0;
                    } else {
                        // anti-lag: wait a few frames
                        if (mAnimationStartFrameNo < 15) {
                            // second time
                            mAnimationStart = currentTime;
                            mAnimationStartFrameNo++;
                        }
                    }
                    float timeFactor = (float) (currentTime-mAnimationStart) / ANIMATION_DURATION;
                    float factor = mAnimationInterpolator.getInterpolation(timeFactor);
                    if (timeFactor <= 1.0) {
                        double barHeight = bottom - top;
                        barHeight = barHeight * factor;
                        top = bottom-barHeight;
                        ViewCompat.postInvalidateOnAnimation(graphView);
                    } else {
                        // animation finished
                        mLastAnimatedValue = valueX;
                    }
                }
            }

            if (reverse) {
                double tmp = top;
                top = bottom + (graphView.getGridLabelRenderer().isHighlightZeroLines()?4:1);
                bottom = tmp;
            }

            // overdraw
            left = Math.max(left, contentLeft);
            right = Math.min(right, contentLeft+contentWidth);
            bottom = Math.min(bottom, contentTop+contentHeight);
            top = Math.max(top, contentTop);

            mDataPoints.put(new RectD(left, top, right, bottom), value);

            Paint p;
            if (mCustomPaint != null) {
                p = mCustomPaint;
            } else {
                p = mPaint;
            }
            canvas.drawRect((float)left, (float)top, (float)right, (float)bottom, p);

            // set values on top of graph
            if (mDrawValuesOnTop) {
                if (reverse) {
                    top = bottom + mValuesOnTopSize + 4;
                    if (top > contentTop+contentHeight) top = contentTop + contentHeight;
                } else {
                    top -= 4;
                    if (top<=contentTop) top+=contentTop+4;
                }

                mPaint.setColor(mValuesOnTopColor);
                canvas.drawText(
                        graphView.getGridLabelRenderer().getLabelFormatter().formatLabel(value.getY(), false)
                        , (float) (left+right)/2, (float) top, mPaint);
            }

            i++;
        }
    }

    /**
     * @return the hook to generate value-dependent color. default null
     */
    public ValueDependentColor<E> getValueDependentColor() {
        return mValueDependentColor;
    }

    /**
     * set a hook to make the color of the bars depending
     * on the actually value/data.
     *
     * @param mValueDependentColor  hook
     *                              null to disable
     */
    public void setValueDependentColor(ValueDependentColor<E> mValueDependentColor) {
        this.mValueDependentColor = mValueDependentColor;
    }

    /**
     * @return the spacing between the bars in percentage
     */
    public int getSpacing() {
        return mSpacing;
    }

    /**
     * @param mSpacing  spacing between the bars in percentage.
     *                  0 => no spacing
     *                  100 => the space between the bars is as big as the bars itself
     */
    public void setSpacing(int mSpacing) {
        this.mSpacing = mSpacing;
    }

    /**
     * @return the interval between data points
     */
    public double getDataWidth() {
        return mDataWidth;
    }

    /**
     * @param mDataWidth    width of a data point (sampling period)
     *                      0 => no prior knowledge of sampling period, interval between bars will be calculated automatically
 *                          >0 => value is the total distance from one bar to another
     */
    public void setDataWidth(double mDataWidth) {
        this.mDataWidth = mDataWidth;
    }

    /**
     * @return whether the values should be drawn above the bars
     */
    public boolean isDrawValuesOnTop() {
        return mDrawValuesOnTop;
    }

    /**
     * @param mDrawValuesOnTop  flag whether the values should drawn
     *                          above the bars as text
     */
    public void setDrawValuesOnTop(boolean mDrawValuesOnTop) {
        this.mDrawValuesOnTop = mDrawValuesOnTop;
    }

    /**
     * @return font color of the values on top of the bars
     * @see #setDrawValuesOnTop(boolean)
     */
    public int getValuesOnTopColor() {
        return mValuesOnTopColor;
    }

    /**
     * @param mValuesOnTopColor the font color of the values on top of the bars
     * @see #setDrawValuesOnTop(boolean)
     */
    public void setValuesOnTopColor(int mValuesOnTopColor) {
        this.mValuesOnTopColor = mValuesOnTopColor;
    }

    /**
     * @return font size of the values above the bars
     * @see #setDrawValuesOnTop(boolean)
     */
    public float getValuesOnTopSize() {
        return mValuesOnTopSize;
    }

    /**
     * @param mValuesOnTopSize font size of the values above the bars
     * @see #setDrawValuesOnTop(boolean)
     */
    public void setValuesOnTopSize(float mValuesOnTopSize) {
        this.mValuesOnTopSize = mValuesOnTopSize;
    }

    /**
     * resets the cached coordinates of the bars
     */
    @Override
    protected void resetDataPoints() {
        mDataPoints.clear();
    }

    /**
     * find the corresponding data point by
     * coordinates.
     *
     * @param x pixels
     * @param y pixels
     * @return datapoint or null
     */
    @Override
    protected E findDataPoint(float x, float y) {
        for (Map.Entry<RectD, E> entry : mDataPoints.entrySet()) {
            if (x >= entry.getKey().left && x <= entry.getKey().right
                && y >= entry.getKey().top && y <= entry.getKey().bottom) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * custom paint that can be used.
     * this will ignore the value dependent color.
     *
     * @return custom paint or null
     */
    public Paint getCustomPaint() {
        return mCustomPaint;
    }

    /**
     * custom paint that can be used.
     * this will ignore the value dependent color.
     *
     * @param mCustomPaint custom paint to use or null
     */
    public void setCustomPaint(Paint mCustomPaint) {
        this.mCustomPaint = mCustomPaint;
    }

    /**
     * draw the series with an animation
     *
     * @param animated animation activated or not
     */
    public void setAnimated(boolean animated) {
        this.mAnimated = animated;
    }

    /**
     * @return rendering is animated or not
     */
    public boolean isAnimated() {
        return mAnimated;
    }

    @Override
    public void drawSelection(GraphView mGraphView, Canvas canvas, boolean b, DataPointInterface value) {
        // TODO
    }
}
