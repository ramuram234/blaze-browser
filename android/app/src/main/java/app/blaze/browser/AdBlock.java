package app.blaze.browser;

import android.net.Uri;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Privacy-focused ad and tracker blocker with fingerprint spoofing. */
public final class AdBlock {
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
        "doubleclick.net","googlesyndication.com","googleadservices.com",
        "adservice.google.com","googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com","ads.twitter.com","ads.yahoo.com",
        "adnxs.com","adsrvr.org","adsafeprotected.com","advertising.com",
        "taboola.com","outbrain.com","criteo.com","criteo.net",
        "moatads.com","scorecardresearch.com","popads.net","popcash.net",
        "popunder.net","propellerads.com","exoclick.com","exosrv.com",
        "trafficjunky.com","juicyads.com","adsterra.com","clickadu.com",
        "hilltopads.com","mgid.com","revcontent.com","zedo.com",
        "adform.net","smartadserver.com","pubmatic.com","rubiconproject.com",
        "openx.net","casalemedia.com","contextweb.com","media.net",
        "bidswitch.net","lijit.com","sharethrough.com","3lift.com",
        "yieldmo.com","teads.tv","spotxchange.com","serving-sys.com",
        "adroll.com","quantserve.com","bluekai.com","krxd.net",
        "agkn.com","mathtag.com","chartbeat.com","newrelic.com",
        "histats.com","whos.amung.us","clck.ru","onclickads.net",
        "adclick.g.doubleclick.net","securepubads.g.doubleclick.net",
        "tpc.googlesyndication.com",
        // Analytics and trackers
        "google-analytics.com","analytics.google.com",
        "googletagmanager.com","googletagservices.com",
        "ssl.google-analytics.com",
        "connect.facebook.net","static.ads-twitter.com",
        "hotjar.com","clarity.ms",
        "segment.io","segment.com","cdn.segment.com",
        "mixpanel.com","cdn.mxpnl.com",
        "amplitude.com","api.amplitude.com",
        "sentry.io","browser.sentry-cdn.com",
        "bugsnag.com","notify.bugsnag.com",
        "branch.io","api2.branch.io",
        "adjust.com","app.adjust.com",
        "appsflyer.com","t.appsflyer.com",
        "moengage.com","sdk.moengage.com",
        "clevertap.com","wzrkt.com",
        "webengage.com","li.webengage.com",
        "heap.io","heapanalytics.com",
        "kissmetrics.com","track.kissmetrics.com",
        "fullstory.com","rs.fullstory.com",
        "logrocket.com","r.lr-ingest.io",
        "mouseflow.com","s.mouseflow.com",
        "smartlook.com","rec.smartlook.com",
        "crazyegg.com","script.crazyegg.com",
        "pingdom.com","rum.pingdom.net",
        "nr-data.net","js-agent.newrelic.com",
        "rum.datadoghq.com","insights.hotjar.com",
        "demdex.net","omtrdc.net","adobedtm.com"
    ));

    public static volatile boolean enabled = true;
    public static volatile boolean powerful = false;
    public static volatile boolean antiFingerprint = true;

    private AdBlock() {}

    public static boolean isAd(Uri uri) {
        if (!enabled || uri == null) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        if (host.startsWith("www.")) host = host.substring(4);
        if (HOSTS.contains(host)) return true;
        for (String blocked : HOSTS) {
            if (host.endsWith("." + blocked)) return true;
        }
        String url = uri.toString().toLowerCase(Locale.US);
        boolean base = url.contains("/pagead/") || url.contains("googleads.")
                || url.contains("doubleclick.net") || url.contains("googlesyndication")
                || url.contains("adservice.google") || url.contains("google-analytics")
                || url.contains("googletagmanager") || url.contains("facebook.com/tr")
                || url.contains("connect.facebook.net") || url.contains("/analytics/")
                || url.contains("/tracking/") || url.contains("popads")
                || url.contains("propellerads") || url.contains("exoclick")
                || url.contains("/ads/banner") || url.contains("ad_click")
                || url.contains("clicktag");
        if (base) return true;
        if (!powerful) return false;
        return url.contains("/ads/") || url.contains("sponsor")
                || url.contains("sticky") || url.contains("banner")
                || host.contains("analytics") || host.contains("tracker")
                || host.contains("pixel") || host.contains("telemetry")
                || host.contains("metrics");
    }

    /** JavaScript injected at page start to spoof fingerprinting APIs. */
    public static String getFingerprintSpoofJs() {
        if (!antiFingerprint) return "";
        return "(function(){"
            + "try{"
            + "Object.defineProperty(navigator,'hardwareConcurrency',{get:function(){return 4;}});"
            + "Object.defineProperty(navigator,'deviceMemory',{get:function(){return 4;}});"
            + "Object.defineProperty(screen,'colorDepth',{get:function(){return 24;}});"
            + "Object.defineProperty(screen,'pixelDepth',{get:function(){return 24;}});"
            + "Object.defineProperty(navigator,'plugins',{get:function(){return[];}});"
            + "if(window.RTCPeerConnection){"
            + "var oRTC=window.RTCPeerConnection;"
            + "window.RTCPeerConnection=function(c){if(c&&c.iceServers)c.iceServers=[];return new oRTC(c);};"
            + "window.RTCPeerConnection.prototype=oRTC.prototype;}"
            + "var oTDU=HTMLCanvasElement.prototype.toDataURL;"
            + "HTMLCanvasElement.prototype.toDataURL=function(t){"
            + "var ctx=this.getContext('2d');"
            + "if(ctx){var d=ctx.getImageData(0,0,this.width,this.height);"
            + "for(var i=0;i<d.data.length;i+=100){d.data[i]^=(Math.random()*2|0);}"
            + "ctx.putImageData(d,0,0);}"
            + "return oTDU.apply(this,arguments);};"
            + "}catch(e){}})()";
    }
}