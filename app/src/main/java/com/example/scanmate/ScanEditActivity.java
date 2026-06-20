package com.example.scanmate;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class ScanEditActivity extends AppCompatActivity {

    private ImageView imgEditPreview;
    private TextView txtEditTitle;
    private final List<TextView> filterButtons = new ArrayList<>();
    private Bitmap originalBitmap;
    private Bitmap currentBitmap;

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

        btnBack.setOnClickListener(v -> finish());
        btnConfirm.setOnClickListener(v -> confirmEdit());
        btnRotateLeft.setOnClickListener(v -> rotateCurrentBitmap());
        btnAnnotate.setOnClickListener(v -> Toast.makeText(this, "標注功能將在下一階段補上", Toast.LENGTH_SHORT).show());
        btnExtractText.setOnClickListener(v -> startActivity(new Intent(this, TextExtractActivity.class)));
        btnSignature.setOnClickListener(v -> openToolFeature("signature", "電子簽名"));

        filterOriginal.setOnClickListener(v -> applyFilter("original", filterOriginal));
        filterBright.setOnClickListener(v -> applyFilter("bright", filterBright));
        filterSharp.setOnClickListener(v -> applyFilter("sharp", filterSharp));
        filterHd.setOnClickListener(v -> applyFilter("hd", filterHd));
        filterShadow.setOnClickListener(v -> applyFilter("shadow", filterShadow));

        markSelected(filterOriginal);
    }

    private void applyFilter(String type, TextView selected) {
        Bitmap base = originalBitmap;
        if (base == null) {
            return;
        }

        if ("original".equals(type)) {
            currentBitmap = base;
        } else if ("bright".equals(type)) {
            currentBitmap = transformPixels(base, 1.05f, 28, false, false);
        } else if ("sharp".equals(type)) {
            currentBitmap = transformPixels(base, 1.28f, 8, false, false);
        } else if ("hd".equals(type)) {
            currentBitmap = transformPixels(base, 1.35f, 12, true, false);
        } else if ("shadow".equals(type)) {
            currentBitmap = transformPixels(base, 1.12f, 18, false, true);
        }

        imgEditPreview.setImageBitmap(currentBitmap);
        markSelected(selected);
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
        startActivity(new Intent(this, ScanPreviewActivity.class));
    }

    private void openToolFeature(String type, String title) {
        Intent intent = new Intent(this, ToolFeatureActivity.class);
        intent.putExtra(ToolFeatureActivity.EXTRA_TYPE, type);
        intent.putExtra(ToolFeatureActivity.EXTRA_TITLE, title);
        startActivity(intent);
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
