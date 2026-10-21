package app.blaze.browser;

import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
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
 * MX Player + UC Browser style media player.
 * Clean implementation - no ClassCastException.
 */
@UnstableApi
public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_URLS    = "urls";
    public static final String EXTRA_REFERER = "referer";
    public static final String EXTRA_UA      = "ua";
    public static final String EXTRA_COOKIE  = "cookie";
    public static final String EXTRA_TITLE   = "title";
    public static final String EXTRA_WIDTH   = "width";
    public static final String EXTRA_HEIGHT  = "height";

    private ExoPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Views
    private PlayerView playerView;
    private View       topBar, bottomBar;
    private TextView   playerTitle, playerHud;
    private TextView   playerPosition, playerDuration, playerSpeedLabel;
    private SeekBar    playerSeekbar;
    private ImageButton playerPlayPause;
    private ProgressBar playerLoading;
    private View        playerErrorView;

    // MX Player side volume/brightness indicators
    private LinearLayout volHint, brightHint;
    private ProgressBar  volBar, brightBar;
    private TextView     volPct, brightPct;

    // State
    private boolean scrubbing = false;
    private boolean locked    = false;
    private float   speed     = 1f;
    private int     orient    = 0;
    private LoudnessEnhancer loudness;

    // Gesture state
    private float  downX, downY;
    private long   downTime;
    private long   gestureStart;
    private String gestureMode = "pending";
    private boolean gestureMoved = false;
    private int    volStart;
    private float  brightStart;
    private long   lastTapTime = 0;
    private float  lastTapX    = 0;

    private static final long HIDE_DELAY = 4000;

    private final Runnable hideBarsRunnable  = () -> setBars(false);
    private final Runnable hideHudRunnable   = () -> { if (playerHud    != null) playerHud.setVisibility(View.GONE); };
    private final Runnable hideVolRunnable   = () -> { if (volHint      != null) volHint.setVisibility(View.GONE); };
    private final Runnable hideBrightRunnable= () -> { if (brightHint   != null) brightHint.setVisibility(View.GONE); };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (player != null) {
                long pos = player.getCurrentPosition();
                long dur = player.getDuration();
                long buf = player.getBufferedPosition();
                if (playerPosition != null) playerPosition.setText(clock(pos));
                if (playerDuration  != null) playerDuration.setText(dur > 0 ? clock(dur) : "--:--");
                if (playerSeekbar != null && !scrubbing && dur > 0) {
                    playerSeekbar.setProgress((int) Math.min(10000, pos * 10000L / dur));
                    playerSeekbar.setSecondaryProgress((int) Math.min(10000, buf * 10000L / dur));
                }
                if (playerLoading != null)
                    playerLoading.setVisibility(player.getPlaybackState() == Player.STATE_BUFFERING
                        ? View.VISIBLE : View.GONE);
            }
            handler.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (urls == null || urls.isEmpty()) { finish(); return; }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Bind views safely
        playerView       = (PlayerView)    findViewById(R.id.player_view);
        topBar           =                 findViewById(R.id.player_top_bar);
        bottomBar        =                 findViewById(R.id.player_bottom_bar);
        playerTitle      = (TextView)      findViewById(R.id.player_title);
        playerHud        = (TextView)      findViewById(R.id.player_hud);
        playerPosition   = (TextView)      findViewById(R.id.player_position);
        playerDuration   = (TextView)      findViewById(R.id.player_duration);
        playerSpeedLabel = (TextView)      findViewById(R.id.player_speed_label);
        playerSeekbar    = (SeekBar)       findViewById(R.id.player_seekbar);
        playerPlayPause  = (ImageButton)   findViewById(R.id.player_play_pause);
        playerLoading    = (ProgressBar)   findViewById(R.id.player_loading);
        playerErrorView  =                 findViewById(R.id.player_error_view);
        volHint          = (LinearLayout)  findViewById(R.id.vol_hint);
        brightHint       = (LinearLayout)  findViewById(R.id.bright_hint);
        if (volHint    != null) { volBar    = volHint.findViewById(R.id.vol_bar);      volPct    = volHint.findViewById(R.id.vol_pct);     }
        if (brightHint != null) { brightBar = brightHint.findViewById(R.id.bright_bar); brightPct = brightHint.findViewById(R.id.bright_pct); }

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (playerTitle != null && title != null && !title.isEmpty()) playerTitle.setText(title);

        // Build ExoPlayer
        Map<String, String> hdrs = new HashMap<>();
        String referer = getIntent().getStringExtra(EXTRA_REFERER);
        String ua = getIntent().getStringExtra(EXTRA_UA);
        String cookie = getIntent().getStringExtra(EXTRA_COOKIE);
        if (ua == null || ua.isEmpty()) ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36";
        hdrs.put("User-Agent", ua);
        if (referer != null && referer.startsWith("http")) hdrs.put("Referer", referer);
        if (cookie  != null && !cookie.isEmpty())          hdrs.put("Cookie", cookie);

        player = new ExoPlayer.Builder(this)
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(3000, 60000, 1000, 2000).build())
                .setMediaSourceFactory(new DefaultMediaSourceFactory(
                        new DefaultHttpDataSource.Factory()
                                .setUserAgent(ua).setDefaultRequestProperties(hdrs)
                                .setAllowCrossProtocolRedirects(true)
                                .setConnectTimeoutMs(12000).setReadTimeoutMs(20000)))
                .build();

        if (playerView != null) { playerView.setPlayer(player); playerView.setUseController(false); }

        ArrayList<MediaItem> items = new ArrayList<>();
        for (String url : urls)
            if (url != null && (url.startsWith("http") || url.startsWith("file")))
                items.add(MediaItem.fromUri(Uri.parse(url)));

        if (items.isEmpty()) {
            Toast.makeText(this, "No playable video URL", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        player.setMediaItems(items, 0, 0);
        player.setPlayWhenReady(true);
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) { updatePlayIcon(isPlaying); }

            @Override public void onPlayerError(PlaybackException e) {
                if (playerLoading != null) playerLoading.setVisibility(View.GONE);
                if (playerErrorView != null) {
                    TextView msg = playerErrorView.findViewById(R.id.player_error_msg);
                    if (msg != null) msg.setText("Cannot play this video");
                    playerErrorView.setVisibility(View.VISIBLE);
                }
            }

            @Override public void onAudioSessionIdChanged(int id) { applySound(id); }
        });

        // Error retry
        if (playerErrorView != null) {
            View retry = playerErrorView.findViewById(R.id.player_error_retry);
            if (retry != null) retry.setOnClickListener(v -> {
                playerErrorView.setVisibility(View.GONE);
                player.prepare(); player.play();
            });
        }

        // Wire buttons - all ImageButton, safe cast
        safeClick(R.id.player_close,    v -> finish());
        safeClick(R.id.player_download, v -> downloadCurrent());
        safeClick(R.id.player_pip,      v -> enterPip());
        safeClick(R.id.player_rotate,   v -> cycleRotation());
        safeClick(R.id.player_lock,     v -> toggleLock());
        safeClick(R.id.player_rewind,   v -> { if (player != null) { player.seekTo(Math.max(0, player.getCurrentPosition() - 10000)); showHud("-10s"); } });
        safeClick(R.id.player_forward,  v -> { if (player != null) { player.seekTo(player.getCurrentPosition() + 10000); showHud("+10s"); } });
        if (playerPlayPause != null) playerPlayPause.setOnClickListener(v -> togglePlayPause());

        // SeekBar
        if (playerSeekbar != null) playerSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar b) { scrubbing = true; handler.removeCallbacks(hideBarsRunnable); }
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) {
                if (!user || player == null) return;
                long dur = player.getDuration();
                if (dur > 0 && playerPosition != null) playerPosition.setText(clock(p * dur / 10000L));
            }
            @Override public void onStopTrackingTouch(SeekBar b) {
                scrubbing = false;
                if (player != null) { long d = player.getDuration(); if (d > 0) player.seekTo(b.getProgress() * d / 10000L); }
                resetHideTimer();
            }
        });

        // Gesture layer
        View gl = findViewById(R.id.gesture_layer);
        if (gl != null) gl.setOnTouchListener((v, e) -> handleGesture(e));

        // Orientation
        int w = getIntent().getIntExtra(EXTRA_WIDTH, 0);
        int h = getIntent().getIntExtra(EXTRA_HEIGHT, 0);
        setRequestedOrientation(h > w && w > 0
            ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        applySound(0);
        scheduleSleep();
        handler.post(tick);
        showBars();
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) player.pause(); else player.play();
        showBars();
    }

    private void updatePlayIcon(boolean playing) {
        if (playerPlayPause != null)
            playerPlayPause.setImageResource(playing
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play);
    }

    private void showBars() { setBars(true); }

    private void setBars(boolean show) {
        if (topBar    != null) topBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!locked && bottomBar != null) bottomBar.setVisibility(show ? View.VISIBLE : View.GONE);
        handler.removeCallbacks(hideBarsRunnable);
        if (show && (player == null || player.isPlaying()))
            handler.postDelayed(hideBarsRunnable, HIDE_DELAY);
    }

    private void resetHideTimer() {
        handler.removeCallbacks(hideBarsRunnable);
        handler.postDelayed(hideBarsRunnable, HIDE_DELAY);
    }

    private void showHud(String text) {
        if (playerHud == null) return;
        playerHud.setText(text); playerHud.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideHudRunnable);
        handler.postDelayed(hideHudRunnable, 900);
    }

    // MX Player style: show side volume indicator
    private void showVolHint(int pct) {
        if (volHint == null) return;
        if (volBar != null) volBar.setProgress(pct);
        if (volPct != null) volPct.setText(pct + "%");
        volHint.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideVolRunnable);
        handler.postDelayed(hideVolRunnable, 1200);
    }

    // MX Player style: show side brightness indicator
    private void showBrightHint(int pct) {
        if (brightHint == null) return;
        if (brightBar != null) brightBar.setProgress(pct);
        if (brightPct != null) brightPct.setText(pct + "%");
        brightHint.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideBrightRunnable);
        handler.postDelayed(hideBrightRunnable, 1200);
    }

    private void toggleLock() {
        locked = !locked;
        ImageButton lockBtn = (ImageButton) findViewById(R.id.player_lock);
        if (lockBtn != null) lockBtn.setAlpha(locked ? 1f : 0.5f);
        if (locked) {
            if (bottomBar != null) bottomBar.setVisibility(View.GONE);
            showHud("\uD83D\uDD12 Locked");
        } else {
            showBars();
            showHud("\uD83D\uDD13 Unlocked");
        }
    }

    private void cycleRotation() {
        orient = (orient + 1) % 3;
        ImageButton btn = (ImageButton) findViewById(R.id.player_rotate);
        if (orient == 0) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            if (btn != null) btn.setImageResource(R.drawable.ic_rotate_landscape);
            showHud("Landscape");
        } else if (orient == 1) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            if (btn != null) btn.setImageResource(R.drawable.ic_rotate_portrait);
            showHud("Portrait");
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            if (btn != null) btn.setImageResource(R.drawable.ic_rotate_auto);
            showHud("Auto rotate");
        }
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26 || player == null) return;
        int w = Math.max(player.getVideoSize().width, 16);
        int h = Math.max(player.getVideoSize().height, 9);
        float r = Math.max(0.5f, Math.min(2.3f, (float) w / h));
        try { enterPictureInPictureMode(new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(Math.round(r * 100), 100)).build());
        } catch (Exception ignored) { Toast.makeText(this, "PiP not available", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (player != null && player.isPlaying()) enterPip();
    }

    private void downloadCurrent() {
        if (player == null || player.getCurrentMediaItem() == null
                || player.getCurrentMediaItem().localConfiguration == null) return;
        String url = player.getCurrentMediaItem().localConfiguration.uri.toString();
        String ttl = playerTitle != null ? playerTitle.getText().toString() : "video";
        BlazeDownloadManager.get().enqueue(url, getIntent().getStringExtra(EXTRA_REFERER),
                getIntent().getStringExtra(EXTRA_UA), ttl);
        Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
    }

    private void applySound(int sid) {
        if (!BrowserPrefs.soundEnhance(this)) return;
        if (sid == 0 && player != null) sid = player.getAudioSessionId();
        if (sid == 0) return;
        try {
            if (loudness != null) loudness.release();
            loudness = new LoudnessEnhancer(sid);
            loudness.setTargetGain(800); loudness.setEnabled(true);
        } catch (Exception ignored) { loudness = null; }
    }

    private void scheduleSleep() {
        int min = BrowserPrefs.autoSleepMinutes(this);
        if (min <= 0) return;
        handler.postDelayed(() -> { if (player != null) player.pause(); Toast.makeText(this, "Auto sleep", Toast.LENGTH_SHORT).show(); finish(); }, min * 60_000L);
    }

    private boolean handleGesture(MotionEvent ev) {
        if (player == null) return false;
        View gl = findViewById(R.id.gesture_layer);
        float W = gl != null && gl.getWidth() > 0 ? gl.getWidth() : 1080f;
        float H = gl != null && gl.getHeight() > 0 ? gl.getHeight() : 1920f;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX(); downY = ev.getY();
                downTime = ev.getEventTime();
                gestureStart = player.getCurrentPosition();
                gestureMode = "pending"; gestureMoved = false;
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                int mx = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                volStart = Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / mx);
                brightStart = getWindow().getAttributes().screenBrightness;
                if (brightStart < 0) brightStart = 0.5f;
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = ev.getX() - downX, dy = ev.getY() - downY;
                if ("pending".equals(gestureMode) && !gestureMoved) {
                    if (Math.hypot(dx, dy) < 20) {
                        if (ev.getEventTime() - downTime > 400) { player.setPlaybackParameters(new PlaybackParameters(2f)); showHud("2x"); }
                        return true;
                    }
                    gestureMoved = true;
                    gestureMode = Math.abs(dx) > Math.abs(dy) * 1.5f ? "seek" : (downX < W/2f ? "bright" : "vol");
                    showBars();
                }
                if ("seek".equals(gestureMode)) {
                    long dur = player.getDuration();
                    if (dur > 0) {
                        long next = Math.max(0, Math.min(dur, gestureStart + (long)(dx / W * dur * 0.35f)));
                        player.seekTo(next);
                        showHud(((next-gestureStart)>=0?"+":"") + ((next-gestureStart)/1000) + "s");
                    }
                } else if ("vol".equals(gestureMode)) {
                    int vol = (int) Math.max(0, Math.min(100, Math.round(volStart - (dy/H)*100)));
                    AudioManager a = (AudioManager) getSystemService(AUDIO_SERVICE); int m = Math.max(1,a.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                    a.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(vol*m/100f), 0);
                    showVolHint(vol);
                } else if ("bright".equals(gestureMode)) {
                    int pct = (int) Math.max(5, Math.min(100, Math.round(brightStart*100-(dy/H)*100)));
                    WindowManager.LayoutParams lp = getWindow().getAttributes(); lp.screenBrightness = pct/100f; getWindow().setAttributes(lp);
                    showBrightHint(pct);
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (player.getPlaybackParameters().speed == 2f && speed != 2f) player.setPlaybackParameters(new PlaybackParameters(speed));
                if (!gestureMoved && ev.getEventTime() - downTime < 300) {
                    long now = ev.getEventTime();
                    if (now - lastTapTime < 300 && Math.abs(ev.getX() - lastTapX) < 100) {
                        int delta = ev.getX() < W/2f ? -10000 : 10000;
                        player.seekTo(Math.max(0, player.getCurrentPosition() + delta));
                        showHud(delta < 0 ? "-10s" : "+10s");
                        lastTapTime = 0;
                    } else {
                        lastTapTime = now; lastTapX = ev.getX();
                        boolean open = topBar != null && topBar.getVisibility() == View.VISIBLE;
                        setBars(!open);
                    }
                }
                gestureMode = "none"; gestureMoved = false;
                return true;

            default: return false;
        }
    }

    private void safeClick(int id, View.OnClickListener l) {
        View v = findViewById(id); if (v != null) v.setOnClickListener(l);
    }

    private static String clock(long ms) {
        int s = (int) Math.max(0, ms/1000), m = s/60; s %= 60; int h = m/60; m %= 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                     : String.format(Locale.US, "%d:%02d", m, s);
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null && !isInPictureInPictureMode()) player.pause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (loudness != null) { try { loudness.release(); } catch (Exception ignored) {} }
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
