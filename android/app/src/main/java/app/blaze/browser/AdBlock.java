package app.blaze.browser;

import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Blocks common ad, popup, and tracker hosts. Not a copy of any other browser. */
public final class AdBlock {
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "googleads.g.doubleclick.net",
            "pagead2.googlesyndication.com",
            "ads.twitter.com",
            "ads.yahoo.com",
            "adnxs.com",
            "adsrvr.org",
            "adsafeprotected.com",
            "advertising.com",
            "taboola.com",
            "outbrain.com",
            "criteo.com",
            "criteo.net",
            "moatads.com",
            "scorecardresearch.com",
            "hotjar.com",
            "popads.net",
            "popcash.net",
            "popunder.net",
            "propellerads.com",
            "exoclick.com",
            "exosrv.com",
            "trafficjunky.com",
            "juicyads.com",
            "adsterra.com",
            "clickadu.com",
            "hilltopads.com",
            "mgid.com",
            "revcontent.com",
            "zedo.com",
            "adform.net",
            "smartadserver.com",
            "pubmatic.com",
            "rubiconproject.com",
            "openx.net",
            "casalemedia.com",
            "contextweb.com",
            "media.net",
            "bidswitch.net",
            "lijit.com",
            "sharethrough.com",
            "3lift.com",
            "yieldmo.com",
            "teads.tv",
            "spotxchange.com",
            "serving-sys.com",
            "adroll.com",
            "quantserve.com",
            "bluekai.com",
            "krxd.net",
            "agkn.com",
            "mathtag.com",
            "chartbeat.com",
            "newrelic.com",
            "histats.com",
            "whos.amung.us",
            "clck.ru",
            "onclickads.net",
            "adclick.g.doubleclick.net",
            "securepubads.g.doubleclick.net",
            "tpc.googlesyndication.com"
    ));

    private AdBlock() {}

    public static boolean isAd(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        if (host.startsWith("www.")) host = host.substring(4);
        if (HOSTS.contains(host)) return true;
        for (String blocked : HOSTS) {
            if (host.endsWith("." + blocked)) return true;
        }
        String url = uri.toString().toLowerCase(Locale.US);
        return url.contains("/pagead/")
                || url.contains("googleads.")
                || url.contains("doubleclick.net")
                || url.contains("googlesyndication")
                || url.contains("adservice.google")
                || url.contains("popads")
                || url.contains("popcash")
                || url.contains("propellerads")
                || url.contains("exoclick")
                || url.contains("/ads/banner")
                || url.contains("ad_click")
                || url.contains("clicktag");
    }
}
