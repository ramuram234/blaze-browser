package app.blaze.browser;

import android.net.Uri;
import java.util.Locale;

/**
 * Detects video stream URLs from WebView network requests.
 * Covers direct files, HLS/DASH manifests, and known CDN patterns.
 */
public final class VideoSniffer {
    private VideoSniffer() {}

    public static boolean isPlayable(Uri uri) {
        if (uri == null || AdBlock.isAd(uri)) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        if (!scheme.equals("http") && !scheme.equals("https")) return false;
        String url = uri.toString().toLowerCase(Locale.US);

        // Skip static assets
        if (url.contains("favicon") || url.endsWith(".js") || url.endsWith(".css")
                || url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png")
                || url.endsWith(".gif") || url.endsWith(".webp") || url.endsWith(".svg")
                || url.endsWith(".woff") || url.endsWith(".woff2") || url.endsWith(".ttf")
                || url.contains("googlesyndication") || url.contains("doubleclick.net")) {
            return false;
        }

        // Skip very short ad clips
        String dur = uri.getQueryParameter("dur");
        if (dur != null) {
            try { if (Double.parseDouble(dur) < 20) return false; }
            catch (NumberFormatException ignored) {}
        }

        // YouTube
        if (url.contains("googlevideo.com") && url.contains("videoplayback")) return true;
        if (url.contains("youtube.com/get_video_info")) return false;

        // Vimeo
        if ((url.contains("vimeocdn.com") || url.contains("vimeo.com"))
                && (url.contains(".m3u8") || url.contains(".mp4") || url.contains("video/"))) return true;

        // Dailymotion
        if (url.contains("dailymotion.com") && url.contains("video")) return true;
        if (url.contains("dmcdn.net") && (url.contains(".mp4") || url.contains(".m3u8"))) return true;

        // Twitter / X video
        if ((url.contains("video.twimg.com") || url.contains("api.twitter.com/1.1/videos"))
                && (url.contains(".mp4") || url.contains(".m3u8"))) return true;

        // Facebook / Meta video
        if ((url.contains("fbcdn.net") || url.contains("facebook.com/video"))
                && (url.contains(".mp4") || url.contains("byterange"))) return true;

        // Instagram
        if (url.contains("cdninstagram.com") && url.contains(".mp4")) return true;

        // JW Player / Video.js CDN patterns
        if (url.contains("/manifest/") && url.contains(".m3u8")) return true;
        if (url.contains("cdn.jwplayer.com") && url.contains("media")) return true;

        // Hotstar / Disney+ CDN
        if (url.contains("hotstar.com") && (url.contains(".m3u8") || url.contains(".mpd"))) return true;

        // MX Player / Sony LIV / ZEE5
        if ((url.contains("mxplayer.in") || url.contains("sonyliv.com") || url.contains("zee5.com"))
                && (url.contains(".m3u8") || url.contains(".mpd"))) return true;

        // SonyLIV / Zee5 CDN
        if (url.contains("akamaihd.net") && (url.contains(".m3u8") || url.contains(".ts"))) return true;
        if (url.contains("llnwd.net") && url.contains(".mp4")) return true;

        // Direct video file extensions
        if (url.contains(".mp4")) return true;
        if (url.contains(".m3u8")) return true;   // HLS
        if (url.contains(".mpd")) return true;    // DASH
        if (url.contains(".webm")) return true;
        if (url.contains(".m4v")) return true;
        if (url.contains(".m4s")) return true;    // DASH segment
        if (url.contains(".mkv")) return true;
        if (url.contains(".flv")) return true;
        if (url.contains(".mov")) return true;
        if (url.contains(".avi")) return true;

        // MIME type hints in URL
        if (url.contains("mime=video")) return true;
        if (url.contains("format=m3u8")) return true;
        if (url.contains("format=mpd")) return true;
        if (url.contains("content-type=video")) return true;
        if (url.contains("type=video")) return true;

        // Generic CDN video segment patterns
        if (url.contains("/hls/") && url.contains(".ts")) return true;
        if (url.contains("/dash/") && (url.contains(".mp4") || url.contains(".m4s"))) return true;
        if (url.contains("/stream/") && (url.contains("video") || url.contains(".mp4"))) return true;
        if (url.contains("segment") && url.contains(".ts")) return true;

        return false;
    }
}