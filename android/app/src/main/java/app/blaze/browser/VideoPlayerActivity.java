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
import java.util.Map;

/**
 * Media3 1.11 player. Official PlayerView controls (seek, buffer, play)
 * plus UC-style gestures: double-tap seek, hold for 2x, brightness and volume.
 */
@UnstableApi
public class VideoPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_URLS = "urls";
    public static final String EXTRA_REFERER = "referer";
    public static final String EXTRA_UA = "ua";
    public static final String EXTRA_COOKIE = "cookie";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_HEIGHT = "height";

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView hud;
    private TextView titleView;
    private TextView speedButton;
    private View topBar;
    private LoudnessEnhancer loudness;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private float speed = 1f;
    private int orient = 0;
    private boolean locked = false;
    private float downX;
    private float downY;
    private long downTime;
    private String mode = "pending";
    private boolean moved;
    private int volStart;
    private float brightStart;
    private long lastTap;
    private float lastTapX;
    private long gestureStart;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (urls == null || urls.isEmpty()) {
            finish();
            return;
        }
        playerView = findViewById(R.id.player_view);
        hud = findViewById(R.id.hud);
        titleView = findViewById(R.id.title);
        speedButton = findViewById(R.id.btn_speed);
        topBar = findViewById(R.id.top_bar);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (titleView != null && title != null && !title.isEmpty()) titleView.setText(title);

        Map<String, String> headers = new HashMap<>();
        String referer = getIntent().getStringExtra(EXTRA_REFERER);
        String ua = getIntent().getStringExtra(EXTRA_UA);
        String cookie = getIntent().getStringExtra(EXTRA_COOKIE);
        if (ua == null || ua.isEmpty()) {
            ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";
        }
        headers.put("User-Agent", ua);
        if (referer != null && referer.startsWith("http")) headers.put("Referer", referer);
        if (cookie != null && !cookie.isEmpty()) headers.put("Cookie", cookie);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12_000)
                .setReadTimeoutMs(20_000);
        DefaultLoadControl load = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_500, 50_000, 800, 1_500)
                .build();
        player = new ExoPlayer.Builder(this)
                .setLoadControl(load)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .build();
        playerView.setPlayer(player);
        playerView.setControllerShowTimeoutMs(4_000);
        playerView.setControllerAutoShow(true);

        ArrayList<MediaItem> items = new ArrayList<>();
        for (String url : urls) {
            if (url != null && url.startsWith("http")) items.add(MediaItem.fromUri(Uri.parse(url)));
        }
        if (items.isEmpty()) {
            Toast.makeText(this, "This video cannot be opened outside the page", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        player.setMediaItems(items, 0, 0);
        player.setPlayWhenReady(true);
        player.prepare();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(VideoPlayerActivity.this, "Playback failed. Try the page player.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onAudioSessionIdChanged(int audioSessionId) {
                applySound();
            }
        });
        applySound();
        scheduleSleep();
        int w = getIntent().getIntExtra(EXTRA_WIDTH, 0);
        int h = getIntent().getIntExtra(EXTRA_HEIGHT, 0);
        if (w > 0 && h > w) {
            orient = 1;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            ((TextView) findViewById(R.id.btn_rotate)).setText("Portrait");
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ImageButton close = findViewById(R.id.btn_close);
        close.setOnClickListener(v -> finish());
        findViewById(R.id.btn_speed).setOnClickListener(v -> cycleSpeed());
        findViewById(R.id.btn_download).setOnClickListener(v -> saveCurrent());
        findViewById(R.id.btn_pip).setOnClickListener(v -> enterPip());
        findViewById(R.id.btn_rotate).setOnClickListener(v -> rotate());
        findViewById(R.id.gesture_layer).setOnTouchListener((v, event) -> onGesture(event));
        playerView.showController();
    }

    private boolean onGesture(MotionEvent event) {
        if (player == null || locked) return true;
        View layer = findViewById(R.id.gesture_layer);
        float width = Math.max(1f, layer.getWidth());
        float height = Math.max(1f, layer.getHeight());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                downTime = event.getEventTime();
                gestureStart = player.getCurrentPosition();
                mode = "pending";
                moved = false;
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                volStart = Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);
                brightStart = getWindow().getAttributes().screenBrightness;
                if (brightStart < 0) brightStart = 0.6f;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if ("pending".equals(mode)) {
                    if (Math.hypot(dx, dy) < 18) {
                        if (event.getEventTime() - downTime > 350) {
                            player.setPlaybackParameters(new PlaybackParameters(2f));
                            showHud("2x");
                        }
                        return true;
                    }
                    moved = true;
                    if (Math.abs(dx) > Math.abs(dy)) mode = "seek";
                    else mode = downX < width / 2f ? "bright" : "volume";
                    playerView.showController();
                }
                if ("seek".equals(mode)) {
                    long dur = player.getDuration();
                    long span = dur > 0 ? (long) (dur * 0.35f) : 60_000L;
                    long next = gestureStart + (long) (dx / width * span);
                    if (dur > 0) next = Math.max(0, Math.min(dur, next));
                    player.seekTo(Math.max(0, next));
                    showHud(((next - gestureStart) >= 0 ? "+" : "") + ((next - gestureStart) / 1000) + "s");
                } else if ("volume".equals(mode)) {
                    int vol = (int) Math.max(0, Math.min(100, Math.round(volStart - (dy / height) * 100f)));
                    am = (AudioManager) getSystemService(AUDIO_SERVICE);
                    max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(vol / 100f * max), 0);
                    showHud("Volume " + vol + "%");
                } else if ("bright".equals(mode)) {
                    int pct = (int) Math.max(5, Math.min(100, Math.round(brightStart * 100 - (dy / height) * 100f)));
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = pct / 100f;
                    getWindow().setAttributes(lp);
                    showHud("Brightness " + pct + "%");
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (player.getPlaybackParameters().speed == 2f && speed != 2f) {
                    player.setPlaybackParameters(new PlaybackParameters(speed));
                }
                if (!moved && event.getEventTime() - downTime < 280) {
                    long now = event.getEventTime();
                    if (now - lastTap < 280 && Math.abs(event.getX() - lastTapX) < 120) {
                        int delta = event.getX() < width / 2f ? -10_000 : 10_000;
                        player.seekTo(Math.max(0, player.getCurrentPosition() + delta));
                        showHud(delta < 0 ? "-10s" : "+10s");
                        lastTap = 0;
                    } else {
                        lastTap = now;
                        lastTapX = event.getX();
                        boolean open = playerView.isControllerFullyVisible();
                        if (open) playerView.hideController();
                        else playerView.showController();
                        if (topBar != null) topBar.setVisibility(open ? View.GONE : View.VISIBLE);
                    }
                }
                mode = "none";
                return true;
            default:
                return false;
        }
    }

    private void cycleSpeed() {
        speed = speed >= 2f ? 0.5f : speed + 0.5f;
        player.setPlaybackParameters(new PlaybackParameters(speed));
        String label = speed == 0.5f ? "0.5x" : (speed == 1f || speed == 2f ? ((int) speed) + ".0x" : speed + "x");
        speedButton.setText(label);
        showHud(label);
    }

    private void rotate() {
        TextView button = findViewById(R.id.btn_rotate);
        orient = (orient + 1) % 3;
        if (orient == 0) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            button.setText("Landscape");
            showHud("Landscape");
        } else if (orient == 1) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            button.setText("Portrait");
            showHud("Portrait");
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            button.setText("Auto");
            showHud("Auto rotate");
        }
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26 || player == null) return;
        int w = Math.max(player.getVideoSize().width, 16);
        int h = Math.max(player.getVideoSize().height, 9);
        float ratio = Math.max(0.5f, Math.min(2.3f, (float) w / h));
        try {
            enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(Math.round(ratio * 100), 100))
                    .build());
        } catch (Exception ignored) {
            Toast.makeText(this, "Picture in picture is not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCurrent() {
        if (player == null || player.getCurrentMediaItem() == null) return;
        String url = player.getCurrentMediaItem().localConfiguration != null
                ? player.getCurrentMediaItem().localConfiguration.uri.toString() : null;
        if (url == null) return;
        DownloadTask queued = BlazeDownloadManager.get().enqueue(
                url,
                getIntent().getStringExtra(EXTRA_REFERER),
                getIntent().getStringExtra(EXTRA_UA),
                titleView != null ? titleView.getText().toString() : "video");
        if (queued != null) Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
    }

    private void applySound() {
        if (player == null || !BrowserPrefs.soundEnhance(this)) return;
        int session = player.getAudioSessionId();
        if (session == 0) return;
        try {
            if (loudness != null) loudness.release();
            loudness = new LoudnessEnhancer(session);
            loudness.setTargetGain(800);
            loudness.setEnabled(true);
        } catch (Exception ignored) {
            loudness = null;
        }
    }

    private void scheduleSleep() {
        int minutes = BrowserPrefs.autoSleepMinutes(this);
        if (minutes <= 0) return;
        handler.postDelayed(() -> {
            if (player != null) player.pause();
            Toast.makeText(this, "Auto sleep", Toast.LENGTH_SHORT).show();
            finish();
        }, minutes * 60_000L);
    }

    private void showHud(String text) {
        if (hud == null) return;
        hud.setText(text);
        hud.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideHud);
        handler.postDelayed(hideHud, 800);
    }

    private final Runnable hideHud = () -> {
        if (hud != null) hud.setVisibility(View.GONE);
    };

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && event.getEventTime() - downTime > 500 && !moved) {
            return super.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null && !isInPictureInPictureMode()) player.pause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (loudness != null) {
            try { loudness.release(); } catch (Exception ignored) {}
        }
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
