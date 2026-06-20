package com.example.scanmate;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class StudyActivity extends AppCompatActivity {

    private EditText edtStudyInput;
    private TextView txtStudyResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study);

        edtStudyInput = findViewById(R.id.edtStudyInput);
        txtStudyResult = findViewById(R.id.txtStudyResult);

        findViewById(R.id.btnStudyBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnGenerateSummary).setOnClickListener(v -> generateSummary());
        findViewById(R.id.btnGenerateQuiz).setOnClickListener(v -> generateQuiz());
    }

    private void generateSummary() {
        String input = edtStudyInput.getText().toString().trim();
        if (input.isEmpty()) {
            txtStudyResult.setText("請先輸入 OCR 結果、課堂筆記或文章內容。");
            return;
        }

        ArrayList<String> sentences = splitSentences(input);
        StringBuilder builder = new StringBuilder();
        builder.append("重點摘要\n\n");

        int limit = Math.min(5, sentences.size());
        for (int i = 0; i < limit; i++) {
            builder.append(i + 1).append(". ").append(sentences.get(i)).append("\n");
        }

        builder.append("\n讀書建議\n");
        builder.append("1. 先背關鍵名詞，再整理流程。\n");
        builder.append("2. 把每個重點改寫成一句自己的話。\n");
        builder.append("3. 考前用下方測驗題自問自答。");
        txtStudyResult.setText(builder.toString());
    }

    private void generateQuiz() {
        String input = edtStudyInput.getText().toString().trim();
        if (input.isEmpty()) {
            txtStudyResult.setText("請先輸入內容，再產生複習題。");
            return;
        }

        ArrayList<String> sentences = splitSentences(input);
        StringBuilder builder = new StringBuilder();
        builder.append("複習題\n\n");

        int limit = Math.min(5, sentences.size());
        for (int i = 0; i < limit; i++) {
            String sentence = sentences.get(i);
            String keyword = pickKeyword(sentence);
            builder.append(i + 1)
                    .append(". 請說明「")
                    .append(keyword)
                    .append("」在此段內容中的意義。\n");
        }

        builder.append("\n答題提示：先回答定義，再補上例子或應用場景。");
        txtStudyResult.setText(builder.toString());
    }

    private ArrayList<String> splitSentences(String input) {
        String[] chunks = input.replace("\r", "\n").split("[。.!?？\\n]+");
        ArrayList<String> sentences = new ArrayList<>();
        for (String chunk : chunks) {
            String sentence = chunk.trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        if (sentences.isEmpty()) {
            sentences.add(input);
        }
        return sentences;
    }

    private String pickKeyword(String sentence) {
        String cleaned = sentence.replaceAll("[,，:：;；()（）]", " ").trim();
        String[] words = cleaned.split("\\s+");
        String best = cleaned.length() > 12 ? cleaned.substring(0, 12) : cleaned;

        for (String word : words) {
            if (word.length() >= 3 && word.length() <= 16) {
                best = word;
                break;
            }
        }
        return best;
    }
}
