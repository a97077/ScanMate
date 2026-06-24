package com.example.scanmate;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DocumentStore {
    private static final int MAX_RECENT_COUNT = 100;
    private static final String PREF_NAME = "scanmate_documents";
    private static final String KEY_DOCUMENTS = "documents";
    private static final ArrayList<DocumentItem> documents = new ArrayList<>();
    private static boolean loaded = false;
    private static Context appContext;

    private DocumentStore() {
    }

    public static void init(Context context) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
        }
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    public static void add(DocumentItem item) {
        documents.add(0, item);
        while (documents.size() > MAX_RECENT_COUNT) {
            documents.remove(documents.size() - 1);
        }
        save();
    }

    public static List<DocumentItem> getDocuments() {
        return new ArrayList<>(documents);
    }

    public static int size() {
        return documents.size();
    }

    public static DocumentItem getLatestPdf() {
        for (DocumentItem item : documents) {
            if (item.pdfUri != null && "PDF".equalsIgnoreCase(item.type)) {
                return item;
            }
        }
        return null;
    }

    public static void remove(DocumentItem item) {
        for (int i = documents.size() - 1; i >= 0; i--) {
            DocumentItem current = documents.get(i);
            boolean sameUri = current.pdfUri != null && item.pdfUri != null
                    && current.pdfUri.toString().equals(item.pdfUri.toString());
            boolean sameMetadata = current.title.equals(item.title)
                    && current.dateTime.equals(item.dateTime)
                    && current.pageCount == item.pageCount;

            if (sameUri || sameMetadata) {
                documents.remove(i);
                save();
                return;
            }
        }
    }

    public static void clear() {
        documents.clear();
        save();
    }

    private static void load() {
        if (appContext == null) {
            return;
        }

        documents.clear();
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_DOCUMENTS, "[]");

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                String title = object.optString("title");
                String dateTime = object.optString("dateTime");
                int pageCount = object.optInt("pageCount");
                String uriString = object.optString("pdfUri");
                String type = object.optString("type", "PDF");
                String documentType = object.optString("documentType", DocumentTypeHelper.TYPE_GENERAL);

                Uri uri = uriString.isEmpty() ? null : Uri.parse(uriString);
                documents.add(new DocumentItem(title, dateTime, pageCount, uri, type, documentType));
            }
        } catch (Exception ignored) {
            documents.clear();
        }
    }

    private static void save() {
        if (appContext == null) {
            return;
        }

        JSONArray array = new JSONArray();

        try {
            for (DocumentItem item : documents) {
                JSONObject object = new JSONObject();
                object.put("title", item.title);
                object.put("dateTime", item.dateTime);
                object.put("pageCount", item.pageCount);
                object.put("pdfUri", item.pdfUri == null ? "" : item.pdfUri.toString());
                object.put("type", item.type);
                object.put("documentType", item.documentType);
                array.put(object);
            }
        } catch (Exception ignored) {
        }

        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DOCUMENTS, array.toString()).apply();
    }
}
