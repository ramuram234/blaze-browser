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

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sniffJs = readAsset("www/sniff.js");

        stack = findViewById(R.id.web_stack);
        address = findViewById(R.id.address);
        progress = findViewById(R.id.progress);
        playChip = findViewById(R.id.btn_play);
        tabButton = findViewById(R.id.btn_tabs);
        tabsPanel = findViewById(R.id.tabs_panel);
        tabList = findViewById(R.id.tab_list);

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
        playChip.setOnClickListener(v -> playDetected());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
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
        settings.setUserAgentString(ua);

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
    }

    private void hideTabs() {
        tabsPanel.setVisibility(View.GONE);
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
        int count = state(current()).videos.size();
        if (count == 0) {
            playChip.setVisibility(View.GONE);
            return;
        }
        playChip.setText(count == 1 ? "Play in Blaze" : "Play in Blaze (" + count + ")");
        playChip.setVisibility(View.VISIBLE);
    }

    private void playDetected() {
        LinkedHashSet<String> videos = state(current()).videos;
        if (videos.isEmpty()) return;
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putStringArrayListExtra(VideoPlayerActivity.EXTRA_URLS, new ArrayList<>(videos));
        String page = current().getUrl();
        intent.putExtra(VideoPlayerActivity.EXTRA_REFERER, page);
        intent.putExtra(VideoPlayerActivity.EXTRA_UA, current().getSettings().getUserAgentString());
        startActivity(intent);
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
        public void prefetch(String url) {
            runOnUiThread(() -> MainActivity.this.prefetch(url));
        }
    }

    private static final class TabState {
        String title = "Home";
        final LinkedHashSet<String> videos = new LinkedHashSet<>();
    }
}
