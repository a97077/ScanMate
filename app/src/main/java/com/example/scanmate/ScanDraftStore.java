package com.example.scanmate;

import android.graphics.Bitmap;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

public class ScanDraftStore {
    private static final ArrayList<Bitmap> pages = new ArrayList<>();
    private static Bitmap originalBitmap;
    private static Bitmap currentBitmap;
    private static Uri sourceUri;
    private static String documentTitle;
    private static int editingPageIndex = -1;

    private ScanDraftStore() {
    }

    public static void start(Uri uri, Bitmap bitmap, String title) {
        if (documentTitle == null || title == null || !documentTitle.equals(title)) {
            pages.clear();
        }
        sourceUri = uri;
        originalBitmap = bitmap;
        currentBitmap = bitmap;
        documentTitle = title;
        editingPageIndex = -1;
    }

    public static void editPage(int index) {
        if (index < 0 || index >= pages.size()) {
            return;
        }
        originalBitmap = pages.get(index);
        currentBitmap = originalBitmap;
        editingPageIndex = index;
    }

    public static Bitmap getOriginalBitmap() {
        return originalBitmap;
    }

    public static Bitmap getCurrentBitmap() {
        return currentBitmap != null ? currentBitmap : originalBitmap;
    }

    public static void setCurrentBitmap(Bitmap bitmap) {
        currentBitmap = bitmap;
    }

    public static void commitCurrentPage() {
        if (currentBitmap == null) {
            return;
        }
        if (editingPageIndex >= 0 && editingPageIndex < pages.size()) {
            pages.set(editingPageIndex, currentBitmap);
        } else {
            pages.add(currentBitmap);
        }
        editingPageIndex = -1;
    }

    public static List<Bitmap> getPages() {
        return new ArrayList<>(pages);
    }

    public static int getPageCount() {
        return pages.size();
    }

    public static Bitmap getLatestPage() {
        if (!pages.isEmpty()) {
            return pages.get(pages.size() - 1);
        }
        return getCurrentBitmap();
    }

    public static Uri getSourceUri() {
        return sourceUri;
    }

    public static String getDocumentTitle() {
        return documentTitle;
    }

    public static void setDocumentTitle(String title) {
        documentTitle = title;
    }

    public static boolean hasDraft() {
        return getCurrentBitmap() != null || !pages.isEmpty();
    }

    public static void clear() {
        pages.clear();
        originalBitmap = null;
        currentBitmap = null;
        sourceUri = null;
        documentTitle = null;
        editingPageIndex = -1;
    }
}
