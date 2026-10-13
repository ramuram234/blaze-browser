package app.blaze.browser;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
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
import android.widget.SeekBar;
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

/**
 * Full-screen video player using Media3 ExoPlayer.
 *
 * Gesture model (UC Browser style):
 *   - Single tap     → toggle controls visibility
 *   - Swipe H (center) → seek
 *   - Swipe V left   → brightness
 *   - Swipe V right  → volume
 *
 * Controls auto-hide after 3.5s when playing.
 * Network speed shown live via NetworkSpeedMonitor.
 * Download button always visible.
 */
public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_URLS    = "urls";
    public static final String EXTRA_REFERER = "referer";
    public static final String EXTRA_UA      = "ua";
    public static final String EXTRA_WIDTH   = "width";
    public static final String EXTRA_HEIGHT  = "height";
    public static final String EXTRA_TITLE   = "title";

    // ── Player ───────────────────────────────────────────────────────────────
    private ExoPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ── Views ────────────────────────────────────────────────────────────────
    private View     controlsTop;
    private View     controlsBottom;
    private TextView hud;
    private TextView hint;
    private SeekBar  seekBar;
    private TextView timeLabel;
    private TextView timeTotalLabel;
    private TextView speedBtn;
    private TextView lockBtn;
    private TextView playPauseBtn;
    private TextView dataMeter;
    private View     downloadBtn;
    private View     settingsPanel;
    private TextView speedOverlay;   // live network KB/s or MB/s

    // ── State ────────────────────────────────────────────────────────────────
    private boolean controlsVisible = true;
    private boolean locked    = false;
    private boolean seeking   = false;
    private boolean bgAudio   = false;
    private int     speedIndex = 2;  // index into SPEEDS[] (default 1.0x)

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

    // ── Gesture ──────────────────────────────────────────────────────────────
    private float   gestureDownX, gestureDownY;
    private long    gesturePosStart;
    private int     gestureVolStart;
    private float   gestureBrightStart;
    private boolean gestureMoved;
    private String  gestureMode = "none";

    /** Minimum movement to classify as a swipe (not a tap). 30px threshold. */
    private static final float TAP_THRESHOLD_PX = 30f;

    // ── Auto-hide ─────────────────────────────────────────────────────────────
    private static final long CONTROLS_TIMEOUT_MS = 3500;
    private final Runnable autoHideRunnable = () -> {
        if (player != null && player.isPlaying()) hideControls();
    };

    // ── Network speed monitor ─────────────────────────────────────────────────
    private NetworkSpeedMonitor speedMonitor;

    // UC Browser VolumeBrightnessHintView pattern
    private android.widget.LinearLayout volumeBrightnessHint;
    private android.widget.ImageView hintIcon;
    private android.widget.ProgressBar hintProgress;
    private final Runnable hideHintRunnable = () -> {
        if (volumeBrightnessHint != null) volumeBrightnessHint.setVisibility(android.view.View.GONE);
    };

    // UC Browser RotationBtn pattern - cycles PORTRAIT -> LANDSCAPE -> SENSOR
    private static final int[] ORIENTATIONS = {
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
    };
    private static final int[] ROTATE_ICONS = {
        R.drawable.ic_rotate_portrait,
        R.drawable.ic_rotate_landscape,
        R.drawable.ic_rotate_auto
    };
    private int rotateIndex = 2; // Start at SENSOR (auto)

    // Download progress ring (R11)
    private DownloadProgressRing downloadRing;
    private String currentVideoUrl = null;
    private final Runnable ringPoller = new Runnable() {
        @Override public void run() {
            if (downloadRing == null || currentVideoUrl == null) return;
            DownloadTask task = findTaskByUrl(currentVideoUrl);
            if (task == null) { downloadRing.setVisibility(android.view.View.GONE); return; }
            int pct = task.progressPercent();
            downloadRing.setProgress(pct);
            if (task.status == DownloadTask.Status.COMPLETED) { downloadRing.setComplete(true); return; }
            if (task.status == DownloadTask.Status.FAILED || task.status == DownloadTask.Status.CANCELLED) {
                downloadRing.setVisibility(android.view.View.GONE); return; }
            handler.postDelayed(this, 1000);
        }
    };
    private DownloadTask findTaskByUrl(String url) {
        if (url == null) return null;
        for (DownloadTask t : BlazeDownloadManager.get().getTasks())
            if (url.equals(t.url)) return t;
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        currentVideoUrl = (urls != null && !urls.isEmpty()) ? urls.get(0) : null;
        if (urls == null || urls.isEmpty()) { finish(); return; }

        String referer = getIntent().getStringExtra(EXTRA_REFERER);
        String title   = getIntent().getStringExtra(EXTRA_TITLE);
        String ua      = getIntent().getStringExtra(EXTRA_UA);
        if (ua == null || ua.isEmpty())
            ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36";

        // ── Bind views ────────────────────────────────────────────────────────
        controlsTop    = findViewById(R.id.player_top_bar);
        controlsBottom = findViewById(R.id.player_bottom);
        hud            = findViewById(R.id.hud);
        hint           = findViewById(R.id.hint);
        seekBar        = (SeekBar)  findViewById(R.id.seek_bar);
        timeLabel      = (TextView) findViewById(R.id.time_current);
        timeTotalLabel = (TextView) findViewById(R.id.time_total);
        speedBtn       = (TextView) findViewById(R.id.btn_speed);
        lockBtn        = (TextView) findViewById(R.id.btn_lock);
        playPauseBtn   = (TextView) findViewById(R.id.btn_play_pause);
        dataMeter      = (TextView) findViewById(R.id.data_meter);
        downloadBtn    = findViewById(R.id.btn_download_overlay);
        settingsPanel  = findViewById(R.id.settings_panel);
        speedOverlay   = (TextView) findViewById(R.id.speed_overlay);

        downloadRing = (DownloadProgressRing) findViewById(R.id.download_progress_ring);

        // UC Browser VolumeBrightnessHintView
        volumeBrightnessHint = (android.widget.LinearLayout) findViewById(R.id.volume_brightness_hint);
        hintIcon             = (android.widget.ImageView) (volumeBrightnessHint != null ? volumeBrightnessHint.findViewById(R.id.hint_icon) : null);
        hintProgress         = (android.widget.ProgressBar) (volumeBrightnessHint != null ? volumeBrightnessHint.findViewById(R.id.hint_progress) : null);

        // UC Browser RotationBtn - cycle rotation
        android.widget.ImageButton btnRotate = (android.widget.ImageButton) findViewById(R.id.btn_rotate);
        if (btnRotate != null) {
            btnRotate.setImageResource(ROTATE_ICONS[rotateIndex]);
            btnRotate.setOnClickListener(v -> {
                rotateIndex = (rotateIndex + 1) % 3;
                setRequestedOrientation(ORIENTATIONS[rotateIndex]);
                btnRotate.setImageResource(ROTATE_ICONS[rotateIndex]);
            });
        }

        // UC Browser WatchLaterButton
        android.widget.ImageButton btnWatchLater = (android.widget.ImageButton) findViewById(R.id.btn_watch_later);
        if (btnWatchLater != null) btnWatchLater.setOnClickListener(v -> {
            String pageUrl = getIntent().getStringExtra("page_url");
            if (pageUrl == null) {
                java.util.ArrayList<String> vurls = getIntent().getStringArrayListExtra(EXTRA_URLS);
                pageUrl = (vurls != null && !vurls.isEmpty()) ? vurls.get(0) : null;
            }
            if (pageUrl == null) return;
            String ttl = getIntent().getStringExtra(EXTRA_TITLE);
            if (ttl == null) ttl = "Saved video";
            BrowserPrefs.toggleBookmark(this, ttl, pageUrl);
            boolean isBookmarked = BrowserPrefs.isBookmarked(this, pageUrl);
            Toast.makeText(this, isBookmarked ? "Saved to bookmarks" : "Removed from bookmarks",
                    Toast.LENGTH_SHORT).show();
        });

        PlayerView playerView = (PlayerView) findViewById(R.id.player_view);
        View gestureLayer     = findViewById(R.id.gesture_layer);
        ImageButton closeBtn  = (ImageButton) findViewById(R.id.close_player);
        View pipBtn    = findViewById(R.id.pip_player);
        View menuBtn   = findViewById(R.id.player_menu_btn);

        TextView titleTv = (TextView) findViewById(R.id.player_title);
        if (titleTv != null && title != null && !title.isEmpty()) titleTv.setText(title);

        // ── Build ExoPlayer ────────────────────────────────────────────────────
        Map<String,String> hdrs = new HashMap<>();
        if (referer != null && referer.startsWith("http")) hdrs.put("Referer", referer);
        String cookie = CookieManager.getInstance().getCookie(urls.get(0));
        if (cookie != null) hdrs.put("Cookie", cookie);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(hdrs)
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(20_000);

        int preloadMode  = BrowserPrefs.videoPreloadMode(this);
        int customBufSec = BrowserPrefs.videoPreloadCustomSec(this);
        int maxBufMs;
        switch (preloadMode) {
            case 0:  maxBufMs =     5_000; break;
            case 1:  maxBufMs =   300_000; break;
            case 2:  maxBufMs =   900_000; break;
            case 3:  maxBufMs = customBufSec * 1_000; break;
            default: maxBufMs =   300_000;
        }

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(preloadMode == 0 ? 1_000 : 5_000, maxBufMs, 800, 1_500)
                .build();

        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(
                        new DefaultDataSource.Factory(this, http)))
                .build();
        playerView.setPlayer(player);
        playerView.setUseController(false);

        ArrayList<MediaItem> items = new ArrayList<>();
        for (String url : urls)
            if (url != null && !url.isEmpty())
                items.add(MediaItem.fromUri(Uri.parse(url)));
        if (items.isEmpty()) { finish(); return; }

        player.setMediaItems(items, 0, 0);
        player.setPlayWhenReady(true);
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException e) {
                Toast.makeText(VideoPlayerActivity.this,
                        "Cannot play this video", Toast.LENGTH_SHORT).show();
                if (player != null && player.getMediaItemCount() <= 1) finish();
            }
            @Override public void onVideoSizeChanged(VideoSize sz) {
                orientToVideo(sz.width, sz.height);
            }
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseBtn(isPlaying);
                if (isPlaying) resetAutoHide();
                else           handler.removeCallbacks(autoHideRunnable);
            }
        });

        orientToVideo(
                getIntent().getIntExtra(EXTRA_WIDTH, 0),
                getIntent().getIntExtra(EXTRA_HEIGHT, 0));

        // ── Wire button clicks ──────────────────────────────────────────────────
        if (closeBtn != null) closeBtn.setOnClickListener(v -> finish());
        if (pipBtn   != null) pipBtn.setOnClickListener(v -> enterPip());
        if (menuBtn  != null && settingsPanel != null)
            menuBtn.setOnClickListener(v -> {
                boolean wasVisible = settingsPanel.getVisibility() == View.VISIBLE;
                settingsPanel.setVisibility(wasVisible ? View.GONE : View.VISIBLE);
                resetAutoHide();
            });

        if (playPauseBtn != null) playPauseBtn.setOnClickListener(v -> togglePlayPause());

        bgAudio = BrowserPrefs.backgroundAudio(this);
        TextView bgBtn = (TextView) findViewById(R.id.btn_bg_audio);
        if (bgBtn != null) {
            bgBtn.setText(bgAudio ? "\uD83D\uDD0A" : "\uD83D\uDD07");
            bgBtn.setAlpha(bgAudio ? 1f : 0.5f);
            bgBtn.setOnClickListener(v -> {
                bgAudio = !bgAudio;
                BrowserPrefs.setBackgroundAudio(this, bgAudio);
                bgBtn.setText(bgAudio ? "\uD83D\uDD0A" : "\uD83D\uDD07");
                bgBtn.setAlpha(bgAudio ? 1f : 0.5f);
                showHud(bgAudio ? "Background audio ON" : "Background audio OFF");
                resetAutoHide();
            });
        }

        if (speedBtn != null) speedBtn.setOnClickListener(v -> {
            if (player == null || locked) return;
            speedIndex = (speedIndex + 1) % SPEEDS.length;
            player.setPlaybackSpeed(SPEEDS[speedIndex]);
            String label = String.format(Locale.US, "%.1fx", SPEEDS[speedIndex]);
            speedBtn.setText(label);
            showHud("Speed " + label);
            resetAutoHide();
        });

        if (lockBtn != null) lockBtn.setOnClickListener(v -> {
            locked = !locked;
            lockBtn.setText(locked ? "\uD83D\uDD12" : "\uD83D\uDD13");
            showHud(locked ? "Locked — tap to unlock" : "Unlocked");
            resetAutoHide();
        });

        if (downloadBtn != null) downloadBtn.setOnClickListener(v -> downloadCurrentVideo());

        if (seekBar != null) seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser || player == null) return;
                long dur = player.getDuration();
                if (dur > 0 && timeLabel != null)
                    timeLabel.setText(fmt(dur * progress / 1000));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {
                seeking = true; // UC Browser PlayerSeekBar: lock during touch
                handler.removeCallbacks(autoHideRunnable);
            }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                if (player != null) {
                    long dur = player.getDuration();
                    if (dur > 0) player.seekTo(dur * sb.getProgress() / 1000);
                }
                seeking = false;
                resetAutoHide();
            }
        });

        if (gestureLayer != null) gestureLayer.setOnTouchListener((v, ev) -> onGesture(ev));

        // ── Start live network speed monitor ────────────────────────────────────
        speedMonitor = new NetworkSpeedMonitor();
        speedMonitor.start((formatted, bps) -> {
            if (speedOverlay != null) {
                speedOverlay.setText("\u25BC " + formatted);
                speedOverlay.setVisibility(bps > 0 ? View.VISIBLE : View.INVISIBLE);
            }
        });

        // Initial state: show controls, auto-hide hint after 3s
        showControls();
        handler.postDelayed(() -> { if (hint != null) hint.setVisibility(View.GONE); }, 3000);
        handler.post(tick);

        checkMobileDataAndWarn();
    }

    // ── Progress tick ────────────────────────────────────────────────────────────
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            long dur = player.getDuration();
            long pos = player.getCurrentPosition();
            if (dur > 0) {
                if (!seeking && seekBar != null) seekBar.setProgress((int)(pos * 1000L / dur));
                if (timeLabel      != null) timeLabel.setText(fmt(pos));
                if (timeTotalLabel != null) timeTotalLabel.setText(fmt(dur));
            } else {
                if (timeLabel != null)
                    timeLabel.setText(player.getPlaybackState() == Player.STATE_BUFFERING ? "..." : fmt(pos));
            }
            handler.postDelayed(this, 250);
        }
    };

    // ── Controls show / hide ──────────────────────────────────────────────────────
    private void showControls() {
        if (controlsTop    != null) controlsTop.setVisibility(View.VISIBLE);
        if (controlsBottom != null) controlsBottom.setVisibility(View.VISIBLE);
        if (downloadBtn    != null) downloadBtn.setVisibility(View.VISIBLE);
        if (dataMeter      != null) dataMeter.setVisibility(View.VISIBLE);
        controlsVisible = true;
        resetAutoHide();
    }

    private void hideControls() {
        if (controlsTop    != null) controlsTop.setVisibility(View.GONE);
        if (controlsBottom != null) controlsBottom.setVisibility(View.GONE);
        if (dataMeter      != null) dataMeter.setVisibility(View.GONE);
        // download button stays visible so user can always download
        controlsVisible = false;
        handler.removeCallbacks(autoHideRunnable);
    }

    private void resetAutoHide() {
        handler.removeCallbacks(autoHideRunnable);
        handler.postDelayed(autoHideRunnable, CONTROLS_TIMEOUT_MS);
    }

    // UC Browser VolumeBrightnessHintView: show volume/brightness with icon + progress bar
    private void showHint(boolean isVolume, int value0to100) {
        if (volumeBrightnessHint == null) return;
        if (hintIcon != null) {
            hintIcon.setImageResource(isVolume
                ? android.R.drawable.ic_lock_silent_mode_off
                : android.R.drawable.ic_menu_compass);
        }
        if (hintProgress != null) hintProgress.setProgress(value0to100);
        volumeBrightnessHint.setVisibility(android.view.View.VISIBLE);
        handler.removeCallbacks(hideHintRunnable);
        handler.postDelayed(hideHintRunnable, 1000);
    }

    private void showHud(String text) {
        if (hud == null) return;
        hud.setText(text);
        hud.setVisibility(View.VISIBLE);
        handler.removeCallbacksAndMessages(hud);
        handler.postDelayed(() -> { if (hud != null) hud.setVisibility(View.GONE); }, 900);
    }

    private void updatePlayPauseBtn(boolean isPlaying) {
        if (playPauseBtn != null)
            playPauseBtn.setText(isPlaying ? "\u23F8" : "\u25B6");
    }

    private void togglePlayPause() {
        if (player == null) return;
        boolean wasPlaying = player.isPlaying();
        if (wasPlaying) player.pause(); else player.play();
        updatePlayPauseBtn(!wasPlaying);
        showHud(wasPlaying ? "\u23F8 Paused" : "\u25B6 Playing");
        resetAutoHide();
    }

    // ── Gesture handler ───────────────────────────────────────────────────────────
    // Single tap = toggle controls. Swipe H=seek, V-left=brightness, V-right=volume.
    private boolean onGesture(MotionEvent event) {
        if (player == null) return false;

        // Locked mode: only show unlock hint on tap
        if (locked) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP && !gestureMoved) {
                showHud("Locked — tap \uD83D\uDD13 to unlock");
            }
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureDownX       = event.getX();
                gestureDownY       = event.getY();
                gesturePosStart    = player.getCurrentPosition();
                gestureVolStart    = currentVolume();
                gestureBrightStart = currentBrightness();
                gestureMode        = "pending";
                gestureMoved       = false;
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - gestureDownX;
                float dy = event.getY() - gestureDownY;
                float viewW = 1080f, viewH = 1920f;
                View gl = findViewById(R.id.gesture_layer);
                if (gl != null && gl.getWidth() > 0) {
                    viewW = gl.getWidth();
                    viewH = gl.getHeight();
                }

                if ("pending".equals(gestureMode)) {
                    if (Math.hypot(dx, dy) < TAP_THRESHOLD_PX) return true;
                    gestureMoved = true;
                    if (Math.abs(dx) > Math.abs(dy) * 1.2f) gestureMode = "seek";
                    else gestureMode = gestureDownX < viewW / 2f ? "bright" : "volume";
                    if (hint != null) hint.setVisibility(View.GONE);
                    if (!controlsVisible) showControls();
                }

                float fdx = event.getX() - gestureDownX;
                float fdy = event.getY() - gestureDownY;

                if ("seek".equals(gestureMode)) {
                    long dur  = player.getDuration();
                    long span = dur > 0 ? (long)(dur * 0.4f) : 60_000L;
                    long next = Math.max(0, Math.min(dur > 0 ? dur : Long.MAX_VALUE / 2,
                            gesturePosStart + (long)(fdx / viewW * span)));
                    player.seekTo(next);
                    showHud((fdx >= 0 ? "+" : "") + fmt(Math.abs(next - gesturePosStart))
                            + "  → " + fmt(next));

                } else if ("volume".equals(gestureMode)) {
                    int vol = (int) Math.max(0, Math.min(100,
                            Math.round(gestureVolStart - (fdy / viewH) * 100f)));
                    setVolume(vol);
                    showHint(true, vol);
                    showHud("Volume " + vol + "%");

                } else if ("bright".equals(gestureMode)) {
                    int pct = (int) Math.max(8, Math.min(100,
                            Math.round(gestureBrightStart * 100 - (fdy / viewH) * 100f)));
                    setBrightness(pct / 100f);
                    showHint(false, pct);
                    showHud("Brightness " + pct + "%");
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!gestureMoved) {
                    // Single tap: toggle controls visibility
                    if (!controlsVisible) {
                        showControls();
                    } else {
                        hideControls();
                    }
                }
                gestureMode  = "none";
                gestureMoved = false;
                return true;

            default:
                return false;
        }
    }

    // ── Orientation ───────────────────────────────────────────────────────────────
    private void orientToVideo(int w, int h) {
        if (w <= 0 || h <= 0) return;
        setRequestedOrientation(w >= h
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }

    // ── PiP ───────────────────────────────────────────────────────────────────────
    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26 || player == null) return;
        int w = Math.max(player.getVideoSize().width, 16);
        int h = Math.max(player.getVideoSize().height, 9);
        float ratio = Math.max(0.5f, Math.min(2.3f, (float) w / h));
        try {
            enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(Math.round(ratio * 100), 100))
                    .build());
        } catch (Exception ignored) {}
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (player != null && player.isPlaying()) enterPip();
    }

    // ── Download ──────────────────────────────────────────────────────────────────
    private void downloadCurrentVideo() {
        ArrayList<String> videoUrls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (videoUrls == null || videoUrls.isEmpty()) return;
        String url = videoUrls.get(0);
        if (url == null || url.isEmpty()) return;
        String ref = getIntent().getStringExtra(EXTRA_REFERER);
        String ua2 = getIntent().getStringExtra(EXTRA_UA);
        String ttl = getIntent().getStringExtra(EXTRA_TITLE);
        BlazeDownloadManager.get().enqueue(url, ref, ua2, ttl != null ? ttl : "video");
        Toast.makeText(this, "Download queued — see Downloads in menu", Toast.LENGTH_SHORT).show();
    }

    // ── Mobile data warning ────────────────────────────────────────────────────────
    private void checkMobileDataAndWarn() {
        if (!BrowserPrefs.warnMobileData(this) || !isOnMobileData()) return;
        int mode = BrowserPrefs.videoPreloadMode(this);
        String bufInfo = mode == 0 ? "5s (min)" : mode == 1 ? "5 min" : "15 min";
        new AlertDialog.Builder(this)
                .setTitle("Playing on Mobile Data")
                .setMessage("Video will use mobile data. Buffer: " + bufInfo)
                .setPositiveButton("Continue", null)
                .setNeutralButton("Set to Off", (d, w) -> BrowserPrefs.setVideoPreloadMode(this, 0))
                .setNegativeButton("Don't warn", (d, w) -> BrowserPrefs.setWarnMobileData(this, false))
                .setCancelable(false).show();
    }

    private boolean isOnMobileData() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= 23) {
            android.net.Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities cap = cm.getNetworkCapabilities(net);
            return cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    && !cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        return false;
    }

    // ── Volume / Brightness helpers ────────────────────────────────────────────────
    private int currentVolume() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        return Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);
    }

    private void setVolume(int pct) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        am.setStreamVolume(AudioManager.STREAM_MUSIC,
                Math.round(Math.max(0, Math.min(100, pct)) * max / 100f), 0);
    }

    private float currentBrightness() {
        float b = getWindow().getAttributes().screenBrightness;
        return b < 0 ? 0.5f : b;
    }

    private void setBrightness(float level) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.05f, Math.min(1f, level));
        getWindow().setAttributes(lp);
    }

    // ── Format helpers ──────────────────────────────────────────────────────────────
    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.US, "%d:%02d", m, sec);
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────────────
    @Override protected void onResume() {
        super.onResume();
        // Resume ring if download was in progress
        if (downloadRing != null && currentVideoUrl != null) {
            DownloadTask t = findTaskByUrl(currentVideoUrl);
            if (t != null && (t.status == DownloadTask.Status.DOWNLOADING || t.status == DownloadTask.Status.QUEUED)) {
                downloadRing.setProgress(t.progressPercent());
                downloadRing.setVisibility(android.view.View.VISIBLE);
                handler.postDelayed(ringPoller, 1000);
            }
        }
    }

    @Override protected void onStop() {
        super.onStop();
        handler.removeCallbacks(autoHideRunnable);
        if (player != null && !bgAudio) player.pause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        handler.removeCallbacks(ringPoller);
        if (speedMonitor != null) speedMonitor.stop();
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
