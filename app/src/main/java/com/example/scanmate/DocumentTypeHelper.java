package com.example.scanmate;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;

final class DocumentTypeHelper {

    static final String TYPE_EXAM = "考卷";
    static final String TYPE_HOMEWORK = "作業";
    static final String TYPE_REPORT = "報告";
    static final String TYPE_LECTURE = "課堂";
    static final String TYPE_GENERAL = "一般文件";

    private static final String[] SUPPORTED_TYPES = {
            TYPE_LECTURE,
            TYPE_HOMEWORK,
            TYPE_REPORT,
            TYPE_EXAM,
            TYPE_GENERAL
    };

    private DocumentTypeHelper() {
    }

    static String[] supportedTypes() {
        return SUPPORTED_TYPES.clone();
    }

    static String normalize(String type) {
        if (type == null || type.trim().isEmpty()) {
            return TYPE_GENERAL;
        }

        String trimmed = type.trim();
        for (String supportedType : SUPPORTED_TYPES) {
            if (supportedType.equals(trimmed)) {
                return supportedType;
            }
        }
        return TYPE_GENERAL;
    }

    static String detectDocumentType(String text) {
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

    static String buildPdfFileName(String documentType, boolean useTypeAwareName) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        if (useTypeAwareName) {
            return "ScanMate_" + normalize(documentType) + "_" + timestamp + ".pdf";
        }
        return "ScanMate_" + timestamp + ".pdf";
    }

    static String buildTypeAwarePdfFileName(String documentType) {
        return buildPdfFileName(documentType, true);
    }

    private static boolean containsAny(String text, String... words) {
        if (text == null) {
            return false;
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (lowerText.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
