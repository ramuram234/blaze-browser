package app.blaze.browser;

import android.net.Uri;

import java.util.Locale;

public final class VideoSniffer {
    private VideoSniffer() {}

    public static boolean isPlayable(Uri uri) {
        if (uri == null || AdBlock.isAd(uri)) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        if (!scheme.equals("http") && !scheme.equals("https")) return false;
        String url = uri.toString().toLowerCase(Locale.US);
        if (url.contains("favicon") || url.contains(".js") || url.contains(".css") || url.contains(".jpg")
                || url.contains(".png") || url.contains(".gif") || url.contains(".webp") || url.contains(".svg")) {
            return false;
        }
        String dur = uri.getQueryParameter("dur");
        if (dur != null) {
            try {
                if (Double.parseDouble(dur) > 0 && Double.parseDouble(dur) < 20) return false;
            } catch (NumberFormatException ignored) {
            }
        }
        if (url.contains("googlevideo.com") && url.contains("videoplayback")) return true;
        return url.contains(".mp4")
                || url.contains(".m3u8")
                || url.contains(".webm")
                || url.contains(".m4v")
                || url.contains("mime=video")
                || url.contains("format=m3u8");
    }
}
