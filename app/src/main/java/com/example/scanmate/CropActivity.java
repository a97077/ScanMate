package com.example.scanmate;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CropActivity extends AppCompatActivity {

    private ImageView imgCropPreview;
    private TextView txtCropTitle;
    private Bitmap workingBitmap;
    private Uri sourceUri;
    private String documentTitle;
    private boolean autoCorrectDocument = true;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        imgCropPreview = findViewById(R.id.imgCropPreview);
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

        txtCropTitle.setText("裁剪");

        String uriString = getIntent().getStringExtra("image_uri");
        if (uriString == null) {
            Toast.makeText(this, "找不到拍攝圖片", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sourceUri = Uri.parse(uriString);
        workingBitmap = decodeBitmap(sourceUri);
        if (workingBitmap == null) {
            Toast.makeText(this, "圖片讀取失敗", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imgCropPreview.setImageBitmap(workingBitmap);

        btnBack.setOnClickListener(v -> finish());
        btnRotateLeft.setOnClickListener(v -> rotateWorkingBitmap(-90));
        btnRotateRight.setOnClickListener(v -> rotateWorkingBitmap(90));
        btnCropAll.setOnClickListener(v -> {
            autoCorrectDocument = false;
            Toast.makeText(this, "已套用整頁範圍，下一步將保留完整圖片", Toast.LENGTH_SHORT).show();
        });
        btnCropNext.setOnClickListener(v -> goToEditScreen());
    }

    private Bitmap decodeBitmap(Uri uri) {
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
                return BitmapFactory.decodeStream(imageStream, null, options);
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
    }

    private void goToEditScreen() {
        if (isProcessing || workingBitmap == null) {
            return;
        }

        isProcessing = true;
        txtCropTitle.setText("文件校正中");
        Toast.makeText(this, autoCorrectDocument ? "正在進行文件校正" : "正在套用整頁圖片", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            Bitmap outputBitmap = workingBitmap;
            if (autoCorrectDocument) {
                Bitmap corrected = runOpenCvDocumentCorrection(workingBitmap);
                if (corrected != null) {
                    outputBitmap = corrected;
                }
            }

            Bitmap finalBitmap = outputBitmap;
            runOnUiThread(() -> {
                isProcessing = false;
                ScanDraftStore.start(sourceUri, finalBitmap, documentTitle);
                Intent intent = new Intent(this, ScanEditActivity.class);
                startActivity(intent);
            });
        }).start();
    }

    private Bitmap runOpenCvDocumentCorrection(Bitmap bitmap) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(this));
            }

            PyObject module = Python.getInstance().getModule("scan_cv");
            PyObject result = module.callAttr("canny_process", outputStream.toByteArray());
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
}
