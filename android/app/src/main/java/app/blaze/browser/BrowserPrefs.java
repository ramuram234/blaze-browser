package app.blaze.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class BrowserPrefs {
    private static final String FILE = "blaze";

    private BrowserPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean adsEnabled(Context context) {
        return prefs(context).getBoolean("ads", true);
    }

    public static void setAdsEnabled(Context context, boolean on) {
        prefs(context).edit().putBoolean("ads", on).apply();
        AdBlock.enabled = on;
    }

    public static boolean desktop(Context context) {
        return prefs(context).getBoolean("desktop", false);
    }

    public static void setDesktop(Context context, boolean on) {
        prefs(context).edit().putBoolean("desktop", on).apply();
    }

    public static void addHistory(Context context, String title, String url) {
        if (url == null || url.startsWith("file:") || url.startsWith("about:")) return;
        try {
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("t", title == null || title.isEmpty() ? url : title);
            item.put("u", url);
            next.put(item);
            JSONArray old = new JSONArray(prefs(context).getString("history", "[]"));
            for (int i = 0; i < old.length() && next.length() < 80; i++) {
                JSONObject row = old.getJSONObject(i);
                if (!url.equals(row.optString("u"))) next.put(row);
            }
            prefs(context).edit().putString("history", next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static List<String[]> history(Context context) {
        return readList(context, "history");
    }

    public static void clearHistory(Context context) {
        prefs(context).edit().putString("history", "[]").apply();
    }

    public static boolean isBookmarked(Context context, String url) {
        for (String[] row : readList(context, "bookmarks")) {
            if (row[1].equals(url)) return true;
        }
        return false;
    }

    public static void toggleBookmark(Context context, String title, String url) {
        if (url == null || url.startsWith("file:")) return;
        try {
            JSONArray old = new JSONArray(prefs(context).getString("bookmarks", "[]"));
            JSONArray next = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < old.length(); i++) {
                JSONObject row = old.getJSONObject(i);
                if (url.equals(row.optString("u"))) removed = true;
                else next.put(row);
            }
            if (!removed) {
                JSONObject item = new JSONObject();
                item.put("t", title == null || title.isEmpty() ? url : title);
                item.put("u", url);
                next.put(item);
            }
            prefs(context).edit().putString("bookmarks", next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static List<String[]> bookmarks(Context context) {
        return readList(context, "bookmarks");
    }

    private static List<String[]> readList(Context context, String key) {
        List<String[]> rows = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                rows.add(new String[]{row.optString("t", "Page"), row.optString("u", "")});
            }
        } catch (Exception ignored) {
        }
        return rows;
    }
}
