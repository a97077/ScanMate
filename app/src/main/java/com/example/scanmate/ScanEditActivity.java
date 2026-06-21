package com.example.scanmate;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScanEditActivity extends AppCompatActivity {

    private ImageView imgEditPreview;
    private TextView txtEditTitle;
    private final List<TextView> filterButtons = new ArrayList<>();
    private Bitmap originalBitmap;
    private Bitmap currentBitmap;
    private String captureMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_edit);

        if (!ScanDraftStore.hasDraft()) {
            Toast.makeText(this, "目前沒有可編輯的掃描圖片", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        originalBitmap = ScanDraftStore.getCurrentBitmap();
        currentBitmap = originalBitmap;
        captureMode = getIntent().getStringExtra("capture_mode");

        imgEditPreview = findViewById(R.id.imgEditPreview);
        txtEditTitle = findViewById(R.id.txtEditTitle);
        txtEditTitle.setText(ScanDraftStore.getDocumentTitle());
        imgEditPreview.setImageBitmap(currentBitmap);

        View btnBack = findViewById(R.id.btnEditBack);
        View btnConfirm = findViewById(R.id.btnEditConfirm);
        View btnRotateLeft = findViewById(R.id.btnEditRotateLeft);
        View btnAnnotate = findViewById(R.id.btnEditAnnotate);
        View btnExtractText = findViewById(R.id.btnEditExtractText);
        View btnSignature = findViewById(R.id.btnEditSignature);

        TextView filterOriginal = findViewById(R.id.filterOriginal);
        TextView filterBright = findViewById(R.id.filterBright);
        TextView filterSharp = findViewById(R.id.filterSharp);
        TextView filterHd = findViewById(R.id.filterHd);
        TextView filterShadow = findViewById(R.id.filterShadow);

        filterButtons.add(filterOriginal);
        filterButtons.add(filterBright);
        filterButtons.add(filterSharp);
        filterButtons.add(filterHd);
        filterButtons.add(filterShadow);
        setupFilterThumbnails(filterOriginal, filterBright, filterSharp, filterHd, filterShadow);

        btnBack.setOnClickListener(v -> finish());
        btnConfirm.setOnClickListener(v -> confirmEdit());
        btnRotateLeft.setOnClickListener(v -> rotateCurrentBitmap());
        btnAnnotate.setOnClickListener(v -> addAnnotation());
        btnExtractText.setOnClickListener(v -> startActivity(new Intent(this, TextExtractActivity.class)));
        btnSignature.setOnClickListener(v -> addSignatureStamp());

        filterOriginal.setOnClickListener(v -> applyFilter("original", filterOriginal));
        filterBright.setOnClickListener(v -> applyFilter("bright", filterBright));
        filterSharp.setOnClickListener(v -> applyFilter("sharp", filterSharp));
        filterHd.setOnClickListener(v -> applyFilter("hd", filterHd));
        filterShadow.setOnClickListener(v -> applyFilter("shadow", filterShadow));

        markSelected(filterOriginal);
        applyCaptureModeHint(filterShadow);
    }

    private void applyFilter(String type, TextView selected) {
        Bitmap base = originalBitmap;
        if (base == null) {
            return;
        }

        currentBitmap = createFilterBitmap(type, base);

        imgEditPreview.setImageBitmap(currentBitmap);
        markSelected(selected);
    }

    private Bitmap createFilterBitmap(String type, Bitmap base) {
        if ("bright".equals(type)) {
            return transformPixels(base, 1.05f, 28, false, false);
        } else if ("sharp".equals(type)) {
            return transformPixels(base, 1.28f, 8, false, false);
        } else if ("hd".equals(type)) {
            return transformPixels(base, 1.35f, 12, true, false);
        } else if ("shadow".equals(type)) {
            return transformPixels(base, 1.12f, 18, false, true);
        }
        return base;
    }

    private void setupFilterThumbnails(
            TextView filterOriginal,
            TextView filterBright,
            TextView filterSharp,
            TextView filterHd,
            TextView filterShadow
    ) {
        if (originalBitmap == null) {
            return;
        }

        Bitmap thumbBase = createCenterCropThumbnail(originalBitmap, dp(86), dp(42));
        bindFilterThumbnail(filterOriginal, "原圖", createFilterBitmap("original", thumbBase));
        bindFilterThumbnail(filterBright, "增亮", createFilterBitmap("bright", thumbBase));
        bindFilterThumbnail(filterSharp, "增強銳化", createFilterBitmap("sharp", thumbBase));
        bindFilterThumbnail(filterHd, "智慧高畫質", createFilterBitmap("hd", thumbBase));
        bindFilterThumbnail(filterShadow, "去陰影", createFilterBitmap("shadow", thumbBase));
    }

    private void bindFilterThumbnail(TextView view, String label, Bitmap thumbnail) {
        BitmapDrawable drawable = new BitmapDrawable(getResources(), thumbnail);
        drawable.setBounds(0, 0, dp(86), dp(42));
        view.setText(label);
        view.setGravity(android.view.Gravity.CENTER);
        view.setCompoundDrawablePadding(dp(4));
        view.setCompoundDrawables(null, drawable, null, null);
    }

    private Bitmap createCenterCropThumbnail(Bitmap source, int width, int height) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        }

        float scale = Math.max((float) width / source.getWidth(), (float) height / source.getHeight());
        int scaledWidth = Math.max(1, Math.round(source.getWidth() * scale));
        int scaledHeight = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);
        int left = Math.max(0, (scaledWidth - width) / 2);
        int top = Math.max(0, (scaledHeight - height) / 2);
        return Bitmap.createBitmap(scaled, left, top, Math.min(width, scaled.getWidth() - left), Math.min(height, scaled.getHeight() - top));
    }

    private Bitmap transformPixels(Bitmap source, float contrast, int brightness, boolean grayscale, boolean liftShadow) {
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int alpha = Color.alpha(color);
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);

            if (liftShadow) {
                int luminance = (red + green + blue) / 3;
                int extra = Math.max(0, 120 - luminance) / 2;
                red += extra;
                green += extra;
                blue += extra;
            }

            red = clamp((int) ((red - 128) * contrast + 128 + brightness));
            green = clamp((int) ((green - 128) * contrast + 128 + brightness));
            blue = clamp((int) ((blue - 128) * contrast + 128 + brightness));

            if (grayscale) {
                int gray = clamp((int) (red * 0.299f + green * 0.587f + blue * 0.114f));
                gray = gray > 170 ? 255 : gray < 80 ? 30 : clamp((int) ((gray - 128) * 1.45f + 150));
                red = gray;
                green = gray;
                blue = gray;
            }

            pixels[i] = Color.argb(alpha, red, green, blue);
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height);
        return output;
    }

    private void rotateCurrentBitmap() {
        if (currentBitmap == null) {
            return;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(-90);
        currentBitmap = Bitmap.createBitmap(
                currentBitmap,
                0,
                0,
                currentBitmap.getWidth(),
                currentBitmap.getHeight(),
                matrix,
                true
        );
        originalBitmap = currentBitmap;
        imgEditPreview.setImageBitmap(currentBitmap);
        ScanDraftStore.setCurrentBitmap(currentBitmap);
    }

    private void confirmEdit() {
        ScanDraftStore.setCurrentBitmap(currentBitmap);
        ScanDraftStore.commitCurrentPage();
        ScanDraftStore.saveDraft(this);
        if ("word".equals(captureMode)) {
            startActivity(new Intent(this, TextExtractActivity.class));
        } else {
            startActivity(new Intent(this, ScanPreviewActivity.class));
        }
    }

    private void applyCaptureModeHint(TextView filterShadow) {
        if ("word".equals(captureMode)) {
            Toast.makeText(this, "拍圖轉 Word 模式：確認後會進入 OCR 文字辨識", Toast.LENGTH_SHORT).show();
        } else if ("signature".equals(captureMode)) {
            Toast.makeText(this, "電子簽名模式：可點選底部電子簽名加入簽章", Toast.LENGTH_SHORT).show();
        } else if ("erase".equals(captureMode)) {
            applyFilter("shadow", filterShadow);
            Toast.makeText(this, "AI 擦除模式：已先套用去陰影清理", Toast.LENGTH_SHORT).show();
        }
    }

    private void addAnnotation() {
        if (currentBitmap == null) {
            return;
        }

        Bitmap annotated = currentBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(annotated);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float scale = Math.max(1f, annotated.getWidth() / 900f);
        float left = 32f * scale;
        float top = 32f * scale;
        float right = Math.min(annotated.getWidth() - 32f * scale, left + 430f * scale);
        float bottom = top + 118f * scale;

        paint.setColor(Color.argb(215, 30, 32, 38));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(left, top, right, bottom, 18f * scale, 18f * scale, paint);

        paint.setColor(Color.rgb(88, 224, 204));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f * scale);
        canvas.drawRoundRect(left, top, right, bottom, 18f * scale, 18f * scale, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(34f * scale);
        canvas.drawText("ScanMate 標注", left + 24f * scale, top + 48f * scale, paint);

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(24f * scale);
        paint.setColor(Color.WHITE);
        String now = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date());
        canvas.drawText(now, left + 24f * scale, top + 88f * scale, paint);

        updateEditedBitmap(annotated);
        Toast.makeText(this, "已加入標注", Toast.LENGTH_SHORT).show();
    }

    private void addSignatureStamp() {
        if (currentBitmap == null) {
            return;
        }

        Bitmap signed = currentBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(signed);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float scale = Math.max(1f, signed.getWidth() / 900f);
        float width = 360f * scale;
        float height = 128f * scale;
        float left = signed.getWidth() - width - 36f * scale;
        float top = signed.getHeight() - height - 36f * scale;
        float right = signed.getWidth() - 36f * scale;
        float bottom = signed.getHeight() - 36f * scale;

        paint.setColor(Color.argb(235, 255, 255, 255));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(left, top, right, bottom, 20f * scale, 20f * scale, paint);

        paint.setColor(Color.rgb(36, 185, 160));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f * scale);
        canvas.drawRoundRect(left, top, right, bottom, 20f * scale, 20f * scale, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        paint.setTextSize(40f * scale);
        canvas.drawText("ScanMate", left + 36f * scale, top + 54f * scale, paint);

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(24f * scale);
        canvas.drawText("電子簽名", left + 36f * scale, top + 94f * scale, paint);

        updateEditedBitmap(signed);
        Toast.makeText(this, "已加入電子簽名", Toast.LENGTH_SHORT).show();
    }

    private void updateEditedBitmap(Bitmap bitmap) {
        currentBitmap = bitmap;
        originalBitmap = bitmap;
        imgEditPreview.setImageBitmap(bitmap);
        ScanDraftStore.setCurrentBitmap(bitmap);
    }

    private void markSelected(TextView selected) {
        for (TextView button : filterButtons) {
            boolean active = button == selected;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(2));
            bg.setColor(active ? Color.parseColor("#31C7AA") : Color.parseColor("#26272B"));
            if (active) {
                bg.setStroke(dp(1), Color.parseColor("#58E0CC"));
            }
            button.setBackground(bg);
            button.setTextColor(active ? Color.WHITE : Color.parseColor("#D8D9DD"));
            button.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
