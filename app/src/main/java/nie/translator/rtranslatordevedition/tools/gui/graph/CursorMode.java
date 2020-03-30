package nie.translator.rtranslatordevedition.tools.gui.graph;

import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Map;

import nie.translator.rtranslatordevedition.tools.gui.graph.series.BaseSeries;
import nie.translator.rtranslatordevedition.tools.gui.graph.series.DataPointInterface;
import nie.translator.rtranslatordevedition.tools.gui.graph.series.Series;

/**
 * Created by jonas on 22/02/2017.
 */

public class CursorMode {
    private final static class Styles {
        public float textSize;
        public int spacing;
        public int padding;
        public int width;
        public int backgroundColor;
        public int margin;
        public int textColor;
    }

    protected final Paint mPaintLine;
    protected final GraphView mGraphView;
    protected float mPosX;
    protected float mPosY;
    protected boolean mCursorVisible;
    protected final Map<BaseSeries, DataPointInterface> mCurrentSelection;
    protected final Paint mRectPaint;
    protected final Paint mTextPaint;
    protected double mCurrentSelectionX;
    protected Styles mStyles;
    protected int cachedLegendWidth;

    public CursorMode(GraphView graphView) {
        mStyles = new Styles();
        mGraphView = graphView;
        mPaintLine = new Paint();
        mPaintLine.setColor(Color.argb(128, 180, 180, 180));
        mPaintLine.setStrokeWidth(10f);
        mCurrentSelection = new HashMap<>();
        mRectPaint = new Paint();
        mTextPaint = new Paint();
        resetStyles();
    }

    /**
     * resets the styles to the defaults
     * and clears the legend width cache
     */
    public void resetStyles() {
        mStyles.textSize = mGraphView.getGridLabelRenderer().getTextSize();
        mStyles.spacing = (int) (mStyles.textSize / 5);
        mStyles.padding = (int) (mStyles.textSize / 2);
        mStyles.width = 0;
        mStyles.backgroundColor = Color.argb(180, 100, 100, 100);
        mStyles.margin = (int) (mStyles.textSize);

        // get matching styles from theme
        TypedValue typedValue = new TypedValue();
        mGraphView.getContext().getTheme().resolveAttribute(android.R.attr.textAppearanceSmall, typedValue, true);

        int color1;

        try {
            TypedArray array = mGraphView.getContext().obtainStyledAttributes(typedValue.data, new int[]{
                    android.R.attr.textColorPrimary});
            color1 = array.getColor(0, Color.BLACK);
            array.recycle();
        } catch (Exception e) {
            color1 = Color.BLACK;
        }

        mStyles.textColor = color1;

        cachedLegendWidth = 0;
    }


    public void onDown(MotionEvent e) {
        mPosX = Math.max(e.getX(), mGraphView.getGraphContentLeft());
        mPosX = Math.min(mPosX, mGraphView.getGraphContentLeft() + mGraphView.getGraphContentWidth());
        mPosY = e.getY();
        mCursorVisible = true;
        findCurrentDataPoint();
        mGraphView.invalidate();
    }

    public void onMove(MotionEvent e) {
        if (mCursorVisible) {
            mPosX = Math.max(e.getX(), mGraphView.getGraphContentLeft());
            mPosX = Math.min(mPosX, mGraphView.getGraphContentLeft() + mGraphView.getGraphContentWidth());
            mPosY = e.getY();
            findCurrentDataPoint();
            mGraphView.invalidate();
        }
    }

    public void draw(Canvas canvas) {
        if (mCursorVisible) {
            canvas.drawLine(mPosX, 0, mPosX, canvas.getHeight(), mPaintLine);
        }

        // selection
        for (Map.Entry<BaseSeries, DataPointInterface> entry : mCurrentSelection.entrySet()) {
            entry.getKey().drawSelection(mGraphView, canvas, false, entry.getValue());
        }

        if (!mCurrentSelection.isEmpty()) {
            drawLegend(canvas);
        }
    }

    protected String getTextForSeries(Series s, DataPointInterface value) {
        StringBuffer txt = new StringBuffer();
        if (s.getTitle() != null) {
            txt.append(s.getTitle());
            txt.append(": ");
        }
        txt.append(mGraphView.getGridLabelRenderer().getLabelFormatter().formatLabel(value.getY(), false));
        return txt.toString();
    }

    protected void drawLegend(Canvas canvas) {
        mTextPaint.setTextSize(mStyles.textSize);
        mTextPaint.setColor(mStyles.textColor);

        int shapeSize = (int) (mStyles.textSize*0.8d);

        // width
        int legendWidth = mStyles.width;
        if (legendWidth == 0) {
            // auto
            legendWidth = cachedLegendWidth;

            if (legendWidth == 0) {
                Rect textBounds = new Rect();
                for (Map.Entry<BaseSeries, DataPointInterface> entry : mCurrentSelection.entrySet()) {
                    String txt = getTextForSeries(entry.getKey(), entry.getValue());
                    mTextPaint.getTextBounds(txt, 0, txt.length(), textBounds);
                    legendWidth = Math.max(legendWidth, textBounds.width());
                }
                if (legendWidth == 0) legendWidth = 1;

                // add shape size
                legendWidth += shapeSize+mStyles.padding*2 + mStyles.spacing;
                cachedLegendWidth = legendWidth;
            }
        }

        float legendPosX = mPosX - mStyles.margin - legendWidth;
        if (legendPosX < 0) {
            legendPosX = 0;
        }

        // rect
        float legendHeight = (mStyles.textSize+mStyles.spacing) * (mCurrentSelection.size() + 1) -mStyles.spacing;

        float legendPosY = mPosY - legendHeight - 4.5f * mStyles.textSize;
        if (legendPosY < 0) {
            legendPosY = 0;
        }

        float lLeft;
        float lTop;
        lLeft = legendPosX;
        lTop = legendPosY;

        float lRight = lLeft+legendWidth;
        float lBottom = lTop+legendHeight+2*mStyles.padding;
        mRectPaint.setColor(mStyles.backgroundColor);
        canvas.drawRoundRect(new RectF(lLeft, lTop, lRight, lBottom), 8, 8, mRectPaint);

        mTextPaint.setFakeBoldText(true);
        canvas.drawText(mGraphView.getGridLabelRenderer().getLabelFormatter().formatLabel(mCurrentSelectionX, true), lLeft+mStyles.padding, lTop+mStyles.padding/2+mStyles.textSize, mTextPaint);

        mTextPaint.setFakeBoldText(false);

        int i=1;
        for (Map.Entry<BaseSeries, DataPointInterface> entry : mCurrentSelection.entrySet()) {
            mRectPaint.setColor(entry.getKey().getColor());
            canvas.drawRect(new RectF(lLeft+mStyles.padding, lTop+mStyles.padding+(i*(mStyles.textSize+mStyles.spacing)), lLeft+mStyles.padding+shapeSize, lTop+mStyles.padding+(i*(mStyles.textSize+mStyles.spacing))+shapeSize), mRectPaint);
            canvas.drawText(getTextForSeries(entry.getKey(), entry.getValue()), lLeft+mStyles.padding+shapeSize+mStyles.spacing, lTop+mStyles.padding/2+mStyles.textSize+(i*(mStyles.textSize+mStyles.spacing)), mTextPaint);
            i++;
        }
    }

    public boolean onUp(MotionEvent event) {
        mCursorVisible = false;
        findCurrentDataPoint();
        mGraphView.invalidate();
        return true;
    }

    private void findCurrentDataPoint() {
        double selX = 0;
        mCurrentSelection.clear();
        for (Series series : mGraphView.getSeries()) {
            if (series instanceof BaseSeries) {
                DataPointInterface p = ((BaseSeries) series).findDataPointAtX(mPosX);
                if (p != null) {
                    selX = p.getX();
                    mCurrentSelection.put((BaseSeries) series, p);
                }
            }
        }

        if (!mCurrentSelection.isEmpty()) {
            mCurrentSelectionX = selX;
        }
    }

    public void setTextSize(float t) {
        mStyles.textSize = t;
    }

    public void setTextColor(int color) {
        mStyles.textColor = color;
    }

    public void setBackgroundColor(int color) {
        mStyles.backgroundColor = color;
    }

    public void setSpacing(int s) {
        mStyles.spacing = s;
    }

    public void setPadding(int s) {
        mStyles.padding = s;
    }

    public void setMargin(int s) {
        mStyles.margin = s;
    }

    public void setWidth(int s) {
        mStyles.width = s;
    }
}
