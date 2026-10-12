package app.blaze.browser;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class VideoPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_URLS = "urls";
    public static final String EXTRA_REFERER = "referer";
    public static final String EXTRA_UA = "ua";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_TITLE  = "title";
    public static final String EXTRA_HEIGHT = "height";

    private ExoPlayer player;
    private TextView hud;
    private TextView hint;
    private TextView timeLabel;
    private android.widget.SeekBar seekBar;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String mode = "none";
    private float downX;
    private float downY;
    private long startPos;
    private int startVolume = 50;
    private float startBright = 0.8f;
    private boolean moved;
    private boolean locked;
    private boolean bgAudio;
    private int speedIndex = 2;
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    private TextView speedBtn;
    private TextView lockBtn;
    private android.widget.TextView dataMeter;
    private long sessionDataBytes = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (urls == null || urls.isEmpty()) {
            finish();
            return;
        }
        String referer = getIntent().getStringExtra(EXTRA_REFERER);
        String ua = getIntent().getStringExtra(EXTRA_UA);
        if (ua == null || ua.isEmpty()) ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36";

        hud = findViewById(R.id.hud);
        hint = findViewById(R.id.hint);
        timeLabel = (TextView) findViewById(R.id.time_current);
        seekBar = (android.widget.SeekBar) findViewById(R.id.seek_bar);

        dataMeter = (android.widget.TextView) findViewById(R.id.data_meter);
        ImageButton close = (ImageButton) findViewById(R.id.close_player);
        View layer = findViewById(R.id.gesture_layer);
        PlayerView playerView = findViewById(R.id.player_view);

        Map<String, String> headers = new HashMap<>();
        if (referer != null && referer.startsWith("http")) headers.put("Referer", referer);
        String cookie = CookieManager.getInstance().getCookie(urls.get(0));
        if (cookie != null) headers.put("Cookie", cookie);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(12000);
        DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(this, http);
        int preloadMode = BrowserPrefs.videoPreloadMode(this);
        int maxBufMs = preloadMode == 0 ? 5_000 : preloadMode == 1 ? 300_000 : 900_000;
        int minBufMs = preloadMode == 0 ? 1_000 : 5_000;
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(minBufMs, maxBufMs, 800, 1_500)
                .build();

        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource))
                .build();
        playerView.setPlayer(player);

        ArrayList<MediaItem> items = new ArrayList<>();
        for (String url : urls) {
            if (url == null || url.isEmpty()) continue;
            items.add(MediaItem.fromUri(Uri.parse(url)));
        }
        if (items.isEmpty()) {
            finish();
            return;
        }
        player.setMediaItems(items, 0, 0);
        player.setPlayWhenReady(true);
        player.prepare();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(VideoPlayerActivity.this, "This video cannot be played here", Toast.LENGTH_SHORT).show();
                if (player != null && player.getMediaItemCount() <= 1) finish();
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                orient(videoSize.width, videoSize.height);
            }
        });

        orient(getIntent().getIntExtra(EXTRA_WIDTH, 0), getIntent().getIntExtra(EXTRA_HEIGHT, 0));
        findViewById(R.id.pip_player).setOnClickListener(v -> enterPip());

        bgAudio = BrowserPrefs.backgroundAudio(this);
        speedBtn = findViewById(R.id.btn_speed);
        lockBtn = findViewById(R.id.btn_lock);
        TextView bgBtn = findViewById(R.id.btn_bg_audio);
        bgBtn.setText(bgAudio ? "BG on" : "BG audio");
        speedBtn.setOnClickListener(v -> {
            if (player == null || locked) return;
            speedIndex = (speedIndex + 1) % SPEEDS.length;
            player.setPlaybackSpeed(SPEEDS[speedIndex]);
            speedBtn.setText(String.format(java.util.Locale.US, "%.2fx", SPEEDS[speedIndex]));
            showHud("Speed " + speedBtn.getText());
        });
        lockBtn.setOnClickListener(v -> {
            locked = !locked;
            lockBtn.setText(locked ? "Unlock" : "Lock");
            showHud(locked ? "Locked" : "Unlocked");
        });
        bgBtn.setOnClickListener(v -> {
            bgAudio = !bgAudio;
            BrowserPrefs.setBackgroundAudio(this, bgAudio);
            bgBtn.setText(bgAudio ? "BG on" : "BG audio");
            showHud(bgAudio ? "Background audio on" : "Background audio off");
        });

        close.setOnClickListener(v -> finish());
        android.view.View dlBtn = findViewById(R.id.btn_download_overlay);
        if (dlBtn != null) dlBtn.setOnClickListener(v -> downloadCurrentVideo());
        layer.setOnTouchListener((v, event) -> onGesture(v, event));
        handler.postDelayed(() -> hint.setVisibility(View.GONE), 2800);
        checkMobileDataAndWarn();
        handler.post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (player == null) return;
            long dur = player.getDuration();
            long pos = player.getCurrentPosition();
            long buf = player.getBufferedPosition();
            if (dur > 0) {
                if (seekBar != null) seekBar.setProgress((int) (pos * 1000 / dur));
                
                timeLabel.setText(format(pos) + " / " + format(dur) + "   buffered " + (buf * 100 / dur) + "%");
            } else {
                timeLabel.setText(player.getPlaybackState() == Player.STATE_BUFFERING ? "BufferingΓÇª" : format(pos));
            }
            handler.postDelayed(this, 250);
        }
    };

    private void orient(int w, int h) {
        if (w <= 0 || h <= 0) return;
        setRequestedOrientation(w >= h
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26 || player == null) return;
        int w = Math.max(player.getVideoSize().width, 16);
        int h = Math.max(player.getVideoSize().height, 9);
        float ratio = w / (float) h;
        if (ratio < 0.5f) ratio = 0.5f;
        if (ratio > 2.3f) ratio = 2.3f;
        try {
            enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(Math.round(ratio * 100), 100))
                    .build());
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (player != null && player.isPlaying()) enterPip();
    }

    private boolean onGesture(View v, MotionEvent event) {
        if (player == null) return false;
        if (locked && event.getActionMasked() != MotionEvent.ACTION_UP) return true;
        if (locked) {
            lockBtn.setVisibility(View.VISIBLE);
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                startPos = player.getCurrentPosition();
                startVolume = currentVolume();
                startBright = currentBrightness();
                mode = "pending";
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                float w = Math.max(1f, v.getWidth());
                float h = Math.max(1f, v.getHeight());
                if ("pending".equals(mode)) {
                    if (Math.hypot(dx, dy) < 16) return true;
                    moved = true;
                    if (Math.abs(dx) > Math.abs(dy) * 1.1f) mode = "seek";
                    else mode = downX < w / 2f ? "bright" : "volume";
                    hint.setVisibility(View.GONE);
                }
                if ("seek".equals(mode)) {
                    long dur = player.getDuration();
                    long span = dur > 0 ? (long) (dur * 0.45) : 60_000;
                    long next = clamp(startPos + (long) (dx / w * span), 0, dur > 0 ? dur : Long.MAX_VALUE / 4);
                    player.seekTo(next);
                    showHud((dx >= 0 ? "+" : "-") + format(Math.abs(next - startPos)) + "   " + format(next));
                } else if ("volume".equals(mode)) {
                    int vol = (int) clamp(Math.round(startVolume - (dy / h) * 120f), 0, 100);
                    setVolume(vol);
                    showHud("Volume " + vol + "%");
                } else if ("bright".equals(mode)) {
                    float bright = (startBright * 100f) - (dy / h) * 120f;
                    int pct = (int) clamp(Math.round(bright), 8, 100);
                    setBrightness(pct / 100f);
                    showHud("Brightness " + pct + "%");
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!moved) {
                    if (player.isPlaying()) player.pause();
                    else player.play();
                }
                mode = "none";
                handler.postDelayed(() -> hud.setVisibility(View.GONE), 280);
                return true;
            default:
                return false;
        }
    }

    private void showHud(String text) {
        hud.setText(text);
        hud.setVisibility(View.VISIBLE);
    }

    private int currentVolume() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        return Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);
    }

    private void setVolume(int percent) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int value = Math.round(Math.max(0, Math.min(100, percent)) / 100f * max);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
    }

    private float currentBrightness() {
        float b = getWindow().getAttributes().screenBrightness;
        return b < 0 ? 0.8f : b;
    }

    private void setBrightness(float level) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.05f, Math.min(1f, level));
        getWindow().setAttributes(lp);
    }

    private static long clamp(long n, long a, long b) {
        return Math.max(a, Math.min(b, n));
    }

    private static String format(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }



    private void downloadCurrentVideo() {
        ArrayList<String> videoUrls2 = getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (videoUrls2 == null || videoUrls2.isEmpty()) return;
        String url = videoUrls2.get(0);
        if (url == null || url.isEmpty()) return;
        String ref = getIntent().getStringExtra(EXTRA_REFERER);
        String ua2 = getIntent().getStringExtra(EXTRA_UA);
        BlazeDownloadManager.get().enqueue(url, ref, ua2, "video");
        Toast.makeText(this, "Download queued", Toast.LENGTH_SHORT).show();
    }
    private void checkMobileDataAndWarn() {
        if (!BrowserPrefs.warnMobileData(this)) return;
        if (!isOnMobileData()) return;
        int mode = BrowserPrefs.videoPreloadMode(this);
        String info = mode == 0 ? "5s (minimum)" : mode == 1 ? "5 min (~100 MB/hr)" : "15 min (~300 MB/hr)";
        new AlertDialog.Builder(this)
            .setTitle("Playing on Mobile Data")
            .setMessage("Video will use mobile data.\nBuffer: " + info)
            .setPositiveButton("Continue", null)
            .setNeutralButton("Set to Off", (d,w) -> BrowserPrefs.setVideoPreloadMode(this, 0))
            .setNegativeButton("Don't warn again", (d,w) -> BrowserPrefs.setWarnMobileData(this, false))
            .setCancelable(false).show();
    }

    private boolean isOnMobileData() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            android.net.Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities cap = cm.getNetworkCapabilities(net);
            return cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    && !cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        return false;
    }
    @Override
    protected void onStop() {
        super.onStop();
        if (player != null && !bgAudio) player.pause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}