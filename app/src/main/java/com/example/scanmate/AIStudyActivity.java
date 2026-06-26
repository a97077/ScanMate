package com.example.scanmate;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;

public class AIStudyActivity extends AppCompatActivity {

    public static final String EXTRA_OCR_TEXT = "ocr_text";
    private static final int MAX_ANALYSIS_CHARS = 6000;
    private static final int AUTO_ANALYZE_DELAY_MS = 450;

    private EditText edtAiOcrText;
    private TextView txtAiEmptyState;
    private LinearLayout layoutAiResults;
    private TextView txtAiType;
    private TextView txtAiName;
    private TextView txtAiSummary;
    private TextView txtAiKeywords;
    private TextView txtAiQuiz;
    private Button btnTypeLecture;
    private Button btnTypeHomework;
    private Button btnTypeReport;
    private Button btnTypeExam;
    private Button btnTypeGeneral;

    private String selectedType = DocumentTypeHelper.TYPE_GENERAL;
    private boolean manualTypeSelected = false;
    private String latestSuggestedName = "";
    private boolean suppressAutoAnalyze = false;
    private final Handler analyzeHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingAnalyzeRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_study);

        edtAiOcrText = findViewById(R.id.edtAiOcrText);
        txtAiEmptyState = findViewById(R.id.txtAiEmptyState);
        layoutAiResults = findViewById(R.id.layoutAiResults);
        txtAiType = findViewById(R.id.txtAiType);
        txtAiName = findViewById(R.id.txtAiName);
        txtAiSummary = findViewById(R.id.txtAiSummary);
        txtAiKeywords = findViewById(R.id.txtAiKeywords);
        txtAiQuiz = findViewById(R.id.txtAiQuiz);
        btnTypeLecture = findViewById(R.id.btnTypeLecture);
        btnTypeHomework = findViewById(R.id.btnTypeHomework);
        btnTypeReport = findViewById(R.id.btnTypeReport);
        btnTypeExam = findViewById(R.id.btnTypeExam);
        btnTypeGeneral = findViewById(R.id.btnTypeGeneral);

        findViewById(R.id.btnAiBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAiAnalyze).setOnClickListener(v -> analyzeCurrentText());
        findViewById(R.id.btnAiClearText).setOnClickListener(v -> clearTextAndResults());
        findViewById(R.id.btnAiCopyName).setOnClickListener(v -> copySuggestedName());
        findViewById(R.id.btnAiOcrCamera).setOnClickListener(v -> openOcr(TextExtractActivity.EXTRA_MODE_CAMERA));
        findViewById(R.id.btnAiOcrGallery).setOnClickListener(v -> openOcr(TextExtractActivity.EXTRA_MODE_GALLERY));

        btnTypeLecture.setOnClickListener(v -> selectType(DocumentTypeHelper.TYPE_LECTURE, true));
        btnTypeHomework.setOnClickListener(v -> selectType(DocumentTypeHelper.TYPE_HOMEWORK, true));
        btnTypeReport.setOnClickListener(v -> selectType(DocumentTypeHelper.TYPE_REPORT, true));
        btnTypeExam.setOnClickListener(v -> selectType(DocumentTypeHelper.TYPE_EXAM, true));
        btnTypeGeneral.setOnClickListener(v -> selectType(DocumentTypeHelper.TYPE_GENERAL, true));

        updateTypeButtons();
        attachAutoAnalyzeWatcher();

        String incomingText = getIntent().getStringExtra(EXTRA_OCR_TEXT);
        if (incomingText != null && !incomingText.trim().isEmpty()) {
            String preparedText = prepareAnalysisText(incomingText.trim());
            suppressAutoAnalyze = true;
            edtAiOcrText.setText(preparedText);
            suppressAutoAnalyze = false;
            edtAiOcrText.postDelayed(() -> analyzeText(getCurrentText()), 250);
        } else {
            renderEmptyState("請先透過 OCR 提取文字，或貼上文字進行整理。也可以先選擇文件標籤，後續命名會套用此類型。");
        }
    }

    private void openOcr(String mode) {
        Intent intent = new Intent(this, TextExtractActivity.class);
        intent.putExtra(TextExtractActivity.EXTRA_AUTO_START_MODE, mode);
        intent.putExtra(TextExtractActivity.EXTRA_RETURN_TO_AI, true);
        startActivity(intent);
    }

    private void selectType(String type, boolean manual) {
        selectedType = DocumentTypeHelper.normalize(type);
        manualTypeSelected = manual;
        updateTypeButtons();

        String text = getCurrentText();
        if (text.isEmpty()) {
            latestSuggestedName = buildSuggestedName(selectedType);
            renderEmptyState("已選擇「" + selectedType + "」標籤。貼上文字或使用 OCR 後，系統會以此類型整理並建議命名。");
            return;
        }
        analyzeText(text);
    }

    private void analyzeCurrentText() {
        cancelPendingAnalyze();
        String text = getCurrentText();
        hideKeyboard();
        if (text.isEmpty()) {
            renderEmptyState("請先透過 OCR 提取文字，或貼上文字進行整理。");
            return;
        }
        analyzeText(text);
    }

    private String getCurrentText() {
        return edtAiOcrText.getText() == null ? "" : edtAiOcrText.getText().toString().trim();
    }

    private void attachAutoAnalyzeWatcher() {
        edtAiOcrText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressAutoAnalyze) {
                    return;
                }
                String text = s == null ? "" : s.toString().trim();
                if (text.isEmpty()) {
                    latestSuggestedName = "";
                    renderEmptyState("請先透過 OCR 提取文字，或貼上文字進行整理。也可以先選擇文件標籤，後續命名會套用此類型。");
                    return;
                }
                scheduleAutoAnalyze(text);
            }
        });
    }

    private void scheduleAutoAnalyze(String text) {
        cancelPendingAnalyze();
        String preparedText = prepareAnalysisText(text);
        pendingAnalyzeRunnable = () -> analyzeText(preparedText);
        analyzeHandler.postDelayed(pendingAnalyzeRunnable, AUTO_ANALYZE_DELAY_MS);
    }

    private void cancelPendingAnalyze() {
        if (pendingAnalyzeRunnable != null) {
            analyzeHandler.removeCallbacks(pendingAnalyzeRunnable);
            pendingAnalyzeRunnable = null;
        }
    }

    private String prepareAnalysisText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text
                .replace('\u0000', ' ')
                .replaceAll("[\\t ]+", " ")
                .trim();
        if (normalized.length() <= MAX_ANALYSIS_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_ANALYSIS_CHARS).trim()
                + "\n\n（OCR 文字較長，已先取前 " + MAX_ANALYSIS_CHARS + " 字進行整理）";
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) {
            focus = edtAiOcrText;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        edtAiOcrText.clearFocus();
    }

    private void analyzeText(String text) {
        text = prepareAnalysisText(text);
        if (text.isEmpty()) {
            renderEmptyState("請先透過 OCR 提取文字，或貼上文字進行整理。");
            return;
        }

        String type = manualTypeSelected ? selectedType : DocumentTypeHelper.detectDocumentType(text);
        selectedType = DocumentTypeHelper.normalize(type);
        latestSuggestedName = buildSuggestedName(selectedType);
        ArrayList<String> keywords = detectKeywords(text);

        updateTypeButtons();
        applyTypeToCurrentDraft(selectedType);

        txtAiType.setText(buildTypeReport(selectedType, manualTypeSelected));
        txtAiName.setText(latestSuggestedName + "\n\n命名邏輯：文件標籤 + 系統日期時間 + PDF 副檔名");
        txtAiSummary.setText(buildSummary(text));
        txtAiKeywords.setText(keywords.isEmpty() ? "尚未偵測到明確關鍵字" : joinKeywords(keywords));
        txtAiQuiz.setText(buildQuiz(selectedType, keywords));

        txtAiEmptyState.setVisibility(View.GONE);
        layoutAiResults.setVisibility(View.VISIBLE);
    }

    private String buildTypeReport(String type, boolean manual) {
        String source = manual ? "使用者一鍵標籤" : "OCR 文字規則判斷";
        return "目前標籤：" + type + "\n"
                + "判斷來源：" + source + "\n"
                + "用途：用於自動命名、最近文件 metadata 與複習整理。";
    }

    private void applyTypeToCurrentDraft(String type) {
        if (!ScanDraftStore.hasDraft()) {
            return;
        }
        ScanDraftStore.setDocumentType(type, true);
    }

    private void renderEmptyState(String message) {
        txtAiEmptyState.setText(message + "\n\n可用流程：拍照 OCR → 一鍵標籤 → 分析文字 → 產生命名、重點與複習題。");
        txtAiEmptyState.setVisibility(View.VISIBLE);
        layoutAiResults.setVisibility(View.GONE);
    }

    private void clearTextAndResults() {
        edtAiOcrText.setText("");
        manualTypeSelected = false;
        selectedType = DocumentTypeHelper.TYPE_GENERAL;
        latestSuggestedName = "";
        updateTypeButtons();
        renderEmptyState("已清除文字。請重新 OCR、導入圖片或貼上文字。");
    }

    private void copySuggestedName() {
        if (latestSuggestedName == null || latestSuggestedName.trim().isEmpty()) {
            Toast.makeText(this, "請先分析文字產生命名建議", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("ScanMate file name", latestSuggestedName));
        }
        Toast.makeText(this, "已複製命名建議", Toast.LENGTH_SHORT).show();
    }

    private void updateTypeButtons() {
        styleTypeButton(btnTypeLecture, DocumentTypeHelper.TYPE_LECTURE);
        styleTypeButton(btnTypeHomework, DocumentTypeHelper.TYPE_HOMEWORK);
        styleTypeButton(btnTypeReport, DocumentTypeHelper.TYPE_REPORT);
        styleTypeButton(btnTypeExam, DocumentTypeHelper.TYPE_EXAM);
        styleTypeButton(btnTypeGeneral, DocumentTypeHelper.TYPE_GENERAL);
    }

    private void styleTypeButton(Button button, String type) {
        boolean selected = type.equals(selectedType);
        button.setText(type);
        button.setTextColor(selected ? Color.parseColor("#101114") : Color.parseColor("#D8DDE6"));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor(selected ? "#55C3A8" : "#2A2D33")
        ));
    }

    private String buildSuggestedName(String type) {
        return DocumentTypeHelper.buildTypeAwarePdfFileName(type);
    }

    private String buildSummary(String text) {
        if (text.length() < 20) {
            return "文字內容不足，請重新 OCR 或補充文字";
        }

        ArrayList<String> lines = collectUsefulLines(text);
        if (lines.isEmpty()) {
            return "文字內容不足，請重新 OCR 或補充文字";
        }

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(5, lines.size());
        for (int i = 0; i < limit; i++) {
            builder.append("• ").append(lines.get(i)).append("\n");
        }
        return builder.toString().trim();
    }

    private ArrayList<String> collectUsefulLines(String text) {
        ArrayList<String> lines = new ArrayList<>();
        String normalized = text.replace("\r", "\n");
        String[] rawLines = normalized.split("\\n+");

        for (String rawLine : rawLines) {
            addUsefulLine(lines, rawLine);
            if (lines.size() >= 5) {
                return lines;
            }
        }

        if (lines.size() < 3) {
            String[] sentenceParts = normalized.split("[。.!?？；;]+");
            for (String part : sentenceParts) {
                addUsefulLine(lines, part);
                if (lines.size() >= 5) {
                    break;
                }
            }
        }
        return lines;
    }

    private void addUsefulLine(ArrayList<String> lines, String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.length() < 8 || lines.contains(line)) {
            return;
        }
        lines.add(line);
    }

    private ArrayList<String> detectKeywords(String text) {
        String[] dictionary = {
                "OpenCV",
                "Python",
                "Android",
                "PDF",
                "OCR",
                "影像處理",
                "邊緣偵測",
                "透視校正",
                "Gamma",
                "CLAHE",
                "Embedded",
                "Linux",
                "STM32",
                "AI",
                "機器學習"
        };

        String lowerText = text.toLowerCase(Locale.ROOT);
        ArrayList<String> keywords = new ArrayList<>();
        for (String keyword : dictionary) {
            if (lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }

    private String joinKeywords(ArrayList<String> keywords) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(keywords.get(i));
        }
        return builder.toString();
    }

    private String buildQuiz(String type, ArrayList<String> keywords) {
        String keywordHint = keywords.isEmpty() ? "目前尚未偵測到明確關鍵字" : joinKeywords(keywords);
        return "1. 這份文件的主題是什麼？\n"
                + "2. 文件中最重要的三個關鍵字是什麼？目前偵測：" + keywordHint + "\n"
                + "3. 這份文件屬於哪一種類型，為什麼？\n\n"
                + "系統判斷此文件可能是：" + type;
    }
}
