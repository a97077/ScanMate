package com.example.scanmate;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class AIStudyActivity extends AppCompatActivity {

    public static final String EXTRA_OCR_TEXT = "ocr_text";

    private static final String TYPE_EXAM = "考卷";
    private static final String TYPE_HOMEWORK = "作業";
    private static final String TYPE_REPORT = "報告";
    private static final String TYPE_LECTURE = "課堂";
    private static final String TYPE_GENERAL = "一般文件";

    private EditText edtAiOcrText;
    private TextView txtAiType;
    private TextView txtAiName;
    private TextView txtAiSummary;
    private TextView txtAiKeywords;
    private TextView txtAiQuiz;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_study);

        edtAiOcrText = findViewById(R.id.edtAiOcrText);
        txtAiType = findViewById(R.id.txtAiType);
        txtAiName = findViewById(R.id.txtAiName);
        txtAiSummary = findViewById(R.id.txtAiSummary);
        txtAiKeywords = findViewById(R.id.txtAiKeywords);
        txtAiQuiz = findViewById(R.id.txtAiQuiz);

        findViewById(R.id.btnAiBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAiAnalyze).setOnClickListener(v -> analyzeCurrentText());

        String incomingText = getIntent().getStringExtra(EXTRA_OCR_TEXT);
        if (incomingText != null && !incomingText.trim().isEmpty()) {
            edtAiOcrText.setText(incomingText.trim());
            analyzeText(incomingText.trim());
        } else {
            renderEmptyState();
        }
    }

    private void analyzeCurrentText() {
        String text = edtAiOcrText.getText() == null ? "" : edtAiOcrText.getText().toString().trim();
        if (text.isEmpty()) {
            renderEmptyState();
            return;
        }
        analyzeText(text);
    }

    private void analyzeText(String text) {
        String type = detectDocumentType(text);
        ArrayList<String> keywords = detectKeywords(text);

        txtAiType.setText("系統判斷此文件可能是：" + type);
        txtAiName.setText(buildSuggestedName(type));
        txtAiSummary.setText(buildSummary(text));
        txtAiKeywords.setText(keywords.isEmpty() ? "尚未偵測到明確關鍵字" : joinKeywords(keywords));
        txtAiQuiz.setText(buildQuiz(type, keywords));
    }

    private void renderEmptyState() {
        txtAiType.setText("請先透過 OCR 提取文字，或貼上文字進行整理。");
        txtAiName.setText("尚無命名建議");
        txtAiSummary.setText("文字內容不足，請重新 OCR 或補充文字");
        txtAiKeywords.setText("尚未偵測到明確關鍵字");
        txtAiQuiz.setText("完成 OCR 或貼上文字後，系統會產生複習題。");
    }

    private String detectDocumentType(String text) {
        if (containsAny(text, "考試", "考卷", "選擇題", "問答題", "分數", "期中", "期末", "quiz", "exam", "test")) {
            return TYPE_EXAM;
        }
        if (containsAny(text, "作業", "Homework", "Lab", "實驗", "繳交", "題目", "習題")) {
            return TYPE_HOMEWORK;
        }
        if (containsAny(text, "摘要", "研究", "方法", "結果", "結論", "參考文獻", "report", "paper")) {
            return TYPE_REPORT;
        }
        if (containsAny(text, "課程", "講義", "章節", "重點", "上課", "筆記", "lecture", "chapter")) {
            return TYPE_LECTURE;
        }
        return TYPE_GENERAL;
    }

    private boolean containsAny(String text, String... words) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (lowerText.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String buildSuggestedName(String type) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return "ScanMate_" + type + "_" + timestamp + ".pdf";
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
            builder.append("- ").append(lines.get(i)).append("\n");
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
