package com.example.scanmate;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ToolboxActivity extends AppCompatActivity {

    private LinearLayout layoutToolboxContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toolbox);

        layoutToolboxContent = findViewById(R.id.layoutToolboxContent);
        findViewById(R.id.toolboxNavHome).setOnClickListener(v -> finish());
        findViewById(R.id.toolboxNavDocuments).setOnClickListener(v ->
                startActivity(new Intent(this, DocumentsActivity.class))
        );
        findViewById(R.id.toolboxNavToolbox).setOnClickListener(v -> showToast("目前位於工具箱"));
        findViewById(R.id.toolboxNavMe).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class))
        );

        buildToolbox();
    }

    private void buildToolbox() {
        addSection("掃描服務");
        addGrid(new ToolItem[]{
                new ToolItem("證", "掃描證件", "#213D38", "#5ED5C5", () ->
                        openCaptureActivity("id")
                ),
                new ToolItem("T", "提取文字", "#213D38", "#5ED5C5", () ->
                        startActivity(new Intent(this, TextExtractActivity.class))
                ),
                new ToolItem("照", "拍證件照", "#26324A", "#5E8DF5", () ->
                        openFeature("id_photo", "拍證件照")
                ),
                new ToolItem("√", "識別公式", "#213D38", "#5ED5C5", () ->
                        openFeature("formula", "識別公式")
                ),
                new ToolItem("譯", "拍照翻譯", "#26324A", "#5E8DF5", () ->
                        openFeature("translate", "拍照翻譯")
                ),
                new ToolItem("書", "掃描書籍", "#243B3F", "#56C7E8", () ->
                        openCaptureActivity("book")
                ),
                new ToolItem("P", "拍 PPT", "#4A302B", "#FF765D", () ->
                        openCaptureActivity("ppt")
                ),
                new ToolItem("板", "拍白板", "#243B3F", "#56C7E8", () ->
                        openCaptureActivity("whiteboard")
                ),
                new ToolItem("印", "水印相機", "#26324A", "#5E8DF5", () ->
                        openFeature("watermark", "水印相機")
                )
        });

        addSection("導入");
        addGrid(new ToolItem[]{
                new ToolItem("圖", "導入圖片", "#213D38", "#5ED5C5", () ->
                        openCaptureActivity("import_image")
                ),
                new ToolItem("文", "導入文檔", "#26324A", "#5E8DF5", () ->
                        startActivity(new Intent(this, DocumentsActivity.class))
                )
        });

        addSection("格式轉換");
        addGrid(new ToolItem[]{
                new ToolItem("W", "轉 Word", "#26324A", "#5E8DF5", () ->
                        openFeature("word", "轉 Word")
                ),
                new ToolItem("X", "轉 Excel", "#243B2C", "#75E26A", () ->
                        openFeature("excel", "轉 Excel")
                ),
                new ToolItem("P", "轉 PPT", "#4A302B", "#FF765D", () ->
                        openFeature("ppt", "轉 PPT")
                ),
                new ToolItem("圖", "逐頁轉圖片", "#213D38", "#5ED5C5", () ->
                        openFeature("pdf_to_image", "逐頁轉圖片")
                ),
                new ToolItem("長", "轉為長圖片", "#243B3F", "#56C7E8", () ->
                        openFeature("long_image", "轉為長圖片")
                )
        });

        addSection("文檔編輯");
        addGrid(new ToolItem[]{
                new ToolItem("PDF", "PDF 工具", "#4A302B", "#FF765D", () ->
                        startActivity(new Intent(this, PdfToolsActivity.class))
                ),
                new ToolItem("頁", "頁面排序", "#26324A", "#5E8DF5", () ->
                        openFeature("page_sort", "頁面排序")
                ),
                new ToolItem("簽", "簽名批註", "#213D38", "#5ED5C5", () ->
                        openFeature("signature", "簽名批註")
                )
        });
    }

    private void addSection(String title) {
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(22);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(28), 0, dp(12));
        layoutToolboxContent.addView(titleView, params);
    }

    private void addGrid(ToolItem[] items) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);

        for (ToolItem item : items) {
            grid.addView(createToolView(item));
        }

        layoutToolboxContent.addView(grid);
    }

    private View createToolView(ToolItem item) {
        LinearLayout view = new LinearLayout(this);
        view.setGravity(Gravity.CENTER);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setOnClickListener(v -> item.action.run());

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(120);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(0, 0, 0, dp(14));
        view.setLayoutParams(params);

        TextView icon = new TextView(this);
        icon.setGravity(Gravity.CENTER);
        icon.setText(item.iconText);
        icon.setTextColor(Color.parseColor(item.iconColor));
        icon.setTextSize(item.iconText.length() > 1 ? 20 : 26);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setBackground(oval(Color.parseColor(item.backgroundColor)));
        view.addView(icon, new LinearLayout.LayoutParams(dp(68), dp(68)));

        TextView label = new TextView(this);
        label.setText(item.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(8), 0, 0);
        view.addView(label);

        return view;
    }

    private void openFeature(String type, String title) {
        Intent intent = new Intent(this, ToolFeatureActivity.class);
        intent.putExtra(ToolFeatureActivity.EXTRA_TYPE, type);
        intent.putExtra(ToolFeatureActivity.EXTRA_TITLE, title);
        startActivity(intent);
    }

    private void openCaptureActivity(String mode) {
        Intent intent = new Intent(this, CameraCaptureActivity.class);
        intent.putExtra("capture_mode", mode);
        startActivity(intent);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class ToolItem {
        final String iconText;
        final String label;
        final String backgroundColor;
        final String iconColor;
        final Runnable action;

        ToolItem(String iconText, String label, String backgroundColor, String iconColor, Runnable action) {
            this.iconText = iconText;
            this.label = label;
            this.backgroundColor = backgroundColor;
            this.iconColor = iconColor;
            this.action = action;
        }
    }
}
