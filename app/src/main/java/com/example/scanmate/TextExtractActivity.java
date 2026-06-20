package com.example.scanmate;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class TextExtractActivity extends AppCompatActivity {

    private ImageView imgTextPreview;
    private TextView txtTextReport;
    private Python py;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            processImage(uri);
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_extract);

        imgTextPreview = findViewById(R.id.imgTextPreview);
        txtTextReport = findViewById(R.id.txtTextReport);
        Button btnSelectTextImage = findViewById(R.id.btnSelectTextImage);

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();

        findViewById(R.id.btnTextBack).setOnClickListener(v -> finish());
        btnSelectTextImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        if (ScanDraftStore.hasDraft() && ScanDraftStore.getCurrentBitmap() != null) {
            btnSelectTextImage.setText("重新選擇圖片");
            processBitmap(ScanDraftStore.getCurrentBitmap());
        }
    }

    private void processImage(Uri uri) {
        try {
            processImageBytes(readBytesFromUri(uri));
        } catch (Exception e) {
            txtTextReport.setText("文字提取預處理失敗：\n" + e.getMessage());
            Toast.makeText(this, "處理失敗", Toast.LENGTH_SHORT).show();
        }
    }

    private void processBitmap(Bitmap bitmap) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            processImageBytes(outputStream.toByteArray());
        } catch (Exception e) {
            txtTextReport.setText("文字提取預處理失敗：\n" + e.getMessage());
        }
    }

    private void processImageBytes(byte[] imageBytes) {
        PyObject module = py.getModule("scan_cv");

        PyObject previewResult = module.callAttr("text_extract_preview", imageBytes);
        byte[] outPng = previewResult.toJava(byte[].class);
        Bitmap preview = BitmapFactory.decodeByteArray(outPng, 0, outPng.length);
        imgTextPreview.setImageBitmap(preview);

        PyObject reportResult = module.callAttr("text_region_report", imageBytes);
        txtTextReport.setText(reportResult.toJava(String.class));
    }

    private byte[] readBytesFromUri(Uri uri) throws Exception {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new RuntimeException("Cannot open input stream");
            }

            byte[] data = new byte[4096];
            int nRead;
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }
}
