package com.example.scanmate;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    private TextView txtProfileStats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        DocumentStore.init(this);

        txtProfileStats = findViewById(R.id.txtProfileStats);
        findViewById(R.id.profileNavHome).setOnClickListener(v -> finish());
        findViewById(R.id.profileNavDocuments).setOnClickListener(v ->
                startActivity(new Intent(this, DocumentsActivity.class))
        );
        findViewById(R.id.profileNavToolbox).setOnClickListener(v ->
                startActivity(new Intent(this, ToolboxActivity.class))
        );
        findViewById(R.id.profileNavMe).setOnClickListener(v -> showToast("目前位於我的"));
        findViewById(R.id.btnProfileClear).setOnClickListener(v -> {
            DocumentStore.clear();
            renderStats();
            showToast("已清空文件紀錄");
        });

        renderStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStats();
    }

    private void renderStats() {
        txtProfileStats.setText(
                "ScanMate 完整版\n\n" +
                        "最近文件：" + DocumentStore.size() + " 份\n" +
                        "掃描能力：拍攝、導入圖片、文件校正、旋轉、濾鏡增強\n" +
                        "PDF 能力：文件頁面管理、PDF 建立、開啟、分享、導入、最近文件保存\n" +
                        "工具能力：文字提取前處理、證件照、公式區塊、拍照翻譯、PDF 轉圖片、長圖片、簽名批註\n" +
                        "資料保存：最近文件 metadata 本機保存"
        );
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
