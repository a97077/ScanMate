package com.example.scanmate;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
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

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScanActivity extends AppCompatActivity {

    private Uri lastPdfUri;
    private String lastPdfName;
    private String pendingPdfFileName;

    private TextView txtScanStatus;
    private ImageView imgOriginal;
    private ImageView imgProcessed;
    private Python py;
    private String currentProcessFunction = "canny_process";
    private String currentProcessName = "文件掃描";

    private final ArrayList<byte[]> imageBytesList = new ArrayList<>();
    private final ArrayList<Bitmap> processedBitmaps = new ArrayList<>();

    private ActivityResultLauncher<String> pickImageLauncher;

    private final ActivityResultLauncher<String> createPdfLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/pdf"),
                    uri -> {
                        if (uri != null) {
                            saveBitmapsToPdf(uri, pendingPdfFileName);
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        DocumentStore.init(this);

        txtScanStatus = findViewById(R.id.txtScanStatus);
        imgOriginal = findViewById(R.id.imgOriginal);
        imgProcessed = findViewById(R.id.imgProcessed);

        Button btnModeScan = findViewById(R.id.btnModeScan);
        Button btnModeGamma = findViewById(R.id.btnModeGamma);
        Button btnModeClahe = findViewById(R.id.btnModeClahe);
        Button btnModeDiagnose = findViewById(R.id.btnModeDiagnose);
        Button btnSelectImage = findViewById(R.id.btnSelectImage);
        Button btnSavePdf = findViewById(R.id.btnSavePdf);
        Button btnOpenPdf = findViewById(R.id.btnOpenPdf);
        Button btnSharePdf = findViewById(R.id.btnSharePdf);
        Button btnBackHome = findViewById(R.id.btnBackHome);

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();

        applyInitialMode();

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris == null || uris.isEmpty()) {
                        showStatus("尚未選擇圖片");
                        return;
                    }
                    processSelectedImages(uris);
                }
        );

        btnModeScan.setOnClickListener(v -> setProcessMode("文件掃描", "canny_process"));
        btnModeGamma.setOnClickListener(v -> setProcessMode("Gamma 校正", "gamma_process"));
        btnModeClahe.setOnClickListener(v -> setProcessMode("CLAHE 增強", "clahe_process"));
        btnModeDiagnose.setOnClickListener(v -> setProcessMode("曝光診斷", "diagnose_process"));

        btnSelectImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnSavePdf.setOnClickListener(v -> {
            if (processedBitmaps.isEmpty()) {
                showStatus("請先選擇圖片並完成掃描");
                return;
            }
            pendingPdfFileName = generatePdfFileName();
            createPdfLauncher.launch(pendingPdfFileName);
        });
        btnOpenPdf.setOnClickListener(v -> openLastPdf());
        btnSharePdf.setOnClickListener(v -> shareLastPdf());
        btnBackHome.setOnClickListener(v -> finish());
    }

    private void applyInitialMode() {
        String scanMode = getIntent().getStringExtra("scan_mode");
        if ("id".equals(scanMode)) {
            currentProcessName = "證件掃描";
            currentProcessFunction = "canny_process";
            showStatus("證件掃描模式\n請選擇身分證、學生證或名片照片");
        }
    }

    private void setProcessMode(String processName, String processFunction) {
        currentProcessName = processName;
        currentProcessFunction = processFunction;

        if (imageBytesList.isEmpty()) {
            showStatus("已切換為：" + currentProcessName + "\n請選擇圖片開始處理");
            return;
        }

        processStoredImages();
    }

    private void processSelectedImages(List<Uri> uris) {
        try {
            imageBytesList.clear();
            processedBitmaps.clear();
            lastPdfUri = null;
            lastPdfName = null;

            PyObject module = py.getModule("scan_cv");

            for (Uri uri : uris) {
                byte[] imageBytes = readBytesFromUri(uri);
                imageBytesList.add(imageBytes);
            }

            byte[] firstImageBytes = imageBytesList.get(0);
            Bitmap firstOriginal = BitmapFactory.decodeByteArray(
                    firstImageBytes,
                    0,
                    firstImageBytes.length
            );

            imgOriginal.setImageBitmap(firstOriginal);
            processStoredImages();

        } catch (Exception e) {
            showStatus("OpenCV 處理失敗：\n" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processStoredImages() {
        try {
            processedBitmaps.clear();
            lastPdfUri = null;
            lastPdfName = null;

            PyObject module = py.getModule("scan_cv");

            for (byte[] imageBytes : imageBytesList) {
                PyObject result = module.callAttr(currentProcessFunction, imageBytes);
                byte[] outPng = result.toJava(byte[].class);

                Bitmap processed = BitmapFactory.decodeByteArray(outPng, 0, outPng.length);
                if (processed == null) {
                    throw new RuntimeException("Cannot decode processed image");
                }
                processedBitmaps.add(processed);
            }

            imgProcessed.setImageBitmap(processedBitmaps.get(0));

            showStatus(
                    currentProcessName + "完成\n" +
                            "已選擇 " + processedBitmaps.size() + " 張圖片\n" +
                            "可儲存為 " + processedBitmaps.size() + " 頁 PDF"
            );
        } catch (Exception e) {
            showStatus("OpenCV 處理失敗：\n" + e.getMessage());
            e.printStackTrace();
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

    private void saveBitmapsToPdf(Uri uri, String fallbackName) {
        if (processedBitmaps.isEmpty()) {
            showStatus("請先選擇圖片並完成掃描");
            return;
        }

        PdfDocument pdfDocument = null;

        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                throw new RuntimeException("Cannot open output stream");
            }

            pdfDocument = new PdfDocument();
            int pageWidth = 595;
            int pageHeight = 842;

            for (int i = 0; i < processedBitmaps.size(); i++) {
                Bitmap bitmap = processedBitmaps.get(i);
                PdfDocument.PageInfo pageInfo =
                        new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create();
                PdfDocument.Page page = pdfDocument.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                float scale = Math.min(
                        (float) pageWidth / bitmap.getWidth(),
                        (float) pageHeight / bitmap.getHeight()
                );
                float scaledWidth = bitmap.getWidth() * scale;
                float scaledHeight = bitmap.getHeight() * scale;
                float left = (pageWidth - scaledWidth) / 2;
                float top = (pageHeight - scaledHeight) / 2;

                canvas.save();
                canvas.translate(left, top);
                canvas.scale(scale, scale);
                canvas.drawBitmap(bitmap, 0, 0, null);
                canvas.restore();

                pdfDocument.finishPage(page);
            }

            pdfDocument.writeTo(outputStream);
            outputStream.flush();

            lastPdfUri = uri;
            lastPdfName = resolveDisplayName(uri, fallbackName);
            String savedAt = formatDisplayTime(System.currentTimeMillis());
            int pageCount = processedBitmaps.size();

            persistUriPermission(lastPdfUri);
            DocumentStore.add(new DocumentItem(lastPdfName, savedAt, pageCount, lastPdfUri, "PDF"));

            showStatus(
                    "PDF 已儲存\n" +
                            "檔名：" + lastPdfName + "\n" +
                            "時間：" + savedAt + "\n" +
                            "頁數：" + pageCount + "\n" +
                            "回首頁可在最近文件查看"
            );
        } catch (Exception e) {
            showStatus("PDF 儲存失敗：\n" + e.getMessage());
            e.printStackTrace();
        } finally {
            if (pdfDocument != null) {
                pdfDocument.close();
            }
        }
    }

    private void persistUriPermission(Uri uri) {
        try {
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
        }
    }

    private void shareLastPdf() {
        if (lastPdfUri == null) {
            showStatus("請先儲存 PDF");
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, lastPdfUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(shareIntent, "分享 PDF"));
        } catch (ActivityNotFoundException e) {
            showStatus("找不到可分享 PDF 的應用程式");
        }
    }

    private void openLastPdf() {
        if (lastPdfUri == null) {
            showStatus("請先儲存 PDF");
            return;
        }

        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(lastPdfUri, "application/pdf");
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(openIntent);
        } catch (ActivityNotFoundException e) {
            showStatus("找不到可開啟 PDF 的應用程式");
        }
    }

    private String generatePdfFileName() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        return "ScanMate_" + timestamp + ".pdf";
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

        if (fallbackName == null || fallbackName.trim().isEmpty()) {
            return generatePdfFileName();
        }
        return fallbackName;
    }

    private String formatDisplayTime(long timeMillis) {
        return new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                .format(new Date(timeMillis));
    }

    private void showStatus(String message) {
        txtScanStatus.setText(message);
        Toast.makeText(this, message.split("\\n")[0], Toast.LENGTH_SHORT).show();
    }
}
