package com.example.scanmate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class ScanDraftStore {
    private static final String PREF_NAME = "scanmate_draft";
    private static final String KEY_TITLE = "title";
    private static final String KEY_SOURCE_URI = "source_uri";
    private static final String KEY_PAGE_COUNT = "page_count";
    private static final String KEY_DOCUMENT_TYPE = "document_type";
    private static final String KEY_USE_TYPE_AWARE_NAME = "use_type_aware_name";
    private static final ArrayList<Bitmap> pages = new ArrayList<>();
    private static Bitmap originalBitmap;
    private static Bitmap currentBitmap;
    private static Uri sourceUri;
    private static String documentTitle;
    private static String documentType = DocumentTypeHelper.TYPE_GENERAL;
    private static boolean useTypeAwareName = false;
    private static int editingPageIndex = -1;

    private ScanDraftStore() {
    }

    public static void start(Uri uri, Bitmap bitmap, String title) {
        if (documentTitle == null || title == null || !documentTitle.equals(title)) {
            pages.clear();
            documentType = DocumentTypeHelper.TYPE_GENERAL;
            useTypeAwareName = false;
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

    public static Bitmap getPage(int index) {
        if (index < 0 || index >= pages.size()) {
            return null;
        }
        return pages.get(index);
    }

    public static void removePage(int index) {
        if (index < 0 || index >= pages.size()) {
            return;
        }
        pages.remove(index);
        if (editingPageIndex == index) {
            editingPageIndex = -1;
        } else if (editingPageIndex > index) {
            editingPageIndex--;
        }
    }

    public static void movePage(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= pages.size() || toIndex < 0 || toIndex >= pages.size() || fromIndex == toIndex) {
            return;
        }
        Bitmap page = pages.remove(fromIndex);
        pages.add(toIndex, page);
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

    public static String getDocumentType() {
        return DocumentTypeHelper.normalize(documentType);
    }

    public static void setDocumentType(String type, boolean useTypeName) {
        documentType = DocumentTypeHelper.normalize(type);
        useTypeAwareName = useTypeName;
    }

    public static boolean shouldUseTypeAwareName() {
        return useTypeAwareName;
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
        documentType = DocumentTypeHelper.TYPE_GENERAL;
        useTypeAwareName = false;
        editingPageIndex = -1;
    }

    public static void saveDraft(Context context) {
        if (context == null) {
            return;
        }

        File dir = getDraftDir(context);
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        clearDraftFiles(dir);

        for (int i = 0; i < pages.size(); i++) {
            File pageFile = new File(dir, "page_" + i + ".png");
            try (FileOutputStream outputStream = new FileOutputStream(pageFile)) {
                pages.get(i).compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            } catch (Exception ignored) {
            }
        }

        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_TITLE, documentTitle == null ? "" : documentTitle)
                .putString(KEY_SOURCE_URI, sourceUri == null ? "" : sourceUri.toString())
                .putInt(KEY_PAGE_COUNT, pages.size())
                .putString(KEY_DOCUMENT_TYPE, getDocumentType())
                .putBoolean(KEY_USE_TYPE_AWARE_NAME, useTypeAwareName)
                .apply();
    }

    public static boolean restoreDraft(Context context) {
        if (context == null || !pages.isEmpty()) {
            return !pages.isEmpty();
        }

        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int pageCount = prefs.getInt(KEY_PAGE_COUNT, 0);
        if (pageCount <= 0) {
            return false;
        }

        File dir = getDraftDir(context);
        ArrayList<Bitmap> restoredPages = new ArrayList<>();
        for (int i = 0; i < pageCount; i++) {
            File pageFile = new File(dir, "page_" + i + ".png");
            Bitmap bitmap = BitmapFactory.decodeFile(pageFile.getAbsolutePath());
            if (bitmap != null) {
                restoredPages.add(bitmap);
            }
        }

        if (restoredPages.isEmpty()) {
            return false;
        }

        pages.clear();
        pages.addAll(restoredPages);
        originalBitmap = pages.get(pages.size() - 1);
        currentBitmap = originalBitmap;
        documentTitle = prefs.getString(KEY_TITLE, "ScanMate Draft");
        documentType = DocumentTypeHelper.normalize(prefs.getString(KEY_DOCUMENT_TYPE, DocumentTypeHelper.TYPE_GENERAL));
        useTypeAwareName = prefs.getBoolean(KEY_USE_TYPE_AWARE_NAME, false);
        String uriString = prefs.getString(KEY_SOURCE_URI, "");
        sourceUri = uriString == null || uriString.isEmpty() ? null : Uri.parse(uriString);
        editingPageIndex = -1;
        return true;
    }

    public static void clearPersistedDraft(Context context) {
        if (context == null) {
            return;
        }
        clearDraftFiles(getDraftDir(context));
        context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    private static File getDraftDir(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "scanmate_draft_pages");
    }

    private static void clearDraftFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.getName().startsWith("page_") && file.getName().endsWith(".png")) {
                file.delete();
            }
        }
    }
}
