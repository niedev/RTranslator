/**
 * GraphView
 * Copyright 2016 Jonas Gehring
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nie.translator.rtranslatordevedition.tools.gui.graph.series;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.core.view.ViewCompat;

import java.util.Iterator;

import nie.translator.rtranslatordevedition.tools.gui.graph.GraphView;

/**
 * Series to plot the data as line.
 * The line can be styled with many options.
 *
 * @author jjoe64
 */
public class LineGraphSeries<E extends DataPointInterface> extends BaseSeries<E> {
    private static final long ANIMATION_DURATION = 800;

    /**
     * wrapped styles regarding the line
     */
    private final class Styles {
        /**
         * the thickness of the line.
         * This option will be ignored if you are
         * using a custom paint via {@link #setCustomPaint(Paint)}
         */
        private int thickness = 5;

        /**
         * flag whether the area under the line to the bottom
         * of the viewport will be filled with a
         * specific background color.
         *
         * @see #backgroundColor
         */
        private boolean drawBackground = false;

        /**
         * flag whether the data points are highlighted as
         * a visible point.
         *
         * @see #dataPointsRadius
         */
        private boolean drawDataPoints = false;

        /**
         * the radius for the data points.
         *
         * @see #drawDataPoints
         */
        private float dataPointsRadius = 10f;

        /**
         * the background color for the filling under
         * the line.
         *
         * @see #drawBackground
         */
        private int backgroundColor = Color.argb(100, 172, 218, 255);
    }

    /**
     * wrapped styles
     */
    private Styles mStyles;

    private Paint mSelectionPaint;

    /**
     * internal paint object
     */
    private Paint mPaint;

    /**
     * paint for the background
     */
    private Paint mPaintBackground;

    /**
     * path for the background filling
     */
    private Path mPathBackground;

    /**
     * path to the line
     */
    private Path mPath;

    /**
     * custom paint that can be used.
     * this will ignore the thickness and color styles.
     */
    private Paint mCustomPaint;

    /**
     * rendering is animated
     */
    private boolean mAnimated;

    /**
     * last animated value
     */
    private double mLastAnimatedValue = Double.NaN;

    /**
     * time of animation start
     */
    private long mAnimationStart;

    /**
     * animation interpolator
     */
    private AccelerateDecelerateInterpolator mAnimationInterpolator=new AccelerateDecelerateInterpolator();

    /**
     * number of animation frame to avoid lagging
     */
    private int mAnimationStartFrameNo;

    /**
     * flag whether the line should be drawn as a path
     * or with single drawLine commands (more performance)
     * By default we use drawLine because it has much more peformance.
     * For some styling reasons it can make sense to draw as path.
     */
    private boolean mDrawAsPath = false;

    /**
     * creates a series without data
     */
    public LineGraphSeries() {
        init();
    }

    /**
     * creates a series with data
     *
     * @param data  data points
     *              important: array has to be sorted from lowest x-value to the highest
     */
    public LineGraphSeries(E[] data) {
        super(data);
        init();
    }

    /**
     * do the initialization
     * creates internal objects
     */
    protected void init() {
        mStyles = new Styles();
        mPaint = new Paint();
        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaintBackground = new Paint();

        mSelectionPaint = new Paint();
        mSelectionPaint.setColor(Color.argb(80, 0, 0, 0));
        mSelectionPaint.setStyle(Paint.Style.FILL);

        mPathBackground = new Path();
        mPath = new Path();
    }

    /**
     * plots the series
     * draws the line and the background
     *
     * @param graphView graphview
     * @param canvas canvas
     * @param isSecondScale flag if it is the second scale
     */
    @Override
    public void draw(GraphView graphView, Canvas canvas, boolean isSecondScale) {
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

        Iterator<E> values = getValues(minX, maxX);

        // draw background
        double lastEndY = 0;
        double lastEndX = 0;

        // draw data
        mPaint.setStrokeWidth(mStyles.thickness);
        mPaint.setColor(getColor());
        mPaintBackground.setColor(mStyles.backgroundColor);

        Paint paint;
        if (mCustomPaint != null) {
            paint = mCustomPaint;
        } else {
            paint = mPaint;
        }

        mPath.reset();

        if (mStyles.drawBackground) {
            mPathBackground.reset();
        }

        double diffY = maxY - minY;
        double diffX = maxX - minX;

        float graphHeight = graphView.getGraphContentHeight();
        float graphWidth = graphView.getGraphContentWidth();
        float graphLeft = graphView.getGraphContentLeft();
        float graphTop = graphView.getGraphContentTop();

        lastEndY = 0;
        lastEndX = 0;

        // needed to end the path for background
        double lastUsedEndX = 0;
        double lastUsedEndY = 0;
        float firstX = -1;
        float firstY = -1;
        float lastRenderedX = Float.NaN;
        int i = 0;
        float lastAnimationReferenceY = graphTop;  //tenere d'occhio

        boolean sameXSkip = false;
        float minYOnSameX = 0f;
        float maxYOnSameX = 0f;

        while (values.hasNext()) {
            E value = values.next();

            double valueY = value.getY();
            double valY = valueY - minY;
            double ratY = valY / diffY;
            double y = graphHeight * ratY;

            double valX = value.getX() - minX;
            double ratX = valX / diffX;
            double x = graphWidth * ratX;

            double orgX = x;
            double orgY = y;

            if (i > 0) {
                // overdraw
                boolean isOverdrawY = false;
                boolean isOverdrawEndPoint = false;
                boolean skipDraw = false;

                if (x > graphWidth) { // end right
                    double b = ((graphWidth - lastEndX) * (y - lastEndY) / (x - lastEndX));
                    y = lastEndY + b;
                    x = graphWidth;
                    isOverdrawEndPoint = true;
                }
                if (y < 0) { // end bottom
                    // skip when previous and this point is out of bound
                    if (lastEndY < 0) {
                        skipDraw = true;
                    } else {
                        double b = ((0 - lastEndY) * (x - lastEndX) / (y - lastEndY));
                        x = lastEndX + b;
                    }
                    y = 0;
                    isOverdrawY = isOverdrawEndPoint = true;
                }
                if (y > graphHeight) { // end top
                    // skip when previous and this point is out of bound
                    if (lastEndY > graphHeight) {
                        skipDraw = true;
                    } else {
                        double b = ((graphHeight - lastEndY) * (x - lastEndX) / (y - lastEndY));
                        x = lastEndX + b;
                    }
                    y = graphHeight;
                    isOverdrawY = isOverdrawEndPoint = true;
                }
                if (lastEndX < 0) { // start left
                    double b = ((0 - x) * (y - lastEndY) / (lastEndX - x));
                    lastEndY = y - b;
                    lastEndX = 0;
                }

                // we need to save the X before it will be corrected when overdraw y
                float orgStartX = (float) lastEndX + (graphLeft + 1);

                if (lastEndY < 0) { // start bottom
                    if (!skipDraw) {
                        double b = ((0 - y) * (x - lastEndX) / (lastEndY - y));
                        lastEndX = x - b;
                    }
                    lastEndY = 0;
                    isOverdrawY = true;
                }
                if (lastEndY > graphHeight) { // start top
                    // skip when previous and this point is out of bound
                    if (!skipDraw) {
                        double b = ((graphHeight - y) * (x - lastEndX) / (lastEndY - y));
                        lastEndX = x - b;
                    }
                    lastEndY = graphHeight;
                    isOverdrawY = true;
                }

                float startX = (float) lastEndX + (graphLeft + 1);
                float startY = (float) (graphTop - lastEndY) + graphHeight;
                float endX = (float) x + (graphLeft + 1);
                float endY = (float) (graphTop - y) + graphHeight;
                float startYAnimated = startY;
                float endYAnimated = endY;

                if (endX < startX) {
                    // dont draw from right to left
                    skipDraw = true;
                }

                // NaN can happen when previous and current value is out of y bounds
                if (!skipDraw && !Float.isNaN(startY) && !Float.isNaN(endY)) {
                    // animation
                    if (mAnimated) {
                        if ((Double.isNaN(mLastAnimatedValue) || mLastAnimatedValue < valueY)) {
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
                            float timeFactor = (float) (currentTime - mAnimationStart) / ANIMATION_DURATION;
                            float factor = mAnimationInterpolator.getInterpolation(timeFactor);
                            if (timeFactor <= 1.0) {
                                startYAnimated = graphHeight-(graphHeight-(startY - lastAnimationReferenceY)) * factor + lastAnimationReferenceY;
                                startYAnimated = Math.max(startYAnimated, lastAnimationReferenceY);
                                endYAnimated =  graphHeight-(graphHeight-(endY - lastAnimationReferenceY)) * factor + lastAnimationReferenceY;
                                ViewCompat.postInvalidateOnAnimation(graphView);
                            } else {
                                // animation finished
                                mLastAnimatedValue = valueY;
                            }
                        } else {
                            lastAnimationReferenceY = endY;
                        }
                    }

                    // draw data point
                    if (!isOverdrawEndPoint) {
                        if (mStyles.drawDataPoints) {
                            // draw first datapoint
                            Paint.Style prevStyle = paint.getStyle();
                            paint.setStyle(Paint.Style.FILL);
                            canvas.drawCircle(endX, endYAnimated, mStyles.dataPointsRadius, paint);
                            paint.setStyle(prevStyle);
                        }
                        registerDataPoint(endX, endY, value);
                    }

                    if (mDrawAsPath) {
                        mPath.moveTo(startX, startYAnimated);
                    }
                    // performance opt.
                    if (Float.isNaN(lastRenderedX) || Math.abs(endX - lastRenderedX) > .3f) {
                        if (mDrawAsPath) {
                            mPath.lineTo(endX, endYAnimated);
                        } else {
                            // draw vertical lines that were skipped
                            if (sameXSkip) {
                                sameXSkip = false;
                                renderLine(canvas, new float[]{lastRenderedX, minYOnSameX, lastRenderedX, maxYOnSameX}, paint);
                            }
                            renderLine(canvas, new float[]{startX, startYAnimated, endX, endYAnimated}, paint);
                        }
                        lastRenderedX = endX;
                    } else {
                        // rendering on same x position
                        // save min+max y position and draw it as line
                        if (sameXSkip) {
                            minYOnSameX = Math.min(minYOnSameX, endY);
                            maxYOnSameX = Math.max(maxYOnSameX, endY);
                        } else {
                            // first
                            sameXSkip = true;
                            minYOnSameX = Math.min(startY, endY);
                            maxYOnSameX = Math.max(startY, endY);
                        }
                    }

                }

                if (mStyles.drawBackground) {
                    if (isOverdrawY) {
                        // start draw original x
                        if (firstX == -1) {
                            firstX = orgStartX;
                            firstY = startY;
                            mPathBackground.moveTo(orgStartX, startY);
                        }
                        // from original start to new start
                        mPathBackground.lineTo(startX, startYAnimated);
                    }
                    if (firstX == -1) {
                        firstX = startX;
                        firstY = startYAnimated;
                        mPathBackground.moveTo(startX, startYAnimated);
                    }
                    mPathBackground.lineTo(startX, startYAnimated);
                    mPathBackground.lineTo(endX, endYAnimated);
                }

                lastUsedEndX = endX;
                lastUsedEndY = endYAnimated;
            } else if (mStyles.drawDataPoints) {
                //fix: last value not drawn as datapoint. Draw first point here, and then on every step the end values (above)
                float first_X = (float) x + (graphLeft + 1);
                float first_Y = (float) (graphTop - y) + graphHeight;

                if (first_X >= graphLeft && first_Y <= (graphTop + graphHeight)) {
                    if (mAnimated && (Double.isNaN(mLastAnimatedValue) || mLastAnimatedValue < valueY)) {
                        long currentTime = System.currentTimeMillis();
                        if (mAnimationStart == 0) {
                            // start animation
                            mAnimationStart = currentTime;
                        }
                        float timeFactor = (float) (currentTime - mAnimationStart) / ANIMATION_DURATION;
                        float factor = mAnimationInterpolator.getInterpolation(timeFactor);
                        if (timeFactor <= 1.0) {
                            first_Y = graphHeight-(graphHeight-(first_Y - lastAnimationReferenceY)) * factor + lastAnimationReferenceY;
                            ViewCompat.postInvalidateOnAnimation(graphView);
                        } else {
                            // animation finished
                            mLastAnimatedValue = valueY;
                        }
                    }


                    Paint.Style prevStyle = paint.getStyle();
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(first_X, first_Y, mStyles.dataPointsRadius, paint);
                    paint.setStyle(prevStyle);
                    registerDataPoint(first_X, first_Y, value);
                }
            }
            lastEndY = orgY;
            lastEndX = orgX;
            i++;
        }

        if (mDrawAsPath) {
            // draw at the end
            canvas.drawPath(mPath, paint);
        }

        if (mStyles.drawBackground && firstX != -1) {
            // end / close path
            if (lastUsedEndY != graphHeight + graphTop) {
                // dont draw line to same point, otherwise the path is completely broken
                mPathBackground.lineTo((float) lastUsedEndX, graphHeight + graphTop);
            }
            mPathBackground.lineTo(firstX, graphHeight + graphTop);
            if (firstY != graphHeight + graphTop) {
                // dont draw line to same point, otherwise the path is completely broken
                mPathBackground.lineTo(firstX, firstY);
            }
            //mPathBackground.close();
            canvas.drawPath(mPathBackground, mPaintBackground);
        }
    }

    /**
     * just a wrapper to draw lines on canvas
     *
     * @param canvas
     * @param pts
     * @param paint
     */
    private void renderLine(Canvas canvas, float[] pts, Paint paint) {
        if (pts.length == 4 && pts[0] == pts[2] && pts[1] == pts[3]) {
            // avoid zero length lines, to makes troubles on some devices
            // see https://github.com/appsthatmatter/GraphView/issues/499
            return;
        }
        canvas.drawLines(pts, paint);
    }

    /**
     * the thickness of the line.
     * This option will be ignored if you are
     * using a custom paint via {@link #setCustomPaint(Paint)}
     *
     * @return the thickness of the line
     */
    public int getThickness() {
        return mStyles.thickness;
    }

    /**
     * the thickness of the line.
     * This option will be ignored if you are
     * using a custom paint via {@link #setCustomPaint(Paint)}
     *
     * @param thickness thickness of the line
     */
    public void setThickness(int thickness) {
        mStyles.thickness = thickness;
    }

    /**
     * flag whether the area under the line to the bottom
     * of the viewport will be filled with a
     * specific background color.
     *
     * @return whether the background will be drawn
     * @see #getBackgroundColor()
     */
    public boolean isDrawBackground() {
        return mStyles.drawBackground;
    }

    /**
     * flag whether the area under the line to the bottom
     * of the viewport will be filled with a
     * specific background color.
     *
     * @param drawBackground whether the background will be drawn
     * @see #setBackgroundColor(int)
     */
    public void setDrawBackground(boolean drawBackground) {
        mStyles.drawBackground = drawBackground;
    }

    /**
     * flag whether the data points are highlighted as
     * a visible point.
     *
     * @return flag whether the data points are highlighted
     * @see #setDataPointsRadius(float)
     */
    public boolean isDrawDataPoints() {
        return mStyles.drawDataPoints;
    }

    /**
     * flag whether the data points are highlighted as
     * a visible point.
     *
     * @param drawDataPoints flag whether the data points are highlighted
     * @see #setDataPointsRadius(float)
     */
    public void setDrawDataPoints(boolean drawDataPoints) {
        mStyles.drawDataPoints = drawDataPoints;
    }

    /**
     * @return the radius for the data points.
     * @see #setDrawDataPoints(boolean)
     */
    public float getDataPointsRadius() {
        return mStyles.dataPointsRadius;
    }

    /**
     * @param dataPointsRadius the radius for the data points.
     * @see #setDrawDataPoints(boolean)
     */
    public void setDataPointsRadius(float dataPointsRadius) {
        mStyles.dataPointsRadius = dataPointsRadius;
    }

    /**
     * @return the background color for the filling under
     *          the line.
     * @see #setDrawBackground(boolean)
     */
    public int getBackgroundColor() {
        return mStyles.backgroundColor;
    }

    /**
     * @param backgroundColor  the background color for the filling under
     *                          the line.
     * @see #setDrawBackground(boolean)
     */
    public void setBackgroundColor(int backgroundColor) {
        mStyles.backgroundColor = backgroundColor;
    }

    /**
     * custom paint that can be used.
     * this will ignore the thickness and color styles.
     *
     * @param customPaint the custom paint to be used for rendering the line
     */
    public void setCustomPaint(Paint customPaint) {
        this.mCustomPaint = customPaint;
    }

    /**
     * @param animated activate the animated rendering
     */
    public void setAnimated(boolean animated) {
        this.mAnimated = animated;
    }

    /**
     * flag whether the line should be drawn as a path
     * or with single drawLine commands (more performance)
     * By default we use drawLine because it has much more peformance.
     * For some styling reasons it can make sense to draw as path.
     */
    public boolean isDrawAsPath() {
        return mDrawAsPath;
    }

    /**
     * flag whether the line should be drawn as a path
     * or with single drawLine commands (more performance)
     * By default we use drawLine because it has much more peformance.
     * For some styling reasons it can make sense to draw as path.
     *
     * @param mDrawAsPath true to draw as path
     */
    public void setDrawAsPath(boolean mDrawAsPath) {
        this.mDrawAsPath = mDrawAsPath;
    }

    /**
     *
     * @param dataPoint values the values must be in the correct order!
     *                  x-value has to be ASC. First the lowest x value and at least the highest x value.
     * @param scrollToEnd true => graphview will scroll to the end (maxX)
     * @param maxDataPoints if max data count is reached, the oldest data
     *                      value will be lost to avoid memory leaks
     * @param silent    set true to avoid rerender the graph
     */
    public void appendData(E dataPoint, boolean scrollToEnd, int maxDataPoints, boolean silent) {
        if (!isAnimationActive()) {
            mAnimationStart = 0;
        }
        super.appendData(dataPoint, scrollToEnd, maxDataPoints, silent);
    }

    /**
     * @return currently animation is active
     */
    private boolean isAnimationActive() {
        if (mAnimated) {
            long curr = System.currentTimeMillis();
            return curr - mAnimationStart <= ANIMATION_DURATION;
        }
        return false;
    }

    @Override
    public void drawSelection(GraphView graphView, Canvas canvas, boolean b, DataPointInterface value) {
        double spanX = graphView.getViewport().getMaxX(false) - graphView.getViewport().getMinX(false);
        double spanXPixel = graphView.getGraphContentWidth();

        double spanY = graphView.getViewport().getMaxY(false) - graphView.getViewport().getMinY(false);
        double spanYPixel = graphView.getGraphContentHeight();

        double pointX = (value.getX() - graphView.getViewport().getMinX(false)) * spanXPixel / spanX;
        pointX += graphView.getGraphContentLeft();

        double pointY = (value.getY() - graphView.getViewport().getMinY(false)) * spanYPixel / spanY;
        pointY = graphView.getGraphContentTop() + spanYPixel - pointY;

        // border
        canvas.drawCircle((float) pointX, (float) pointY, 30f, mSelectionPaint);

        // fill
        Paint.Style prevStyle = mPaint.getStyle();
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle((float) pointX, (float) pointY, 23f, mPaint);
        mPaint.setStyle(prevStyle);
    }
}
