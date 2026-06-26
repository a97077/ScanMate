package com.example.scanmate;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TextExtractActivity extends AppCompatActivity {

    public static final String EXTRA_AUTO_START_MODE = "auto_start_mode";
    public static final String EXTRA_RETURN_TO_AI = "return_to_ai";
    public static final String EXTRA_MODE_CAMERA = "camera";
    public static final String EXTRA_MODE_GALLERY = "gallery";

    private ImageView imgTextPreview;
    private TextView txtTextReport;
    private Python py;
    private String pendingOcrText = "";
    private Uri pendingCameraUri;
    private boolean returnToAiAfterOcr = false;
    private boolean autoInputLaunched = false;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            processImage(uri, false);
                        }
                    }
            );

    private final ActivityResultLauncher<Uri> captureImageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.TakePicture(),
                    success -> {
                        if (success && pendingCameraUri != null) {
                            processImage(pendingCameraUri, true);
                        } else {
                            Toast.makeText(this, "未取得拍照影像", Toast.LENGTH_SHORT).show();
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

        DocumentStore.init(this);

        imgTextPreview = findViewById(R.id.imgTextPreview);
        txtTextReport = findViewById(R.id.txtTextReport);
        Button btnSelectTextImage = findViewById(R.id.btnSelectTextImage);
        Button btnCaptureTextImage = findViewById(R.id.btnCaptureTextImage);
        Button btnExportOcrText = findViewById(R.id.btnExportOcrText);
        Button btnSendToAiStudy = findViewById(R.id.btnSendToAiStudy);
        String autoStartMode = getIntent().getStringExtra(EXTRA_AUTO_START_MODE);
        returnToAiAfterOcr = getIntent().getBooleanExtra(EXTRA_RETURN_TO_AI, false);

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();

        findViewById(R.id.btnTextBack).setOnClickListener(v -> finish());
        btnSelectTextImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnCaptureTextImage.setOnClickListener(v -> launchTextCamera());
        btnExportOcrText.setOnClickListener(v -> {
            if (pendingOcrText == null || pendingOcrText.trim().isEmpty()) {
                Toast.makeText(this, "尚無可匯出的辨識文字", Toast.LENGTH_SHORT).show();
                return;
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            exportTextLauncher.launch("ScanMate_OCR_" + timestamp + ".txt");
        });
        btnSendToAiStudy.setOnClickListener(v -> openAiStudyAssistant());

        if (autoStartMode == null && ScanDraftStore.hasDraft() && ScanDraftStore.getCurrentBitmap() != null) {
            btnSelectTextImage.setText("重新選擇圖片");
            processBitmap(ScanDraftStore.getCurrentBitmap());
        } else if (autoStartMode != null && !autoStartMode.trim().isEmpty()) {
            findViewById(android.R.id.content).post(() -> launchAutoInput(autoStartMode));
        }
    }

    private void launchAutoInput(String mode) {
        if (autoInputLaunched) {
            return;
        }
        autoInputLaunched = true;
        if (EXTRA_MODE_CAMERA.equals(mode)) {
            launchTextCamera();
        } else if (EXTRA_MODE_GALLERY.equals(mode)) {
            pickImageLauncher.launch("image/*");
        }
    }

    private void processImage(Uri uri, boolean forcePortrait) {
        try {
            processImageBytes(readNormalizedImageBytes(uri, forcePortrait));
        } catch (Exception e) {
            txtTextReport.setText("文字提取預處理失敗：\n" + e.getMessage());
            Toast.makeText(this, "處理失敗", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchTextCamera() {
        try {
            File photoFile = new File(getCacheDir(), "scanmate_ocr_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photoFile
            );
            captureImageLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, "無法開啟相機：" + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        txtTextReport.setText("正在進行 OCR 前處理，請稍候...");

        new Thread(() -> {
            try {
                PyObject module = py.getModule("scan_cv");

                PyObject previewResult = module.callAttr("text_extract_preview", imageBytes);
                byte[] outPng = previewResult.toJava(byte[].class);
                Bitmap preview = BitmapFactory.decodeByteArray(outPng, 0, outPng.length);

                PyObject reportResult = module.callAttr("text_region_report", imageBytes);
                String regionReport = reportResult.toJava(String.class);
                Bitmap sourceBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                runOnUiThread(() -> {
                    imgTextPreview.setImageBitmap(preview);
                    txtTextReport.setText(regionReport + "\n\n正在進行 OCR 文字辨識...");
                    runMlKitOcr(sourceBitmap, regionReport);
                });
            } catch (Exception e) {
                runOnUiThread(() -> txtTextReport.setText("文字提取預處理失敗：\n" + e.getMessage()));
            }
        }).start();
    }

    private void runMlKitOcr(Bitmap bitmap, String regionReport) {
        if (bitmap == null) {
            txtTextReport.setText(regionReport + "\n\nOCR 失敗：圖片解碼失敗");
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build()
        );
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

        if (returnToAiAfterOcr && !text.isEmpty()) {
            returnToAiAfterOcr = false;
            Intent intent = new Intent(this, AIStudyActivity.class);
            intent.putExtra(AIStudyActivity.EXTRA_OCR_TEXT, text);
            startActivity(intent);
            finish();
        }
    }

    private void openAiStudyAssistant() {
        if (pendingOcrText == null || pendingOcrText.trim().isEmpty()) {
            Toast.makeText(this, "請先完成 OCR 辨識", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, AIStudyActivity.class);
        intent.putExtra(AIStudyActivity.EXTRA_OCR_TEXT, pendingOcrText.trim());
        startActivity(intent);
    }

    private void exportOcrText(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                throw new RuntimeException("Cannot open output stream");
            }
            outputStream.write(pendingOcrText.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            recordExportedText(uri);
            Toast.makeText(this, "已匯出 OCR 文字", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "匯出失敗：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void recordExportedText(Uri uri) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String title = resolveDisplayName(uri, "ScanMate_OCR_" + timestamp + ".txt");
        String dateTime = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date());
        DocumentStore.add(new DocumentItem(title, dateTime, 1, uri, "TXT"));
    }

    private String resolveDisplayName(Uri uri, String fallbackName) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String displayName = cursor.getString(nameIndex);
                    if (displayName != null && !displayName.trim().isEmpty()) {
                        return displayName;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return fallbackName;
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

    private byte[] readNormalizedImageBytes(Uri uri, boolean forcePortrait) throws Exception {
        byte[] rawBytes = readBytesFromUri(uri);
        Bitmap bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.length);
        Bitmap normalized = BitmapOrientationHelper.applyExifAndPortrait(
                getContentResolver(),
                uri,
                bitmap,
                forcePortrait
        );

        if (normalized == null) {
            return rawBytes;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        normalized.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        return outputStream.toByteArray();
    }
}
