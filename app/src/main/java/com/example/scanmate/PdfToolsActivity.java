package com.example.scanmate;

import android.content.ActivityNotFoundException;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfToolsActivity extends AppCompatActivity {

    private TextView txtPdfToolsStatus;
    private DocumentItem pendingCopyDocument;

    private final ActivityResultLauncher<String[]> importDocumentLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            importPdfDocument(uri);
                        }
                    }
            );

    private final ActivityResultLauncher<String[]> mergeDocumentLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            mergeLatestWith(uri);
                        }
                    }
            );

    private final ActivityResultLauncher<String> copyDocumentLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/pdf"),
                    uri -> {
                        if (uri != null && pendingCopyDocument != null) {
                            copyPdfToUri(pendingCopyDocument, uri);
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_tools);

        DocumentStore.init(this);
        txtPdfToolsStatus = findViewById(R.id.txtPdfToolsStatus);

        findViewById(R.id.btnPdfBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPdfScan).setOnClickListener(v -> {
            Intent intent = new Intent(this, CameraCaptureActivity.class);
            intent.putExtra("capture_mode", "scan");
            startActivity(intent);
        });
        findViewById(R.id.btnPdfImport).setOnClickListener(v ->
                importDocumentLauncher.launch(new String[]{"application/pdf"})
        );
        findViewById(R.id.btnPdfOpenLatest).setOnClickListener(v -> openLatestPdf());
        findViewById(R.id.btnPdfShareLatest).setOnClickListener(v -> shareLatestPdf());
        findViewById(R.id.btnPdfCopyLatest).setOnClickListener(v -> copyLatestPdf());
        findViewById(R.id.btnPdfMerge).setOnClickListener(v ->
                mergeDocumentLauncher.launch(new String[]{"application/pdf"})
        );
        findViewById(R.id.btnPdfSplitFirst).setOnClickListener(v -> splitFirstPage());
        findViewById(R.id.btnPdfDeleteFirst).setOnClickListener(v -> deleteFirstPage());
        findViewById(R.id.btnPdfReverse).setOnClickListener(v -> reverseLatestPdf());
        findViewById(R.id.btnPdfRotate).setOnClickListener(v -> rotateLatestPdf());
        findViewById(R.id.btnPdfCompress).setOnClickListener(v -> compressLatestPdf());
        findViewById(R.id.btnPdfToImage).setOnClickListener(v -> exportFirstPageImage());
        findViewById(R.id.btnPdfToLongImage).setOnClickListener(v -> exportLongImage());
        findViewById(R.id.btnPdfAllDocs).setOnClickListener(v ->
                startActivity(new Intent(this, DocumentsActivity.class))
        );

        renderStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStatus();
    }

    private void renderStatus() {
        DocumentItem latest = DocumentStore.getLatestPdf();
        if (latest == null) {
            txtPdfToolsStatus.setText("尚無 PDF。請先掃描圖片產生 PDF，或導入既有 PDF。");
            return;
        }

        txtPdfToolsStatus.setText(
                "最近 PDF\n\n" +
                        "檔名：" + latest.title + "\n" +
                        "時間：" + latest.dateTime + "\n" +
                        "頁數：" + latest.pageCount + "\n\n" +
                        "可直接開啟、分享，或到全部文檔管理。"
        );
    }

    private void openLatestPdf() {
        DocumentItem latest = DocumentStore.getLatestPdf();
        if (latest == null) {
            showToast("請先儲存或導入 PDF");
            return;
        }

        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(latest.pdfUri, "application/pdf");
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(openIntent);
        } catch (ActivityNotFoundException e) {
            showToast("找不到可開啟 PDF 的應用程式");
        }
    }

    private void shareLatestPdf() {
        DocumentItem latest = DocumentStore.getLatestPdf();
        if (latest == null) {
            showToast("請先儲存或導入 PDF");
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, latest.pdfUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(shareIntent, "分享 PDF"));
        } catch (ActivityNotFoundException e) {
            showToast("找不到可分享 PDF 的應用程式");
        }
    }

    private void copyLatestPdf() {
        DocumentItem latest = DocumentStore.getLatestPdf();
        if (latest == null) {
            showToast("請先儲存或導入 PDF");
            return;
        }

        pendingCopyDocument = latest;
        String fileName = latest.title.endsWith(".pdf")
                ? latest.title.replace(".pdf", "_copy.pdf")
                : latest.title + "_copy.pdf";
        copyDocumentLauncher.launch(fileName);
    }

    private void copyPdfToUri(DocumentItem sourceDocument, Uri targetUri) {
        try (InputStream inputStream = getContentResolver().openInputStream(sourceDocument.pdfUri);
             OutputStream outputStream = getContentResolver().openOutputStream(targetUri)) {
            if (inputStream == null || outputStream == null) {
                throw new RuntimeException("Cannot open PDF stream");
            }

            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();

            String title = resolveDisplayName(targetUri, sourceDocument.title);
            String dateTime = formatDisplayTime(System.currentTimeMillis());
            DocumentStore.add(new DocumentItem(
                    title,
                    dateTime,
                    sourceDocument.pageCount,
                    targetUri,
                    "PDF",
                    sourceDocument.documentType
            ));
            renderStatus();
            showToast("已另存為 PDF：" + title);
        } catch (Exception e) {
            showToast("另存 PDF 失敗：" + e.getMessage());
        } finally {
            pendingCopyDocument = null;
        }
    }

    private void mergeLatestWith(Uri secondPdfUri) {
        DocumentItem latest = getLatestOrToast();
        if (latest == null) {
            return;
        }
        persistReadPermission(secondPdfUri);

        try {
            List<Bitmap> pages = renderPdfPages(latest.pdfUri, 900, Integer.MAX_VALUE);
            pages.addAll(renderPdfPages(secondPdfUri, 900, Integer.MAX_VALUE));
            Uri uri = createPdfFromBitmaps("merged", pages);
            showToast(uri == null ? "合併失敗" : "已合併 PDF");
        } catch (Exception e) {
            showToast("合併失敗：" + e.getMessage());
        }
    }

    private void splitFirstPage() {
        DocumentItem latest = getLatestOrToast();
        if (latest == null) {
            return;
        }

        try {
            List<Bitmap> pages = renderPdfPages(latest.pdfUri, 1000, 1);
            Uri uri = createPdfFromBitmaps("first_page", pages);
            showToast(uri == null ? "分割失敗" : "已分割第一頁");
        } catch (Exception e) {
            showToast("分割失敗：" + e.getMessage());
        }
    }

    private void deleteFirstPage() {
        DocumentItem latest = getLatestOrToast();
        if (latest == null) {
            return;
        }
        if (latest.pageCount <= 1) {
            showToast("此 PDF 只有一頁，無法刪頁");
            return;
        }

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("輸入 1-" + latest.pageCount);
        new AlertDialog.Builder(this)
                .setTitle("刪除指定頁")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("刪除並另存", (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    int pageNumber;
                    try {
                        pageNumber = Integer.parseInt(value);
                    } catch (Exception e) {
                        showToast("頁碼格式錯誤");
                        return;
                    }
                    deletePdfPage(pageNumber - 1);
                })
                .show();
    }

    private void deletePdfPage(int pageIndex) {
        DocumentItem latest = getLatestOrToast();
        if (latest == null) {
            return;
        }
        if (pageIndex < 0 || pageIndex >= latest.pageCount) {
            showToast("頁碼超出範圍");
            return;
        }

        try {
            List<Bitmap> pages = renderPdfPages(latest.pdfUri, 900, Integer.MAX_VALUE);
            pages.remove(pageIndex);
            Uri uri = createPdfFromBitmaps("delete_page", pages);
            showToast(uri == null ? "刪頁失敗" : "已刪除第 " + (pageIndex + 1) + " 頁並另存");
        } catch (Exception e) {
            showToast("刪頁失敗：" + e.getMessage());
        }
    }

    private void reverseLatestPdf() {
        DocumentItem latest = getLatestOrToast();
        if (latest == null) {
            return;
        }

        try {
            List<Bitmap> pages = renderPdfPages(latest.pdfUri, 900, Integer.MAX_VALUE);
            Collections.reverse(pages);
            Uri uri = createPdfFromBitmaps("reordered", pages);
            showToast(uri == null ? "重排失敗" : "已倒序重排並另存");
        } catch (Exception e) {
            showToast("重排失敗：" + e.getMessage());
        }
    }

    private void rotateLatestPdf() {
        DocumentItem latest = getLatestOrToast();
        if (latest == null) {
            return;
        }

        try {
            List<Bitmap> pages = renderPdfPages(latest.pdfUri, 900, Integer.MAX_VALUE);
            ArrayList<Bitmap> rotatedPages = new ArrayList<>();
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            for (Bitmap page : pages) {
                rotatedPages.add(Bitmap.createBitmap(page, 0, 0, page.getWidth(), page.getHeight(), matrix, true));
            }
            Uri uri = createPdfFromBitmaps("rotated", rotatedPages);
            showToast(uri == null ? "旋轉失敗" : "已旋轉並另存 PDF");
        } catch (Exception e) {
            showToast("旋轉失敗：" + e.getMessage());
        }
    }

    private void compressLatestPdf() {
        DocumentItem latest = getLatestOrToast();
        if (latest == null) {
            return;
        }

        try {
            List<Bitmap> pages = renderPdfPages(latest.pdfUri, 560, Integer.MAX_VALUE);
            Uri uri = createPdfFromBitmaps("compressed", pages);
            showToast(uri == null ? "壓縮失敗" : "已壓縮並另存 PDF");
        } catch (Exception e) {
            showToast("壓縮失敗：" + e.getMessage());
        }
    }

    private void exportFirstPageImage() {
        DocumentItem latest = getLatestOrToast();
        if (latest == null) {
            return;
        }

        try {
            List<Bitmap> pages = renderPdfPages(latest.pdfUri, 1200, 1);
            if (pages.isEmpty()) {
                showToast("沒有可轉換的頁面");
                return;
            }
            Uri uri = saveBitmapAsPng(pages.get(0), "page_image", "PNG");
            showToast(uri == null ? "轉圖片失敗" : "已輸出第一頁圖片");
        } catch (Exception e) {
            showToast("轉圖片失敗：" + e.getMessage());
        }
    }

    private void exportLongImage() {
        DocumentItem latest = getLatestOrToast();
        if (latest == null) {
            return;
        }

        try {
            List<Bitmap> pages = renderPdfPages(latest.pdfUri, 900, 8);
            if (pages.isEmpty()) {
                showToast("沒有可轉換的頁面");
                return;
            }

            int spacing = 20;
            int totalHeight = 0;
            for (Bitmap page : pages) {
                totalHeight += page.getHeight() + spacing;
            }

            Bitmap longImage = Bitmap.createBitmap(900, Math.max(1, totalHeight), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(longImage);
            canvas.drawColor(Color.WHITE);
            int top = 0;
            for (Bitmap page : pages) {
                canvas.drawBitmap(page, 0, top, null);
                top += page.getHeight() + spacing;
            }

            Uri uri = saveBitmapAsPng(longImage, "long_image", "LongImage");
            showToast(uri == null ? "轉長圖失敗" : "已輸出長圖片");
        } catch (Exception e) {
            showToast("轉長圖失敗：" + e.getMessage());
        }
    }

    private DocumentItem getLatestOrToast() {
        DocumentItem latest = DocumentStore.getLatestPdf();
        if (latest == null || latest.pdfUri == null) {
            showToast("請先儲存或導入 PDF");
            return null;
        }
        return latest;
    }

    private List<Bitmap> renderPdfPages(Uri uri, int targetWidth, int maxPages) throws Exception {
        ArrayList<Bitmap> pages = new ArrayList<>();
        ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "r");
        if (descriptor == null) {
            throw new RuntimeException("Cannot open PDF");
        }

        PdfRenderer renderer = new PdfRenderer(descriptor);
        try {
            int count = Math.min(renderer.getPageCount(), maxPages);
            for (int i = 0; i < count; i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                int width = targetWidth;
                int height = Math.max(1, page.getHeight() * width / page.getWidth());
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
                pages.add(bitmap);
            }
        } finally {
            renderer.close();
            descriptor.close();
        }
        return pages;
    }

    private Uri createPdfFromBitmaps(String suffix, List<Bitmap> pages) {
        if (pages == null || pages.isEmpty()) {
            return null;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String pdfName = "ScanMate_" + suffix + "_" + timestamp + ".pdf";
        File dir = new File(getExternalFilesDir(null), "ScanMatePDF");
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }

        File pdfFile = new File(dir, pdfName);
        PdfDocument pdfDocument = new PdfDocument();
        try (FileOutputStream outputStream = new FileOutputStream(pdfFile)) {
            int pageWidth = 595;
            int pageHeight = 842;
            for (int i = 0; i < pages.size(); i++) {
                Bitmap bitmap = pages.get(i);
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create();
                PdfDocument.Page page = pdfDocument.startPage(pageInfo);
                Canvas canvas = page.getCanvas();
                canvas.drawColor(Color.WHITE);
                float scale = Math.min((float) pageWidth / bitmap.getWidth(), (float) pageHeight / bitmap.getHeight());
                float left = (pageWidth - bitmap.getWidth() * scale) / 2f;
                float top = (pageHeight - bitmap.getHeight() * scale) / 2f;
                canvas.save();
                canvas.translate(left, top);
                canvas.scale(scale, scale);
                canvas.drawBitmap(bitmap, 0, 0, null);
                canvas.restore();
                pdfDocument.finishPage(page);
            }
            pdfDocument.writeTo(outputStream);
            outputStream.flush();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdfFile);
            DocumentItem sourceDocument = DocumentStore.getLatestPdf();
            String documentType = sourceDocument == null
                    ? DocumentTypeHelper.TYPE_GENERAL
                    : sourceDocument.documentType;
            DocumentStore.add(new DocumentItem(
                    pdfName,
                    formatDisplayTime(System.currentTimeMillis()),
                    pages.size(),
                    uri,
                    "PDF",
                    documentType
            ));
            renderStatus();
            return uri;
        } catch (Exception ignored) {
            return null;
        } finally {
            pdfDocument.close();
        }
    }

    private Uri saveBitmapAsPng(Bitmap bitmap, String suffix, String type) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "ScanMate_" + suffix + "_" + timestamp + ".png";
        File dir = new File(getExternalFilesDir(null), "ScanMateImages");
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }

        File outputFile = new File(dir, fileName);
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", outputFile);
            DocumentItem sourceDocument = DocumentStore.getLatestPdf();
            String documentType = sourceDocument == null
                    ? DocumentTypeHelper.TYPE_GENERAL
                    : sourceDocument.documentType;
            DocumentStore.add(new DocumentItem(
                    fileName,
                    formatDisplayTime(System.currentTimeMillis()),
                    1,
                    uri,
                    type,
                    documentType
            ));
            renderStatus();
            return uri;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void importPdfDocument(Uri uri) {
        persistReadPermission(uri);

        String title = resolveDisplayName(uri, "Imported_" + formatDisplayTime(System.currentTimeMillis()) + ".pdf");
        int pageCount = countPdfPages(uri);
        String dateTime = formatDisplayTime(System.currentTimeMillis());

        DocumentStore.add(new DocumentItem(title, dateTime, pageCount, uri, "PDF"));
        renderStatus();
        showToast("已導入：" + title);
    }

    private void persistReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private int countPdfPages(Uri uri) {
        ParcelFileDescriptor descriptor = null;
        PdfRenderer renderer = null;

        try {
            descriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (descriptor == null) return 1;
            renderer = new PdfRenderer(descriptor);
            return Math.max(1, renderer.getPageCount());
        } catch (Exception ignored) {
            return 1;
        } finally {
            try {
                if (renderer != null) renderer.close();
                if (descriptor != null) descriptor.close();
            } catch (Exception ignored) {
            }
        }
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
            if (cursor != null) cursor.close();
        }
        return fallbackName;
    }

    private String formatDisplayTime(long timeMillis) {
        return new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                .format(new Date(timeMillis));
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
