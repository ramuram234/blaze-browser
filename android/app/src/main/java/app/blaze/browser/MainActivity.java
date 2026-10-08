package app.blaze.browser;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Intent;
import android.media.AudioManager;
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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {
    private static final String HOME = "file:///android_asset/www/index.html";

    private WebView web;
    private EditText address;
    private ProgressBar progress;
    private String gestureJs = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        gestureJs = readAsset("www/gestures.js");

        web = findViewById(R.id.web);
        address = findViewById(R.id.address);
        progress = findViewById(R.id.progress);

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
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        String ua = settings.getUserAgentString().replace("; wv", "");
        settings.setUserAgentString(ua + " BlazeBrowser/1.0");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "BlazeBridge");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(newProgress > 0 && newProgress < 100 ? View.VISIBLE : View.GONE);
                progress.setProgress(newProgress);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return !(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://"));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && url.startsWith("file:")) {
                    address.setText("");
                } else if (url != null) {
                    address.setText(url);
                }
                if (!gestureJs.isEmpty()) {
                    view.evaluateJavascript(gestureJs, null);
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
                dm.enqueue(req);
                Toast.makeText(this, "Downloading " + name, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not start download", Toast.LENGTH_SHORT).show();
            }
        });

        address.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (!go) return false;
            openInput(address.getText().toString());
            hideKeyboard();
            return true;
        });

        findViewById(R.id.btn_go).setOnClickListener(v -> {
            openInput(address.getText().toString());
            hideKeyboard();
        });
        findViewById(R.id.btn_home).setOnClickListener(v -> web.loadUrl(HOME));
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (web.canGoBack()) web.goBack();
            else web.loadUrl(HOME);
        });
        findViewById(R.id.btn_forward).setOnClickListener(v -> {
            if (web.canGoForward()) web.goForward();
        });
        findViewById(R.id.btn_reload).setOnClickListener(v -> {
            String current = web.getUrl();
            if (current == null || current.startsWith("file:")) web.loadUrl(HOME);
            else web.reload();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack();
                else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            handleIntent(getIntent(), true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent, false);
    }

    private void handleIntent(Intent intent, boolean fallbackHome) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            web.loadUrl(intent.getData().toString());
            return;
        }
        if (fallbackHome) web.loadUrl(HOME);
    }

    private void openInput(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) return;
        String url;
        if (t.startsWith("http://") || t.startsWith("https://")) {
            url = t;
        } else if (t.contains(".") && !t.contains(" ")) {
            url = "https://" + t;
        } else {
            url = "https://duckduckgo.com/?q=" + Uri.encode(t);
        }
        web.loadUrl(url);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(address.getWindowToken(), 0);
    }

    private String readAsset(String path) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open(path), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    public class Bridge {
        @JavascriptInterface
        public void setVolume(int percent) {
            runOnUiThread(() -> {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                int value = Math.round(Math.max(0, Math.min(100, percent)) / 100f * max);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
            });
        }

        @JavascriptInterface
        public int getVolume() {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            return Math.round(cur * 100f / max);
        }

        @JavascriptInterface
        public void setBrightness(int percent) {
            runOnUiThread(() -> {
                float level = Math.max(5, Math.min(100, percent)) / 100f;
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = level;
                getWindow().setAttributes(lp);
            });
        }

        @JavascriptInterface
        public void open(String raw) {
            runOnUiThread(() -> openInput(raw));
        }
    }
}
