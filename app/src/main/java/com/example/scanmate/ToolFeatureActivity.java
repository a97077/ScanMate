package com.example.scanmate;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfRenderer;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.View;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ToolFeatureActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "feature_type";
    public static final String EXTRA_TITLE = "feature_title";

    private String featureType;
    private String featureTitle;
    private TextView txtFeatureTitle;
    private TextView txtFeatureReport;
    private ImageView imgFeaturePreview;
    private Button btnSelectImage;
    private Button btnCaptureImage;
    private Button btnSelectPdf;
    private Button btnExport;
    private Python py;
    private String pendingText;
    private Bitmap pendingBitmap;
    private String pendingExportType;
    private Uri pendingCameraUri;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) processImage(uri, false);
            });

    private final ActivityResultLauncher<Uri> captureImageLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingCameraUri != null) {
                    processImage(pendingCameraUri, true);
                } else {
                    Toast.makeText(this, "未取得拍照影像", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String[]> pickPdfLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) processPdf(uri);
            });

    private final ActivityResultLauncher<String> createTextLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
                if (uri != null && pendingText != null) writeText(uri);
            });

    private final ActivityResultLauncher<String> createImageLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("image/png"), uri -> {
                if (uri != null && pendingBitmap != null) writeBitmap(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tool_feature);

        DocumentStore.init(this);

        featureType = getIntent().getStringExtra(EXTRA_TYPE);
        featureTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (featureType == null) featureType = "general";
        if (featureTitle == null) featureTitle = "工具";

        txtFeatureTitle = findViewById(R.id.txtFeatureTitle);
        txtFeatureReport = findViewById(R.id.txtFeatureReport);
        imgFeaturePreview = findViewById(R.id.imgFeaturePreview);
        btnSelectImage = findViewById(R.id.btnToolSelectImage);
        btnCaptureImage = findViewById(R.id.btnToolCaptureImage);
        btnSelectPdf = findViewById(R.id.btnToolSelectPdf);
        btnExport = findViewById(R.id.btnToolExport);

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();

        findViewById(R.id.btnToolBack).setOnClickListener(v -> finish());
        btnSelectImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnCaptureImage.setOnClickListener(v -> launchToolCamera());
        btnSelectPdf.setOnClickListener(v -> pickPdfLauncher.launch(new String[]{"application/pdf"}));
        btnExport.setOnClickListener(v -> exportCurrentResult());

        setupFeature();
    }

    private void setupFeature() {
        txtFeatureTitle.setText(featureTitle);
        txtFeatureReport.setText(featureDescription());

        boolean imageFeature = isImageFeature();
        boolean pdfFeature = isPdfFeature();
        boolean exportFeature = isExportFeature();

        btnSelectImage.setVisibility(imageFeature ? View.VISIBLE : View.GONE);
        btnCaptureImage.setVisibility(imageFeature ? View.VISIBLE : View.GONE);
        btnSelectPdf.setVisibility(pdfFeature ? View.VISIBLE : View.GONE);
        btnExport.setVisibility((exportFeature || pdfFeature) ? View.VISIBLE : View.GONE);

        if (exportFeature) {
            pendingText = buildExportContent();
            pendingExportType = exportDocumentType();
            btnExport.setText("匯出結果");
            txtFeatureReport.setText(featureDescription() + "\n\n已準備好匯出內容。");
        }
    }

    private boolean isImageFeature() {
        return featureType.equals("id_photo")
                || featureType.equals("formula")
                || featureType.equals("translate")
                || featureType.equals("watermark");
    }

    private boolean isPdfFeature() {
        return featureType.equals("pdf_to_image")
                || featureType.equals("long_image")
                || featureType.equals("page_sort")
                || featureType.equals("signature");
    }

    private boolean isExportFeature() {
        return featureType.equals("word")
                || featureType.equals("excel")
                || featureType.equals("ppt");
    }

    private String featureDescription() {
        switch (featureType) {
            case "id_photo":
                return "選擇照片後會產生證件照比例預覽。";
            case "formula":
                return "選擇圖片後會標示疑似公式或文字行區塊。";
            case "translate":
                return "選擇圖片後會產生拍照翻譯前處理預覽，可銜接 OCR/翻譯服務。";
            case "watermark":
                return "選擇圖片後會加入 ScanMate 浮水印。";
            case "pdf_to_image":
                return "選擇 PDF 後會輸出第一頁 PNG。";
            case "long_image":
                return "選擇 PDF 後會把前幾頁接成長圖片 PNG。";
            case "page_sort":
                return "選擇 PDF 後會產生頁面順序預覽，方便展示頁面管理流程。";
            case "signature":
                return "選擇 PDF 後會在第一頁預覽加上 ScanMate 簽名章。";
            case "word":
                return "匯出 Word 文字大綱，可貼到 Word 編輯。";
            case "excel":
                return "匯出 CSV 文件清單，可用 Excel 開啟。";
            case "ppt":
                return "匯出 PPT 簡報大綱，可貼到簡報軟體。";
            default:
                return "此工具已建立基礎處理流程，可接續擴充正式服務。";
        }
    }

    private void processImage(Uri uri, boolean forcePortrait) {
        try {
            byte[] imageBytes = readNormalizedImageBytes(uri, forcePortrait);
            PyObject module = py.getModule("scan_cv");
            String functionName = imageFunctionName();

            PyObject result = module.callAttr(functionName, imageBytes);
            byte[] outPng = result.toJava(byte[].class);
            Bitmap preview = BitmapFactory.decodeByteArray(outPng, 0, outPng.length);
            pendingBitmap = preview;
            pendingExportType = "PNG";
            imgFeaturePreview.setImageBitmap(preview);
            txtFeatureReport.setText(featureDescription() + "\n\n處理完成，可匯出 PNG 預覽。");
            btnExport.setVisibility(View.VISIBLE);
            btnExport.setText("匯出 PNG");
        } catch (Exception e) {
            txtFeatureReport.setText("處理失敗：\n" + e.getMessage());
        }
    }

    private void launchToolCamera() {
        try {
            File photoFile = new File(getCacheDir(), "scanmate_tool_" + System.currentTimeMillis() + ".jpg");
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

    private String imageFunctionName() {
        switch (featureType) {
            case "id_photo":
                return "id_photo_preview";
            case "formula":
                return "formula_preview";
            case "translate":
                return "translate_preview";
            case "watermark":
                return "watermark_preview";
            default:
                return "clahe_process";
        }
    }

    private void processPdf(Uri uri) {
        ParcelFileDescriptor descriptor = null;
        PdfRenderer renderer = null;

        try {
            descriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (descriptor == null) throw new RuntimeException("Cannot open PDF");

            renderer = new PdfRenderer(descriptor);
            int pageCount = renderer.getPageCount();
            if (pageCount == 0) throw new RuntimeException("PDF has no pages");

            if (featureType.equals("long_image")) {
                pendingBitmap = renderLongImage(renderer);
            } else {
                pendingBitmap = renderFirstPage(renderer);
                if (featureType.equals("signature")) {
                    drawSignature(pendingBitmap);
                } else if (featureType.equals("page_sort")) {
                    drawPageSortLabel(pendingBitmap, pageCount);
                }
            }

            imgFeaturePreview.setImageBitmap(pendingBitmap);
            pendingExportType = "PNG";
            btnExport.setVisibility(View.VISIBLE);
            btnExport.setText("匯出 PNG");
            txtFeatureReport.setText(featureDescription() + "\n\nPDF 頁數：" + pageCount + "\n預覽已產生。");
        } catch (Exception e) {
            txtFeatureReport.setText("PDF 處理失敗：\n" + e.getMessage());
        } finally {
            try {
                if (renderer != null) renderer.close();
                if (descriptor != null) descriptor.close();
            } catch (Exception ignored) {
            }
        }
    }

    private Bitmap renderFirstPage(PdfRenderer renderer) {
        PdfRenderer.Page page = renderer.openPage(0);
        int width = 900;
        int height = Math.max(1, page.getHeight() * width / page.getWidth());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        return bitmap;
    }

    private Bitmap renderLongImage(PdfRenderer renderer) {
        int limit = Math.min(renderer.getPageCount(), 5);
        int targetWidth = 900;
        Bitmap[] pages = new Bitmap[limit];
        int totalHeight = 0;

        for (int i = 0; i < limit; i++) {
            PdfRenderer.Page page = renderer.openPage(i);
            int height = Math.max(1, page.getHeight() * targetWidth / page.getWidth());
            Bitmap bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            pages[i] = bitmap;
            totalHeight += height + 18;
        }

        Bitmap output = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.WHITE);

        int top = 0;
        for (Bitmap pageBitmap : pages) {
            canvas.drawBitmap(pageBitmap, 0, top, null);
            top += pageBitmap.getHeight() + 18;
        }
        return output;
    }

    private void drawSignature(Bitmap bitmap) {
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(85, 195, 168));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        canvas.drawRoundRect(40, 40, 360, 150, 24, 24, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(42f);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText("ScanMate 簽章", 64, 108, paint);
    }

    private void drawPageSortLabel(Bitmap bitmap, int pageCount) {
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.argb(210, 30, 32, 38));
        canvas.drawRect(0, 0, bitmap.getWidth(), 92, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(36f);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText("頁面排序預覽：1 / " + pageCount, 32, 58, paint);
    }

    private void exportCurrentResult() {
        if (pendingBitmap != null) {
            createImageLauncher.launch(defaultFileName(".png"));
            return;
        }

        if (pendingText == null) {
            pendingText = buildExportContent();
        }
        createTextLauncher.launch(defaultFileName(exportExtension()));
    }

    private String buildExportContent() {
        List<DocumentItem> documents = DocumentStore.getDocuments();
        String now = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date());

        if (featureType.equals("excel")) {
            StringBuilder csv = new StringBuilder();
            csv.append("title,dateTime,pageCount,type,documentType\n");
            for (DocumentItem item : documents) {
                csv.append('"').append(item.title.replace("\"", "\"\"")).append('"')
                        .append(",").append('"').append(item.dateTime).append('"')
                        .append(",").append(item.pageCount)
                        .append(",").append(item.type)
                        .append(",").append('"').append(item.documentType).append('"')
                        .append("\n");
            }
            return csv.toString();
        }

        StringBuilder builder = new StringBuilder();
        builder.append(featureTitle).append("\n");
        builder.append("Generated by ScanMate at ").append(now).append("\n\n");

        if (featureType.equals("ppt")) {
            builder.append("Slide 1: Project Overview\n");
            builder.append("- ScanMate 智慧掃描與 PDF 管理\n\n");
            builder.append("Slide 2: Core Workflow\n");
            builder.append("- 選圖、OpenCV 處理、多頁 PDF、分享\n\n");
            builder.append("Slide 3: Recent Documents\n");
            builder.append("- 目前文件數：").append(documents.size()).append("\n");
        } else {
            builder.append("文件清單\n");
            if (documents.isEmpty()) {
                builder.append("- 尚無文件\n");
            }
            for (DocumentItem item : documents) {
                builder.append("- ")
                        .append(item.title)
                        .append(" | ")
                        .append(item.dateTime)
                        .append(" | ")
                        .append(item.pageCount)
                        .append(" 頁 | ")
                        .append(item.documentType)
                        .append("\n");
            }
        }

        return builder.toString();
    }

    private String exportExtension() {
        if (featureType.equals("excel")) return ".csv";
        if (featureType.equals("ppt")) return "_ppt_outline.txt";
        return ".txt";
    }

    private String defaultFileName(String extension) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return "ScanMate_" + featureType + "_" + timestamp + extension;
    }

    private void writeText(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) throw new RuntimeException("Cannot open output stream");
            outputStream.write(pendingText.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            recordExportedDocument(uri, defaultFileName(exportExtension()), 1, pendingExportType == null ? "TXT" : pendingExportType);
            Toast.makeText(this, "已匯出", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            txtFeatureReport.setText("匯出失敗：\n" + e.getMessage());
        }
    }

    private void writeBitmap(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) throw new RuntimeException("Cannot open output stream");
            pendingBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            recordExportedDocument(uri, defaultFileName(".png"), 1, pendingExportType == null ? "PNG" : pendingExportType);
            Toast.makeText(this, "已匯出 PNG", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            txtFeatureReport.setText("匯出 PNG 失敗：\n" + e.getMessage());
        }
    }

    private String exportDocumentType() {
        if (featureType.equals("excel")) return "CSV";
        if (featureType.equals("ppt")) return "TXT";
        if (featureType.equals("word")) return "TXT";
        return "TXT";
    }

    private void recordExportedDocument(Uri uri, String fallbackName, int pageCount, String type) {
        String title = resolveDisplayName(uri, fallbackName);
        String dateTime = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date());
        DocumentStore.add(new DocumentItem(title, dateTime, pageCount, uri, type));
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
