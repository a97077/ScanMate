package com.example.scanmate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class CropOverlayView extends View {

    private static final int MOVE_NONE = -2;
    private static final int MOVE_POLYGON = -1;
    private static final int MOVE_TOP = 4;
    private static final int MOVE_RIGHT = 5;
    private static final int MOVE_BOTTOM = 6;
    private static final int MOVE_LEFT = 7;
    private static final int POINT_COUNT = 4;

    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cropPath = new Path();
    private final RectF imageBounds = new RectF();
    private final RectF polygonBounds = new RectF();
    private final PointF[] points = new PointF[POINT_COUNT];

    private int bitmapWidth = 1;
    private int bitmapHeight = 1;
    private int activePoint = MOVE_NONE;
    private float handleRadius;
    private float touchRadius;
    private float edgeHandleHalfWidth;
    private float edgeHandleHalfHeight;
    private float minEdgeDistance;
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
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        for (int i = 0; i < POINT_COUNT; i++) {
            points[i] = new PointF();
        }

        handleRadius = dp(13);
        touchRadius = dp(34);
        edgeHandleHalfWidth = dp(22);
        edgeHandleHalfHeight = dp(7);
        minEdgeDistance = dp(54);

        shadePaint.setColor(Color.argb(150, 0, 0, 0));
        shadePaint.setStyle(Paint.Style.FILL);

        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        framePaint.setColor(Color.rgb(93, 232, 205));
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(2));

        handleFillPaint.setColor(Color.WHITE);
        handleFillPaint.setStyle(Paint.Style.FILL);

        handleStrokePaint.setColor(Color.rgb(93, 232, 205));
        handleStrokePaint.setStyle(Paint.Style.STROKE);
        handleStrokePaint.setStrokeWidth(dp(2));
    }

    public void setBitmapSize(int width, int height) {
        bitmapWidth = Math.max(1, width);
        bitmapHeight = Math.max(1, height);
    }

    public void setImageBounds(RectF bounds) {
        if (bounds == null || bounds.width() <= 1 || bounds.height() <= 1) {
            boundsReady = false;
            invalidate();
            return;
        }

        boolean hadBounds = boundsReady;
        RectF oldBounds = new RectF(imageBounds);
        imageBounds.set(bounds);
        boundsReady = true;

        if (!hadBounds || oldBounds.width() <= 1 || oldBounds.height() <= 1) {
            resetToFullImage();
        } else {
            remapPoints(oldBounds, imageBounds);
            clampAllPoints();
            invalidate();
        }
    }

    public void resetToFullImage() {
        if (!boundsReady) {
            return;
        }
        points[0].set(imageBounds.left, imageBounds.top);
        points[1].set(imageBounds.right, imageBounds.top);
        points[2].set(imageBounds.right, imageBounds.bottom);
        points[3].set(imageBounds.left, imageBounds.bottom);
        invalidate();
    }

    public void setCropPointsFromBitmap(float[] bitmapPoints) {
        if (!boundsReady || bitmapPoints == null || bitmapPoints.length < 8) {
            return;
        }

        for (int i = 0; i < POINT_COUNT; i++) {
            float bitmapX = clampFloat(bitmapPoints[i * 2], 0, bitmapWidth);
            float bitmapY = clampFloat(bitmapPoints[i * 2 + 1], 0, bitmapHeight);
            points[i].set(
                    imageBounds.left + bitmapX / bitmapWidth * imageBounds.width(),
                    imageBounds.top + bitmapY / bitmapHeight * imageBounds.height()
            );
        }
        clampAllPoints();
        invalidate();
    }

    public float[] getCropPointsInBitmap() {
        float[] output = new float[8];
        if (!boundsReady) {
            output[0] = 0;
            output[1] = 0;
            output[2] = bitmapWidth;
            output[3] = 0;
            output[4] = bitmapWidth;
            output[5] = bitmapHeight;
            output[6] = 0;
            output[7] = bitmapHeight;
            return output;
        }

        for (int i = 0; i < POINT_COUNT; i++) {
            output[i * 2] = clampFloat((points[i].x - imageBounds.left) / imageBounds.width() * bitmapWidth, 0, bitmapWidth);
            output[i * 2 + 1] = clampFloat((points[i].y - imageBounds.top) / imageBounds.height() * bitmapHeight, 0, bitmapHeight);
        }
        return output;
    }

    public Rect getCropBoundsInBitmap() {
        float[] cropPoints = getCropPointsInBitmap();
        float left = bitmapWidth;
        float top = bitmapHeight;
        float right = 0;
        float bottom = 0;

        for (int i = 0; i < POINT_COUNT; i++) {
            float x = cropPoints[i * 2];
            float y = cropPoints[i * 2 + 1];
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x);
            bottom = Math.max(bottom, y);
        }

        int l = clamp(Math.round(left), 0, bitmapWidth - 1);
        int t = clamp(Math.round(top), 0, bitmapHeight - 1);
        int r = clamp(Math.round(right), l + 1, bitmapWidth);
        int b = clamp(Math.round(bottom), t + 1, bitmapHeight);
        return new Rect(l, t, r, b);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!boundsReady) {
            return;
        }

        updatePath();

        int layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), shadePaint);
        canvas.drawPath(cropPath, clearPaint);
        canvas.restoreToCount(layer);

        canvas.drawPath(cropPath, framePaint);
        for (PointF point : points) {
            canvas.drawCircle(point.x, point.y, handleRadius, handleFillPaint);
            canvas.drawCircle(point.x, point.y, handleRadius, handleStrokePaint);
        }
        drawEdgeHandle(canvas, (points[0].x + points[1].x) / 2f, (points[0].y + points[1].y) / 2f, true);
        drawEdgeHandle(canvas, (points[1].x + points[2].x) / 2f, (points[1].y + points[2].y) / 2f, false);
        drawEdgeHandle(canvas, (points[2].x + points[3].x) / 2f, (points[2].y + points[3].y) / 2f, true);
        drawEdgeHandle(canvas, (points[3].x + points[0].x) / 2f, (points[3].y + points[0].y) / 2f, false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!boundsReady) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePoint = findActivePoint(x, y);
                if (activePoint == MOVE_NONE) {
                    return false;
                }
                lastTouchX = x;
                lastTouchY = y;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;
                if (activePoint == MOVE_POLYGON) {
                    movePolygon(dx, dy);
                } else if (activePoint >= MOVE_TOP && activePoint <= MOVE_LEFT) {
                    moveEdge(activePoint, dx, dy);
                } else if (activePoint >= 0 && activePoint < POINT_COUNT) {
                    points[activePoint].x = clampFloat(points[activePoint].x + dx, imageBounds.left, imageBounds.right);
                    points[activePoint].y = clampFloat(points[activePoint].y + dy, imageBounds.top, imageBounds.bottom);
                }
                lastTouchX = x;
                lastTouchY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePoint = MOVE_NONE;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;

            default:
                return true;
        }
    }

    private int findActivePoint(float x, float y) {
        for (int i = 0; i < POINT_COUNT; i++) {
            float dx = x - points[i].x;
            float dy = y - points[i].y;
            if (dx * dx + dy * dy <= touchRadius * touchRadius) {
                return i;
            }
        }
        if (isNearEdgeHandle(x, y, (points[0].x + points[1].x) / 2f, (points[0].y + points[1].y) / 2f, true)) {
            return MOVE_TOP;
        }
        if (isNearEdgeHandle(x, y, (points[1].x + points[2].x) / 2f, (points[1].y + points[2].y) / 2f, false)) {
            return MOVE_RIGHT;
        }
        if (isNearEdgeHandle(x, y, (points[2].x + points[3].x) / 2f, (points[2].y + points[3].y) / 2f, true)) {
            return MOVE_BOTTOM;
        }
        if (isNearEdgeHandle(x, y, (points[3].x + points[0].x) / 2f, (points[3].y + points[0].y) / 2f, false)) {
            return MOVE_LEFT;
        }
        if (isInsidePolygon(x, y)) {
            return MOVE_POLYGON;
        }
        return MOVE_NONE;
    }

    private void drawEdgeHandle(Canvas canvas, float centerX, float centerY, boolean horizontal) {
        float halfWidth = horizontal ? edgeHandleHalfWidth : edgeHandleHalfHeight;
        float halfHeight = horizontal ? edgeHandleHalfHeight : edgeHandleHalfWidth;
        RectF rect = new RectF(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight);
        canvas.drawRoundRect(rect, halfHeight, halfHeight, handleFillPaint);
        canvas.drawRoundRect(rect, halfHeight, halfHeight, handleStrokePaint);
    }

    private boolean isNearEdgeHandle(float x, float y, float centerX, float centerY, boolean horizontal) {
        float halfWidth = horizontal ? touchRadius : edgeHandleHalfWidth;
        float halfHeight = horizontal ? edgeHandleHalfWidth : touchRadius;
        return x >= centerX - halfWidth
                && x <= centerX + halfWidth
                && y >= centerY - halfHeight
                && y <= centerY + halfHeight;
    }

    private void moveEdge(int edge, float dx, float dy) {
        if (edge == MOVE_TOP) {
            float minY = imageBounds.top;
            float maxY = Math.min(points[2].y, points[3].y) - minEdgeDistance;
            float newY0 = clampFloat(points[0].y + dy, minY, maxY);
            float newY1 = clampFloat(points[1].y + dy, minY, maxY);
            points[0].y = newY0;
            points[1].y = newY1;
        } else if (edge == MOVE_RIGHT) {
            float minX = Math.max(points[0].x, points[3].x) + minEdgeDistance;
            float maxX = imageBounds.right;
            float newX1 = clampFloat(points[1].x + dx, minX, maxX);
            float newX2 = clampFloat(points[2].x + dx, minX, maxX);
            points[1].x = newX1;
            points[2].x = newX2;
        } else if (edge == MOVE_BOTTOM) {
            float minY = Math.max(points[0].y, points[1].y) + minEdgeDistance;
            float maxY = imageBounds.bottom;
            float newY2 = clampFloat(points[2].y + dy, minY, maxY);
            float newY3 = clampFloat(points[3].y + dy, minY, maxY);
            points[2].y = newY2;
            points[3].y = newY3;
        } else if (edge == MOVE_LEFT) {
            float minX = imageBounds.left;
            float maxX = Math.min(points[1].x, points[2].x) - minEdgeDistance;
            float newX0 = clampFloat(points[0].x + dx, minX, maxX);
            float newX3 = clampFloat(points[3].x + dx, minX, maxX);
            points[0].x = newX0;
            points[3].x = newX3;
        }
    }

    private boolean isInsidePolygon(float x, float y) {
        boolean inside = false;
        for (int i = 0, j = POINT_COUNT - 1; i < POINT_COUNT; j = i++) {
            boolean intersects = (points[i].y > y) != (points[j].y > y)
                    && x < (points[j].x - points[i].x) * (y - points[i].y) / (points[j].y - points[i].y + 0.0001f) + points[i].x;
            if (intersects) {
                inside = !inside;
            }
        }
        return inside;
    }

    private void movePolygon(float dx, float dy) {
        computePolygonBounds();
        if (polygonBounds.left + dx < imageBounds.left) {
            dx = imageBounds.left - polygonBounds.left;
        }
        if (polygonBounds.right + dx > imageBounds.right) {
            dx = imageBounds.right - polygonBounds.right;
        }
        if (polygonBounds.top + dy < imageBounds.top) {
            dy = imageBounds.top - polygonBounds.top;
        }
        if (polygonBounds.bottom + dy > imageBounds.bottom) {
            dy = imageBounds.bottom - polygonBounds.bottom;
        }

        for (PointF point : points) {
            point.offset(dx, dy);
        }
    }

    private void remapPoints(RectF oldBounds, RectF newBounds) {
        for (PointF point : points) {
            float rx = (point.x - oldBounds.left) / oldBounds.width();
            float ry = (point.y - oldBounds.top) / oldBounds.height();
            point.set(
                    newBounds.left + rx * newBounds.width(),
                    newBounds.top + ry * newBounds.height()
            );
        }
    }

    private void clampAllPoints() {
        for (PointF point : points) {
            point.x = clampFloat(point.x, imageBounds.left, imageBounds.right);
            point.y = clampFloat(point.y, imageBounds.top, imageBounds.bottom);
        }
    }

    private void updatePath() {
        cropPath.reset();
        cropPath.moveTo(points[0].x, points[0].y);
        cropPath.lineTo(points[1].x, points[1].y);
        cropPath.lineTo(points[2].x, points[2].y);
        cropPath.lineTo(points[3].x, points[3].y);
        cropPath.close();
    }

    private void computePolygonBounds() {
        float left = points[0].x;
        float top = points[0].y;
        float right = points[0].x;
        float bottom = points[0].y;

        for (int i = 1; i < POINT_COUNT; i++) {
            left = Math.min(left, points[i].x);
            top = Math.min(top, points[i].y);
            right = Math.max(right, points[i].x);
            bottom = Math.max(bottom, points[i].y);
        }
        polygonBounds.set(left, top, right, bottom);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
