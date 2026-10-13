package app.blaze.browser;

import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import java.util.Locale;

/**
 * Monitors real-time network download speed using TrafficStats.
 * Inspired by UC Browser's TrafficRoundProgressBar pattern.
 * Poll interval: 1 second.
 */
public class NetworkSpeedMonitor {

    public interface SpeedListener {
        /** Called every second on the main thread. */
        void onSpeed(String formatted, long bytesPerSec);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeedListener listener;
    private long lastRxBytes;
    private long lastTimeMs;
    private boolean running = false;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long nowMs  = System.currentTimeMillis();
            long rxNow  = TrafficStats.getTotalRxBytes();
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
        this.listener = l;
        lastRxBytes   = TrafficStats.getTotalRxBytes();
        lastTimeMs    = System.currentTimeMillis();
        running       = true;
        handler.postDelayed(tick, 1000);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    /** Format bytes/sec → human-readable "123 KB/s" or "1.4 MB/s". */
    public static String format(long bps) {
        if (bps < 0)          return "0 B/s";
        if (bps < 1024L)      return bps + " B/s";
        if (bps < 1024*1024L) return String.format(Locale.US, "%.0f KB/s", bps / 1024f);
        return                       String.format(Locale.US, "%.1f MB/s", bps / (1024f * 1024f));
    }
}
