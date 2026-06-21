package com.example.scanmate;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

public class IdCardComposer {

    private IdCardComposer() {
    }

    public static Bitmap composeA4Page(Bitmap cardBitmap, int sideIndex, String title) {
        int width = 1240;
        int height = 1754;
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(24, 26, 32));
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(54f);
        canvas.drawText(title == null ? "ScanMate 證件掃描" : title, 90f, 120f, paint);

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(34f);
        paint.setColor(Color.rgb(85, 96, 112));
        canvas.drawText(sideIndex <= 1 ? "正面" : "反面", 90f, 176f, paint);

        RectF cardRect = fitInside(cardBitmap, new RectF(130f, 300f, 1110f, 920f));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(245, 247, 250));
        canvas.drawRoundRect(cardRect.left - 28, cardRect.top - 28, cardRect.right + 28, cardRect.bottom + 28, 22, 22, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(Color.rgb(90, 210, 190));
        canvas.drawRoundRect(cardRect.left - 28, cardRect.top - 28, cardRect.right + 28, cardRect.bottom + 28, 22, 22, paint);

        canvas.drawBitmap(cardBitmap, null, cardRect, null);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(125, 132, 144));
        paint.setTextSize(28f);
        canvas.drawText("ScanMate ID Scan", 90f, height - 90f, paint);
        return output;
    }

    private static RectF fitInside(Bitmap bitmap, RectF bounds) {
        float scale = Math.min(bounds.width() / bitmap.getWidth(), bounds.height() / bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        float left = bounds.left + (bounds.width() - width) / 2f;
        float top = bounds.top + (bounds.height() - height) / 2f;
        return new RectF(left, top, left + width, top + height);
    }
}
