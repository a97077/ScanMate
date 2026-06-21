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
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TextExtractActivity extends AppCompatActivity {

    private ImageView imgTextPreview;
    private TextView txtTextReport;
    private Python py;
    private String pendingOcrText = "";

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            processImage(uri);
                        }
                    }
            );

    private final ActivityResultLauncher<String> exportTextLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("text/plain"),
                    uri -> {
                        if (uri != null) {
                            exportOcrText(uri);
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
        Button btnExportOcrText = findViewById(R.id.btnExportOcrText);

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();

        findViewById(R.id.btnTextBack).setOnClickListener(v -> finish());
        btnSelectTextImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnExportOcrText.setOnClickListener(v -> {
            if (pendingOcrText == null || pendingOcrText.trim().isEmpty()) {
                Toast.makeText(this, "尚無可匯出的辨識文字", Toast.LENGTH_SHORT).show();
                return;
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            exportTextLauncher.launch("ScanMate_OCR_" + timestamp + ".txt");
        });

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
        String regionReport = reportResult.toJava(String.class);
        Bitmap sourceBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        txtTextReport.setText(regionReport + "\n\n正在進行 OCR 文字辨識...");
        runMlKitOcr(sourceBitmap, regionReport);
    }

    private void runMlKitOcr(Bitmap bitmap, String regionReport) {
        if (bitmap == null) {
            txtTextReport.setText(regionReport + "\n\nOCR 失敗：圖片解碼失敗");
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(image)
                .addOnSuccessListener(result -> renderOcrResult(result, regionReport))
                .addOnFailureListener(e -> txtTextReport.setText(
                        regionReport + "\n\nOCR 失敗：\n" + e.getMessage()
                ));
    }

    private void renderOcrResult(Text result, String regionReport) {
        String text = result.getText() == null ? "" : result.getText().trim();
        pendingOcrText = text;

        int blockCount = result.getTextBlocks().size();
        StringBuilder builder = new StringBuilder();
        builder.append(regionReport).append("\n\n");
        builder.append("OCR 文字辨識完成\n");
        builder.append("文字區塊：").append(blockCount).append("\n\n");

        if (text.isEmpty()) {
            builder.append("未辨識到清楚文字，請嘗試提高亮度或重新裁切。");
        } else {
            builder.append(text);
        }
        txtTextReport.setText(builder.toString());
    }

    private void exportOcrText(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                throw new RuntimeException("Cannot open output stream");
            }
            outputStream.write(pendingOcrText.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            Toast.makeText(this, "已匯出 OCR 文字", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "匯出失敗：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
