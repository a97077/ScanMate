package com.example.scanmate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class CropOverlayView extends View {

    private static final int HANDLE_NONE = 0;
    private static final int HANDLE_MOVE = 1;
    private static final int HANDLE_LEFT = 1 << 1;
    private static final int HANDLE_TOP = 1 << 2;
    private static final int HANDLE_RIGHT = 1 << 3;
    private static final int HANDLE_BOTTOM = 1 << 4;

    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageBounds = new RectF();
    private final RectF cropRect = new RectF();
    private final RectF handleRect = new RectF();

    private float handleRadius;
    private float edgeTouchSize;
    private float minCropSize;
    private int activeHandle = HANDLE_NONE;
    private float lastTouchX;
    private float lastTouchY;
    private boolean boundsReady = false;

    public CropOverlayView(Context context) {
        super(context);
        init();
    }

    public CropOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CropOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);

        handleRadius = dp(13);
        edgeTouchSize = dp(30);
        minCropSize = dp(96);

        shadePaint.setColor(Color.argb(130, 0, 0, 0));
        shadePaint.setStyle(Paint.Style.FILL);

        framePaint.setColor(Color.rgb(93, 232, 205));
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(2));

        handleFillPaint.setColor(Color.WHITE);
        handleFillPaint.setStyle(Paint.Style.FILL);

        handleStrokePaint.setColor(Color.rgb(93, 232, 205));
        handleStrokePaint.setStyle(Paint.Style.STROKE);
        handleStrokePaint.setStrokeWidth(dp(2));
    }

    public void setImageBounds(RectF bounds) {
        if (bounds == null || bounds.width() <= 1 || bounds.height() <= 1) {
            boundsReady = false;
            invalidate();
            return;
        }

        imageBounds.set(bounds);
        boundsReady = true;

        if (cropRect.isEmpty() || !imageBounds.contains(cropRect)) {
            resetToFullImage();
        } else {
            clampCropRect();
            invalidate();
        }
    }

    public void resetToFullImage() {
        if (!boundsReady) {
            return;
        }
        cropRect.set(imageBounds);
        invalidate();
    }

    public Rect getCropRectInBitmap(int bitmapWidth, int bitmapHeight) {
        if (!boundsReady || bitmapWidth <= 0 || bitmapHeight <= 0) {
            return new Rect(0, 0, Math.max(1, bitmapWidth), Math.max(1, bitmapHeight));
        }

        float scaleX = bitmapWidth / imageBounds.width();
        float scaleY = bitmapHeight / imageBounds.height();

        int left = clamp(Math.round((cropRect.left - imageBounds.left) * scaleX), 0, bitmapWidth - 1);
        int top = clamp(Math.round((cropRect.top - imageBounds.top) * scaleY), 0, bitmapHeight - 1);
        int right = clamp(Math.round((cropRect.right - imageBounds.left) * scaleX), left + 1, bitmapWidth);
        int bottom = clamp(Math.round((cropRect.bottom - imageBounds.top) * scaleY), top + 1, bitmapHeight);

        return new Rect(left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!boundsReady || cropRect.isEmpty()) {
            return;
        }

        canvas.drawRect(0, 0, getWidth(), cropRect.top, shadePaint);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), shadePaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, shadePaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, shadePaint);

        canvas.drawRect(cropRect, framePaint);
        drawCornerHandle(canvas, cropRect.left, cropRect.top);
        drawCornerHandle(canvas, cropRect.right, cropRect.top);
        drawCornerHandle(canvas, cropRect.left, cropRect.bottom);
        drawCornerHandle(canvas, cropRect.right, cropRect.bottom);
        drawEdgeHandle(canvas, (cropRect.left + cropRect.right) / 2f, cropRect.top, true);
        drawEdgeHandle(canvas, (cropRect.left + cropRect.right) / 2f, cropRect.bottom, true);
        drawEdgeHandle(canvas, cropRect.left, (cropRect.top + cropRect.bottom) / 2f, false);
        drawEdgeHandle(canvas, cropRect.right, (cropRect.top + cropRect.bottom) / 2f, false);
    }

    private void drawCornerHandle(Canvas canvas, float cx, float cy) {
        canvas.drawCircle(cx, cy, handleRadius, handleFillPaint);
        canvas.drawCircle(cx, cy, handleRadius, handleStrokePaint);
    }

    private void drawEdgeHandle(Canvas canvas, float cx, float cy, boolean horizontal) {
        float longSide = dp(26);
        float shortSide = dp(7);
        if (horizontal) {
            handleRect.set(cx - longSide, cy - shortSide, cx + longSide, cy + shortSide);
        } else {
            handleRect.set(cx - shortSide, cy - longSide, cx + shortSide, cy + longSide);
        }
        canvas.drawRoundRect(handleRect, shortSide, shortSide, handleFillPaint);
        canvas.drawRoundRect(handleRect, shortSide, shortSide, handleStrokePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!boundsReady || cropRect.isEmpty()) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeHandle = findHandle(x, y);
                if (activeHandle == HANDLE_NONE) {
                    return false;
                }
                lastTouchX = x;
                lastTouchY = y;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;
                updateCropRect(dx, dy);
                lastTouchX = x;
                lastTouchY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeHandle = HANDLE_NONE;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;

            default:
                return true;
        }
    }

    private int findHandle(float x, float y) {
        if (near(x, y, cropRect.left, cropRect.top)) {
            return HANDLE_LEFT | HANDLE_TOP;
        }
        if (near(x, y, cropRect.right, cropRect.top)) {
            return HANDLE_RIGHT | HANDLE_TOP;
        }
        if (near(x, y, cropRect.left, cropRect.bottom)) {
            return HANDLE_LEFT | HANDLE_BOTTOM;
        }
        if (near(x, y, cropRect.right, cropRect.bottom)) {
            return HANDLE_RIGHT | HANDLE_BOTTOM;
        }
        if (Math.abs(y - cropRect.top) <= edgeTouchSize && x >= cropRect.left && x <= cropRect.right) {
            return HANDLE_TOP;
        }
        if (Math.abs(y - cropRect.bottom) <= edgeTouchSize && x >= cropRect.left && x <= cropRect.right) {
            return HANDLE_BOTTOM;
        }
        if (Math.abs(x - cropRect.left) <= edgeTouchSize && y >= cropRect.top && y <= cropRect.bottom) {
            return HANDLE_LEFT;
        }
        if (Math.abs(x - cropRect.right) <= edgeTouchSize && y >= cropRect.top && y <= cropRect.bottom) {
            return HANDLE_RIGHT;
        }
        if (cropRect.contains(x, y)) {
            return HANDLE_MOVE;
        }
        return HANDLE_NONE;
    }

    private boolean near(float x, float y, float targetX, float targetY) {
        float distanceX = x - targetX;
        float distanceY = y - targetY;
        return distanceX * distanceX + distanceY * distanceY <= edgeTouchSize * edgeTouchSize;
    }

    private void updateCropRect(float dx, float dy) {
        if (activeHandle == HANDLE_MOVE) {
            cropRect.offset(dx, dy);
            clampMoveRect();
            return;
        }

        if ((activeHandle & HANDLE_LEFT) != 0) {
            cropRect.left += dx;
        }
        if ((activeHandle & HANDLE_RIGHT) != 0) {
            cropRect.right += dx;
        }
        if ((activeHandle & HANDLE_TOP) != 0) {
            cropRect.top += dy;
        }
        if ((activeHandle & HANDLE_BOTTOM) != 0) {
            cropRect.bottom += dy;
        }
        clampCropRect();
    }

    private void clampMoveRect() {
        float offsetX = 0f;
        float offsetY = 0f;
        if (cropRect.left < imageBounds.left) {
            offsetX = imageBounds.left - cropRect.left;
        } else if (cropRect.right > imageBounds.right) {
            offsetX = imageBounds.right - cropRect.right;
        }
        if (cropRect.top < imageBounds.top) {
            offsetY = imageBounds.top - cropRect.top;
        } else if (cropRect.bottom > imageBounds.bottom) {
            offsetY = imageBounds.bottom - cropRect.bottom;
        }
        cropRect.offset(offsetX, offsetY);
    }

    private void clampCropRect() {
        if (cropRect.left < imageBounds.left) {
            cropRect.left = imageBounds.left;
        }
        if (cropRect.top < imageBounds.top) {
            cropRect.top = imageBounds.top;
        }
        if (cropRect.right > imageBounds.right) {
            cropRect.right = imageBounds.right;
        }
        if (cropRect.bottom > imageBounds.bottom) {
            cropRect.bottom = imageBounds.bottom;
        }

        float maxMinWidth = Math.min(minCropSize, imageBounds.width());
        float maxMinHeight = Math.min(minCropSize, imageBounds.height());

        if (cropRect.width() < maxMinWidth) {
            if ((activeHandle & HANDLE_LEFT) != 0) {
                cropRect.left = cropRect.right - maxMinWidth;
            } else {
                cropRect.right = cropRect.left + maxMinWidth;
            }
        }
        if (cropRect.height() < maxMinHeight) {
            if ((activeHandle & HANDLE_TOP) != 0) {
                cropRect.top = cropRect.bottom - maxMinHeight;
            } else {
                cropRect.bottom = cropRect.top + maxMinHeight;
            }
        }

        if (cropRect.left < imageBounds.left) {
            cropRect.offset(imageBounds.left - cropRect.left, 0);
        }
        if (cropRect.right > imageBounds.right) {
            cropRect.offset(imageBounds.right - cropRect.right, 0);
        }
        if (cropRect.top < imageBounds.top) {
            cropRect.offset(0, imageBounds.top - cropRect.top);
        }
        if (cropRect.bottom > imageBounds.bottom) {
            cropRect.offset(0, imageBounds.bottom - cropRect.bottom);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
