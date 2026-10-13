package app.blaze.browser;

import android.net.Uri;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * UC Browser-based ad and tracker blocking.
 *
 * Rewritten from UC Browser source:
 *   com.uc.nezha.plugin.adblock.ADBlockFilter
 *   com.uc.browser.business.advfilter.AdBlockerSettingWindow
 *
 * UC Browser ADBlockFilter architecture:
 *   - Include rules: ArrayList of patterns that ARE ads (block them)
 *   - Exclude rules: ArrayList of patterns prefixed with ~ (never block)
 *   - Rule types: host-exact, host-suffix, URL-pattern
 *   - Powerful mode: enables wider blocking (same as UC Browser EnablePowerFulADBlock)
 *   - Data saver mode: blocks tracking pixels/beacons (KEY_SMART_NOPIC companion)
 */
public final class AdBlock {

    // ?? UC Browser ADBlockFilter: include rule hosts ??????????????????????
    // Mirrors UC Browser's built-in ad rule database
    private static final Set<String> AD_HOSTS = new HashSet<>(Arrays.asList(
        // Ad networks
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com", "adnxs.com", "adsrvr.org",
        "adsafeprotected.com", "advertising.com", "taboola.com",
        "outbrain.com", "criteo.com", "criteo.net", "moatads.com",
        "scorecardresearch.com", "popads.net", "popcash.net",
        "propellerads.com", "exoclick.com", "trafficjunky.com",
        "juicyads.com", "adsterra.com", "clickadu.com", "hilltopads.com",
        "mgid.com", "revcontent.com", "zedo.com", "adform.net",
        "smartadserver.com", "pubmatic.com", "rubiconproject.com",
        "openx.net", "casalemedia.com", "media.net", "bidswitch.net",
        "sharethrough.com", "3lift.com", "teads.tv", "spotxchange.com",
        "adroll.com", "quantserve.com", "bluekai.com", "krxd.net",
        "agkn.com", "mathtag.com", "onclickads.net",
        // Analytics (UC EnableAdBlock=1 blocks these)
        "google-analytics.com", "analytics.google.com",
        "googletagmanager.com", "googletagservices.com",
        "ssl.google-analytics.com", "hotjar.com", "clarity.ms",
        "segment.io", "segment.com", "mixpanel.com",
        "amplitude.com", "sentry.io", "bugsnag.com", "branch.io",
        "adjust.com", "appsflyer.com", "moengage.com", "clevertap.com",
        "webengage.com", "heap.io", "kissmetrics.com",
        "fullstory.com", "logrocket.com", "mouseflow.com",
        "smartlook.com", "crazyegg.com", "pingdom.com",
        "nr-data.net", "demdex.net", "omtrdc.net", "adobedtm.com",
        "connect.facebook.net", "ads.twitter.com", "ads.yahoo.com",
        "chartbeat.com", "newrelic.com", "histats.com"
    ));

    // UC Browser ADBlockFilter: exclude rules (never block these hosts)
    // Prefix ~ in UC Browser filter syntax
    private static final Set<String> EXCLUDE_HOSTS = new HashSet<>(Arrays.asList(
        "google.com", "googleapis.com", "gstatic.com", "youtube.com",
        "ytimg.com", "googlevideo.com", "fonts.gstatic.com",
        "fonts.googleapis.com", "accounts.google.com"
    ));

    // UC Browser tracker patterns for data saver mode (KEY_SMART_NOPIC companion)
    private static final String[] TRACKER_PATTERNS = {
        "analytics", "tracker", "pixel", "beacon", "telemetry",
        "metrics", "collect", "track.", "/tr?", "utm_"
    };

    // UC Browser URL patterns (EnablePowerFulADBlock rules)
    private static final String[] URL_PATTERNS = {
        "/pagead/", "googleads.", "doubleclick.net", "googlesyndication",
        "adservice.google", "google-analytics", "googletagmanager",
        "facebook.com/tr", "connect.facebook.net", "/ads/banner",
        "ad_click", "clicktag", "popads", "propellerads", "exoclick"
    };

    // UC Browser AdBlockerSettingWindow settings flags
    // EnableAdBlock = "1" means enabled
    // EnablePowerFulADBlock = "1" means powerful mode
    public static volatile boolean enabled      = true;
    public static volatile boolean powerful     = false;
    public static volatile boolean antiFingerprint = true;
    public static volatile boolean dataSaverMode  = false;

    private AdBlock() {}

    /**
     * Main ad detection method.
     * Mirrors UC Browser's ADBlockFilter.isAd() logic with include/exclude rules.
     */
    public static boolean isAd(Uri uri) {
        if (!enabled || uri == null) return false;

        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        if (host.startsWith("www.")) host = host.substring(4);

        // UC Browser exclude rules (~pattern): never block these
        if (EXCLUDE_HOSTS.contains(host)) return false;
        for (String exc : EXCLUDE_HOSTS) {
            if (host.endsWith("." + exc)) return false;
        }

        // UC Browser include rules: exact host match
        if (AD_HOSTS.contains(host)) return true;

        // UC Browser include rules: host suffix match (subdomain.adhost.com)
        for (String adHost : AD_HOSTS) {
            if (host.endsWith("." + adHost)) return true;
        }

        // UC Browser data saver mode: block tracker URL patterns
        if (dataSaverMode) {
            String raw = uri.toString().toLowerCase(Locale.US);
            for (String p : TRACKER_PATTERNS) {
                if (raw.contains(p)) return true;
            }
        }

        // UC Browser URL-pattern matching (base rules)
        String url = uri.toString().toLowerCase(Locale.US);
        for (String pattern : URL_PATTERNS) {
            if (url.contains(pattern)) return true;
        }

        // UC Browser powerful mode (EnablePowerFulADBlock)
        if (!powerful) return false;
        return url.contains("/ads/")    || url.contains("sponsor")
            || url.contains("sticky")   || url.contains("banner")
            || host.contains("analytics") || host.contains("tracker")
            || host.contains("pixel")   || host.contains("telemetry")
            || host.contains("metrics");
    }

    /**
     * Anti-fingerprinting JavaScript (UC Browser privacy protection pattern).
     * Spoofs hardware/software fingerprinting APIs.
     */
    public static String getFingerprintSpoofJs() {
        if (!antiFingerprint) return "";
        return "(function(){try{"
            // Spoof hardware concurrency (UC Browser: hide real CPU count)
            + "Object.defineProperty(navigator,'hardwareConcurrency',{get:function(){return 4;}});"
            // Spoof device memory
            + "Object.defineProperty(navigator,'deviceMemory',{get:function(){return 4;}});"
            // Spoof screen color depth
            + "Object.defineProperty(screen,'colorDepth',{get:function(){return 24;}});"
            + "Object.defineProperty(screen,'pixelDepth',{get:function(){return 24;}});"
            // Disable plugin enumeration
            + "Object.defineProperty(navigator,'plugins',{get:function(){return[];}});"
            // Block WebRTC IP leak
            + "if(window.RTCPeerConnection){var oRTC=window.RTCPeerConnection;"
            + "window.RTCPeerConnection=function(c){if(c&&c.iceServers)c.iceServers=[];return new oRTC(c);};"
            + "window.RTCPeerConnection.prototype=oRTC.prototype;}"
            // Canvas noise injection
            + "var oTDU=HTMLCanvasElement.prototype.toDataURL;"
            + "HTMLCanvasElement.prototype.toDataURL=function(t){"
            + "var ctx=this.getContext('2d');"
            + "if(ctx){var d=ctx.getImageData(0,0,this.width,this.height);"
            + "for(var i=0;i<d.data.length;i+=100){d.data[i]^=(Math.random()*2|0);}"
            + "ctx.putImageData(d,0,0);}return oTDU.apply(this,arguments);};"
            + "}catch(e){}})()";
    }
}
