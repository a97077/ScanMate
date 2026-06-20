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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private TextView txtResult;
    private TextView txtRecentEmpty;
    private LinearLayout layoutRecentDocuments;
    private String recentQuery = "";
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
        setContentView(R.layout.activity_main);

        DocumentStore.init(this);

        txtResult = findViewById(R.id.txtResult);
        txtRecentEmpty = findViewById(R.id.txtRecentEmpty);
        layoutRecentDocuments = findViewById(R.id.layoutRecentDocuments);
        EditText edtHomeSearch = findViewById(R.id.edtHomeSearch);

        View btnSelectImage = findViewById(R.id.btnSelectImage);
        View btnSavePdf = findViewById(R.id.btnSavePdf);
        View btnImportImage = findViewById(R.id.btnImportImage);
        View btnSharePdf = findViewById(R.id.btnSharePdf);
        View btnIdScan = findViewById(R.id.btnIdScan);
        View btnOcr = findViewById(R.id.btnOcr);
        View btnAiStudy = findViewById(R.id.btnAiStudy);
        View btnAll = findViewById(R.id.btnAll);
        View btnFloatingScan = findViewById(R.id.btnFloatingScan);
        View navHome = findViewById(R.id.navHome);
        View navDocuments = findViewById(R.id.navDocuments);
        View navToolbox = findViewById(R.id.navToolbox);
        View navMe = findViewById(R.id.navMe);

        btnSelectImage.setOnClickListener(v -> openCaptureActivity("scan"));
        btnImportImage.setOnClickListener(v -> openCaptureActivity("import_image"));
        btnFloatingScan.setOnClickListener(v -> openCaptureActivity("scan"));

        btnSavePdf.setOnClickListener(v -> {
            startActivity(new Intent(this, PdfToolsActivity.class));
        });

        btnSharePdf.setOnClickListener(v -> importDocumentLauncher.launch(new String[]{"application/pdf"}));
        btnIdScan.setOnClickListener(v -> openCaptureActivity("id"));
        btnOcr.setOnClickListener(v -> startActivity(new Intent(this, TextExtractActivity.class)));
        btnAiStudy.setOnClickListener(v -> startActivity(new Intent(this, StudyActivity.class)));
        btnAll.setOnClickListener(v -> openToolboxActivity());

        navHome.setOnClickListener(v -> showStatus("目前位於首頁"));
        navDocuments.setOnClickListener(v -> startActivity(new Intent(this, DocumentsActivity.class)));
        navToolbox.setOnClickListener(v -> openToolboxActivity());
        navMe.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));

        edtHomeSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                recentQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                renderRecentDocuments();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderRecentDocuments();
    }

    private void openCaptureActivity(String mode) {
        Intent intent = new Intent(this, CameraCaptureActivity.class);
        intent.putExtra("capture_mode", mode);
        startActivity(intent);
    }

    private void openToolboxActivity() {
        startActivity(new Intent(this, ToolboxActivity.class));
    }

    private void renderRecentDocuments() {
        layoutRecentDocuments.removeAllViews();
        List<DocumentItem> documents = DocumentStore.getDocuments();

        if (documents.isEmpty()) {
            txtRecentEmpty.setVisibility(View.VISIBLE);
            return;
        }

        txtRecentEmpty.setVisibility(View.GONE);
        int renderedCount = 0;

        for (DocumentItem document : documents) {
            if (!matchesRecentQuery(document)) {
                continue;
            }

            renderedCount++;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(12), dp(12), dp(12));

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, dp(14));
            card.setLayoutParams(cardParams);

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);

            TextView iconView = new TextView(this);
            iconView.setGravity(Gravity.CENTER);
            iconView.setText("P");
            iconView.setTextColor(Color.WHITE);
            iconView.setTextSize(30);
            iconView.setTypeface(Typeface.DEFAULT_BOLD);
            iconView.setBackground(rounded(Color.parseColor("#F28B2D"), dp(8)));

            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(72), dp(72));
            iconParams.setMargins(0, 0, dp(14), 0);
            row.addView(iconView, iconParams);

            LinearLayout textGroup = new LinearLayout(this);
            textGroup.setOrientation(LinearLayout.VERTICAL);
            textGroup.setGravity(Gravity.CENTER_VERTICAL);

            TextView titleView = new TextView(this);
            titleView.setText(document.title);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(18);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setSingleLine(true);

            TextView metaView = new TextView(this);
            metaView.setText(document.dateTime + "  |  " + document.pageCount + " 頁");
            metaView.setTextColor(Color.parseColor("#9EA2AA"));
            metaView.setTextSize(14);
            metaView.setPadding(0, dp(4), 0, 0);

            textGroup.addView(titleView);
            textGroup.addView(metaView);
            row.addView(textGroup, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView checkView = new TextView(this);
            checkView.setText("□");
            checkView.setTextColor(Color.parseColor("#5C5F66"));
            checkView.setTextSize(30);
            row.addView(checkView);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(12), 0, 0);

            Button shareButton = actionButton("分享");
            shareButton.setOnClickListener(v -> sharePdf(document.pdfUri));

            Button saveButton = actionButton("另存為PDF");
            saveButton.setOnClickListener(v -> copyDocument(document));

            Button openButton = actionButton("查看");
            openButton.setOnClickListener(v -> openPdf(document.pdfUri));

            actions.addView(shareButton, actionLayoutParams(true));
            actions.addView(saveButton, actionLayoutParams(true));
            actions.addView(openButton, actionLayoutParams(false));

            card.addView(row);
            card.addView(actions);
            layoutRecentDocuments.addView(card);
        }

        if (renderedCount == 0) {
            txtRecentEmpty.setVisibility(View.VISIBLE);
            txtRecentEmpty.setText("找不到符合搜尋的最近文檔");
        } else {
            txtRecentEmpty.setVisibility(View.GONE);
            txtRecentEmpty.setText("尚無最近文檔，掃描並儲存 PDF 後會顯示在這裡");
        }
    }

    private boolean matchesRecentQuery(DocumentItem document) {
        if (recentQuery.isEmpty()) {
            return true;
        }

        return document.title.toLowerCase(Locale.ROOT).contains(recentQuery)
                || document.dateTime.toLowerCase(Locale.ROOT).contains(recentQuery)
                || document.type.toLowerCase(Locale.ROOT).contains(recentQuery);
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1
        );
        if (hasRightMargin) {
            params.setMargins(0, 0, dp(10), 0);
        }
        return params;
    }

    private void sharePdf(Uri pdfUri) {
        if (pdfUri == null) {
            showStatus("請先儲存 PDF");
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(shareIntent, "分享 PDF"));
        } catch (ActivityNotFoundException e) {
            showStatus("找不到可分享 PDF 的應用程式");
        }
    }

    private void openPdf(Uri pdfUri) {
        if (pdfUri == null) {
            showStatus("請先儲存 PDF");
            return;
        }

        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(pdfUri, "application/pdf");
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(openIntent);
        } catch (ActivityNotFoundException e) {
            showStatus("找不到可開啟 PDF 的應用程式");
        }
    }

    private void importPdfDocument(Uri uri) {
        persistReadPermission(uri);

        String title = resolveDisplayName(uri, "Imported_" + formatDisplayTime(System.currentTimeMillis()) + ".pdf");
        int pageCount = countPdfPages(uri);
        String dateTime = formatDisplayTime(System.currentTimeMillis());

        DocumentStore.add(new DocumentItem(title, dateTime, pageCount, uri, "PDF"));
        renderRecentDocuments();
        showStatus("已導入文檔：" + title);
    }

    private void copyDocument(DocumentItem document) {
        if (document.pdfUri == null) {
            showStatus("找不到可另存的 PDF");
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
            renderRecentDocuments();
            showStatus("已另存為 PDF：" + title);
        } catch (Exception e) {
            showStatus("另存 PDF 失敗：" + e.getMessage());
        } finally {
            pendingCopyDocument = null;
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
            if (descriptor == null) {
                return 1;
            }
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
            if (cursor != null) {
                cursor.close();
            }
        }

        return fallbackName;
    }

    private String formatDisplayTime(long timeMillis) {
        return new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                .format(new Date(timeMillis));
    }

    private void showStatus(String message) {
        txtResult.setText(message);
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
