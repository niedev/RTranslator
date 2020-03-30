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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.TypedValue;

import java.util.LinkedHashMap;
import java.util.Map;

import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.gui.GuiTools;

/**
 * The default renderer for the grid
 * and the labels.
 *
 * @author jjoe64
 */
public class GridLabelRenderer {

    /**
     * Hoziontal label alignment
     */
    public enum VerticalLabelsVAlign {
        /**
         * Above vertical line
         */
        ABOVE,
        /**
         * Mid vertical line
         */
        MID,
        /**
         * Below vertical line
         */
        BELOW
    }


    /**
     * wrapper for the styles regarding
     * to the grid and the labels
     */
    public final class Styles {
        /**
         * the general text size of the axis titles.
         * can be overwritten with #verticalAxisTitleTextSize
         * and #horizontalAxisTitleTextSize
         */
        public float textSize;

        /**
         * the alignment of the vertical labels
         */
        public Paint.Align verticalLabelsAlign;

        /**
         * the alignment of the labels on the right side
         */
        public Paint.Align verticalLabelsSecondScaleAlign;

        /**
         * the color of the vertical labels
         */
        public int verticalLabelsColor;

        /**
         * the color of the labels on the right side
         */
        public int verticalLabelsSecondScaleColor;

        /**
         * the color of the horizontal labels
         */
        public int horizontalLabelsColor;

        /**
         * the color of the grid lines
         */
        public int gridColor;

        /**
         * flag whether the zero-lines (vertical+
         * horizontal) shall be highlighted
         */
        public boolean highlightZeroLines;

        /**
         * the padding around the graph and labels
         */
        public int horizontalPadding;

        /**
         * the padding around the graph and labels
         */
        public int verticalPadding;

        /**
         * font size of the vertical axis title
         */
        public float verticalAxisTitleTextSize;

        /**
         * font color of the vertical axis title
         */
        public int verticalAxisTitleColor;

        /**
         * font size of the horizontal axis title
         */
        public float horizontalAxisTitleTextSize;

        /**
         * font color of the horizontal axis title
         */
        public int horizontalAxisTitleColor;
        
        /**
         * angle of the horizontal axis label in 
         * degrees between 0 and 180
         */
        public float horizontalLabelsAngle;

        /**
         * flag whether the horizontal labels are
         * visible
         */
        boolean horizontalLabelsVisible;

        /**
         * flag whether the vertical labels are
         * visible
         */
        boolean verticalLabelsVisible;

        /**
         * defines which lines will be drawn in the background
         */
        GridStyle gridStyle;

        /**
         * the space between the labels text and the graph content
         */
        int horizontalLabelsSpace;

        /**
         * the space between the labels text and the graph content
         */
        int verticalLabelsSpace;

        /**
         * vertical labels vertical align (above, below, mid of the grid line)
         */
        VerticalLabelsVAlign verticalLabelsVAlign = VerticalLabelsVAlign.MID;
    }

    /**
     * Definition which lines will be drawn in the background
     */
    public enum GridStyle {
        /**
         * show vertical and horizonal lines
         * this is the default
         */
        BOTH,

        /**
         * show only vertical lines
         */
        VERTICAL,

        /**
         * show only horizontal lines
         */
        HORIZONTAL,

        /**
         * dont draw any lines
         */
        NONE;

        public boolean drawVertical() { return this == BOTH || this == VERTICAL && this != NONE; }
        public boolean drawHorizontal() { return this == BOTH || this == HORIZONTAL && this != NONE; }
    }

    /**
     * wraps the styles regarding the
     * grid and labels
     */
    protected Styles mStyles;

    /**
     * reference to graphview
     */
    private final GraphView mGraphView;

    /**
     * cache of the vertical steps
     * (horizontal lines and vertical labels)
     * Key      = Pixel (y)
     * Value    = y-value
     */
    private Map<Integer, Double> mStepsVertical;

    /**
     * cache of the vertical steps for the
     * second scale, which is on the right side
     * (horizontal lines and vertical labels)
     * Key      = Pixel (y)
     * Value    = y-value
     */
    private Map<Integer, Double> mStepsVerticalSecondScale;

    /**
     * cache of the horizontal steps
     * (vertical lines and horizontal labels)
     * Value    = x-value
     */
    private Map<Integer, Double> mStepsHorizontal;

    /**
     * the paint to draw the grid lines
     */
    private Paint mPaintLine;

    /**
     * the paint to draw the labels
     */
    private Paint mPaintLabel;

    /**
     * the paint to draw axis titles
     */
    private Paint mPaintAxisTitle;

    /**
     * flag whether is bounds are automatically
     * adjusted for nice human-readable numbers
     */
    protected boolean mIsAdjusted;

    /**
     * the width of the vertical labels
     */
    private Integer mLabelVerticalWidth;

    /**
     * indicates if the width was set manually
     */
    private boolean mLabelVerticalWidthFixed;

    /**
     * the height of the vertical labels
     */
    private Integer mLabelVerticalHeight;

    /**
     * indicates if the height was set manually
     */
    private boolean mLabelHorizontalHeightFixed;

    /**
     * the width of the vertical labels
     * of the second scale
     */
    private Integer mLabelVerticalSecondScaleWidth;

    /**
     * the height of the vertical labels
     * of the second scale
     */
    private Integer mLabelVerticalSecondScaleHeight;

    /**
     * the width of the horizontal labels
     */
    private Integer mLabelHorizontalWidth;

    /**
     * the height of the horizontal labels
     */
    private Integer mLabelHorizontalHeight;

    /**
     * the label formatter, that converts
     * the raw numbers to strings
     */
    private LabelFormatter mLabelFormatter;

    /**
     * the title of the horizontal axis
     */
    private String mHorizontalAxisTitle;

    /**
     * the title of the vertical axis
     */
    private String mVerticalAxisTitle;

    /**
     * count of the vertical labels, that
     * will be shown at one time.
     */
    private int mNumVerticalLabels;

    /**
     * count of the horizontal labels, that
     * will be shown at one time.
     */
    private int mNumHorizontalLabels;

    /**
     * sets the space for the vertical labels on the right side
     *
     * @param newWidth set fixed width. set null to calculate it automatically
     */
    public void setSecondScaleLabelVerticalWidth(Integer newWidth) {
        mLabelVerticalSecondScaleWidth = newWidth;
    }

    /**
     * activate or deactivate human rounding of the
     * horizontal axis. GraphView tries to fit the labels
     * to display numbers that can be divided by 1, 2, or 5.
     */
    private boolean mHumanRoundingY;

    /**
     * activate or deactivate human rounding of the
     * horizontal axis. GraphView tries to fit the labels
     * to display numbers that can be divided by 1, 2, or 5.
     *
     * By default this is enabled. It makes sense to deactivate it
     * when using Dates on the x axis.
     */
    private boolean mHumanRoundingX;

    /**
     * create the default grid label renderer.
     *
     * @param graphView the corresponding graphview object
     */
    public GridLabelRenderer(GraphView graphView) {
        mGraphView = graphView;
        setLabelFormatter(new DefaultLabelFormatter());
        mStyles = new Styles();
        resetStyles();
        mNumVerticalLabels = 5;
        mNumHorizontalLabels = 5;
        mHumanRoundingX = true;
        mHumanRoundingY = true;
    }

    /**
     * resets the styles. This loads the style
     * from reading the values of the current
     * theme.
     */
    public void resetStyles() {
        // get matching styles from theme
        TypedValue typedValue = new TypedValue();
        mGraphView.getContext().getTheme().resolveAttribute(android.R.attr.textAppearanceSmall, typedValue, true);

        int color1;
        int color2;
        int size;
        int size2;

        TypedArray array = null;
        try {
            array = mGraphView.getContext().obtainStyledAttributes(typedValue.data, new int[]{
                    android.R.attr.textColorPrimary
                    , android.R.attr.textColorSecondary
                    , android.R.attr.textSize
                    , android.R.attr.horizontalGap});
            color1 = array.getColor(0, Color.BLACK);
            color2 = array.getColor(1, Color.GRAY);
            size = array.getDimensionPixelSize(2, 20);
            size2 = array.getDimensionPixelSize(3, 20);
            array.recycle();
        } catch (Exception e) {
            color1 = Color.BLACK;
            color2 = Color.GRAY;
            size = 20;
            size2 = 20;
        }

        mStyles.verticalLabelsColor = color1;
        mStyles.verticalLabelsSecondScaleColor = color1;
        mStyles.horizontalLabelsColor = color1;
        mStyles.gridColor = color2;
        mStyles.textSize = size;
        mStyles.horizontalPadding = size2;
        mStyles.verticalPadding=size2;
        mStyles.horizontalLabelsSpace = (int) mStyles.textSize/5;
        mStyles.verticalLabelsSpace = (int) mStyles.textSize/5;

        mStyles.verticalLabelsAlign = Paint.Align.RIGHT;
        mStyles.verticalLabelsSecondScaleAlign = Paint.Align.LEFT;
        mStyles.highlightZeroLines = true;

        mStyles.verticalAxisTitleColor = mStyles.verticalLabelsColor;
        mStyles.horizontalAxisTitleColor = mStyles.horizontalLabelsColor;
        mStyles.verticalAxisTitleTextSize = mStyles.textSize;
        mStyles.horizontalAxisTitleTextSize = mStyles.textSize;

        mStyles.horizontalLabelsVisible = true;
        mStyles.verticalLabelsVisible = true;
        
        mStyles.horizontalLabelsAngle = 0f;

        mStyles.gridStyle = GridStyle.BOTH;
        reloadStyles();
    }

    /**
     * will load the styles to the internal
     * paint objects (color, text size, text align)
     */
    public void reloadStyles() {
        mPaintLine = new Paint();
        mPaintLine.setColor(mStyles.gridColor);
        mPaintLine.setStrokeWidth(0);

        mPaintLabel = new Paint();
        mPaintLabel.setTextSize(getTextSize());
        mPaintLabel.setAntiAlias(true);

        mPaintAxisTitle = new Paint();
        mPaintAxisTitle.setTextSize(getTextSize());
        mPaintAxisTitle.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * GraphView tries to fit the labels
     * to display numbers that can be divided by 1, 2, or 5.
     *
     * By default this is enabled. It makes sense to deactivate it
     * when using Dates on the x axis.

     * @return if human rounding is enabled
     */
    public boolean isHumanRoundingX() {
        return mHumanRoundingX;
    }

    /**
     * GraphView tries to fit the labels
     * to display numbers that can be divided by 1, 2, or 5.
     *
     * @return if human rounding is enabled
     */
    public boolean isHumanRoundingY() {
        return mHumanRoundingY;
    }

    /**
     * activate or deactivate human rounding of the
     * horizontal axis. GraphView tries to fit the labels
     * to display numbers that can be divided by 1, 2, or 5.
     *
     * By default this is enabled. It makes sense to deactivate it
     * when using Dates on the x axis.
     *
     * @param humanRoundingX false to deactivate
     * @param humanRoundingY false to deactivate
     */
    public void setHumanRounding(boolean humanRoundingX, boolean humanRoundingY) {
        this.mHumanRoundingX = humanRoundingX;
        this.mHumanRoundingY = humanRoundingY;
    }

    /**
     * activate or deactivate human rounding of the
     * horizontal axis. GraphView tries to fit the labels
     * to display numbers that can be divided by 1, 2, or 5.
     *
     * By default this is enabled.
     *
     * @param humanRoundingBoth false to deactivate on both axises
     */
    public void setHumanRounding(boolean humanRoundingBoth) {
        this.mHumanRoundingX = humanRoundingBoth;
        this.mHumanRoundingY = humanRoundingBoth;
    }

    /**
     * @return the general text size for the axis titles
     */
    public float getTextSize() {
        return mStyles.textSize;
    }

    /**
     * @return the font color of the vertical labels
     */
    public int getVerticalLabelsColor() {
        return mStyles.verticalLabelsColor;
    }

    /**
     * @return  the alignment of the text of the
     *          vertical labels
     */
    public Paint.Align getVerticalLabelsAlign() {
        return mStyles.verticalLabelsAlign;
    }

    /**
     * @return the font color of the horizontal labels
     */
    public int getHorizontalLabelsColor() {
        return mStyles.horizontalLabelsColor;
    }
    
    /**
     * @return the angle of the horizontal labels
     */
    public float getHorizontalLabelsAngle() {
        return mStyles.horizontalLabelsAngle;
    }

    /**
     * clears the internal cache and forces
     * to redraw the grid and labels.
     * Normally you should always call {@link GraphView#onDataChanged(boolean, boolean)}
     * which will call this method.
     *
     * @param keepLabelsSize true if you don't want
     *                       to recalculate the size of
     *                       the labels. It is recommended
     *                       to use "true" because this will
     *                       improve performance and prevent
     *                       a flickering.
     * @param keepViewport true if you don't want that
     *                     the viewport will be recalculated.
     *                     It is recommended to use "true" for
     *                     performance.
     */
    public void invalidate(boolean keepLabelsSize, boolean keepViewport) {
        if (!keepViewport) {
            mIsAdjusted = false;
        }
        if (!keepLabelsSize) {
            if (!mLabelVerticalWidthFixed) {
                mLabelVerticalWidth = null;
            }
            mLabelVerticalHeight = null;
            mLabelVerticalSecondScaleWidth = null;
            mLabelVerticalSecondScaleHeight = null;
        }
        //reloadStyles();
    }

    /**
     * calculates the vertical steps of
     * the second scale.
     * This will not do any automatically update
     * of the bounds.
     * Use always manual bounds for the second scale.
     *
     * @return true if it is ready
     */
    protected boolean adjustVerticalSecondScale() {
        if (mLabelHorizontalHeight == null) {
            return false;
        }
        if (mGraphView.mSecondScale == null) {
            return true;
        }

        double minY = mGraphView.mSecondScale.getMinY(false);
        double maxY = mGraphView.mSecondScale.getMaxY(false);

        // TODO find the number of labels
        int numVerticalLabels = mNumVerticalLabels;

        double newMinY;
        double exactSteps;

        if (mGraphView.mSecondScale.isYAxisBoundsManual()) {
            // split range into equal steps
            exactSteps = (maxY - minY) / (numVerticalLabels - 1);

            // round because of floating error
            exactSteps = Math.round(exactSteps * 1000000d) / 1000000d;
        } else {
            // TODO auto adjusting
            throw new IllegalStateException("Not yet implemented");
        }

        if (mStepsVerticalSecondScale != null && mStepsVerticalSecondScale.size() > 1) {
            // else choose other nice steps that previous
            // steps are included (divide to have more, or multiplicate to have less)

            double d1 = 0, d2 = 0;
            int i = 0;
            for (Double v : mStepsVerticalSecondScale.values()) {
                if (i == 0) {
                    d1 = v;
                } else {
                    d2 = v;
                    break;
                }
                i++;
            }
            double oldSteps = d2 - d1;
            if (oldSteps > 0) {
                double newSteps = Double.NaN;

                if (oldSteps > exactSteps) {
                    newSteps = oldSteps / 2;
                } else if (oldSteps < exactSteps) {
                    newSteps = oldSteps * 2;
                }

                // only if there wont be more than numLabels
                // and newSteps will be better than oldSteps
                int numStepsOld = (int) ((maxY - minY) / oldSteps);
                int numStepsNew = (int) ((maxY - minY) / newSteps);

                boolean shouldChange;

                // avoid switching between 2 steps
                if (numStepsOld <= numVerticalLabels && numStepsNew <= numVerticalLabels) {
                    // both are possible
                    // only the new if it hows more labels
                    shouldChange = numStepsNew > numStepsOld;
                } else {
                    shouldChange = true;
                }

                if (newSteps != Double.NaN && shouldChange && numStepsNew <= numVerticalLabels) {
                    exactSteps = newSteps;
                } else {
                    // try to stay to the old steps
                    exactSteps = oldSteps;
                }
            }
        } else {
            // first time
        }

        // find the first data point that is relevant to display
        // starting from 1st datapoint so that the steps have nice numbers
        // goal is to start with the minY or 1 step before
        newMinY = mGraphView.getSecondScale().mReferenceY;
        // must be down-rounded
        double count = Math.floor((minY-newMinY)/exactSteps);
        newMinY = count*exactSteps + newMinY;

        // it can happen that we need to add some more labels to fill the complete screen
        numVerticalLabels = (int) ((mGraphView.getSecondScale().mCurrentViewport.height()*-1 / exactSteps)) + 2;

        // ensure that the value is valid (minimum 2)
        // see https://github.com/appsthatmatter/GraphView/issues/520
        numVerticalLabels = Math.max(numVerticalLabels, 2);

        if (mStepsVerticalSecondScale != null) {
            mStepsVerticalSecondScale.clear();
        } else {
            mStepsVerticalSecondScale = new LinkedHashMap<>(numVerticalLabels);
        }

        int height = mGraphView.getGraphContentHeight();
        // convert data-y to pixel-y in current viewport
        double pixelPerData = height / mGraphView.getSecondScale().mCurrentViewport.height()*-1;

        for (int i = 0; i < numVerticalLabels; i++) {
            // dont draw if it is top of visible screen
            if (newMinY + (i * exactSteps) > mGraphView.getSecondScale().mCurrentViewport.top) {
                continue;
            }
            // dont draw if it is below of visible screen
            if (newMinY + (i * exactSteps) < mGraphView.getSecondScale().mCurrentViewport.bottom) {
                continue;
            }


            // where is the data point on the current screen
            double dataPointPos = newMinY + (i * exactSteps);
            double relativeToCurrentViewport = dataPointPos - mGraphView.getSecondScale().mCurrentViewport.bottom;

            double pixelPos = relativeToCurrentViewport * pixelPerData;
            mStepsVerticalSecondScale.put((int) pixelPos, dataPointPos);
        }

        return true;
    }

    /**
     * calculates the vertical steps. This will
     * automatically change the bounds to nice
     * human-readable min/max.
     *
     * @return true if it is ready
     */
    protected boolean adjustVertical(boolean changeBounds) {
        if (mLabelHorizontalHeight == null) {
            return false;
        }

        double minY = mGraphView.getViewport().getMinY(false);
        double maxY = mGraphView.getViewport().getMaxY(false);

        if (minY == maxY) {
            return false;
        }

        // TODO find the number of labels
        int numVerticalLabels = mNumVerticalLabels;

        double newMinY;
        double exactSteps;

        // split range into equal steps
        exactSteps = (maxY - minY) / (numVerticalLabels - 1);

        // round because of floating error
        exactSteps = Math.round(exactSteps * 1000000d) / 1000000d;

        // smallest viewport
        if (exactSteps == 0d) {
            exactSteps = 0.0000001d;
            maxY = minY + exactSteps * (numVerticalLabels - 1);
        }

        // human rounding to have nice numbers (1, 2, 5, ...)
        if (isHumanRoundingY()) {
            exactSteps = humanRound(exactSteps, changeBounds);
        } else if (mStepsVertical != null && mStepsVertical.size() > 1) {
            // else choose other nice steps that previous
            // steps are included (divide to have more, or multiplicate to have less)

            double d1 = 0, d2 = 0;
            int i = 0;
            for (Double v : mStepsVertical.values()) {
                if (i == 0) {
                    d1 = v;
                } else {
                    d2 = v;
                    break;
                }
                i++;
            }
            double oldSteps = d2 - d1;
            if (oldSteps > 0) {
                double newSteps = Double.NaN;

                if (oldSteps > exactSteps) {
                    newSteps = oldSteps / 2;
                } else if (oldSteps < exactSteps) {
                    newSteps = oldSteps * 2;
                }

                // only if there wont be more than numLabels
                // and newSteps will be better than oldSteps
                int numStepsOld = (int) ((maxY - minY) / oldSteps);
                int numStepsNew = (int) ((maxY - minY) / newSteps);

                boolean shouldChange;

                // avoid switching between 2 steps
                if (numStepsOld <= numVerticalLabels && numStepsNew <= numVerticalLabels) {
                    // both are possible
                    // only the new if it hows more labels
                    shouldChange = numStepsNew > numStepsOld;
                } else {
                    shouldChange = true;
                }

                if (newSteps != Double.NaN && shouldChange && numStepsNew <= numVerticalLabels) {
                    exactSteps = newSteps;
                } else {
                    // try to stay to the old steps
                    exactSteps = oldSteps;
                }
            }
        } else {
            // first time
        }

        // find the first data point that is relevant to display
        // starting from 1st datapoint so that the steps have nice numbers
        // goal is to start with the minX or 1 step before
        newMinY = mGraphView.getViewport().getReferenceY();
        // must be down-rounded
        double count = Math.floor((minY-newMinY)/exactSteps);
        newMinY = count*exactSteps + newMinY;

        // now we have our labels bounds
        if (changeBounds) {
            mGraphView.getViewport().setMinY(newMinY);
            mGraphView.getViewport().setMaxY(Math.max(maxY, newMinY + (numVerticalLabels - 1) * exactSteps));
            mGraphView.getViewport().mYAxisBoundsStatus = Viewport.AxisBoundsStatus.AUTO_ADJUSTED;
        }

        // it can happen that we need to add some more labels to fill the complete screen
        numVerticalLabels = (int) ((mGraphView.getViewport().mCurrentViewport.height()*-1 / exactSteps)) + 2;

        if (mStepsVertical != null) {
            mStepsVertical.clear();
        } else {
            mStepsVertical = new LinkedHashMap<>((int) numVerticalLabels);
        }

        int height = mGraphView.getGraphContentHeight();
        // convert data-y to pixel-y in current viewport
        double pixelPerData = height / mGraphView.getViewport().mCurrentViewport.height()*-1;

        for (int i = 0; i < numVerticalLabels; i++) {
            // dont draw if it is top of visible screen
            if (newMinY + (i * exactSteps) > mGraphView.getViewport().mCurrentViewport.top) {
                continue;
            }
            // dont draw if it is below of visible screen
            if (newMinY + (i * exactSteps) < mGraphView.getViewport().mCurrentViewport.bottom) {
                continue;
            }


            // where is the data point on the current screen
            double dataPointPos = newMinY + (i * exactSteps);
            double relativeToCurrentViewport = dataPointPos - mGraphView.getViewport().mCurrentViewport.bottom;

            double pixelPos = relativeToCurrentViewport * pixelPerData;
            mStepsVertical.put((int) pixelPos, dataPointPos);
        }

        return true;
    }

    /**
     * calculates the horizontal steps.
     *
     * @param changeBounds This will automatically change the
     *                     bounds to nice human-readable min/max.
     * @return true if it is ready
     */
    protected boolean adjustHorizontal(boolean changeBounds) {
        if (mLabelVerticalWidth == null) {
            return false;
        }

        double minX = mGraphView.getViewport().getMinX(false);
        double maxX = mGraphView.getViewport().getMaxX(false);
        if (minX == maxX) return false;

        // TODO find the number of labels
        int numHorizontalLabels = mNumHorizontalLabels;

        double newMinX;
        double exactSteps;

        // split range into equal steps
        exactSteps = (maxX - minX) / (numHorizontalLabels - 1);

        // round because of floating error
        exactSteps = Math.round(exactSteps * 1000000d) / 1000000d;

        // smallest viewport
        if (exactSteps == 0d) {
            exactSteps = 0.0000001d;
            maxX = minX + exactSteps * (numHorizontalLabels - 1);
        }

        // human rounding to have nice numbers (1, 2, 5, ...)
        if (isHumanRoundingX()) {
            exactSteps = humanRound(exactSteps, true);
        } else if (mStepsHorizontal != null && mStepsHorizontal.size() > 1) {
            // else choose other nice steps that previous
            // steps are included (divide to have more, or multiplicate to have less)

            double d1 = 0, d2 = 0;
            int i = 0;
            for (Double v : mStepsHorizontal.values()) {
                if (i == 0) {
                    d1 = v;
                } else {
                    d2 = v;
                    break;
                }
                i++;
            }
            double oldSteps = d2 - d1;
            if (oldSteps > 0) {
                double newSteps = Double.NaN;

                if (oldSteps > exactSteps) {
                    newSteps = oldSteps / 2;
                } else if (oldSteps < exactSteps) {
                    newSteps = oldSteps * 2;
                }

                // only if there wont be more than numLabels
                // and newSteps will be better than oldSteps
                int numStepsOld = (int) ((maxX - minX) / oldSteps);
                int numStepsNew = (int) ((maxX - minX) / newSteps);

                boolean shouldChange;

                // avoid switching between 2 steps
                if (numStepsOld <= numHorizontalLabels && numStepsNew <= numHorizontalLabels) {
                    // both are possible
                    // only the new if it hows more labels
                    shouldChange = numStepsNew > numStepsOld;
                } else {
                    shouldChange = true;
                }

                if (newSteps != Double.NaN && shouldChange && numStepsNew <= numHorizontalLabels) {
                    exactSteps = newSteps;
                } else {
                    // try to stay to the old steps
                    exactSteps = oldSteps;
                }
            }
        } else {
            // first time
        }


        // starting from 1st datapoint
        // goal is to start with the minX or 1 step before
        newMinX = mGraphView.getViewport().getReferenceX();
        // must be down-rounded
        double count = Math.floor((minX-newMinX)/exactSteps);
        newMinX = count*exactSteps + newMinX;

        // now we have our labels bounds
        if (changeBounds) {
            mGraphView.getViewport().setMinX(newMinX);
            mGraphView.getViewport().setMaxX(newMinX + (numHorizontalLabels - 1) * exactSteps);
            mGraphView.getViewport().mXAxisBoundsStatus = Viewport.AxisBoundsStatus.AUTO_ADJUSTED;
        }

        // it can happen that we need to add some more labels to fill the complete screen
        numHorizontalLabels = (int) ((mGraphView.getViewport().mCurrentViewport.width() / exactSteps)) + 1;

        if (mStepsHorizontal != null) {
            mStepsHorizontal.clear();
        } else {
            mStepsHorizontal = new LinkedHashMap<>((int) numHorizontalLabels);
        }

        int width = mGraphView.getGraphContentWidth();
        // convert data-x to pixel-x in current viewport
        double pixelPerData = width / mGraphView.getViewport().mCurrentViewport.width();

        for (int i = 0; i < numHorizontalLabels; i++) {
            // dont draw if it is left of visible screen
            if (newMinX + (i * exactSteps) < mGraphView.getViewport().mCurrentViewport.left) {
                continue;
            }

            // where is the data point on the current screen
            double dataPointPos = newMinX + (i * exactSteps);
            double relativeToCurrentViewport = dataPointPos - mGraphView.getViewport().mCurrentViewport.left;

            double pixelPos = relativeToCurrentViewport * pixelPerData;
            mStepsHorizontal.put((int) pixelPos, dataPointPos);
        }

        return true;
    }

    /**
     * adjusts the grid and labels to match to the data
     * this will automatically change the bounds to
     * nice human-readable values, except the bounds
     * are manual.
     */
    protected void adjustSteps() {
        mIsAdjusted = adjustVertical(! Viewport.AxisBoundsStatus.FIX.equals(mGraphView.getViewport().mYAxisBoundsStatus));
        mIsAdjusted &= adjustVerticalSecondScale();
        mIsAdjusted &= adjustHorizontal(! Viewport.AxisBoundsStatus.FIX.equals(mGraphView.getViewport().mXAxisBoundsStatus));
    }

    /**
     * calculates the vertical label size
     * @param canvas canvas
     */
    protected void calcLabelVerticalSize(Canvas canvas) {
        // test label with first and last label
        String testLabel = mLabelFormatter.formatLabel(mGraphView.getViewport().getMaxY(false), false);
        if (testLabel == null) testLabel = "";

        Rect textBounds = new Rect();
        mPaintLabel.getTextBounds(testLabel, 0, testLabel.length(), textBounds);
        mLabelVerticalWidth = textBounds.width();
        mLabelVerticalHeight = textBounds.height();

        testLabel = mLabelFormatter.formatLabel(mGraphView.getViewport().getMinY(false), false);
        if (testLabel == null) testLabel = "";

        mPaintLabel.getTextBounds(testLabel, 0, testLabel.length(), textBounds);
        mLabelVerticalWidth = Math.max(mLabelVerticalWidth, textBounds.width());

        // add some pixel to get a margin
        mLabelVerticalWidth += 6;

        // space between text and graph content
        mLabelVerticalWidth += mStyles.horizontalLabelsSpace;

        // multiline
        int lines = 1;
        for (byte c : testLabel.getBytes()) {
            if (c == '\n') lines++;
        }
        mLabelVerticalHeight *= lines;
    }

    /**
     * calculates the vertical second scale
     * label size
     * @param canvas canvas
     */
    protected void calcLabelVerticalSecondScaleSize(Canvas canvas) {
        if (mGraphView.mSecondScale == null) {
            mLabelVerticalSecondScaleWidth = 0;
            mLabelVerticalSecondScaleHeight = 0;
            return;
        }

        // test label
        double testY = ((mGraphView.mSecondScale.getMaxY(false) - mGraphView.mSecondScale.getMinY(false)) * 0.783) + mGraphView.mSecondScale.getMinY(false);
        String testLabel = mGraphView.mSecondScale.getLabelFormatter().formatLabel(testY, false);
        Rect textBounds = new Rect();
        mPaintLabel.getTextBounds(testLabel, 0, testLabel.length(), textBounds);
        mLabelVerticalSecondScaleWidth = textBounds.width();
        mLabelVerticalSecondScaleHeight = textBounds.height();

        // multiline
        int lines = 1;
        for (byte c : testLabel.getBytes()) {
            if (c == '\n') lines++;
        }
        mLabelVerticalSecondScaleHeight *= lines;
    }

    /**
     * calculates the horizontal label size
     * @param canvas canvas
     */
    protected void calcLabelHorizontalSize(Canvas canvas) {
        // test label
        double testX = ((mGraphView.getViewport().getMaxX(false) - mGraphView.getViewport().getMinX(false)) * 0.783) + mGraphView.getViewport().getMinX(false);
        String testLabel = mLabelFormatter.formatLabel(testX, true);
        if (testLabel == null) {
            testLabel = "";
        }
        Rect textBounds = new Rect();
        mPaintLabel.getTextBounds(testLabel, 0, testLabel.length(), textBounds);
        mLabelHorizontalWidth = textBounds.width();

        if (!mLabelHorizontalHeightFixed) {
            mLabelHorizontalHeight = textBounds.height();

            // multiline
            int lines = 1;
            for (byte c : testLabel.getBytes()) {
                if (c == '\n') lines++;
            }
            mLabelHorizontalHeight *= lines;

            mLabelHorizontalHeight = (int) Math.max(mLabelHorizontalHeight, mStyles.textSize);
        }

        if (mStyles.horizontalLabelsAngle > 0f && mStyles.horizontalLabelsAngle <= 180f) {
            int adjHorizontalHeightH = (int) Math.round(Math.abs(mLabelHorizontalHeight*Math.cos(Math.toRadians(mStyles.horizontalLabelsAngle))));
            int adjHorizontalHeightW = (int) Math.round(Math.abs(mLabelHorizontalWidth*Math.sin(Math.toRadians(mStyles.horizontalLabelsAngle))));
            int adjHorizontalWidthH = (int) Math.round(Math.abs(mLabelHorizontalHeight*Math.sin(Math.toRadians(mStyles.horizontalLabelsAngle))));
            int adjHorizontalWidthW = (int) Math.round(Math.abs(mLabelHorizontalWidth*Math.cos(Math.toRadians(mStyles.horizontalLabelsAngle))));

            mLabelHorizontalHeight = adjHorizontalHeightH + adjHorizontalHeightW;
            mLabelHorizontalWidth = adjHorizontalWidthH + adjHorizontalWidthW;
        }

        // space between text and graph content
        mLabelHorizontalHeight += mStyles.verticalLabelsSpace;
    }

    /**
     * do the drawing of the grid
     * and labels
     * @param canvas canvas
     */
    public void draw(Canvas canvas, Context context) {
        boolean labelSizeChanged = false;
        if (mLabelHorizontalWidth == null) {
            calcLabelHorizontalSize(canvas);
            labelSizeChanged = true;
        }
        if (mLabelVerticalWidth == null) {
            calcLabelVerticalSize(canvas);
            labelSizeChanged = true;
        }
        if (mLabelVerticalSecondScaleWidth == null) {
            calcLabelVerticalSecondScaleSize(canvas);
            labelSizeChanged = true;
        }
        if (labelSizeChanged) {
            // redraw directly
            mGraphView.drawGraphElements(canvas);
            return;
        }

        if (!mIsAdjusted) {
            adjustSteps();
        }

        if (mIsAdjusted) {
            drawVerticalSteps(canvas, context);
            drawVerticalStepsSecondScale(canvas);
            drawHorizontalSteps(canvas);
        } else {
            // we can not draw anything
            return;
        }

        drawHorizontalAxisTitle(canvas);
        drawVerticalAxisTitle(canvas);

        // draw second scale axis title if it exists
        if (mGraphView.mSecondScale != null) {
            mGraphView.mSecondScale.drawVerticalAxisTitle(canvas);
        }
    }

    /**
     * draws the horizontal axis title if
     * it is set
     * @param canvas canvas
     */
    protected void drawHorizontalAxisTitle(Canvas canvas) {
        if (mHorizontalAxisTitle != null && mHorizontalAxisTitle.length() > 0) {
            mPaintAxisTitle.setColor(getHorizontalAxisTitleColor());
            mPaintAxisTitle.setTextSize(getHorizontalAxisTitleTextSize());
            float x = canvas.getWidth() / 2;
            float y = canvas.getHeight() - mStyles.verticalPadding;
            canvas.drawText(mHorizontalAxisTitle, x, y, mPaintAxisTitle);
        }
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
            float x = getVerticalAxisTitleWidth();
            float y = canvas.getHeight() / 2;
            canvas.save();
            canvas.rotate(-90, x, y);
            canvas.drawText(mVerticalAxisTitle, x, y, mPaintAxisTitle);
            canvas.restore();
        }
    }

    /**
     * @return  the horizontal axis title height
     *          or 0 if there is no title
     */
    public int getHorizontalAxisTitleHeight() {
        if (mHorizontalAxisTitle != null && mHorizontalAxisTitle.length() > 0) {
            return (int) getHorizontalAxisTitleTextSize();
        } else {
            return 0;
        }
    }

    /**
     * @return  the vertical axis title width
     *          or 0 if there is no title
     */
    public int getVerticalAxisTitleWidth() {
        if (mVerticalAxisTitle != null && mVerticalAxisTitle.length() > 0) {
            return (int) getVerticalAxisTitleTextSize();
        } else {
            return 0;
        }
    }

    /**
     * draws the horizontal steps
     * vertical lines and horizontal labels
     *
     * @param canvas canvas
     */
    protected void drawHorizontalSteps(Canvas canvas) {
        // draw horizontal steps (vertical lines and horizontal labels)
        mPaintLabel.setColor(getHorizontalLabelsColor());
        int i = 0;
        for (Map.Entry<Integer, Double> e : mStepsHorizontal.entrySet()) {
            // draw line
            if (mStyles.highlightZeroLines) {
                if (e.getValue() == 0d) {
                    mPaintLine.setStrokeWidth(3);
                    //mPaintLine.setColor(getVerticalLabelsColor());
                } else {
                    mPaintLine.setStrokeWidth(0);
                    //mPaintLine.setColor(getGridColor());
                }
            }
            if (mStyles.gridStyle.drawVertical()) {
                // dont draw if it is right of visible screen
                if (e.getKey() <= mGraphView.getGraphContentWidth()) {
                    int left=mGraphView.getGraphContentLeft();
                    canvas.drawLine(left+e.getKey(), mGraphView.getGraphContentTop(), mGraphView.getGraphContentLeft()+e.getKey(), mGraphView.getGraphContentTop() + mGraphView.getGraphContentHeight(), mPaintLine);
                }
            }

            // draw label
            if (isHorizontalLabelsVisible()) {
                if (mStyles.horizontalLabelsAngle > 0f && mStyles.horizontalLabelsAngle <= 180f) {
                    if (mStyles.horizontalLabelsAngle < 90f) {
                        mPaintLabel.setTextAlign((Paint.Align.RIGHT));
                    } else if (mStyles.horizontalLabelsAngle <= 180f) {
                        mPaintLabel.setTextAlign((Paint.Align.LEFT));
                    }
                } else {
                    mPaintLabel.setTextAlign(Paint.Align.CENTER);
                    if (i == mStepsHorizontal.size() - 1)
                        mPaintLabel.setTextAlign(Paint.Align.RIGHT);
                    if (i == 0)
                        mPaintLabel.setTextAlign(Paint.Align.LEFT);
                }

                // multiline labels
                String label = mLabelFormatter.formatLabel(e.getValue(), true);
                if (label == null) {
                    label = "";
                }
                String[] lines = label.split("\n");
                
                // If labels are angled, calculate adjustment to line them up with the grid
                int labelWidthAdj = 0;
                if (mStyles.horizontalLabelsAngle > 0f && mStyles.horizontalLabelsAngle <= 180f) {
                    Rect textBounds = new Rect();
                    mPaintLabel.getTextBounds(lines[0], 0, lines[0].length(), textBounds);
                    labelWidthAdj = (int) Math.abs(textBounds.width()*Math.cos(Math.toRadians(mStyles.horizontalLabelsAngle)));
                }
                for (int li = 0; li < lines.length; li++) {
                    // for the last line y = height
                    //y è l' altezza dei label orizzontali
                    float y = (canvas.getHeight() - mStyles.verticalPadding - getHorizontalAxisTitleHeight()) - (lines.length - li - 1) * getTextSize() * 1.1f + mStyles.verticalLabelsSpace;
                    float x = mGraphView.getGraphContentLeft()+e.getKey();
                    if(i==0 && x>getTextSize()/2){
                        Rect bounds=new Rect();
                        mPaintLabel.getTextBounds(lines[li], 0, lines[li].length(), bounds);
                        x-=bounds.width()/2;
                    }
                    if (mStyles.horizontalLabelsAngle > 0 && mStyles.horizontalLabelsAngle < 90f) {
                        canvas.save();
                        canvas.rotate(mStyles.horizontalLabelsAngle, x + labelWidthAdj, y);
                        canvas.drawText(lines[li], x + labelWidthAdj, y, mPaintLabel);
                        canvas.restore();
                    } else if (mStyles.horizontalLabelsAngle > 0 && mStyles.horizontalLabelsAngle <= 180f) {
                        canvas.save();
                        canvas.rotate(mStyles.horizontalLabelsAngle - 180f, x - labelWidthAdj, y);
                        canvas.drawText(lines[li], x - labelWidthAdj, y, mPaintLabel);
                        canvas.restore();
                    } else {
                        canvas.drawText(lines[li], x, y, mPaintLabel);
                    }
                }
            }
            i++;
        }
    }

    /**
     * draws the vertical steps for the
     * second scale on the right side
     *
     * @param canvas canvas
     */
    protected void drawVerticalStepsSecondScale(Canvas canvas) {
        if (mGraphView.mSecondScale == null) {
            return;
        }

        // draw only the vertical labels on the right
        float startLeft = mGraphView.getGraphContentLeft() + mGraphView.getGraphContentWidth();
        mPaintLabel.setColor(getVerticalLabelsSecondScaleColor());
        mPaintLabel.setTextAlign(getVerticalLabelsSecondScaleAlign());
        for (Map.Entry<Integer, Double> e : mStepsVerticalSecondScale.entrySet()) {
            float posY = mGraphView.getGraphContentTop()+mGraphView.getGraphContentHeight()-e.getKey();

            // draw label
            int labelsWidth = mLabelVerticalSecondScaleWidth;
            int labelsOffset = (int) startLeft;
            if (getVerticalLabelsSecondScaleAlign() == Paint.Align.RIGHT) {
                labelsOffset += labelsWidth;
            } else if (getVerticalLabelsSecondScaleAlign() == Paint.Align.CENTER) {
                labelsOffset += labelsWidth / 2;
            }

            float y = posY;

            String[] lines = mGraphView.mSecondScale.mLabelFormatter.formatLabel(e.getValue(), false).split("\n");
            y += (lines.length * getTextSize() * 1.1f) / 2; // center text vertically
            for (int li = 0; li < lines.length; li++) {
                // for the last line y = height
                float y2 = y - (lines.length - li - 1) * getTextSize() * 1.1f;
                canvas.drawText(lines[li], labelsOffset, y2, mPaintLabel);
            }
        }
    }

    /**
     * draws the vertical steps
     * horizontal lines and vertical labels
     *
     * @param canvas canvas
     */
    protected void drawVerticalSteps(Canvas canvas, Context context) {
        // draw vertical steps (horizontal lines and vertical labels)
        float startLeft = mGraphView.getGraphContentLeft();
        mPaintLabel.setColor(getVerticalLabelsColor());
        mPaintLabel.setTextAlign(getVerticalLabelsAlign());

        int numberOfLine = mStepsVertical.size();
        int currentLine = 1;

        for (Map.Entry<Integer, Double> e : mStepsVertical.entrySet()) {
            float posY = mGraphView.getGraphContentTop()+mGraphView.getGraphContentHeight()-e.getKey();

            // draw line (compresa x)
            if (mStyles.highlightZeroLines) {
                if (e.getValue() == 0d) {
                    mPaintLine.setStrokeWidth(3);
                    mPaintLine.setColor(GuiTools.getColor(context, R.color.light_gray));
                    if (mStyles.gridStyle.drawHorizontal()) {
                        canvas.drawLine(getHorizontalPadding(), posY, startLeft + mGraphView.getGraphContentWidth(), posY, mPaintLine);
                    }
                } else {
                    mPaintLine.setStrokeWidth(0);
                    mPaintLine.setColor(getGridColor());
                    if (mStyles.gridStyle.drawHorizontal()) {
                        canvas.drawLine(getHorizontalPadding(), posY, startLeft + mGraphView.getGraphContentWidth(), posY, mPaintLine);
                    }
                }
            }

            //if draw the label above or below the line, we mustn't draw the first for last label, for beautiful design.
            boolean isDrawLabel = true;
            if ((mStyles.verticalLabelsVAlign == VerticalLabelsVAlign.ABOVE && currentLine == numberOfLine)
                    || (mStyles.verticalLabelsVAlign == VerticalLabelsVAlign.BELOW && currentLine == 1)){
                isDrawLabel = false;
            }

            // draw label
            if (isVerticalLabelsVisible() && isDrawLabel) {
                int labelsWidth = mLabelVerticalWidth;
                int labelsOffset = 0;
                if (getVerticalLabelsAlign() == Paint.Align.RIGHT) {
                    labelsOffset = labelsWidth;
                    labelsOffset -= mStyles.horizontalLabelsSpace;
                } else if (getVerticalLabelsAlign() == Paint.Align.CENTER) {
                    labelsOffset = labelsWidth / 2;
                }
                labelsOffset += mStyles.horizontalPadding + getVerticalAxisTitleWidth();

                float y = posY;

                String label = mLabelFormatter.formatLabel(e.getValue(), false);
                if (label == null) {
                    label = "";
                }
                String[] lines = label.split("\n");
                switch (mStyles.verticalLabelsVAlign){
                    case MID:
                        y += (lines.length * getTextSize() * 1.1f) / 2; // center text vertically
                        break;
                    case ABOVE:
                        y -= 5;
                        break;
                    case BELOW:
                        y += (lines.length * getTextSize() * 1.1f) + 5;
                        break;
                }

                for (int li = 0; li < lines.length; li++) {
                    //if(!lines[li].equals("0")) {
                        // for the last line y = height
                        lines[li] = lines[li] + "$";
                        float y2 = y - (lines.length - li - 1) * getTextSize() * 1.1f;
                        canvas.drawText(lines[li], labelsOffset, y2, mPaintLabel);
                    //}
                }
            }

            currentLine ++;
        }
    }

    /**
     * this will do rounding to generate
     * nice human-readable bounds.
     *
     * @param in the raw value that is to be rounded
     * @param roundAlwaysUp true if it shall always round up (ceil)
     * @return the rounded number
     */
    protected double humanRound(double in, boolean roundAlwaysUp) {
        // round-up to 1-steps, 2-steps or 5-steps
        int ten = 0;
        while (Math.abs(in) >= 10d) {
            in /= 10d;
            ten++;
        }
        while (Math.abs(in) < 1d) {
            in *= 10d;
            ten--;
        }
        if (roundAlwaysUp) {
            in=Math.ceil(in);
        } else { // always round down
            in=Math.floor(in);
        }
        return in * Math.pow(10d, ten);
    }

    /**
     * @return the wrapped styles
     */
    public Styles getStyles() {
        return mStyles;
    }

    /**
     * @return  the vertical label width
     *          0 if there are no vertical labels
     */
    public int getLabelVerticalWidth() {
        return mLabelVerticalWidth == null || !isVerticalLabelsVisible() ? 0 : mLabelVerticalWidth;
    }

    /**
     * sets a manual and fixed with of the space for
     * the vertical labels. This will prevent GraphView to
     * calculate the width automatically.
     *
     * @param width     the width of the space for the vertical labels.
     *                  Use null to let GraphView automatically calculate the width.
     */
    public void setLabelVerticalWidth(Integer width) {
        mLabelVerticalWidth = width;
        mLabelVerticalWidthFixed = mLabelVerticalWidth != null;
    }

    /**
     * @return  the horizontal label height
     *          0 if there are no horizontal labels
     */
    public int getLabelHorizontalHeight() {
        return mLabelHorizontalHeight == null || !isHorizontalLabelsVisible() ? 0 : mLabelHorizontalHeight;
    }

    /**
     * sets a manual and fixed height of the space for
     * the horizontal labels. This will prevent GraphView to
     * calculate the height automatically.
     *
     * @param height     the height of the space for the horizontal labels.
     *                  Use null to let GraphView automatically calculate the height.
     */
    public void setLabelHorizontalHeight(Integer height) {
        mLabelHorizontalHeight = height;
        mLabelHorizontalHeightFixed = mLabelHorizontalHeight != null;
    }

    /**
     * @return the grid line color
     */
    public int getGridColor() {
        return mStyles.gridColor;
    }

    /**
     * @return whether the line at 0 are highlighted
     */
    public boolean isHighlightZeroLines() {
        return mStyles.highlightZeroLines;
    }

    /**
     * @return the padding around the grid and labels
     */
    public int getHorizontalPadding() {
        return mStyles.horizontalPadding;
    }

    /**
     * @return the padding around the grid and labels
     */
    public int getVerticalPadding() {
        return mStyles.verticalPadding;
    }

    /**
     * @param textSize  the general text size of the axis titles.
     *                  can be overwritten with {@link #setVerticalAxisTitleTextSize(float)}
     *                  and {@link #setHorizontalAxisTitleTextSize(float)}
     */
    public void setTextSize(float textSize) {
        mStyles.textSize = textSize;
        reloadStyles();
    }

    /**
     * @param verticalLabelsAlign the alignment of the vertical labels
     */
    public void setVerticalLabelsAlign(Paint.Align verticalLabelsAlign) {
        mStyles.verticalLabelsAlign = verticalLabelsAlign;
    }

    /**
     * @param verticalLabelsColor the color of the vertical labels
     */
    public void setVerticalLabelsColor(int verticalLabelsColor) {
        mStyles.verticalLabelsColor = verticalLabelsColor;
    }

    /**
     * @param horizontalLabelsColor the color of the horizontal labels
     */
    public void setHorizontalLabelsColor(int horizontalLabelsColor) {
        mStyles.horizontalLabelsColor = horizontalLabelsColor;
    }
    
    /**
     * @param horizontalLabelsAngle the angle of the horizontal labels in degrees
     */
    public void setHorizontalLabelsAngle(int horizontalLabelsAngle) {
        mStyles.horizontalLabelsAngle = horizontalLabelsAngle;
    }

    /**
     * @param gridColor the color of the grid lines
     */
    public void setGridColor(int gridColor) {
        mStyles.gridColor = gridColor;
        reloadStyles();
    }

    /**
     * @param highlightZeroLines    flag whether the zero-lines (vertical+
     *                              horizontal) shall be highlighted
     */
    public void setHighlightZeroLines(boolean highlightZeroLines) {
        mStyles.highlightZeroLines = highlightZeroLines;
    }

    /**
     * @param padding the padding around the graph and labels
     */
    public void setHorizontalPadding(int padding) {
        mStyles.horizontalPadding = padding;
    }

    /**
     * @param padding the padding around the graph and labels
     */
    public void setVerticalPadding(int padding) {
        mStyles.verticalPadding = padding;
    }

    /**
     * @return  the label formatter, that converts
     *          the raw numbers to strings
     */
    public LabelFormatter getLabelFormatter() {
        return mLabelFormatter;
    }

    /**
     * @param mLabelFormatter   the label formatter, that converts
     *                          the raw numbers to strings
     */
    public void setLabelFormatter(LabelFormatter mLabelFormatter) {
        this.mLabelFormatter = mLabelFormatter;
        mLabelFormatter.setViewport(mGraphView.getViewport());
    }

    /**
     * @return the title of the horizontal axis
     */
    public String getHorizontalAxisTitle() {
        return mHorizontalAxisTitle;
    }

    /**
     * @param mHorizontalAxisTitle the title of the horizontal axis
     */
    public void setHorizontalAxisTitle(String mHorizontalAxisTitle) {
        this.mHorizontalAxisTitle = mHorizontalAxisTitle;
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
        this.mVerticalAxisTitle = mVerticalAxisTitle;
    }

    /**
     * @return font size of the vertical axis title
     */
    public float getVerticalAxisTitleTextSize() {
        return mStyles.verticalAxisTitleTextSize;
    }

    /**
     * @param verticalAxisTitleTextSize font size of the vertical axis title
     */
    public void setVerticalAxisTitleTextSize(float verticalAxisTitleTextSize) {
        mStyles.verticalAxisTitleTextSize = verticalAxisTitleTextSize;
    }

    /**
     * @return font color of the vertical axis title
     */
    public int getVerticalAxisTitleColor() {
        return mStyles.verticalAxisTitleColor;
    }

    /**
     * @param verticalAxisTitleColor font color of the vertical axis title
     */
    public void setVerticalAxisTitleColor(int verticalAxisTitleColor) {
        mStyles.verticalAxisTitleColor = verticalAxisTitleColor;
    }

    /**
     * @return font size of the horizontal axis title
     */
    public float getHorizontalAxisTitleTextSize() {
        return mStyles.horizontalAxisTitleTextSize;
    }

    /**
     * @param horizontalAxisTitleTextSize font size of the horizontal axis title
     */
    public void setHorizontalAxisTitleTextSize(float horizontalAxisTitleTextSize) {
        mStyles.horizontalAxisTitleTextSize = horizontalAxisTitleTextSize;
    }

    /**
     * @return font color of the horizontal axis title
     */
    public int getHorizontalAxisTitleColor() {
        return mStyles.horizontalAxisTitleColor;
    }

    /**
     * @param horizontalAxisTitleColor font color of the horizontal axis title
     */
    public void setHorizontalAxisTitleColor(int horizontalAxisTitleColor) {
        mStyles.horizontalAxisTitleColor = horizontalAxisTitleColor;
    }

    /**
     * @return the alignment of the labels on the right side
     */
    public Paint.Align getVerticalLabelsSecondScaleAlign() {
        return mStyles.verticalLabelsSecondScaleAlign;
    }

    /**
     * @param verticalLabelsSecondScaleAlign the alignment of the labels on the right side
     */
    public void setVerticalLabelsSecondScaleAlign(Paint.Align verticalLabelsSecondScaleAlign) {
        mStyles.verticalLabelsSecondScaleAlign = verticalLabelsSecondScaleAlign;
    }

    /**
     * @return the color of the labels on the right side
     */
    public int getVerticalLabelsSecondScaleColor() {
        return mStyles.verticalLabelsSecondScaleColor;
    }

    /**
     * @param verticalLabelsSecondScaleColor the color of the labels on the right side
     */
    public void setVerticalLabelsSecondScaleColor(int verticalLabelsSecondScaleColor) {
        mStyles.verticalLabelsSecondScaleColor = verticalLabelsSecondScaleColor;
    }

    /**
     * @return  the width of the vertical labels
     *          of the second scale
     */
    public int getLabelVerticalSecondScaleWidth() {
        return mLabelVerticalSecondScaleWidth==null?0:mLabelVerticalSecondScaleWidth;
    }

    /**
     * @return  flag whether the horizontal labels are
     *          visible
     */
    public boolean isHorizontalLabelsVisible() {
        return mStyles.horizontalLabelsVisible;
    }

    /**
     * @param horizontalTitleVisible    flag whether the horizontal labels are
     *                                  visible
     */
    public void setHorizontalLabelsVisible(boolean horizontalTitleVisible) {
        mStyles.horizontalLabelsVisible = horizontalTitleVisible;
    }

    /**
     * @return  flag whether the vertical labels are
     *          visible
     */
    public boolean isVerticalLabelsVisible() {
        return mStyles.verticalLabelsVisible;
    }

    /**
     * @param verticalTitleVisible  flag whether the vertical labels are
     *                              visible
     */
    public void setVerticalLabelsVisible(boolean verticalTitleVisible) {
        mStyles.verticalLabelsVisible = verticalTitleVisible;
    }

    /**
     * @return  count of the vertical labels, that
     *          will be shown at one time.
     */
    public int getNumVerticalLabels() {
        return mNumVerticalLabels;
    }

    /**
     * @param mNumVerticalLabels    count of the vertical labels, that
     *                              will be shown at one time.
     */
    public void setNumVerticalLabels(int mNumVerticalLabels) {
        this.mNumVerticalLabels = mNumVerticalLabels;
    }

    /**
     * @return  count of the horizontal labels, that
     *          will be shown at one time.
     */
    public int getNumHorizontalLabels() {
        return mNumHorizontalLabels;
    }

    /**
     * @param mNumHorizontalLabels  count of the horizontal labels, that
     *                              will be shown at one time.
     */
    public void setNumHorizontalLabels(int mNumHorizontalLabels) {
        this.mNumHorizontalLabels = mNumHorizontalLabels;
    }

    /**
     * @return the grid style
     */
    public GridStyle getGridStyle() {
        return mStyles.gridStyle;
    }

    /**
     * Define which grid lines shall be drawn
     *
     * @param gridStyle the grid style
     */
    public void setGridStyle(GridStyle gridStyle) {
        mStyles.gridStyle = gridStyle;
    }

    /**
     * @return the space between the labels text and the graph content
     */
    public int getVerticalLabelsSpace() {
        return mStyles.verticalLabelsSpace;
    }

    /**
     * @return the space between the labels text and the graph content
     */
    public int getHorizontalLabelsSpace() {
        return mStyles.horizontalLabelsSpace;
    }

    /**
     * the space between the labels text and the graph content
     *
     * @param labelsSpace the space between the labels text and the graph content
     */
    public void setVerticalLabelsSpace(int labelsSpace) {
        mStyles.verticalLabelsSpace = labelsSpace;
    }

    /**
     * the space between the labels text and the graph content
     *
     * @param labelsSpace the space between the labels text and the graph content
     */
    public void setHorizontalLabelsSpace(int labelsSpace) {
        mStyles.horizontalLabelsSpace = labelsSpace;
    }


    /**
     * set horizontal label align
     * @param align
     */
    public void setVerticalLabelsVAlign(VerticalLabelsVAlign align){
        mStyles.verticalLabelsVAlign = align;
    }

    /**
     * Get horizontal label align
     * @return align
     */
    public VerticalLabelsVAlign getVerticalLabelsVAlign(){
        return mStyles.verticalLabelsVAlign;
    }
}
