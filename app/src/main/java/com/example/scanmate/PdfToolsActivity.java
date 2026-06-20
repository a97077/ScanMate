package com.example.scanmate;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
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
        findViewById(R.id.btnPdfScan).setOnClickListener(v ->
                startActivity(new Intent(this, ScanActivity.class))
        );
        findViewById(R.id.btnPdfImport).setOnClickListener(v ->
                importDocumentLauncher.launch(new String[]{"application/pdf"})
        );
        findViewById(R.id.btnPdfOpenLatest).setOnClickListener(v -> openLatestPdf());
        findViewById(R.id.btnPdfShareLatest).setOnClickListener(v -> shareLatestPdf());
        findViewById(R.id.btnPdfCopyLatest).setOnClickListener(v -> copyLatestPdf());
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
            DocumentStore.add(new DocumentItem(title, dateTime, sourceDocument.pageCount, targetUri, "PDF"));
            renderStatus();
            showToast("已另存為 PDF：" + title);
        } catch (Exception e) {
            showToast("另存 PDF 失敗：" + e.getMessage());
        } finally {
            pendingCopyDocument = null;
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
