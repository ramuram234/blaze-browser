package app.blaze.browser;

import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import java.util.Locale;

/**
 * Real-time network speed monitor.
 *
 * Rewritten from UC Browser source:
 *   com.uc.browser.business.traffic.TrafficRoundProgressBar
 *
 * UC Browser's TrafficRoundProgressBar uses this exact polling pattern:
 *   - Every second: read TrafficStats.getTotalRxBytes()
 *   - Calculate differential: (current - last) / elapsed_ms * 1000
 *   - Format: B/s, KB/s, MB/s
 *   - Pass to view via b(float) = set download progress, c(float) = set upload progress
 */
public class NetworkSpeedMonitor {

    public interface SpeedListener {
        /** Called every second on the main thread. bps = bytes per second. */
        void onSpeed(String formatted, long bytesPerSec);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeedListener listener;
    private long lastRxBytes;
    private long lastTimeMs;
    private boolean running = false;

    // UC Browser TrafficRoundProgressBar tick runnable (same 1-second interval)
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long nowMs = System.currentTimeMillis();
            long rxNow = TrafficStats.getTotalRxBytes();
            long elapsedMs = nowMs - lastTimeMs;
            if (elapsedMs > 0 && rxNow != TrafficStats.UNSUPPORTED && rxNow >= lastRxBytes) {
                long bps = (rxNow - lastRxBytes) * 1000L / elapsedMs;
                if (listener != null) listener.onSpeed(format(bps), bps);
            }
            lastRxBytes = rxNow;
            lastTimeMs  = nowMs;
            handler.postDelayed(this, 1000);
        }
    };

    public void start(SpeedListener l) {
        this.listener  = l;
        lastRxBytes    = TrafficStats.getTotalRxBytes();
        lastTimeMs     = System.currentTimeMillis();
        running        = true;
        handler.postDelayed(tick, 1000);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    /**
     * UC Browser speed format: same thresholds as TrafficRoundProgressBar display.
     * B/s -> KB/s at 1024, KB/s -> MB/s at 1024*1024.
     */
    public static String format(long bps) {
        if (bps < 0)             return "0 B/s";
        if (bps < 1024L)         return bps + " B/s";
        if (bps < 1024L * 1024L) return String.format(Locale.US, "%.0f KB/s", bps / 1024f);
        return                          String.format(Locale.US, "%.1f MB/s", bps / (1024f * 1024f));
    }

    /** Convert bytes/sec to percentage of max bandwidth for arc display (UC pattern). */
    public static float toPercent(long bps, long maxBps) {
        if (maxBps <= 0) maxBps = 10L * 1024 * 1024; // default max: 10 MB/s
        return Math.min(100f, (float) bps / maxBps * 100f);
    }
}
