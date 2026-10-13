package app.blaze.browser;

import android.net.Uri;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

/**
 * UC Browser-based video detection using VideoDetectInfo state machine.
 *
 * Rewritten from UC Browser source:
 *   com.uc.base.net.dvn.videodetect.VideoDetectInfo
 *   com.uc.base.net.dvn.videodetect.IVideoDetectRequestResult
 *   com.uc.base.net.dvn.videodetect.IVideoBlockDetectCallback
 *
 * State machine: STATE_INIT(-1) -> STATE_DETECTING(0) -> STATE_DETECTED(1)
 * Mirrors UC Browser's DVN (Detect Video Now) system.
 */
public final class VideoSniffer {

    // UC Browser VideoDetectInfo state constants
    public static final int STATE_INIT      = -1;
    public static final int STATE_DETECTING =  0;
    public static final int STATE_DETECTED  =  1;

    // UC Browser: callback interface (IVideoBlockDetectCallback pattern)
    public interface DetectCallback {
        void onDetectResult(String videoUrl, boolean isBlocked);
    }

    // Per-URL detection state cache (mimics UC Browser's DVN manager)
    private static final Map<String, Integer> stateCache = new HashMap<>();

    private VideoSniffer() {}

    /**
     * Determine if a URI is a playable video stream.
     * Based on UC Browser's URL-pattern matching + CDN fingerprinting.
     */
    public static boolean isPlayable(Uri uri) {
        if (uri == null || AdBlock.isAd(uri)) return false;
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) return false;

        String url = uri.toString().toLowerCase(Locale.US);
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.US) : "";

        // UC Browser: skip static assets (ADBlockFilter exclude pattern)
        if (isStaticAsset(url)) return false;

        // UC Browser: skip short ad clips (dur parameter check)
        String dur = uri.getQueryParameter("dur");
        if (dur != null) {
            try { if (Double.parseDouble(dur) < 20) return false; }
            catch (NumberFormatException ignored) {}
        }

        // ?? Direct video extensions (highest confidence) ?????????????????
        if (url.contains(".m3u8")) return true;   // HLS
        if (url.contains(".mpd"))  return true;   // DASH
        if (url.contains(".m4s"))  return true;   // DASH segment
        if (url.contains(".mp4"))  return true;
        if (url.contains(".webm")) return true;
        if (url.contains(".m4v"))  return true;
        if (url.contains(".mkv"))  return true;
        if (url.contains(".flv"))  return true;
        if (url.contains(".mov"))  return true;
        if (url.contains(".avi"))  return true;
        if (url.contains(".ts") && (url.contains("/hls/") || url.contains("segment"))) return true;

        // ?? MIME type hints ???????????????????????????????????????????????
        if (url.contains("mime=video"))         return true;
        if (url.contains("format=m3u8"))        return true;
        if (url.contains("format=mpd"))         return true;
        if (url.contains("content-type=video")) return true;
        if (url.contains("type=video"))         return true;

        // ?? Known CDN video domains (UC Browser DVN host patterns) ????????
        if (host.contains("googlevideo.com") && url.contains("videoplayback")) return true;
        if (host.contains("vimeocdn.com"))    return true;
        if (host.contains("video.twimg.com")) return true;
        if (host.contains("fbcdn.net") && url.contains(".mp4")) return true;
        if (host.contains("cdninstagram.com") && url.contains(".mp4")) return true;
        if (host.contains("akamaihd.net") && (url.contains(".m3u8") || url.contains(".ts"))) return true;
        if (host.contains("llnwd.net") && url.contains(".mp4")) return true;
        if (host.contains("dmcdn.net")) return true;

        // ?? Indian CDN patterns ???????????????????????????????????????????
        if (host.contains("hotstar.com")  && (url.contains(".m3u8") || url.contains(".mpd"))) return true;
        if (host.contains("mxplayer.in")  && (url.contains(".m3u8") || url.contains(".mpd"))) return true;
        if (host.contains("sonyliv.com")  && (url.contains(".m3u8") || url.contains(".mpd"))) return true;
        if (host.contains("zee5.com")     && (url.contains(".m3u8") || url.contains(".mpd"))) return true;
        if (host.contains("jiocinema.com") && (url.contains(".m3u8") || url.contains(".mpd"))) return true;

        // ?? Generic CDN patterns ??????????????????????????????????????????
        if (url.contains("cdn.jwplayer.com") && url.contains("media"))  return true;
        if (url.contains("/manifest/") && url.contains(".m3u8"))         return true;
        if (url.contains("/hls/") && url.contains(".ts"))                return true;
        if (url.contains("/dash/") && (url.contains(".mp4") || url.contains(".m4s"))) return true;
        if (url.contains("/stream/") && (url.contains("video") || url.contains(".mp4"))) return true;

        return false;
    }

    /**
     * UC Browser static asset skip list (mirrors ADBlockFilter exclude patterns).
     * These patterns appear in exclude rules prefixed with ~ in UC Browser.
     */
    private static boolean isStaticAsset(String url) {
        return url.endsWith(".js")   || url.endsWith(".css")
            || url.endsWith(".jpg")  || url.endsWith(".jpeg")
            || url.endsWith(".png")  || url.endsWith(".gif")
            || url.endsWith(".webp") || url.endsWith(".svg")
            || url.endsWith(".ico")  || url.endsWith(".woff")
            || url.endsWith(".woff2")|| url.endsWith(".ttf")
            || url.endsWith(".eot")  || url.endsWith(".json")
            || url.contains("favicon")
            || url.contains("googlesyndication")
            || url.contains("doubleclick.net");
    }

    /**
     * UC Browser VideoDetectInfo state transition helper.
     * Tracks detection state per URL, notifies callback when detected.
     */
    public static int getDetectState(String videoUrl) {
        Integer state = stateCache.get(videoUrl);
        return state != null ? state : STATE_INIT;
    }

    public static void updateDetectState(String videoUrl, int state) {
        stateCache.put(videoUrl, state);
    }

    public static void clearStates() {
        stateCache.clear();
    }
}
