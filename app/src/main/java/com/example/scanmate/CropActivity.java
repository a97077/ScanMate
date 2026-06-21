package com.example.scanmate;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CropActivity extends AppCompatActivity {

    private ImageView imgCropPreview;
    private CropOverlayView cropOverlay;
    private TextView txtCropTitle;
    private Bitmap workingBitmap;
    private Uri sourceUri;
    private String documentTitle;
    private String captureMode;
    private boolean autoCorrectDocument = true;
    private boolean isProcessing = false;
    private boolean isDetectingCorners = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        imgCropPreview = findViewById(R.id.imgCropPreview);
        cropOverlay = findViewById(R.id.cropOverlay);
        txtCropTitle = findViewById(R.id.txtCropTitle);

        View btnBack = findViewById(R.id.btnCropBack);
        View btnRotateLeft = findViewById(R.id.btnCropRotateLeft);
        View btnRotateRight = findViewById(R.id.btnCropRotateRight);
        View btnCropAll = findViewById(R.id.btnCropAll);
        View btnCropNext = findViewById(R.id.btnCropNext);

        documentTitle = getIntent().getStringExtra("document_title");
        if (documentTitle == null || documentTitle.trim().isEmpty()) {
            documentTitle = "ScanMate " + new SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(new Date());
        }
        captureMode = getIntent().getStringExtra("capture_mode");

        txtCropTitle.setText("裁剪");

        String uriString = getIntent().getStringExtra("image_uri");
        if (uriString == null) {
            Toast.makeText(this, "找不到拍攝圖片", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sourceUri = Uri.parse(uriString);
        boolean forcePortrait = getIntent().getBooleanExtra("force_portrait", false);
        workingBitmap = decodeBitmap(sourceUri, forcePortrait);
        if (workingBitmap == null) {
            Toast.makeText(this, "圖片讀取失敗", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imgCropPreview.setImageBitmap(workingBitmap);
        refreshCropOverlayBounds();
        applyDetectedDocumentCorners();

        btnBack.setOnClickListener(v -> finish());
        btnRotateLeft.setOnClickListener(v -> rotateWorkingBitmap(-90));
        btnRotateRight.setOnClickListener(v -> rotateWorkingBitmap(90));
        btnCropAll.setOnClickListener(v -> {
            autoCorrectDocument = false;
            cropOverlay.resetToFullImage();
            Toast.makeText(this, "已套用整頁範圍，下一步將保留完整圖片", Toast.LENGTH_SHORT).show();
        });
        btnCropNext.setOnClickListener(v -> goToEditScreen());
    }

    private Bitmap decodeBitmap(Uri uri, boolean forcePortrait) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream boundsStream = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(boundsStream, null, bounds);
            }

            int sampleSize = 1;
            int maxSize = 2200;
            while (bounds.outWidth / sampleSize > maxSize || bounds.outHeight / sampleSize > maxSize) {
                sampleSize *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream imageStream = getContentResolver().openInputStream(uri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(imageStream, null, options);
                return BitmapOrientationHelper.applyExifAndPortrait(
                        getContentResolver(),
                        uri,
                        bitmap,
                        forcePortrait
                );
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private void rotateWorkingBitmap(float degrees) {
        if (workingBitmap == null) {
            return;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        workingBitmap = Bitmap.createBitmap(
                workingBitmap,
                0,
                0,
                workingBitmap.getWidth(),
                workingBitmap.getHeight(),
                matrix,
                true
        );
        imgCropPreview.setImageBitmap(workingBitmap);
        refreshCropOverlayBounds();
        applyDetectedDocumentCorners();
    }

    private void goToEditScreen() {
        if (isProcessing || workingBitmap == null) {
            return;
        }

        isProcessing = true;
        txtCropTitle.setText("文件校正中");
        Toast.makeText(this, autoCorrectDocument ? "正在進行文件校正" : "正在套用裁切範圍", Toast.LENGTH_SHORT).show();

        float[] cropPoints = cropOverlay.getCropPointsInBitmap();

        new Thread(() -> {
            Bitmap outputBitmap;
            if (autoCorrectDocument) {
                outputBitmap = runPerspectiveCorrection(workingBitmap, cropPoints, false);
                if (outputBitmap == null) {
                    outputBitmap = cropWorkingBitmap();
                }
            } else {
                outputBitmap = cropWorkingBitmap();
            }

            if (outputBitmap == null) {
                outputBitmap = workingBitmap;
            }

            if ("id".equals(captureMode)) {
                outputBitmap = IdCardComposer.composeA4Page(outputBitmap, ScanDraftStore.getPageCount() + 1, documentTitle);
            }

            Bitmap finalBitmap = outputBitmap;
            runOnUiThread(() -> {
                isProcessing = false;
                ScanDraftStore.start(sourceUri, finalBitmap, documentTitle);
                Intent intent = new Intent(this, ScanEditActivity.class);
                intent.putExtra("capture_mode", captureMode);
                startActivity(intent);
            });
        }).start();
    }

    private Bitmap runPerspectiveCorrection(Bitmap bitmap, float[] cropPoints, boolean enhance) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(this));
            }

            PyObject module = Python.getInstance().getModule("scan_cv");
            PyObject result = module.callAttr(
                    "perspective_process",
                    outputStream.toByteArray(),
                    buildPointsJson(cropPoints),
                    enhance
            );
            byte[] pngBytes = result.toJava(byte[].class);
            Bitmap corrected = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.length);
            if (corrected == null) {
                throw new RuntimeException("OpenCV output decode failed");
            }
            return corrected;
        } catch (Exception e) {
            runOnUiThread(() ->
                    Toast.makeText(this, "自動校正失敗，已保留原圖", Toast.LENGTH_SHORT).show()
            );
            return null;
        }
    }

    private void refreshCropOverlayBounds() {
        imgCropPreview.post(() -> {
            if (workingBitmap == null || cropOverlay == null) {
                return;
            }
            cropOverlay.setBitmapSize(workingBitmap.getWidth(), workingBitmap.getHeight());
            RectF displayedBounds = calculateDisplayedImageBounds();
            cropOverlay.setImageBounds(displayedBounds);
        });
    }

    private RectF calculateDisplayedImageBounds() {
        int viewWidth = imgCropPreview.getWidth();
        int viewHeight = imgCropPreview.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0 || workingBitmap == null) {
            return new RectF();
        }

        float bitmapWidth = workingBitmap.getWidth();
        float bitmapHeight = workingBitmap.getHeight();
        float scale = Math.min(viewWidth / bitmapWidth, viewHeight / bitmapHeight);
        float displayedWidth = bitmapWidth * scale;
        float displayedHeight = bitmapHeight * scale;
        float left = (viewWidth - displayedWidth) / 2f;
        float top = (viewHeight - displayedHeight) / 2f;
        return new RectF(left, top, left + displayedWidth, top + displayedHeight);
    }

    private Bitmap cropWorkingBitmap() {
        if (workingBitmap == null || cropOverlay == null) {
            return workingBitmap;
        }

        Rect cropRect = cropOverlay.getCropBoundsInBitmap();
        int width = cropRect.width();
        int height = cropRect.height();
        if (width <= 0 || height <= 0) {
            return workingBitmap;
        }

        boolean fullWidth = cropRect.left == 0 && cropRect.right == workingBitmap.getWidth();
        boolean fullHeight = cropRect.top == 0 && cropRect.bottom == workingBitmap.getHeight();
        if (fullWidth && fullHeight) {
            return workingBitmap;
        }

        try {
            return Bitmap.createBitmap(workingBitmap, cropRect.left, cropRect.top, width, height);
        } catch (Exception ignored) {
            return workingBitmap;
        }
    }

    private void applyDetectedDocumentCorners() {
        if (workingBitmap == null || isDetectingCorners) {
            return;
        }

        isDetectingCorners = true;
        Bitmap bitmapSnapshot = workingBitmap;
        new Thread(() -> {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                bitmapSnapshot.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

                if (!Python.isStarted()) {
                    Python.start(new AndroidPlatform(this));
                }

                PyObject module = Python.getInstance().getModule("scan_cv");
                String json = module.callAttr("detect_document_corners", outputStream.toByteArray()).toJava(String.class);
                float[] points = parsePointsJson(json);

                runOnUiThread(() -> {
                    if (workingBitmap == bitmapSnapshot && cropOverlay != null) {
                        cropOverlay.setCropPointsFromBitmap(points);
                    }
                    isDetectingCorners = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> isDetectingCorners = false);
            }
        }).start();
    }

    private String buildPointsJson(float[] cropPoints) throws Exception {
        JSONArray array = new JSONArray();
        for (int i = 0; i < 4; i++) {
            JSONObject object = new JSONObject();
            object.put("x", cropPoints[i * 2]);
            object.put("y", cropPoints[i * 2 + 1]);
            array.put(object);
        }
        return array.toString();
    }

    private float[] parsePointsJson(String rawJson) throws Exception {
        JSONArray array = new JSONArray(rawJson);
        float[] points = new float[8];
        for (int i = 0; i < 4 && i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            points[i * 2] = (float) object.optDouble("x", 0);
            points[i * 2 + 1] = (float) object.optDouble("y", 0);
        }
        return points;
    }
}
