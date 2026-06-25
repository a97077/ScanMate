package com.example.scanmate;

import android.content.ActivityNotFoundException;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
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
    private LinearLayout layoutPreviewThumbnails;
    private int currentPageIndex = 0;
    private boolean isFinishingDocument = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_preview);

        DocumentStore.init(this);

        if (ScanDraftStore.getPageCount() == 0) {
            ScanDraftStore.restoreDraft(this);
        }

        if (ScanDraftStore.getPageCount() == 0) {
            Toast.makeText(this, "目前沒有掃描頁面", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imgPreviewDocument = findViewById(R.id.imgPreviewDocument);
        txtPreviewTitle = findViewById(R.id.txtPreviewTitle);
        txtPreviewPage = findViewById(R.id.txtPreviewPage);
        layoutPreviewThumbnails = findViewById(R.id.layoutPreviewThumbnails);

        View btnBack = findViewById(R.id.btnPreviewBack);
        View btnTitleEdit = findViewById(R.id.btnPreviewTitleEdit);
        View btnGrid = findViewById(R.id.btnPreviewGrid);
        View btnMore = findViewById(R.id.btnPreviewMore);
        View btnDone = findViewById(R.id.btnPreviewDone);
        View btnContinueAdd = findViewById(R.id.btnPreviewContinueAdd);
        View btnAdd = findViewById(R.id.btnPreviewAdd);
        View btnEdit = findViewById(R.id.btnPreviewEdit);
        View btnShare = findViewById(R.id.btnPreviewShare);
        View btnSignature = findViewById(R.id.btnPreviewSignature);

        btnBack.setOnClickListener(v -> finish());
        btnTitleEdit.setOnClickListener(v -> showRenameDialog());
        btnGrid.setOnClickListener(v -> showPageActions());
        btnMore.setOnClickListener(v -> showMoreActions());
        btnDone.setOnClickListener(v -> finishDocument());
        btnContinueAdd.setOnClickListener(v -> openCaptureForAppend());
        btnAdd.setOnClickListener(v -> openCaptureForAppend());
        btnEdit.setOnClickListener(v -> editLatestPage());
        btnShare.setOnClickListener(v -> shareCurrentPdf());
        btnSignature.setOnClickListener(v -> signCurrentPage());

        renderPreview();
    }

    private void finishDocument() {
        if (isFinishingDocument) {
            return;
        }

        isFinishingDocument = true;
        Uri pdfUri = createLocalPdf();
        if (pdfUri == null) {
            isFinishingDocument = false;
            return;
        }

        Toast.makeText(this, "文件已完成並儲存到最近文檔", Toast.LENGTH_SHORT).show();
        ScanDraftStore.clear();
        ScanDraftStore.clearPersistedDraft(this);

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (imgPreviewDocument != null && ScanDraftStore.getPageCount() > 0) {
            renderPreview();
        }
    }

    private void renderPreview() {
        int pageCount = ScanDraftStore.getPageCount();
        if (pageCount == 0) {
            finish();
            return;
        }

        currentPageIndex = Math.max(0, Math.min(currentPageIndex, pageCount - 1));
        Bitmap page = ScanDraftStore.getPage(currentPageIndex);
        imgPreviewDocument.setImageBitmap(page);
        txtPreviewTitle.setText(ScanDraftStore.getDocumentTitle());
        txtPreviewPage.setText((currentPageIndex + 1) + "/" + pageCount);
        renderThumbnails();
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
        ScanDraftStore.editPage(currentPageIndex);
        startActivity(new Intent(this, ScanEditActivity.class));
    }

    private void signCurrentPage() {
        if (ScanDraftStore.getPageCount() == 0) {
            Toast.makeText(this, "尚無可簽名的頁面", Toast.LENGTH_SHORT).show();
            return;
        }
        ScanDraftStore.editPage(currentPageIndex);
        Intent intent = new Intent(this, ScanEditActivity.class);
        intent.putExtra("capture_mode", "signature");
        startActivity(intent);
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

    private void showRenameDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(ScanDraftStore.getDocumentTitle());
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle("重新命名")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("確定", (dialog, which) -> {
                    String title = input.getText() == null ? "" : input.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(this, "檔名不可空白", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ScanDraftStore.setDocumentTitle(title);
                    ScanDraftStore.saveDraft(this);
                    renderPreview();
                })
                .show();
    }

    private void showMoreActions() {
        String[] actions = {"儲存 PDF", "分享 PDF", "文件類型/命名", "頁面管理", "全部文檔"};
        new AlertDialog.Builder(this)
                .setTitle("文件操作")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        Uri pdfUri = createLocalPdf();
                        if (pdfUri != null) {
                            Toast.makeText(this, "已儲存到最近文件", Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 1) {
                        shareCurrentPdf();
                    } else if (which == 2) {
                        showDocumentTypeDialog();
                    } else if (which == 3) {
                        showPageActions();
                    } else {
                        startActivity(new Intent(this, DocumentsActivity.class));
                    }
                })
                .show();
    }

    private void showDocumentTypeDialog() {
        String[] types = DocumentTypeHelper.supportedTypes();
        new AlertDialog.Builder(this)
                .setTitle("文件類型與命名")
                .setItems(types, (dialog, which) -> {
                    String type = types[which];
                    ScanDraftStore.setDocumentType(type, true);
                    ScanDraftStore.saveDraft(this);
                    Toast.makeText(
                            this,
                            "已設定文件類型：" + type + "，儲存時會使用類型命名",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .show();
    }

    private void renderThumbnails() {
        layoutPreviewThumbnails.removeAllViews();
        int pageCount = ScanDraftStore.getPageCount();
        for (int i = 0; i < pageCount; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(4), dp(4), dp(4), dp(3));
            item.setBackground(rounded(index == currentPageIndex ? "#2DBEA6" : "#303136", dp(8)));
            item.setOnClickListener(v -> {
                currentPageIndex = index;
                renderPreview();
            });

            ImageView thumbnail = new ImageView(this);
            thumbnail.setImageBitmap(ScanDraftStore.getPage(index));
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackgroundColor(Color.parseColor("#111113"));
            item.addView(thumbnail, new LinearLayout.LayoutParams(dp(56), dp(58)));

            TextView label = new TextView(this);
            label.setText(String.valueOf(index + 1));
            label.setGravity(android.view.Gravity.CENTER);
            label.setTextColor(Color.WHITE);
            label.setTextSize(12);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            item.addView(label, new LinearLayout.LayoutParams(dp(56), dp(20)));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, dp(10), 0);
            layoutPreviewThumbnails.addView(item, params);
        }
    }

    private void showPageActions() {
        String[] actions = {"編輯本頁", "刪除此頁", "本頁前移", "本頁後移", "繼續添加"};
        new AlertDialog.Builder(this)
                .setTitle("頁面管理 " + (currentPageIndex + 1) + "/" + ScanDraftStore.getPageCount())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        editLatestPage();
                    } else if (which == 1) {
                        deleteCurrentPage();
                    } else if (which == 2) {
                        moveCurrentPage(-1);
                    } else if (which == 3) {
                        moveCurrentPage(1);
                    } else {
                        openCaptureForAppend();
                    }
                })
                .show();
    }

    private void deleteCurrentPage() {
        ScanDraftStore.removePage(currentPageIndex);
        if (ScanDraftStore.getPageCount() == 0) {
            ScanDraftStore.clear();
            ScanDraftStore.clearPersistedDraft(this);
            finish();
            return;
        }
        currentPageIndex = Math.min(currentPageIndex, ScanDraftStore.getPageCount() - 1);
        ScanDraftStore.saveDraft(this);
        renderPreview();
        Toast.makeText(this, "已刪除此頁", Toast.LENGTH_SHORT).show();
    }

    private void moveCurrentPage(int direction) {
        int targetIndex = currentPageIndex + direction;
        if (targetIndex < 0 || targetIndex >= ScanDraftStore.getPageCount()) {
            Toast.makeText(this, "此頁已在邊界", Toast.LENGTH_SHORT).show();
            return;
        }
        ScanDraftStore.movePage(currentPageIndex, targetIndex);
        currentPageIndex = targetIndex;
        ScanDraftStore.saveDraft(this);
        renderPreview();
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
                    "PDF",
                    ScanDraftStore.getDocumentType()
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

    private String generatePdfFileName() {
        return DocumentTypeHelper.buildPdfFileName(
                ScanDraftStore.getDocumentType(),
                ScanDraftStore.shouldUseTypeAwareName()
        );
    }

    private GradientDrawable rounded(String color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
