package app.blaze.browser;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.app.PictureInPictureParams;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String HOME = "file:///android_asset/www/index.html";

    private final List<WebView> tabs = new ArrayList<>();
    private int index = 0;
    private String sniffJs = "";
    private String lastPrefetch = "";

    private FrameLayout stack;
    private EditText address;
    private ProgressBar progress;
    private TextView playChip;
    private TextView tabButton;
    private FrameLayout tabsPanel;
    private LinearLayout tabList;
    private WebView warmer;
    private View browserUi;
    private FrameLayout fullscreenHolder;
    private FrameLayout fsVideo;
    private View fsGestures;
    private View fsChrome;
    private TextView fsHud;
    private FrameLayout menuSheetScrim;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private String mobileUa = "";
    private String gestureMode = "none";
    private float downX;
    private float downY;
    private boolean gestureMoved;
    private boolean landscapeLock;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AdBlock.enabled = BrowserPrefs.adsEnabled(this);
        BlazeDownloadManager.get().init(this);
        AdBlock.powerful = BrowserPrefs.powerfulAds(this);
        sniffJs = readAsset("www/sniff.js");

        stack = findViewById(R.id.web_stack);
        address = findViewById(R.id.address);
        progress = findViewById(R.id.progress);
        playChip = findViewById(R.id.btn_play);
        tabButton = findViewById(R.id.btn_tabs);
        tabsPanel = findViewById(R.id.tabs_panel);
        tabList = findViewById(R.id.tab_list);
        browserUi = findViewById(R.id.browser_ui);
        fullscreenHolder = (FrameLayout) findViewById(R.id.fullscreen_holder);
        menuSheetScrim    = (FrameLayout) findViewById(R.id.menu_sheet_scrim);
        fsVideo = findViewById(R.id.fs_video);
        fsGestures = findViewById(R.id.fs_gestures);
        fsChrome = findViewById(R.id.fs_chrome);
        fsHud = findViewById(R.id.fs_hud);

        address.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (!go) return false;
            openInput(address.getText().toString(), false);
            hideKeyboard();
            return true;
        });
        findViewById(R.id.btn_go).setOnClickListener(v -> {
            String cu = current().getUrl();
            if (cu == null || cu.startsWith("file:")) current().loadUrl(HOME);
            else current().reload();
        });
        findViewById(R.id.btn_home).setOnClickListener(v -> {
            hideTabs();
            current().loadUrl(HOME);
        });
        findViewById(R.id.btn_back).setOnClickListener(v -> goBack());
        findViewById(R.id.btn_forward).setOnClickListener(v -> {
            if (current().canGoForward()) current().goForward();
        });

        View tabsWrapper = findViewById(R.id.btn_tabs_wrapper);
        if (tabsWrapper != null) tabsWrapper.setOnClickListener(v -> {
            if (tabsPanel.getVisibility() == View.VISIBLE) hideTabs();
            else showTabs();
        });
        findViewById(R.id.btn_new_tab).setOnClickListener(v -> {
            hideTabs();
            newTab(HOME);
        });
        findViewById(R.id.btn_menu).setOnClickListener(v -> showMenuSheet());
        findViewById(R.id.fs_close).setOnClickListener(v -> exitPageFullscreen());
        findViewById(R.id.fs_pip).setOnClickListener(v -> enterPip());
        findViewById(R.id.fs_rotate).setOnClickListener(v -> toggleRotate());
        fsGestures.setOnTouchListener(this::onVideoGesture);
        playChip.setOnClickListener(v -> playDetected());
        View bkBtn = findViewById(R.id.btn_bookmark_page);
        if (bkBtn != null) bkBtn.setOnClickListener(v -> {
            String pg = current().getUrl();
            if (pg != null && !pg.startsWith("file:")) {
                BrowserPrefs.toggleBookmark(this, state(current()).title, pg);
                Toast.makeText(this, "Bookmarks updated", Toast.LENGTH_SHORT).show();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (customView != null) {
                    exitPageFullscreen();
                    return;
                }
                if (menuSheetScrim != null && menuSheetScrim.getVisibility() == View.VISIBLE) {
                    hideMenuSheet();
                    return;
                }
                if (tabsPanel.getVisibility() == View.VISIBLE) {
                    hideTabs();
                    return;
                }
                if (current().canGoBack()) {
                    current().goBack();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        newTab(HOME);
    }

    private void newTab(String url) {
        if (tabs.size() >= 8) {
            Toast.makeText(this, "Close a tab first", Toast.LENGTH_SHORT).show();
            return;
        }
        WebView web = makeWeb();
        tabs.add(web);
        stack.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        show(tabs.size() - 1);
        web.loadUrl(url);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView makeWeb() {
        WebView web = new WebView(this);
        web.setTag(new TabState());
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(!BrowserPrefs.disableJs(this));
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(BrowserPrefs.imageQuality(this) > 0);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setOffscreenPreRaster(true);
        String ua = settings.getUserAgentString().replace("; wv", "");
        if (mobileUa.isEmpty()) mobileUa = ua;
        if (BrowserPrefs.desktop(this)) {
            settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
            settings.setLoadWithOverviewMode(false);
        } else {
            settings.setUserAgentString(mobileUa);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, !BrowserPrefs.blockThirdPartyCookies(this));

        web.addJavascriptInterface(new Bridge(), "BlazeBridge");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (view != current()) return;
                progress.setVisibility(newProgress > 0 && newProgress < 100 ? View.VISIBLE : View.GONE);
                progress.setProgress(newProgress);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                state(view).title = title == null || title.isEmpty() ? "Tab" : title;
                if (view == current()) {
                    String page = view.getUrl();
                    if (page != null && !page.startsWith("file:")) {
                        BrowserPrefs.addHistory(MainActivity.this, state(view).title, page);
                    }
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                enterPageFullscreen(view, callback);
            }

            @Override
            public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
                enterPageFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                exitPageFullscreen();
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String url = uri.toString();
                if (!(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://"))) {
                    return true;
                }
                String rawUrl2 = request.getUrl().toString();
                if (BrowserPrefs.httpsOnly(MainActivity.this) && rawUrl2.startsWith("http://")
                        && !rawUrl2.startsWith("http://localhost") && !rawUrl2.startsWith("http://127.")) {
                    view.loadUrl(rawUrl2.replaceFirst("http://", "https://"));
                    return true;
                }
                if (request.isForMainFrame() && AdBlock.isAd(uri)) {
                    if (view == current()) {
                        Toast.makeText(MainActivity.this, "Blocked an ad page", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (AdBlock.isAd(uri)) {
                    return empty();
                }
                if (VideoSniffer.isPlayable(uri)) {
                    String url = uri.toString();
                    view.post(() -> rememberVideo(view, url));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (requestIsMain(url)) {
                    state(view).videos.clear();
                    state(view).pageVideo = false;
                    if (view == current()) updatePlayChip();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (view == current()) {
                    address.setText(url != null && url.startsWith("file:") ? "" : url);
                }
                if (sniffJs != null && !sniffJs.isEmpty() && url != null && !url.startsWith("file:")) {
                    view.evaluateJavascript(sniffJs, null);
                }
                if (BrowserPrefs.blockFonts(MainActivity.this) && url != null && !url.startsWith("file:")) {
                    view.evaluateJavascript("(function(){var s=document.getElementById('blaze-nf');if(s)return;s=document.createElement('style');s.id='blaze-nf';s.textContent='*{font-family:sans-serif!important;}';document.head.appendChild(s);})()", null);
                }
                if (BrowserPrefs.blockAutoplay(MainActivity.this) && url != null && !url.startsWith("file:")) {
                    view.evaluateJavascript("(function(){var o=HTMLMediaElement.prototype.play;HTMLMediaElement.prototype.play=function(){if(!this.hasAttribute('ba')){this.muted=true;}return o.apply(this,arguments);};document.addEventListener('click',function(e){var v=e.target;while(v){if(v.tagName=='VIDEO'||v.tagName=='AUDIO'){v.setAttribute('ba','1');break;}v=v.parentElement;}},true);})()", null);
                }
                if (url != null && !url.startsWith("file:") && BrowserPrefs.nightMode(MainActivity.this)) {
                    // Night mode: CSS filter invert on page, re-invert images/video so they look natural.
                    view.evaluateJavascript(
                            "(function(){var id='blaze-night';if(document.getElementById(id))return;"
                            + "var s=document.createElement('style');s.id=id;"
                            + "s.textContent='html{filter:invert(90%) hue-rotate(180deg)!important;background:#111!important;}'"
                            + "+'html img,html video,html canvas,html picture{filter:invert(110%) hue-rotate(180deg)!important;}';"
                            + "(document.head||document.documentElement).appendChild(s);})();",
                            null);
                }
            }
        });
        web.setDownloadListener((url, userAgent, contentDisposition, mime, contentLength) -> {
            try {
                String name = URLUtil.guessFileName(url, contentDisposition, mime);
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mime);
                req.addRequestHeader("User-Agent", userAgent);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (dm != null) dm.enqueue(req);
                Toast.makeText(this, "Downloading " + name, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not start download", Toast.LENGTH_SHORT).show();
            }
        });
        web.setOnLongClickListener(v -> {
            WebView.HitTestResult hit = ((WebView) v).getHitTestResult();
            int type = hit.getType();
            String extra = hit.getExtra();
            if (extra == null) return false;
            if (type == WebView.HitTestResult.IMAGE_TYPE
                    || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Image")
                    .setItems(new String[]{"Save image","Share URL","Copy URL","Open in new tab"}, (d2,w2) -> {
                        if (w2==0) {
                            try {
                                android.app.DownloadManager.Request req = new android.app.DownloadManager.Request(Uri.parse(extra));
                                req.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES,"img_"+System.currentTimeMillis()+".jpg");
                                ((android.app.DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                                Toast.makeText(MainActivity.this,"Saving image…",Toast.LENGTH_SHORT).show();
                            } catch(Exception e2){Toast.makeText(MainActivity.this,"Failed: "+e2.getMessage(),Toast.LENGTH_SHORT).show();}
                        } else if(w2==1){Intent s2=new Intent(Intent.ACTION_SEND);s2.setType("text/plain");s2.putExtra(Intent.EXTRA_TEXT,extra);startActivity(Intent.createChooser(s2,"Share"));}
                        else if(w2==2){android.content.ClipboardManager cb2=(android.content.ClipboardManager)getSystemService(android.content.Context.CLIPBOARD_SERVICE);if(cb2!=null)cb2.setPrimaryClip(android.content.ClipData.newPlainText("url",extra));Toast.makeText(MainActivity.this,"Copied",Toast.LENGTH_SHORT).show();}
                        else{newTab(extra);}
                    }).show();
                return true;
            }
            if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                showLinkMenu(extra);
                return true;
            }
            return false;
        });
        return web;
    }

    private void showLinkMenu(String url) {
        if (AdBlock.isAd(Uri.parse(url))) {
            Toast.makeText(this, "Blocked an ad link", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Link")
                .setItems(new String[]{"Open", "Open in new tab", "Copy link"}, (dialog, which) -> {
                    if (which == 0) current().loadUrl(url);
                    else if (which == 1) newTab(url);
                    else copy(url);
                })
                .show();
    }

    private void show(int next) {
        index = next;
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        WebView web = current();
        String url = web.getUrl();
        address.setText(url == null || url.startsWith("file:") ? "" : url);
        if (tabButton != null) tabButton.setText(String.valueOf(tabs.size()));
        updatePlayChip();
    }

    private void showTabs() {
        tabList.removeAllViews();
        int dp8  = (int)(8  * getResources().getDisplayMetrics().density);
        int dp12 = (int)(12 * getResources().getDisplayMetrics().density);
        int dp16 = (int)(16 * getResources().getDisplayMetrics().density);
        int dp20 = (int)(20 * getResources().getDisplayMetrics().density);
        int dp48 = (int)(48 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < tabs.size(); i++) {
            final int tabIndex = i;
            TabState state = state(tabs.get(i));
            String url   = tabs.get(i).getUrl();
            String title = (state.title == null || state.title.isEmpty()) ? "New Tab" : state.title;
            if (url == null || url.startsWith("file:")) title = "Home";

            // Row container
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp16, dp12, dp12, dp12);
            row.setMinimumHeight(dp48 + dp8);
            // Highlight active tab
            if (i == index) {
                row.setBackgroundColor(0xFF2A3A4A);
            } else {
                android.util.TypedValue tv = new android.util.TypedValue();
                getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
                row.setBackgroundResource(tv.resourceId);
            }

            // Favicon letter
            android.widget.TextView fav = new android.widget.TextView(this);
            fav.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp48, dp48));
            fav.setGravity(android.view.Gravity.CENTER);
            fav.setBackgroundColor(i == index ? 0xFF1A73E8 : 0xFF3A3A3A);
            fav.setTextColor(0xFFFFFFFF);
            fav.setTextSize(18);
            fav.setText(title.isEmpty() ? "?" : String.valueOf(title.charAt(0)).toUpperCase());
            row.addView(fav);

            // Title + URL
            android.widget.LinearLayout info = new android.widget.LinearLayout(this);
            info.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.LinearLayout.LayoutParams ilp =
                new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            ilp.setMarginStart(dp16);
            info.setLayoutParams(ilp);

            android.widget.TextView titleTv = new android.widget.TextView(this);
            titleTv.setText(title);
            titleTv.setTextColor(0xFFEEEEEE);
            titleTv.setTextSize(14);
            titleTv.setMaxLines(1);
            titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(titleTv);

            if (url != null && !url.startsWith("file:")) {
                android.widget.TextView urlTv = new android.widget.TextView(this);
                try { urlTv.setText(new java.net.URL(url).getHost()); }
                catch (Exception e) { urlTv.setText(url.length() > 40 ? url.substring(0,40)+"..." : url); }
                urlTv.setTextColor(0xFF888888);
                urlTv.setTextSize(11);
                urlTv.setMaxLines(1);
                urlTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                info.addView(urlTv);
            }
            row.addView(info);

            // Close button
            android.widget.TextView close = new android.widget.TextView(this);
            close.setText("\u2715");
            close.setTextColor(0xFF888888);
            close.setTextSize(18);
            close.setPadding(dp16, 0, dp8, 0);
            close.setOnClickListener(v -> closeTab(tabIndex));
            row.addView(close);

            row.setOnClickListener(v -> { hideTabs(); show(tabIndex); });

            tabList.addView(row);

            // Divider
            android.view.View div = new android.view.View(this);
            div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
            div.setBackgroundColor(0xFF3A3A3A);
            tabList.addView(div);
        }
        tabsPanel.setVisibility(android.view.View.VISIBLE);
        hideMenuSheet();
        // Wire bottom buttons in tabs panel
        tabsPanel.findViewById(R.id.btn_new_tab).setOnClickListener(v2 -> { hideTabs(); newTab(HOME); });
        android.view.View privBtn = tabsPanel.findViewById(R.id.btn_private_tab);
        if (privBtn != null) privBtn.setOnClickListener(v2 -> {
            hideTabs();
            Toast.makeText(this, "Private browsing opened", Toast.LENGTH_SHORT).show();
            newTabPrivate();
        });
    }

    private void hideTabs() {
        tabsPanel.setVisibility(View.GONE);
    }

    private void hideMenu() {
        hideMenuSheet();
    }

    private void closeTab(int tabIndex) {
        if (tabs.size() == 1) {
            current().loadUrl(HOME);
            hideTabs();
            return;
        }
        WebView web = tabs.remove(tabIndex);
        stack.removeView(web);
        web.destroy();
        if (index >= tabs.size()) index = tabs.size() - 1;
        show(index);
        showTabs();
    }

    private void goBack() {
        if (customView != null) {
            exitPageFullscreen();
            return;
        }
        if (menuSheetScrim != null && menuSheetScrim.getVisibility() == View.VISIBLE) {
            hideMenu();
            return;
        }
        if (tabsPanel.getVisibility() == View.VISIBLE) {
            hideTabs();
            return;
        }
        if (current().canGoBack()) current().goBack();
    }

    private void openInput(String raw, boolean newTab) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) return;
        String url;
        if (t.startsWith("http://") || t.startsWith("https://")) url = t;
        else if (t.contains(".") && !t.contains(" ")) url = "https://" + t;
        else url = buildSearchUrl(t);
        if (AdBlock.isAd(Uri.parse(url))) {
            Toast.makeText(this, "Blocked an ad link", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newTab) newTab(url);
        else current().loadUrl(url);
    }

    private void rememberVideo(WebView view, String url) {
        if (url == null || url.startsWith("blob:") || url.startsWith("data:")) return;
        if (AdBlock.isAd(Uri.parse(url)) || !VideoSniffer.isPlayable(Uri.parse(url))) return;
        LinkedHashSet<String> videos = state(view).videos;
        if (videos.size() >= 8 || !videos.add(url)) return;
        if (view == current()) updatePlayChip();
    }

    private void updatePlayChip() {
        TabState tab = state(current());
        boolean hasFile = !tab.videos.isEmpty();
        if (!hasFile && !tab.pageVideo) {
            playChip.setVisibility(View.GONE);
            return;
        }
        playChip.setText(hasFile ? "Watch video" : "Fullscreen video");
        playChip.setVisibility(View.VISIBLE);
    }

    private void playDetected() {
        LinkedHashSet<String> videos = state(current()).videos;
        if (!videos.isEmpty()) {
            Intent intent = new Intent(this, VideoPlayerActivity.class);
            intent.putStringArrayListExtra(VideoPlayerActivity.EXTRA_URLS, new ArrayList<>(videos));
            intent.putExtra(VideoPlayerActivity.EXTRA_REFERER, current().getUrl());
            intent.putExtra(VideoPlayerActivity.EXTRA_UA, current().getSettings().getUserAgentString());
            intent.putExtra(VideoPlayerActivity.EXTRA_WIDTH, state(current()).vw);
            intent.putExtra(VideoPlayerActivity.EXTRA_HEIGHT, state(current()).vh);
            startActivity(intent);
            return;
        }
        current().evaluateJavascript(
                "(function(){var list=document.querySelectorAll('video');var best=null,area=0;"
                        + "for(var i=0;i<list.length;i++){var v=list[i];var a=(v.videoWidth||v.clientWidth||0)*(v.videoHeight||v.clientHeight||0);"
                        + "if(!best||a>=area){best=v;area=a;}}"
                        + "if(!best)return 'none';try{best.play();}catch(e){}"
                        + "if(best.webkitEnterFullscreen)best.webkitEnterFullscreen();"
                        + "else if(best.requestFullscreen)best.requestFullscreen();"
                        + "return 'ok';})();",
                value -> {
                    if (value != null && value.contains("none")) {
                        Toast.makeText(this, "No video on this page yet. Start the video, then tap again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void prefetch(String url) {
        if (!BrowserPrefs.preload(this)) return;
        if (url == null || url.equals(lastPrefetch)) return;
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return;
        if (AdBlock.isAd(Uri.parse(url))) return;
        lastPrefetch = url;
        if (warmer == null) {
            warmer = new WebView(this);
            warmer.getSettings().setJavaScriptEnabled(false);
            warmer.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            warmer.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if (AdBlock.isAd(request.getUrl())) return empty();
                    return super.shouldInterceptRequest(view, request);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return AdBlock.isAd(request.getUrl());
                }
            });
        }
        warmer.loadUrl(url);
    }

    private WebView current() {
        return tabs.get(index);
    }

    private TabState state(WebView web) {
        Object tag = web.getTag();
        if (tag instanceof TabState) return (TabState) tag;
        TabState created = new TabState();
        web.setTag(created);
        return created;
    }

    private void copy(String url) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("link", url));
        Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(address.getWindowToken(), 0);
    }

    private static boolean requestIsMain(String url) {
        return url != null && !url.startsWith("about:");
    }

    private static WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    private String readAsset(String path) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open(path), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (customView != null) enterPip();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPip, newConfig);
        if (fsChrome != null) fsChrome.setVisibility(inPip ? View.GONE : View.VISIBLE);
        if (fsGestures != null) fsGestures.setVisibility(inPip ? View.GONE : View.VISIBLE);
        if (fsHud != null && inPip) fsHud.setVisibility(View.GONE);
    }

    private void showMenu() { showMenuSheet(); }





    private void showFind() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Find");
        new AlertDialog.Builder(this)
                .setTitle("Find on page")
                .setView(input)
                .setPositiveButton("Find", (d, w) -> current().findAllAsync(input.getText().toString()))
                .setNeutralButton("Next", (d, w) -> current().findNext(true))
                .show();
        hideMenu();
    }

    private void applyChromeMode(WebView web) {
        WebSettings settings = web.getSettings();
        if (BrowserPrefs.desktop(this)) {
            settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(false);
        } else if (!mobileUa.isEmpty()) {
            settings.setUserAgentString(mobileUa);
            settings.setLoadWithOverviewMode(true);
        }
    }

    private void enterPageFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customCallback = callback;
        fsVideo.removeAllViews();
        fsVideo.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        fullscreenHolder.setVisibility(View.VISIBLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        applyVideoOrientation();
    }

    private void exitPageFullscreen() {
        if (customView == null) return;
        fsVideo.removeAllViews();
        fullscreenHolder.setVisibility(View.GONE);
        fsHud.setVisibility(View.GONE);
        customView = null;
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private void applyVideoOrientation() {
        TabState tab = state(current());
        if (tab.vw > 0 && tab.vh > 0 && tab.vw >= tab.vh) {
            landscapeLock = true;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else if (tab.vh > tab.vw && tab.vw > 0) {
            landscapeLock = false;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }
    }

    private void toggleRotate() {
        landscapeLock = !landscapeLock;
        setRequestedOrientation(landscapeLock
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26 || customView == null) return;
        TabState tab = state(current());
        int w = tab.vw > 0 ? tab.vw : 16;
        int h = tab.vh > 0 ? tab.vh : 9;
        float ratio = w / (float) h;
        if (ratio < 0.5f) ratio = 0.5f;
        if (ratio > 2.3f) ratio = 2.3f;
        try {
            enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(Math.round(ratio * 100), 100))
                    .build());
        } catch (Exception e) {
            Toast.makeText(this, "Picture in picture is not available", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean onVideoGesture(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                gestureMode = "pending";
                gestureMoved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                float width = Math.max(1f, v.getWidth());
                float height = Math.max(1f, v.getHeight());
                if ("pending".equals(gestureMode)) {
                    if (Math.hypot(dx, dy) < 16) return true;
                    gestureMoved = true;
                    if (Math.abs(dx) > Math.abs(dy)) gestureMode = "seek";
                    else gestureMode = downX < width / 2f ? "bright" : "volume";
                }
                if ("seek".equals(gestureMode)) {
                    int sec = Math.round(dx / width * 40f);
                    current().evaluateJavascript(
                            "(function(){var list=document.querySelectorAll('video');var v=null;"
                                    + "for(var i=0;i<list.length;i++){if(!list[i].paused)v=list[i];}"
                                    + "if(!v&&list.length)v=list[0];if(!v)return;var d=v.duration||0;"
                                    + "var n=v.currentTime+" + sec + ";if(d)n=Math.max(0,Math.min(d,n));v.currentTime=n;})();",
                            null);
                    showFsHud((sec >= 0 ? "+" : "") + sec + "s");
                } else if ("volume".equals(gestureMode)) {
                    int vol = Math.max(0, Math.min(100, Math.round(70 - (dy / height) * 120f)));
                    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                    int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(vol / 100f * max), 0);
                    showFsHud("Volume " + vol + "%");
                } else if ("bright".equals(gestureMode)) {
                    int pct = Math.max(8, Math.min(100, Math.round(80 - (dy / height) * 120f)));
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = pct / 100f;
                    getWindow().setAttributes(lp);
                    showFsHud("Brightness " + pct + "%");
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!gestureMoved) {
                    current().evaluateJavascript(
                            "(function(){var v=document.querySelector('video');if(!v)return;if(v.paused)v.play();else v.pause();})();",
                            null);
                }
                gestureMode = "none";
                fsHud.postDelayed(() -> fsHud.setVisibility(View.GONE), 280);
                return true;
            default:
                return false;
        }
    }

    private void showFsHud(String text) {
        fsHud.setText(text);
        fsHud.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (warmer != null) warmer.destroy();
        for (WebView web : tabs) web.destroy();
        tabs.clear();
        super.onDestroy();
    }

    public class Bridge {
        @JavascriptInterface
        public void open(String raw) {
            runOnUiThread(() -> openInput(raw, false));
        }

        @JavascriptInterface
        public void onVideo(String url) {
            runOnUiThread(() -> rememberVideo(current(), url));
        }

        @JavascriptInterface
        public void onPageVideo(int width, int height) {
            runOnUiThread(() -> {
                TabState tab = state(current());
                tab.pageVideo = true;
                if (width > 0 && height > 0) {
                    tab.vw = width;
                    tab.vh = height;
                }
                updatePlayChip();
            });
        }

        @JavascriptInterface
        public void prefetch(String url) {
            runOnUiThread(() -> MainActivity.this.prefetch(url));
        }
    }

    private static final class TabState {
        boolean isPrivate = false;
        String title = "Home";
        final LinkedHashSet<String> videos = new LinkedHashSet<>();
        boolean pageVideo;
        int vw;
        int vh;
    }

    private String buildSearchUrl(String query) {
        String enc = Uri.encode(query);
        switch (BrowserPrefs.searchEngine(this)) {
            case "duckduckgo": return "https://duckduckgo.com/?q=" + enc;
            case "bing":       return "https://www.bing.com/search?q=" + enc;
            case "brave":      return "https://search.brave.com/search?q=" + enc;
            default:           return "https://www.google.com/search?q=" + enc + "&hl=en";
        }
    }

    private boolean inReaderMode = false;
    private String originalUrl = "";

    private void toggleReaderMode() {
        if (!inReaderMode) {
            originalUrl = current().getUrl() != null ? current().getUrl() : "";
            current().evaluateJavascript(
                "(function(){var t=document.title||'';var art=document.querySelector('article')||document.querySelector('main')||document.body;var els=art.querySelectorAll('p,h1,h2,h3,h4');var html='';for(var i=0;i<els.length;i++)html+=els[i].outerHTML;return JSON.stringify({t:t,b:html});})()",
                value -> runOnUiThread(() -> {
                    if (value == null || value.equals("null")) {
                        Toast.makeText(this, "Reader mode not available", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        String j = value;
                        if (j.startsWith("\"")) j = j.substring(1, j.length() - 1);
                        j = j.replace("\\\\", "BACKSLASH4").replace("\\\"", "\"").replace("\\n", "\n").replace("BACKSLASH4", "\\\\");
                        org.json.JSONObject obj = new org.json.JSONObject(j);
                        String t2 = obj.optString("t", "Article");
                        String b2 = obj.optString("b", "");
                        boolean night = BrowserPrefs.nightMode(this);
                        String bg = night ? "#121212" : "#FAFAFA";
                        String fg = night ? "#E0E0E0" : "#222222";
                        String lk = night ? "#8AB4F8" : "#1A73E8";
                        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'/>"
                            + "<meta name='viewport' content='width=device-width,initial-scale=1'/>"
                            + "<style>body{margin:0;padding:20px;background:" + bg + ";color:" + fg + ";font-family:Georgia,serif;font-size:18px;line-height:1.7}h1,h2,h3{line-height:1.3}a{color:" + lk + "}p{margin:0 0 1em}</style></head>"
                            + "<body><h1 style='font-size:22px;margin-bottom:1em'>" + obj.optString("t","Article") + "</h1>" + b2 + "</body></html>";
                        current().loadDataWithBaseURL(originalUrl, html, "text/html", "utf-8", null);
                        inReaderMode = true;
                        Toast.makeText(this, "Reader mode ON", Toast.LENGTH_SHORT).show();
                    } catch (Exception e2) {
                        Toast.makeText(this, "Could not parse page", Toast.LENGTH_SHORT).show();
                    }
                })
            );
        } else {
            inReaderMode = false;
            if (!originalUrl.isEmpty()) current().loadUrl(originalUrl);
            Toast.makeText(this, "Reader mode OFF", Toast.LENGTH_SHORT).show();
        }
    }

    private void newTabPrivate() {
        if (tabs.size() >= 8) { Toast.makeText(this,"Close a tab first",Toast.LENGTH_SHORT).show(); return; }
        android.webkit.WebView web2 = makeWeb();
        ((TabState)web2.getTag()).isPrivate = true;
        tabs.add(web2);
        stack.addView(web2, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        show(tabs.size() - 1);
        web2.loadUrl(HOME);
        Toast.makeText(this, "Private tab opened", Toast.LENGTH_SHORT).show();
    }

    private void fetchSuggestions(String query, java.util.function.Consumer<java.util.List<String>> cb) {
        new Thread(() -> {
            java.util.List<String> results = new java.util.ArrayList<>();
            try {
                String apiUrl = "duckduckgo".equals(BrowserPrefs.searchEngine(this))
                    ? "https://duckduckgo.com/ac/?q=" + Uri.encode(query) + "&type=list"
                    : "https://suggestqueries.google.com/complete/search?client=firefox&q=" + Uri.encode(query);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(apiUrl).openConnection();
                conn.setConnectTimeout(2500); conn.setReadTimeout(2500);
                conn.setRequestProperty("User-Agent","Mozilla/5.0");
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb2 = new StringBuilder();
                    String ln; while ((ln = br.readLine()) != null) sb2.append(ln);
                    conn.disconnect();
                    String raw = sb2.toString().trim();
                    int s2 = raw.indexOf('[', 1), e2 = raw.lastIndexOf(']');
                    if (s2 >= 0 && e2 > s2) {
                        String inner = raw.substring(s2 + 1, e2);
                        for (String item : inner.split(",")) {
                            String sg = item.trim().replace("\"","");
                            if (!sg.isEmpty() && !sg.startsWith("{") && !sg.startsWith("[")) {
                                results.add(sg); if (results.size() >= 5) break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            cb.accept(results);
        }).start();
    }

    private void showMenuSheet() {
        if (menuSheetScrim == null) return;
        menuSheetScrim.setVisibility(android.view.View.VISIBLE);
        wireMenuBtn(R.id.menu_bookmarks, v -> { hideMenuSheet(); showPageList("Bookmarks", BrowserPrefs.bookmarks(this)); });
        wireMenuBtn(R.id.menu_history,   v -> { hideMenuSheet(); showPageList("History",   BrowserPrefs.history(this)); });
        wireMenuBtn(R.id.menu_downloads, v -> { hideMenuSheet(); startActivity(new android.content.Intent(this, DownloadsActivity.class)); });
        wireMenuBtn(R.id.menu_night, v -> {
            boolean on = !BrowserPrefs.nightMode(this);
            BrowserPrefs.setNightMode(this, on);
            hideMenuSheet();
            if (on) {
                // Inject night mode CSS immediately — no reload needed
                current().evaluateJavascript(
                    "(function(){var id='blaze-night';if(document.getElementById(id))return;"
                    + "var s=document.createElement('style');s.id=id;"
                    + "s.textContent='html{filter:invert(90%) hue-rotate(180deg)!important;background:#111!important;}'"
                    + "+'html img,html video,html canvas,html picture{filter:invert(110%) hue-rotate(180deg)!important;}';"
                    + "(document.head||document.documentElement).appendChild(s);})();", null);
            } else {
                // Remove night mode CSS without reload
                current().evaluateJavascript(
                    "(function(){var s=document.getElementById('blaze-night');if(s)s.parentNode.removeChild(s);})();", null);
            }
            Toast.makeText(this, on ? "Night mode ON" : "Night mode OFF", Toast.LENGTH_SHORT).show();
        });
        wireMenuBtn(R.id.menu_settings_btn, v -> { hideMenuSheet(); startActivity(new android.content.Intent(this, SettingsActivity.class)); });
        wireMenuBtn(R.id.menu_add_bookmark, v -> { String pg=current().getUrl(); if(pg!=null&&!pg.startsWith("file:"))BrowserPrefs.toggleBookmark(this,state(current()).title,pg); Toast.makeText(this,"Bookmarks updated",Toast.LENGTH_SHORT).show(); hideMenuSheet(); });
        wireMenuBtn(R.id.menu_refresh, v -> { String url=current().getUrl(); if(url==null||url.startsWith("file:"))current().loadUrl(HOME); else current().reload(); hideMenuSheet(); });
        wireMenuBtn(R.id.menu_share, v -> { String pg=current().getUrl(); if(pg!=null&&!pg.startsWith("file:")){android.content.Intent s=new android.content.Intent(android.content.Intent.ACTION_SEND);s.setType("text/plain");s.putExtra(android.content.Intent.EXTRA_TEXT,pg);startActivity(android.content.Intent.createChooser(s,"Share"));} hideMenuSheet(); });
        wireMenuBtn(R.id.menu_desktop, v -> { boolean next=!BrowserPrefs.desktop(this);BrowserPrefs.setDesktop(this,next);applyChromeMode(current());current().reload(); android.widget.TextView lbl=(android.widget.TextView)menuSheetScrim.findViewById(R.id.menu_desktop_label);if(lbl!=null)lbl.setText(next?"Desktop: ON":"Desktop"); });
        wireMenuBtn(R.id.menu_clear_cache, v -> { for(android.webkit.WebView w:tabs)w.clearCache(true);Toast.makeText(this,"Cache cleared",Toast.LENGTH_SHORT).show();hideMenuSheet(); });
        wireMenuBtn(R.id.menu_find,        v -> { hideMenuSheet(); showFind(); });
        wireMenuBtn(R.id.menu_translate,   v -> { hideMenuSheet(); String pg=current().getUrl(); if(pg!=null&&!pg.startsWith("file:"))current().loadUrl("https://translate.google.com/translate?sl=auto&tl=en&u="+Uri.encode(pg)); });
        wireMenuBtn(R.id.menu_reader_mode, v -> { hideMenuSheet(); toggleReaderMode(); });
        wireMenuBtn(R.id.menu_new_private, v -> { hideMenuSheet(); newTabPrivate(); });
        wireMenuBtn(R.id.menu_close,       v -> hideMenuSheet());
        menuSheetScrim.setOnClickListener(v -> hideMenuSheet());
    }

    private void wireMenuBtn(int id, android.view.View.OnClickListener l) {
        android.view.View v = menuSheetScrim.findViewById(id);
        if (v != null) v.setOnClickListener(l);
    }

    private void hideMenuSheet() {
        if (menuSheetScrim != null) menuSheetScrim.setVisibility(android.view.View.GONE);
    }

    private void showPageList(String title, java.util.List<String[]> rows) {
        if (rows.isEmpty()) { Toast.makeText(this, title + " is empty", Toast.LENGTH_SHORT).show(); return; }
        String[] labels = rows.stream().map(r -> r[0]).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(title)
            .setItems(labels, (d,i) -> current().loadUrl(rows.get(i)[1]))
            .setNegativeButton("Close", null).show();
    }

}