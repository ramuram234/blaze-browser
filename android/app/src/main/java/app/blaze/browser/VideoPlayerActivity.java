package app.blaze.browser;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

    private ExoPlayer player;
    private TextView hud;
    private TextView hint;
    private TextView timeLabel;
    private ProgressBar playBar;
    private ProgressBar bufferBar;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String mode = "none";
    private float downX;
    private float downY;
    private long startPos;
    private int startVolume = 50;
    private float startBright = 0.8f;
    private boolean moved;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (urls == null || urls.isEmpty()) {
            finish();
            return;
        }
        String referer = getIntent().getStringExtra(EXTRA_REFERER);
        String ua = getIntent().getStringExtra(EXTRA_UA);
        if (ua == null || ua.isEmpty()) ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36";

        hud = findViewById(R.id.hud);
        hint = findViewById(R.id.hint);
        timeLabel = findViewById(R.id.time_label);
        playBar = findViewById(R.id.play_bar);
        bufferBar = findViewById(R.id.buffer_bar);
        ImageButton close = findViewById(R.id.close_player);
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
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(1_500, 50_000, 400, 1_000)
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
                if (player.getMediaItemCount() <= 1) finish();
            }
        });

        close.setOnClickListener(v -> finish());
        layer.setOnTouchListener((v, event) -> onGesture(v, event));
        handler.postDelayed(() -> hint.setVisibility(View.GONE), 2800);
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
                playBar.setProgress((int) (pos * 1000 / dur));
                bufferBar.setProgress((int) (Math.min(buf, dur) * 1000 / dur));
                timeLabel.setText(format(pos) + " / " + format(dur) + "   buffered " + (buf * 100 / dur) + "%");
            } else {
                timeLabel.setText(player.getPlaybackState() == Player.STATE_BUFFERING ? "Buffering…" : format(pos));
            }
            handler.postDelayed(this, 250);
        }
    };

    private boolean onGesture(View v, MotionEvent event) {
        if (player == null) return false;
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

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
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
