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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import nie.translator.rtranslatordevedition.tools.gui.graph.series.BaseSeries;
import nie.translator.rtranslatordevedition.tools.gui.graph.series.Series;

/**
 * @author jjoe64
 */
public class GraphView extends View {
    /**
     * Class to wrap style options that are general
     * to graphs.
     *
     * @author jjoe64
     */
    private static final class Styles {
        /**
         * The font size of the title that can be displayed
         * above the graph.
         *
         * @see GraphView#setTitle(String)
         */
        float titleTextSize;

        /**
         * The font color of the title that can be displayed
         * above the graph.
         *
         * @see GraphView#setTitle(String)
         */
        int titleColor;
    }

    /**
     * Helper class to detect tap events on the
     * graph.
     *
     * @author jjoe64
     */
    private class TapDetector {
        /**
         * save the time of the last down event
         */
        private long lastDown;

        /**
         * point of the tap down event
         */
        private PointF lastPoint;

        /**
         * to be called to process the events
         *
         * @param event
         * @return true if there was a tap event. otherwise returns false.
         */
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                lastDown = System.currentTimeMillis();
                lastPoint = new PointF(event.getX(), event.getY());
            } else if (lastDown > 0 && event.getAction() == MotionEvent.ACTION_MOVE) {
                if (Math.abs(event.getX() - lastPoint.x) > 60
                        || Math.abs(event.getY() - lastPoint.y) > 60) {
                    lastDown = 0;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (System.currentTimeMillis() - lastDown < 400) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * our series (this does not contain the series
     * that can be displayed on the right side. The
     * right side series is a special feature of
     * the {@link SecondScale} feature.
     */
    private List<Series> mSeries;

    /**
     * the renderer for the grid and labels
     */
    private GridLabelRenderer mGridLabelRenderer;

    /**
     * viewport that holds the current bounds of
     * view.
     */
    private Viewport mViewport;

    /**
     * title of the graph that will be shown above
     */
    private String mTitle;

    /**
     * wraps the general styles
     */
    private Styles mStyles;

    /**
     * feature to have a second scale e.g. on the
     * right side
     */
    protected SecondScale mSecondScale;

    /**
     * tap detector
     */
    private TapDetector mTapDetector;

    /**
     * renderer for the legend
     */
    private LegendRenderer mLegendRenderer;

    /**
     * paint for the graph title
     */
    private Paint mPaintTitle;

    private boolean mIsCursorMode;

    /**
     * paint for the preview (in the SDK)
     */
    private Paint mPreviewPaint;

    private CursorMode mCursorMode;

    /**
     * Initialize the GraphView view
     * @param context
     */
    public GraphView(Context context) {
        super(context);
        init();
    }

    /**
     * Initialize the GraphView view.
     *
     * @param context
     * @param attrs
     */
    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Initialize the GraphView view
     *
     * @param context
     * @param attrs
     * @param defStyle
     */
    public GraphView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    /**
     * create the internal objects.
     * This method has to be called directly
     * in the constructors.
     */
    protected void init() {
        mPreviewPaint = new Paint();
        mPreviewPaint.setTextAlign(Paint.Align.CENTER);
        mPreviewPaint.setColor(Color.BLACK);
        mPreviewPaint.setTextSize(50);

        mStyles = new Styles();
        mViewport = new Viewport(this);
        mGridLabelRenderer = new GridLabelRenderer(this);
        mLegendRenderer = new LegendRenderer(this);

        mSeries = new ArrayList<Series>();
        mPaintTitle = new Paint();

        mTapDetector = new TapDetector();

        loadStyles();
    }

    /**
     * loads the font
     */
    protected void loadStyles() {
        mStyles.titleColor = mGridLabelRenderer.getHorizontalLabelsColor();
        mStyles.titleTextSize = mGridLabelRenderer.getTextSize();
    }

    /**
     * @return the renderer for the grid and labels
     */
    public GridLabelRenderer getGridLabelRenderer() {
        return mGridLabelRenderer;
    }

    /**
     * Add a new series to the graph. This will
     * automatically redraw the graph.
     * @param s the series to be added
     */
    public void addSeries(Series s) {
        s.onGraphViewAttached(this);
        mSeries.add(s);
        onDataChanged(false, false);
    }

    /**
     * important: do not do modifications on the list
     * object that will be returned.
     * Use {@link #removeSeries(Series)} and {@link #addSeries(Series)}
     *
     * @return all series
     */
    public List<Series> getSeries() {
        // TODO immutable array
        return mSeries;
    }

    /**
     * call this to let the graph redraw and
     * recalculate the viewport.
     * This will be called when a new series
     * was added or removed and when data
     * was appended via {@link com.google.cloud.android.speech.tools.gui.graph.series.BaseSeries#appendData(com.google.cloud.android.speech.tools.gui.graph.series.DataPointInterface, boolean, int)}
     * or {@link com.google.cloud.android.speech.tools.gui.graph.series.BaseSeries#resetData(com.google.cloud.android.speech.tools.gui.graph.series.DataPointInterface[])}.
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
    public void onDataChanged(boolean keepLabelsSize, boolean keepViewport) {
        // adjustSteps grid system
        mViewport.calcCompleteRange();
        if (mSecondScale != null) {
            mSecondScale.calcCompleteRange();
        }
        mGridLabelRenderer.invalidate(keepLabelsSize, keepViewport);
        postInvalidate();
    }

    /**
     * draw all the stuff on canvas
     *
     * @param canvas
     */
    protected void drawGraphElements(Canvas canvas) {
        // must be in hardware accelerated mode
        if (android.os.Build.VERSION.SDK_INT >= 11 && !canvas.isHardwareAccelerated()) {
            // just warn about it, because it is ok when making a snapshot
            Log.w("GraphView", "GraphView should be used in hardware accelerated mode." +
                    "You can use android:hardwareAccelerated=\"true\" on your activity. Read this for more info:" +
                    "https://developer.android.com/guide/topics/graphics/hardware-accel.html");
        }

        drawTitle(canvas);
        mViewport.drawFirst(canvas);
        mGridLabelRenderer.draw(canvas, getContext());
        for (Series s : mSeries) {
            s.draw(this, canvas, false);
        }
        if (mSecondScale != null) {
            for (Series s : mSecondScale.getSeries()) {
                s.draw(this, canvas, true);
            }
        }

        if (mCursorMode != null) {
            mCursorMode.draw(canvas);
        }

        mViewport.draw(canvas);
        mLegendRenderer.draw(canvas);
    }

    /**
     * will be called from Android system.
     *
     * @param canvas Canvas
     */
    @Override
    protected void onDraw(Canvas canvas) {
        if (isInEditMode()) {
            canvas.drawColor(Color.rgb(200, 200, 200));
            canvas.drawText("GraphView: No Preview available", canvas.getWidth()/2, canvas.getHeight()/2, mPreviewPaint);
        } else {
            drawGraphElements(canvas);
        }
    }

    /**
     * Draws the Graphs title that will be
     * shown above the viewport.
     * Will be called by GraphView.
     *
     * @param canvas Canvas
     */
    protected void drawTitle(Canvas canvas) {
        if (mTitle != null && mTitle.length()>0) {
            mPaintTitle.setColor(mStyles.titleColor);
            mPaintTitle.setTextSize(mStyles.titleTextSize);
            mPaintTitle.setTextAlign(Paint.Align.CENTER);
            float x = canvas.getWidth()/2;
            float y = mPaintTitle.getTextSize();
            canvas.drawText(mTitle, x, y, mPaintTitle);
        }
    }

    /**
     * Calculates the height of the title.
     *
     * @return  the actual size of the title.
     *          if there is no title, 0 will be
     *          returned.
     */
    protected int getTitleHeight() {
        if (mTitle != null && mTitle.length()>0) {
            return (int) mPaintTitle.getTextSize();
        } else {
            return 0;
        }
    }

    /**
     * @return the viewport of the Graph.
     * @see Viewport
     */
    public Viewport getViewport() {
        return mViewport;
    }

    /**
     * Called by Android system if the size
     * of the view was changed. Will recalculate
     * the viewport and labels.
     *
     * @param w
     * @param h
     * @param oldw
     * @param oldh
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        onDataChanged(false, false);
    }

    /**
     * @return  the space on the left side of the
     *          view from the left border to the
     *          beginning of the graph viewport.
     */
    public int getGraphContentLeft() {
        int border = getGridLabelRenderer().getStyles().horizontalPadding;
        int verticalLabelWidth=getGridLabelRenderer().getLabelVerticalWidth();
        int verticalTitleWidth=getGridLabelRenderer().getVerticalAxisTitleWidth();
        return border + verticalLabelWidth+verticalTitleWidth;
    }

    /**
     * @return  the space on the top of the
     *          view from the top border to the
     *          beginning of the graph viewport.
     */
    public int getGraphContentTop() {
        int border = getGridLabelRenderer().getStyles().verticalPadding + getTitleHeight();
        return border;
    }

    /**
     * @return  the height of the graph viewport.
     */
    public int getGraphContentHeight() {
        int border = getGridLabelRenderer().getStyles().verticalPadding;
        int graphheight = getHeight() - (2 * border) - getGridLabelRenderer().getLabelHorizontalHeight() - getTitleHeight();
        graphheight -= getGridLabelRenderer().getHorizontalAxisTitleHeight();
        return graphheight;
    }

    /**
     * @return  the width of the graph viewport.
     */
    public int getGraphContentWidth() {
        int border = getGridLabelRenderer().getStyles().horizontalPadding;
        int graphwidth = getWidth() - (2 * border) - getGridLabelRenderer().getLabelVerticalWidth();
        if (mSecondScale != null) {
            graphwidth -= getGridLabelRenderer().getLabelVerticalSecondScaleWidth();
            graphwidth -= mSecondScale.getVerticalAxisTitleTextSize();
        }
        return graphwidth;
    }

    /**
     * will be called from Android system.
     *
     * @param event
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean b = mViewport.onTouchEvent(event);
        boolean a = super.onTouchEvent(event);

        // is it a click?
        if (mTapDetector.onTouchEvent(event)) {
            for (Series s : mSeries) {
                s.onTap(event.getX(), event.getY());
            }
            if (mSecondScale != null) {
                for (Series s : mSecondScale.getSeries()) {
                    s.onTap(event.getX(), event.getY());
                }
            }
        }

        return b || a;
    }

    /**
     *
     */
    @Override
    public void computeScroll() {
        super.computeScroll();
        mViewport.computeScroll();
    }

    /**
     * @return the legend renderer.
     * @see LegendRenderer
     */
    public LegendRenderer getLegendRenderer() {
        return mLegendRenderer;
    }

    /**
     * use a specific legend renderer
     *
     * @param mLegendRenderer the new legend renderer
     */
    public void setLegendRenderer(LegendRenderer mLegendRenderer) {
        this.mLegendRenderer = mLegendRenderer;
    }

    /**
     * @return  the title that will be shown
     *          above the graph.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Set the title of the graph that will
     * be shown above the graph's viewport.
     *
     * @param mTitle the title
     * @see #setTitleColor(int) to set the font color
     * @see #setTitleTextSize(float) to set the font size
     */
    public void setTitle(String mTitle) {
        this.mTitle = mTitle;
    }

    /**
     * @return the title font size
     */
    public float getTitleTextSize() {
        return mStyles.titleTextSize;
    }

    /**
     * Set the title's font size
     *
     * @param titleTextSize font size
     * @see #setTitle(String)
     */
    public void setTitleTextSize(float titleTextSize) {
        mStyles.titleTextSize = titleTextSize;
    }

    /**
     * @return font color of the title
     */
    public int getTitleColor() {
        return mStyles.titleColor;
    }

    /**
     * Set the title's font color
     *
     * @param titleColor font color of the title
     * @see #setTitle(String)
     */
    public void setTitleColor(int titleColor) {
        mStyles.titleColor = titleColor;
    }

    /**
     * creates the second scale logic and returns it
     *
     * @return second scale object
     */
    public SecondScale getSecondScale() {
        if (mSecondScale == null) {
            // this creates the second scale
            mSecondScale = new SecondScale(this);
            mSecondScale.setVerticalAxisTitleTextSize(mGridLabelRenderer.mStyles.textSize);
        }
        return mSecondScale;
    }

    /**
     * clears the second scale
     */
    public void clearSecondScale() {
        if (mSecondScale != null) {
            mSecondScale.removeAllSeries();
            mSecondScale = null;
        }
    }

    /**
     * Removes all series of the graph.
     */
    public void removeAllSeries() {
        mSeries.clear();
        onDataChanged(false, false);
    }

    /**
     * Remove a specific series of the graph.
     * This will also re-draw the graph, but
     * without recalculating the viewport and
     * label sizes.
     * If you want this, you have to call {@link #onDataChanged(boolean, boolean)}
     * manually.
     *
     * @param series
     */
    public void removeSeries(Series<?> series) {
        mSeries.remove(series);
        onDataChanged(false, false);
    }

    /**
     * takes a snapshot and return it as bitmap
     *
     * @return snapshot of graph
     */
    public Bitmap takeSnapshot() {
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        draw(canvas);
        return bitmap;
    }

    /**
     * takes a snapshot, stores it and open the share dialog.
     * Notice that you need the permission android.permission.WRITE_EXTERNAL_STORAGE
     *
     * @param context
     * @param imageName
     * @param title
     */
    public void takeSnapshotAndShare(Context context, String imageName, String title) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Bitmap inImage = takeSnapshot();
        inImage.compress(Bitmap.CompressFormat.PNG, 100, bytes);

        String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), inImage, imageName, null);
        if (path == null) {
            // most likely a security problem
            throw new SecurityException("Could not get path from MediaStore. Please check permissions.");
        }

        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_STREAM, Uri.parse(path));
        try {
            context.startActivity(Intent.createChooser(i, title));
        } catch (android.content.ActivityNotFoundException ex) {
            ex.printStackTrace();
        }
    }

    public void setCursorMode(boolean b) {
        mIsCursorMode = b;
        if (mIsCursorMode) {
            if (mCursorMode == null) {
                mCursorMode = new CursorMode(this);
            }
        } else {
            mCursorMode = null;
            invalidate();
        }
        for (Series series : mSeries) {
            if (series instanceof BaseSeries) {
                ((BaseSeries) series).clearCursorModeCache();
            }
        }
    }

    public CursorMode getCursorMode() {
        return mCursorMode;
    }

    public boolean isCursorMode() {
        return mIsCursorMode;
    }
}
