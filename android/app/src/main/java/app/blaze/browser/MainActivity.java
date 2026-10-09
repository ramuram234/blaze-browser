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
    private LinearLayout tabsPanel;
    private LinearLayout tabList;
    private WebView warmer;
    private View browserUi;
    private FrameLayout fullscreenHolder;
    private FrameLayout fsVideo;
    private View fsGestures;
    private View fsChrome;
    private TextView fsHud;
    private LinearLayout menuPanel;
    private LinearLayout menuList;
    private TextView menuTitle;
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
        sniffJs = readAsset("www/sniff.js");

        stack = findViewById(R.id.web_stack);
        address = findViewById(R.id.address);
        progress = findViewById(R.id.progress);
        playChip = findViewById(R.id.btn_play);
        tabButton = findViewById(R.id.btn_tabs);
        tabsPanel = findViewById(R.id.tabs_panel);
        tabList = findViewById(R.id.tab_list);
        browserUi = findViewById(R.id.browser_ui);
        fullscreenHolder = findViewById(R.id.fullscreen_holder);
        fsVideo = findViewById(R.id.fs_video);
        fsGestures = findViewById(R.id.fs_gestures);
        fsChrome = findViewById(R.id.fs_chrome);
        fsHud = findViewById(R.id.fs_hud);
        menuPanel = findViewById(R.id.menu_panel);
        menuList = findViewById(R.id.menu_list);
        menuTitle = findViewById(R.id.menu_title);

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
            openInput(address.getText().toString(), false);
            hideKeyboard();
        });
        findViewById(R.id.btn_home).setOnClickListener(v -> {
            hideTabs();
            current().loadUrl(HOME);
        });
        findViewById(R.id.btn_back).setOnClickListener(v -> goBack());
        findViewById(R.id.btn_forward).setOnClickListener(v -> {
            if (current().canGoForward()) current().goForward();
        });
        findViewById(R.id.btn_reload).setOnClickListener(v -> {
            String url = current().getUrl();
            if (url == null || url.startsWith("file:")) current().loadUrl(HOME);
            else current().reload();
        });
        tabButton.setOnClickListener(v -> {
            if (tabsPanel.getVisibility() == View.VISIBLE) hideTabs();
            else showTabs();
        });
        findViewById(R.id.btn_new_tab).setOnClickListener(v -> {
            hideTabs();
            newTab(HOME);
        });
        findViewById(R.id.btn_menu).setOnClickListener(v -> {
            if (menuPanel.getVisibility() == View.VISIBLE) hideMenu();
            else showMenu();
        });
        findViewById(R.id.fs_close).setOnClickListener(v -> exitPageFullscreen());
        findViewById(R.id.fs_pip).setOnClickListener(v -> enterPip());
        findViewById(R.id.fs_rotate).setOnClickListener(v -> toggleRotate());
        fsGestures.setOnTouchListener(this::onVideoGesture);
        playChip.setOnClickListener(v -> playDetected());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (customView != null) {
                    exitPageFullscreen();
                    return;
                }
                if (menuPanel.getVisibility() == View.VISIBLE) {
                    hideMenu();
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
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
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
        cookies.setAcceptThirdPartyCookies(web, true);

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
            if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                    || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
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
        tabButton.setText(String.valueOf(tabs.size()));
        updatePlayChip();
    }

    private void showTabs() {
        tabList.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            final int tabIndex = i;
            TabState state = state(tabs.get(i));
            String url = tabs.get(i).getUrl();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(12, 18, 12, 18);
            TextView label = new TextView(this);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            String title = state.title == null ? "Tab" : state.title;
            if (url == null || url.startsWith("file:")) title = "Home";
            label.setText(title);
            label.setTextColor(0xFF16181C);
            label.setTextSize(15);
            label.setOnClickListener(v -> {
                hideTabs();
                show(tabIndex);
            });
            TextView close = new TextView(this);
            close.setText("Close");
            close.setTextColor(0xFF9A2E00);
            close.setPadding(16, 0, 0, 0);
            close.setOnClickListener(v -> closeTab(tabIndex));
            row.addView(label);
            row.addView(close);
            tabList.addView(row);
        }
        tabsPanel.setVisibility(View.VISIBLE);
        menuPanel.setVisibility(View.GONE);
    }

    private void hideTabs() {
        tabsPanel.setVisibility(View.GONE);
    }

    private void hideMenu() {
        menuPanel.setVisibility(View.GONE);
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
        if (menuPanel.getVisibility() == View.VISIBLE) {
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
        else url = "https://www.google.com/search?q=" + Uri.encode(t) + "&hl=en";
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

    private void showMenu() {
        hideTabs();
        menuTitle.setText("Menu");
        menuList.removeAllViews();
        String page = current().getUrl();
        addRow("History", () -> showPages("History", BrowserPrefs.history(this)));
        addRow("Bookmarks", () -> showPages("Bookmarks", BrowserPrefs.bookmarks(this)));
        addRow(page != null && BrowserPrefs.isBookmarked(this, page) ? "Remove bookmark" : "Bookmark this page", () -> {
            BrowserPrefs.toggleBookmark(this, state(current()).title, page);
            Toast.makeText(this, "Bookmarks updated", Toast.LENGTH_SHORT).show();
            hideMenu();
        });
        addRow("Share page", () -> {
            if (page == null || page.startsWith("file:")) {
                Toast.makeText(this, "Open a page first", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, page);
            startActivity(Intent.createChooser(send, "Share"));
        });
        addRow("Find on page", this::showFind);
        addRow(BrowserPrefs.desktop(this) ? "Desktop site: on" : "Desktop site: off", () -> {
            boolean next = !BrowserPrefs.desktop(this);
            BrowserPrefs.setDesktop(this, next);
            applyChromeMode(current());
            current().reload();
            hideMenu();
        });
        addRow(BrowserPrefs.adsEnabled(this) ? "Block ads: on" : "Block ads: off", () -> {
            boolean next = !BrowserPrefs.adsEnabled(this);
            BrowserPrefs.setAdsEnabled(this, next);
            Toast.makeText(this, next ? "Ad blocking on" : "Ad blocking off", Toast.LENGTH_SHORT).show();
            hideMenu();
        });
        addRow("Clear cache", () -> {
            for (WebView web : tabs) web.clearCache(true);
            Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
            hideMenu();
        });
        addRow("Clear history", () -> {
            BrowserPrefs.clearHistory(this);
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
            hideMenu();
        });
        menuPanel.setVisibility(View.VISIBLE);
    }

    private void addRow(String label, Runnable action) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextColor(0xFF16181C);
        row.setTextSize(16);
        row.setPadding(8, 28, 8, 28);
        row.setOnClickListener(v -> action.run());
        menuList.addView(row);
    }

    private void showPages(String title, java.util.List<String[]> rows) {
        menuTitle.setText(title);
        menuList.removeAllViews();
        addRow("Back to menu", this::showMenu);
        if (rows.isEmpty()) {
            addRow("Nothing saved yet", () -> {});
            return;
        }
        for (String[] row : rows) {
            addRow(row[0], () -> {
                hideMenu();
                current().loadUrl(row[1]);
            });
        }
    }

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
        String title = "Home";
        final LinkedHashSet<String> videos = new LinkedHashSet<>();
        boolean pageVideo;
        int vw;
        int vh;
    }
}
