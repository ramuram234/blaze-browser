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

    public static boolean powerfulAds(Context context) {
        return prefs(context).getBoolean("powerful_ads", false);
    }

    public static void setPowerfulAds(Context context, boolean on) {
        prefs(context).edit().putBoolean("powerful_ads", on).apply();
        AdBlock.powerful = on;
    }

    public static boolean nightMode(Context context) {
        return prefs(context).getBoolean("night", false);
    }

    public static void setNightMode(Context context, boolean on) {
        prefs(context).edit().putBoolean("night", on).apply();
    }

    public static boolean preload(Context context) {
        return prefs(context).getBoolean("preload", true);
    }

    public static void setPreload(Context context, boolean on) {
        prefs(context).edit().putBoolean("preload", on).apply();
    }

    public static boolean boost(Context context) {
        return prefs(context).getBoolean("boost", true);
    }

    public static void setBoost(Context context, boolean on) {
        prefs(context).edit().putBoolean("boost", on).apply();
    }

    public static int imageQuality(Context context) {
        return prefs(context).getInt("image_q", 1);
    }

    public static void setImageQuality(Context context, int q) {
        prefs(context).edit().putInt("image_q", Math.max(0, Math.min(2, q))).apply();
    }

    public static boolean backgroundAudio(Context context) {
        return prefs(context).getBoolean("bg_audio", false);
    }

    public static void setBackgroundAudio(Context context, boolean on) {
        prefs(context).edit().putBoolean("bg_audio", on).apply();
    }

    public static void clearAll(Context context) {
        prefs(context).edit().putString("history", "[]").putString("bookmarks", "[]").apply();
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

    public static boolean antiFingerprint(Context context) {
        return prefs(context).getBoolean("anti_fp", true);
    }

    public static void setAntiFingerprint(Context context, boolean on) {
        prefs(context).edit().putBoolean("anti_fp", on).apply();
        AdBlock.antiFingerprint = on;
    }

    public static boolean downloadBoost(Context context) {
        return prefs(context).getBoolean("dl_boost", true);
    }

    public static void setDownloadBoost(Context context, boolean on) {
        prefs(context).edit().putBoolean("dl_boost", on).apply();
    }

    /** Video buffer cache size in bytes. Default 500 MB. */
    public static long videoCacheSize(Context context) {
        return prefs(context).getLong("video_cache_mb", 500L) * 1024L * 1024L;
    }

    public static void setVideoCacheMb(Context context, long mb) {
        prefs(context).edit().putLong("video_cache_mb", mb).apply();
    }

    /** 0=Off (5s), 1=Normal (5min), 2=Full (15min), 3=Custom */
    public static int videoPreloadMode(Context context) {
        return prefs(context).getInt("video_preload", 1); // default Normal
    }

    public static void setVideoPreloadMode(Context context, int mode) {
        prefs(context).edit().putInt("video_preload", Math.max(0, Math.min(2, mode))).apply();
    }

    /** Custom buffer duration in seconds (mode 3). Range 60-900. */
    public static int videoPreloadCustomSec(Context context) {
        return prefs(context).getInt("video_preload_custom_sec", 300); // default 5 min
    }

    public static void setVideoPreloadCustomSec(Context context, int seconds) {
        int clamped = Math.max(60, Math.min(900, seconds));
        prefs(context).edit().putInt("video_preload_custom_sec", clamped).apply();
    }

    /** Whether to warn when playing video on mobile data. */
    public static boolean warnMobileData(Context context) {
        return prefs(context).getBoolean("warn_mobile_data", true);
    }

    public static void setWarnMobileData(Context context, boolean on) {
        prefs(context).edit().putBoolean("warn_mobile_data", on).apply();
    }

    public static boolean httpsOnly(Context context) { return prefs(context).getBoolean("https_only", false); }
    public static void setHttpsOnly(Context context, boolean on) { prefs(context).edit().putBoolean("https_only", on).apply(); }

    public static boolean blockThirdPartyCookies(Context context) { return prefs(context).getBoolean("block_3p", true); }
    public static void setBlockThirdPartyCookies(Context context, boolean on) { prefs(context).edit().putBoolean("block_3p", on).apply(); }

    public static boolean blockFonts(Context context) { return prefs(context).getBoolean("block_fonts", false); }
    public static void setBlockFonts(Context context, boolean on) { prefs(context).edit().putBoolean("block_fonts", on).apply(); }

    public static boolean disableJs(Context context) { return prefs(context).getBoolean("disable_js", false); }
    public static void setDisableJs(Context context, boolean on) { prefs(context).edit().putBoolean("disable_js", on).apply(); }

    public static boolean blockAutoplay(Context context) { return prefs(context).getBoolean("block_autoplay", true); }
    public static void setBlockAutoplay(Context context, boolean on) { prefs(context).edit().putBoolean("block_autoplay", on).apply(); }

    public static String searchEngine(Context context) { return prefs(context).getString("search_engine", "google"); }
    public static void setSearchEngine(Context context, String e) { prefs(context).edit().putString("search_engine", e).apply(); }

    public static boolean readerMode(Context context) { return prefs(context).getBoolean("reader_mode", false); }
    public static void setReaderMode(Context context, boolean on) { prefs(context).edit().putBoolean("reader_mode", on).apply(); }

    // Data Saver Mode (R10)
    public static boolean dataSaver(Context context) {
        return prefs(context).getBoolean("data_saver", false);
    }
    public static void setDataSaver(Context context, boolean on) {
        prefs(context).edit().putBoolean("data_saver", on).apply();
    }

    // ?? UC Browser Feature Prefs ??????????????????????????????????????????

    /** Activity slide animations (UC AnimationIsOpen). Default: true */
    public static boolean animationsEnabled(Context context) {
        return prefs(context).getBoolean("animations", true);
    }
    public static void setAnimationsEnabled(Context context, boolean on) {
        prefs(context).edit().putBoolean("animations", on).apply();
    }

    /** Auto-rotate in video player (UC KEY_ROTATESCREEN). Default: false */
    public static boolean autoRotateVideo(Context context) {
        return prefs(context).getBoolean("auto_rotate", false);
    }
    public static void setAutoRotateVideo(Context context, boolean on) {
        prefs(context).edit().putBoolean("auto_rotate", on).apply();
    }

    /** Hardware acceleration (UC OpenHWAC). Default: true */
    public static boolean hardwareAccel(Context context) {
        return prefs(context).getBoolean("hw_accel", true);
    }
    public static void setHardwareAccel(Context context, boolean on) {
        prefs(context).edit().putBoolean("hw_accel", on).apply();
    }

    /** Text zoom percentage (UC UCCustomFontSizeLayout). Range 75-200. Default: 100 */
    public static int fontSizePercent(Context context) {
        return prefs(context).getInt("font_size", 100);
    }
    public static void setFontSizePercent(Context context, int percent) {
        int clamped = Math.max(75, Math.min(200, percent));
        prefs(context).edit().putInt("font_size", clamped).apply();
    }

    /** Swipe forward/back navigation (UC EnableSwipeForwardOrBackward). Default: true */
    public static boolean swipeNavEnabled(Context context) {
        return prefs(context).getBoolean("swipe_nav", true);
    }
    public static void setSwipeNavEnabled(Context context, boolean on) {
        prefs(context).edit().putBoolean("swipe_nav", on).apply();
    }

    /** Block images to save data (UC KEY_SMART_NOPIC). Default: false */
    public static boolean blockImages(Context context) {
        return prefs(context).getBoolean("block_images", false);
    }
    public static void setBlockImages(Context context, boolean on) {
        prefs(context).edit().putBoolean("block_images", on).apply();
    }

    // UC Browser: wifi_only - Download on WiFi only
    public static boolean downloadOnWifiOnly(Context context) {
        return prefs(context).getBoolean("dl_wifi_only", false);
    }
    public static void setDownloadOnWifiOnly(Context context, boolean on) {
        prefs(context).edit().putBoolean("dl_wifi_only", on).apply();
    }
}
