package com.example.scanmate;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScanPreviewActivity extends AppCompatActivity {

    private ImageView imgPreviewDocument;
    private TextView txtPreviewTitle;
    private TextView txtPreviewPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_preview);

        DocumentStore.init(this);

        if (ScanDraftStore.getPageCount() == 0) {
            Toast.makeText(this, "目前沒有掃描頁面", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imgPreviewDocument = findViewById(R.id.imgPreviewDocument);
        txtPreviewTitle = findViewById(R.id.txtPreviewTitle);
        txtPreviewPage = findViewById(R.id.txtPreviewPage);

        View btnBack = findViewById(R.id.btnPreviewBack);
        View btnTitleEdit = findViewById(R.id.btnPreviewTitleEdit);
        View btnGrid = findViewById(R.id.btnPreviewGrid);
        View btnMore = findViewById(R.id.btnPreviewMore);
        View btnContinueAdd = findViewById(R.id.btnPreviewContinueAdd);
        View btnAdd = findViewById(R.id.btnPreviewAdd);
        View btnEdit = findViewById(R.id.btnPreviewEdit);
        View btnShare = findViewById(R.id.btnPreviewShare);
        View btnConvertWord = findViewById(R.id.btnPreviewConvertWord);
        View btnSignature = findViewById(R.id.btnPreviewSignature);

        btnBack.setOnClickListener(v -> finish());
        btnTitleEdit.setOnClickListener(v -> Toast.makeText(this, "檔名編輯將在下一階段補上", Toast.LENGTH_SHORT).show());
        btnGrid.setOnClickListener(v -> startActivity(new Intent(this, DocumentsActivity.class)));
        btnMore.setOnClickListener(v -> Toast.makeText(this, "更多文件操作將在下一階段補上", Toast.LENGTH_SHORT).show());
        btnContinueAdd.setOnClickListener(v -> openCaptureForAppend());
        btnAdd.setOnClickListener(v -> openCaptureForAppend());
        btnEdit.setOnClickListener(v -> editLatestPage());
        btnShare.setOnClickListener(v -> shareCurrentPdf());
        btnConvertWord.setOnClickListener(v -> openToolFeature("word", "轉 Word"));
        btnSignature.setOnClickListener(v -> openToolFeature("signature", "電子簽名"));

        renderPreview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (imgPreviewDocument != null && ScanDraftStore.getPageCount() > 0) {
            renderPreview();
        }
    }

    private void renderPreview() {
        Bitmap latest = ScanDraftStore.getLatestPage();
        int pageCount = ScanDraftStore.getPageCount();
        imgPreviewDocument.setImageBitmap(latest);
        txtPreviewTitle.setText(ScanDraftStore.getDocumentTitle());
        txtPreviewPage.setText(pageCount + "/" + pageCount);
    }

    private void openCaptureForAppend() {
        Intent intent = new Intent(this, CameraCaptureActivity.class);
        intent.putExtra("document_title", ScanDraftStore.getDocumentTitle());
        intent.putExtra("capture_mode", "append");
        startActivity(intent);
    }

    private void editLatestPage() {
        int pageCount = ScanDraftStore.getPageCount();
        if (pageCount == 0) {
            return;
        }
        ScanDraftStore.editPage(pageCount - 1);
        startActivity(new Intent(this, ScanEditActivity.class));
    }

    private void shareCurrentPdf() {
        Uri pdfUri = createLocalPdf();
        if (pdfUri == null) {
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(shareIntent, "分享 PDF"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "找不到可分享 PDF 的應用程式", Toast.LENGTH_SHORT).show();
        }
    }

    private Uri createLocalPdf() {
        List<Bitmap> pages = ScanDraftStore.getPages();
        if (pages.isEmpty()) {
            Toast.makeText(this, "請先新增掃描頁面", Toast.LENGTH_SHORT).show();
            return null;
        }

        String pdfName = generatePdfFileName();
        File dir = new File(getExternalFilesDir(null), "ScanMatePDF");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "無法建立 PDF 資料夾", Toast.LENGTH_SHORT).show();
            return null;
        }

        File pdfFile = new File(dir, pdfName);
        PdfDocument pdfDocument = new PdfDocument();

        try (FileOutputStream outputStream = new FileOutputStream(pdfFile)) {
            int pageWidth = 595;
            int pageHeight = 842;
            for (int i = 0; i < pages.size(); i++) {
                Bitmap bitmap = pages.get(i);
                PdfDocument.PageInfo pageInfo =
                        new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create();
                PdfDocument.Page page = pdfDocument.startPage(pageInfo);
                Canvas canvas = page.getCanvas();
                canvas.drawColor(Color.WHITE);

                float scale = Math.min(
                        (float) pageWidth / bitmap.getWidth(),
                        (float) pageHeight / bitmap.getHeight()
                );
                float scaledWidth = bitmap.getWidth() * scale;
                float scaledHeight = bitmap.getHeight() * scale;
                float left = (pageWidth - scaledWidth) / 2f;
                float top = (pageHeight - scaledHeight) / 2f;

                canvas.save();
                canvas.translate(left, top);
                canvas.scale(scale, scale);
                canvas.drawBitmap(bitmap, 0, 0, null);
                canvas.restore();
                pdfDocument.finishPage(page);
            }

            pdfDocument.writeTo(outputStream);
            outputStream.flush();

            Uri pdfUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdfFile);
            DocumentStore.add(new DocumentItem(
                    pdfName,
                    new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date()),
                    pages.size(),
                    pdfUri,
                    "PDF"
            ));
            Toast.makeText(this, "已建立 PDF：" + pdfName, Toast.LENGTH_SHORT).show();
            return pdfUri;
        } catch (Exception e) {
            Toast.makeText(this, "PDF 建立失敗：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        } finally {
            pdfDocument.close();
        }
    }

    private void openToolFeature(String type, String title) {
        Intent intent = new Intent(this, ToolFeatureActivity.class);
        intent.putExtra(ToolFeatureActivity.EXTRA_TYPE, type);
        intent.putExtra(ToolFeatureActivity.EXTRA_TITLE, title);
        startActivity(intent);
    }

    private String generatePdfFileName() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return "ScanMate_" + timestamp + ".pdf";
    }
}
