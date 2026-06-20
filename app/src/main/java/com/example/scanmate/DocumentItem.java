package com.example.scanmate;

import android.net.Uri;

public class DocumentItem {
    public final String title;
    public final String dateTime;
    public final int pageCount;
    public final Uri pdfUri;
    public final String type;

    public DocumentItem(String title, String dateTime, int pageCount, Uri pdfUri, String type) {
        this.title = title;
        this.dateTime = dateTime;
        this.pageCount = pageCount;
        this.pdfUri = pdfUri;
        this.type = type;
    }
}
