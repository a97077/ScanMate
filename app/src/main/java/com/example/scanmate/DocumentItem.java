package com.example.scanmate;

import android.net.Uri;

public class DocumentItem {
    public final String title;
    public final String dateTime;
    public final int pageCount;
    public final Uri pdfUri;
    public final String type;
    public final String documentType;

    public DocumentItem(String title, String dateTime, int pageCount, Uri pdfUri, String type) {
        this(title, dateTime, pageCount, pdfUri, type, DocumentTypeHelper.TYPE_GENERAL);
    }

    public DocumentItem(String title, String dateTime, int pageCount, Uri pdfUri, String type, String documentType) {
        this.title = title == null || title.trim().isEmpty() ? "Untitled" : title;
        this.dateTime = dateTime == null || dateTime.trim().isEmpty() ? "-" : dateTime;
        this.pageCount = Math.max(1, pageCount);
        this.pdfUri = pdfUri;
        this.type = type == null || type.trim().isEmpty() ? "PDF" : type;
        this.documentType = DocumentTypeHelper.normalize(documentType);
    }
}
