package com.example.scanmate;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DocumentsActivity extends AppCompatActivity {

    private EditText edtSearchDocuments;
    private TextView txtDocumentCount;
    private TextView txtDocumentEmpty;
    private LinearLayout layoutDocumentList;
    private String currentQuery = "";
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
        setContentView(R.layout.activity_documents);

        DocumentStore.init(this);

        edtSearchDocuments = findViewById(R.id.edtSearchDocuments);
        txtDocumentCount = findViewById(R.id.txtDocumentCount);
        txtDocumentEmpty = findViewById(R.id.txtDocumentEmpty);
        layoutDocumentList = findViewById(R.id.layoutDocumentList);

        findViewById(R.id.btnImportPdf).setOnClickListener(v ->
                importDocumentLauncher.launch(new String[]{"application/pdf"})
        );
        findViewById(R.id.btnClearDocuments).setOnClickListener(v -> {
            DocumentStore.clear();
            renderDocuments();
            showToast("已清空文件紀錄");
        });
        findViewById(R.id.docsNavHome).setOnClickListener(v -> finish());
        findViewById(R.id.docsNavDocuments).setOnClickListener(v -> showToast("目前位於全部文檔"));
        findViewById(R.id.docsNavToolbox).setOnClickListener(v ->
                startActivity(new Intent(this, ToolboxActivity.class))
        );
        findViewById(R.id.docsNavMe).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class))
        );

        edtSearchDocuments.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                renderDocuments();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDocuments();
    }

    private void renderDocuments() {
        layoutDocumentList.removeAllViews();
        List<DocumentItem> documents = DocumentStore.getDocuments();
        int visibleCount = 0;

        for (DocumentItem document : documents) {
            if (!matchesQuery(document)) {
                continue;
            }

            visibleCount++;
            layoutDocumentList.addView(createDocumentCard(document));
        }

        txtDocumentCount.setText("共 " + documents.size() + " 份文件");
        txtDocumentEmpty.setVisibility(visibleCount == 0 ? TextView.VISIBLE : TextView.GONE);
    }

    private boolean matchesQuery(DocumentItem document) {
        if (currentQuery.isEmpty()) {
            return true;
        }

        return document.title.toLowerCase(Locale.ROOT).contains(currentQuery)
                || document.dateTime.toLowerCase(Locale.ROOT).contains(currentQuery)
                || document.type.toLowerCase(Locale.ROOT).contains(currentQuery);
    }

    private LinearLayout createDocumentCard(DocumentItem document) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(Color.parseColor("#303136"), dp(10)));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView icon = new TextView(this);
        icon.setText("P");
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(28);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(Color.parseColor("#F28B2D"), dp(8)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(64), dp(64));
        iconParams.setMargins(0, 0, dp(14), 0);
        row.addView(icon, iconParams);

        LinearLayout textGroup = new LinearLayout(this);
        textGroup.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(document.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);

        TextView meta = new TextView(this);
        meta.setText(document.dateTime + "  |  " + document.pageCount + " 頁  |  " + document.type);
        meta.setTextColor(Color.parseColor("#A8ACB4"));
        meta.setTextSize(14);
        meta.setPadding(0, dp(4), 0, 0);

        textGroup.addView(title);
        textGroup.addView(meta);
        row.addView(textGroup, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);

        Button open = actionButton("查看");
        open.setOnClickListener(v -> openPdf(document.pdfUri));
        Button share = actionButton("分享");
        share.setOnClickListener(v -> sharePdf(document.pdfUri));
        Button copy = actionButton("另存");
        copy.setOnClickListener(v -> copyDocument(document));
        Button delete = actionButton("刪除");
        delete.setOnClickListener(v -> {
            DocumentStore.remove(document);
            renderDocuments();
            showToast("已刪除文件紀錄");
        });

        actions.addView(open, actionLayoutParams(true));
        actions.addView(share, actionLayoutParams(true));
        actions.addView(copy, actionLayoutParams(true));
        actions.addView(delete, actionLayoutParams(false));
        card.addView(actions);

        return card;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setBackground(rounded(Color.parseColor("#4A4B50"), dp(6)));
        return button;
    }

    private LinearLayout.LayoutParams actionLayoutParams(boolean hasRightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
        if (hasRightMargin) {
            params.setMargins(0, 0, dp(10), 0);
        }
        return params;
    }

    private void importPdfDocument(Uri uri) {
        persistReadPermission(uri);

        String title = resolveDisplayName(uri, "Imported_" + formatDisplayTime(System.currentTimeMillis()) + ".pdf");
        int pageCount = countPdfPages(uri);
        String dateTime = formatDisplayTime(System.currentTimeMillis());

        DocumentStore.add(new DocumentItem(title, dateTime, pageCount, uri, "PDF"));
        renderDocuments();
        showToast("已導入：" + title);
    }

    private void copyDocument(DocumentItem document) {
        if (document.pdfUri == null) {
            showToast("找不到可另存的 PDF");
            return;
        }

        pendingCopyDocument = document;
        String fileName = document.title.endsWith(".pdf")
                ? document.title.replace(".pdf", "_copy.pdf")
                : document.title + "_copy.pdf";
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
            renderDocuments();
            showToast("已另存為 PDF：" + title);
        } catch (Exception e) {
            showToast("另存 PDF 失敗：" + e.getMessage());
        } finally {
            pendingCopyDocument = null;
        }
    }

    private void openPdf(Uri pdfUri) {
        if (pdfUri == null) {
            showToast("找不到 PDF");
            return;
        }

        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(pdfUri, "application/pdf");
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(openIntent);
        } catch (ActivityNotFoundException e) {
            showToast("找不到可開啟 PDF 的應用程式");
        }
    }

    private void sharePdf(Uri pdfUri) {
        if (pdfUri == null) {
            showToast("找不到 PDF");
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(shareIntent, "分享 PDF"));
        } catch (ActivityNotFoundException e) {
            showToast("找不到可分享 PDF 的應用程式");
        }
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

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
