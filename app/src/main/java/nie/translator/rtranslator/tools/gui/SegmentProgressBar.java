package nie.translator.rtranslator.tools.gui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import nie.translator.rtranslator.R;

public class SegmentProgressBar extends View {

    public static final float DEFAULT_MAX_PROGRESS = 100.0F;
    public static final int DEFAULT_CORNERS = 6;
    private static final int PROGRESS_BAR_BG_COLOR = Color.parseColor("#333333");

    private float progressWidth = 0F;
    private float maxProgress = 0F;
    private float cornerRadius = (float) DEFAULT_CORNERS;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF progressRectF = new RectF();
    private final Path path = new Path();

    private int bgProgressBar = PROGRESS_BAR_BG_COLOR;

    // Dynamic segments list
    private final List<Segment> segments = new ArrayList<>();

    private float redLineValue = -1;
    private float orangeLineValue = -1;

    @Nullable
    private SegmentChangeListener segmentChangeListener;


    public SegmentProgressBar(Context context) {
        this(context, null);
    }

    public SegmentProgressBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SegmentProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initAttributes(context, attrs);
    }

    private void initAttributes(Context context, AttributeSet attrs) {
        TypedArray t = context.obtainStyledAttributes(attrs, R.styleable.SegmentProgressBar, 0, 0);

        bgProgressBar = t.getColor(R.styleable.SegmentProgressBar_backgroundColor, PROGRESS_BAR_BG_COLOR);
        cornerRadius = (float) t.getDimensionPixelSize(R.styleable.SegmentProgressBar_cornerRadius, DEFAULT_CORNERS);
        maxProgress = t.getFloat(R.styleable.SegmentProgressBar_maxProgress, DEFAULT_MAX_PROGRESS);

        // Fetch the array resource IDs
        int colorsResId = t.getResourceId(R.styleable.SegmentProgressBar_segmentColors, 0);
        int progressesResId = t.getResourceId(R.styleable.SegmentProgressBar_segmentProgresses, 0);

        if (colorsResId != 0 && progressesResId != 0) {
            loadSegmentsFromArrays(colorsResId, progressesResId);
        }

        t.recycle();
    }

    private void loadSegmentsFromArrays(int colorsResId, int progressesResId) {
        try {
            // getIntArray automatically resolves @color/... to actual color ints
            int[] colorsArr = getResources().getIntArray(colorsResId);

            // We use string array for progress to support float decimals (e.g., "30.5")
            String[] progressesArr = getResources().getStringArray(progressesResId);

            int count = Math.min(colorsArr.length, progressesArr.length);
            for (int i = 0; i < count; i++) {
                int color = colorsArr[i];
                float progress = Float.parseFloat(progressesArr[i].trim());
                segments.add(new Segment(progress, color));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        progressWidth = (float) getWidth();
    }

    // --- Dynamic Segment Methods ---

    /**
     * Adds a new segment to the end of the progress bar.
     */
    public void addSegment(float progress, int color) {
        segments.add(new Segment(progress, color));
        invalidate();
    }

    /**
     * Removes a segment at a specific index.
     */
    public void removeSegment(int index) {
        if (index >= 0 && index < segments.size()) {
            segments.remove(index);
            invalidate();
        }
    }

    /**
     * Clears all segments.
     */
    public void clearSegments() {
        segments.clear();
        invalidate();
    }

    /**
     * Updates the progress of a specific segment. (todo: add the option to animate this change in the future)
     */
    public void setSegmentProgress(int index, float progress) {
        if (index >= 0 && index < segments.size()) {
            segments.get(index).progress = progress;
            invalidate();
            if(segmentChangeListener != null) segmentChangeListener.onSegmentProgressChanged();
        }
    }

    public void setSegmentProgressChangeListener(SegmentChangeListener listener){
        segmentChangeListener = listener;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public void setRedLine(float value){
        redLineValue = value;
        invalidate();
    }

    public void setOrangeLine(float value){
        orangeLineValue = value;
        invalidate();
    }

    public void setMax(float max) {
        maxProgress = max;
        invalidate();
    }

    public void setCornerRadius(float cornerRadius) {
        this.cornerRadius = cornerRadius;
        invalidate();
    }

    public float getRedLineValue() {
        return redLineValue;
    }

    public float getOrangeLineValue() {
        return orangeLineValue;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw Background
        paint.setColor(bgProgressBar);
        drawRoundRect(
                canvas,
                0F, 0F, progressWidth, (float) getHeight(),
                cornerRadius, cornerRadius, cornerRadius, cornerRadius,
                paint
        );

        if (segments.isEmpty() || maxProgress <= 0) return;

        // Draw Segments
        float currentOffset = 0F;

        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (segment.progress <= 0) continue;

            paint.setColor(segment.color);
            float segmentWidth = (progressWidth / maxProgress) * segment.progress;

            // Determine corner radii based on position
            float leftRadius = (i == 0) ? cornerRadius : 0F;
            float rightRadius = 0F;

            drawRoundRect(
                    canvas,
                    currentOffset,
                    0F,
                    currentOffset + segmentWidth,
                    (float) getHeight(),
                    leftRadius,
                    rightRadius,
                    leftRadius,
                    rightRadius,
                    paint
            );

            currentOffset += segmentWidth;
        }

        // Draw redLine
        if(redLineValue >= 0){
            paint.setColor(getResources().getColor(R.color.red));
            float redLineWidth = (progressWidth / maxProgress) * 0.5F;
            float segmentHorizontalPosition = (progressWidth / maxProgress) * redLineValue;
            drawRoundRect(
                    canvas,
                    segmentHorizontalPosition,
                    0F,
                    segmentHorizontalPosition + redLineWidth,
                    (float) getHeight(),
                    0F,
                    0F,
                    0F,
                    0F,
                    paint
            );
        }

        // Draw orangeLine
        if(orangeLineValue >= 0){
            paint.setColor(getResources().getColor(R.color.orange));
            float orangeLineWidth = (progressWidth / maxProgress) * 0.5F;
            float segmentHorizontalPosition = (progressWidth / maxProgress) * orangeLineValue;
            drawRoundRect(
                    canvas,
                    segmentHorizontalPosition,
                    0F,
                    segmentHorizontalPosition + orangeLineWidth,
                    (float) getHeight(),
                    0F,
                    0F,
                    0F,
                    0F,
                    paint
            );
        }
    }

    private void drawRoundRect(
            Canvas canvas, float left, float top, float right, float bottom,
            float topLeftRadius, float topRightRadius, float bottomLeftRadius, float bottomRightRadius,
            Paint paint
    ) {
        float[] radiusArr = new float[]{
                topLeftRadius, topLeftRadius,
                topRightRadius, topRightRadius,
                bottomRightRadius, bottomRightRadius,
                bottomLeftRadius, bottomLeftRadius
        };

        path.reset();
        progressRectF.set(left, top, right, bottom);
        path.addRoundRect(progressRectF, radiusArr, Path.Direction.CW);
        path.close();
        canvas.drawPath(path, paint);
    }

    public static abstract class SegmentChangeListener {
        public abstract void onSegmentProgressChanged();
    }

    // --- Inner Class for Segment Data ---
    public static class Segment {
        public float progress;
        public int color;

        public Segment(float progress, int color) {
            this.progress = progress;
            this.color = color;
        }
    }
}