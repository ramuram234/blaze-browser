package app.blaze.browser;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Multi-threaded chunked downloader.
 * Splits the file into THREADS parts and downloads each in parallel
 * using HTTP Range requests, writing directly to the correct file offset.
 * Falls back to single-thread if the server does not support Range requests.
 */
public final class FastDownloader {

    private static final int    THREADS          = 4;
    private static final int    NOTIFICATION_ID  = 9001;
    private static final String CHANNEL_ID       = "blaze_dl";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36";

    public interface Callback {
        void onStart(String filename, long totalBytes);
        void onProgress(long downloaded, long total, float speedKbps);
        void onComplete(File file);
        void onError(String message);
    }

    private FastDownloader() {}

    /** Start a download on a background thread. Returns immediately. */
    public static void start(Context ctx, String videoUrl, String referer, String ua, Callback cb) {
        Executors.newSingleThreadExecutor().execute(() -> download(ctx, videoUrl, referer, ua, cb));
    }

    private static void download(Context ctx, String rawUrl, String referer, String userAgent, Callback cb) {
        Handler main = new Handler(Looper.getMainLooper());
        String ua2 = (userAgent != null && !userAgent.isEmpty()) ? userAgent : UA;

        try {
            // ── Step 1: HEAD request → file size + range support ──────────────
            HttpURLConnection head = openConnection(rawUrl, referer, ua2);
            head.setRequestMethod("HEAD");
            head.setConnectTimeout(8000);
            head.setReadTimeout(8000);
            head.connect();

            long fileSize  = head.getContentLengthLong();
            boolean ranges = "bytes".equalsIgnoreCase(head.getHeaderField("Accept-Ranges"));
            String contentType = head.getContentType();
            String rawFilename = guessFilename(rawUrl, head.getHeaderField("Content-Disposition"), contentType);
            head.disconnect();

            // ── Step 2: Prepare destination file ──────────────────────────────
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            File dest = uniqueFile(dir, rawFilename);

            main.post(() -> cb.onStart(dest.getName(), fileSize));
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            createChannel(nm);

            // ── Step 3: Download ───────────────────────────────────────────────
            long startTime = System.currentTimeMillis();
            AtomicLong totalDownloaded = new AtomicLong(0);
            AtomicBoolean failed = new AtomicBoolean(false);
            AtomicReference<String> errorMsg = new AtomicReference<>("");

            if (ranges && fileSize > 0 && fileSize > THREADS * 512 * 1024L) {
                // ── Parallel chunked download ──────────────────────────────────
                long chunkSize = fileSize / THREADS;
                CountDownLatch latch = new CountDownLatch(THREADS);
                ExecutorService pool = Executors.newFixedThreadPool(THREADS);

                // Pre-allocate the file to its full size
                try (RandomAccessFile raf = new RandomAccessFile(dest, "rw")) {
                    raf.setLength(fileSize);
                }

                for (int t = 0; t < THREADS; t++) {
                    final long from = t * chunkSize;
                    final long to   = (t == THREADS - 1) ? fileSize - 1 : from + chunkSize - 1;
                    pool.execute(() -> {
                        try {
                            downloadChunk(rawUrl, referer, ua2, from, to, dest, totalDownloaded, failed);
                        } catch (Exception e) {
                            failed.set(true);
                            errorMsg.set(e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                // Progress reporting while threads run
                while (latch.getCount() > 0) {
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    long dl = totalDownloaded.get();
                    long elapsed = System.currentTimeMillis() - startTime;
                    float kbps = elapsed > 0 ? (dl / 1024f) / (elapsed / 1000f) : 0;
                    int pct = fileSize > 0 ? (int)(dl * 100 / fileSize) : 0;
                    showProgress(nm, ctx, dest.getName(), pct, kbps);
                    main.post(() -> cb.onProgress(dl, fileSize, kbps));
                }

                latch.await();
                pool.shutdown();

            } else {
                // ── Single-thread fallback ─────────────────────────────────────
                downloadSingle(rawUrl, referer, ua2, dest, fileSize, totalDownloaded,
                        failed, errorMsg, startTime, nm, ctx, main, cb);
            }

            if (failed.get()) {
                dest.delete();
                nm.cancel(NOTIFICATION_ID);
                String msg = "Download failed: " + errorMsg.get();
                main.post(() -> cb.onError(msg));
                return;
            }

            // ── Done ──────────────────────────────────────────────────────────
            long elapsed = System.currentTimeMillis() - startTime;
            float avgKbps = elapsed > 0 ? (totalDownloaded.get() / 1024f) / (elapsed / 1000f) : 0;
            showDone(nm, ctx, dest, dest.getName(), avgKbps);

            // Notify media scanner
            ctx.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(dest)));
            main.post(() -> cb.onComplete(dest));

        } catch (Exception e) {
            main.post(() -> cb.onError("Download error: " + e.getMessage()));
        }
    }

    /** Download a single byte range [from, to] and write to file at correct offset. */
    private static void downloadChunk(String url, String referer, String ua,
                                      long from, long to, File dest,
                                      AtomicLong totalDl, AtomicBoolean failed)
            throws IOException {
        if (failed.get()) return;
        HttpURLConnection conn = openConnection(url, referer, ua);
        conn.setRequestProperty("Range", "bytes=" + from + "-" + to);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.connect();

        int code = conn.getResponseCode();
        if (code != 206 && code != 200) {
            conn.disconnect();
            failed.set(true);
            return;
        }

        byte[] buf = new byte[128 * 1024]; // 128 KB buffer
        try (InputStream in = conn.getInputStream();
             RandomAccessFile raf = new RandomAccessFile(dest, "rw")) {
            raf.seek(from);
            int read;
            while ((read = in.read(buf)) != -1) {
                if (failed.get()) return;
                raf.write(buf, 0, read);
                totalDl.addAndGet(read);
            }
        } finally {
            conn.disconnect();
        }
    }

    /** Single-thread download (fallback). */
    private static void downloadSingle(String url, String referer, String ua,
                                       File dest, long fileSize,
                                       AtomicLong totalDl, AtomicBoolean failed,
                                       AtomicReference<String> errorMsg,
                                       long startTime, NotificationManager nm,
                                       Context ctx, Handler main, Callback cb)
            throws IOException {
        HttpURLConnection conn = openConnection(url, referer, ua);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.connect();

        int code = conn.getResponseCode();
        if (code < 200 || code >= 400) {
            conn.disconnect();
            failed.set(true);
            errorMsg.set("HTTP " + code);
            return;
        }

        byte[] buf = new byte[128 * 1024];
        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
                totalDl.addAndGet(read);
                long dl = totalDl.get();
                long elapsed = System.currentTimeMillis() - startTime;
                float kbps = elapsed > 0 ? (dl / 1024f) / (elapsed / 1000f) : 0;
                int pct = fileSize > 0 ? (int)(dl * 100 / fileSize) : -1;
                showProgress(nm, ctx, dest.getName(), pct, kbps);
                main.post(() -> cb.onProgress(dl, fileSize, kbps));
            }
        } finally {
            conn.disconnect();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static HttpURLConnection openConnection(String url, String referer, String ua)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", ua);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Accept-Encoding", "identity"); // no gzip for binary
        if (referer != null && !referer.isEmpty())
            conn.setRequestProperty("Referer", referer);
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null) conn.setRequestProperty("Cookie", cookie);
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    private static String guessFilename(String url, String contentDisposition, String contentType) {
        // Try Content-Disposition first: attachment; filename="video.mp4"
        if (contentDisposition != null) {
            int idx = contentDisposition.indexOf("filename=");
            if (idx >= 0) {
                String name = contentDisposition.substring(idx + 9).replace("\"", "").trim();
                if (!name.isEmpty()) return sanitize(name);
            }
        }
        // Try URL path
        try {
            String path = new URL(url).getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (name.contains(".")) return sanitize(name.split("\\?")[0]);
        } catch (Exception ignored) {}
        // Fallback: timestamp + extension from MIME
        String ext = "mp4";
        if (contentType != null && contentType.contains("/")) {
            String guessed = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(contentType.split(";")[0].trim());
            if (guessed != null) ext = guessed;
        }
        return "blaze_video_" + System.currentTimeMillis() + "." + ext;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext  = dot > 0 ? name.substring(dot)    : "";
        for (int i = 2; i < 100; i++) {
            f = new File(dir, base + "_" + i + ext);
            if (!f.exists()) return f;
        }
        return new File(dir, "blaze_" + System.currentTimeMillis() + ext);
    }

    private static void createChannel(NotificationManager nm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Blaze Browser download progress");
            nm.createNotificationChannel(ch);
        }
    }

    private static void showProgress(NotificationManager nm, Context ctx,
                                     String name, int pct, float kbps) {
        String speed = kbps > 1024
                ? String.format(Locale.US, "%.1f MB/s", kbps / 1024f)
                : String.format(Locale.US, "%.0f KB/s", kbps);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(name)
                .setContentText(pct >= 0 ? pct + "% — " + speed : "Downloading… " + speed)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (pct >= 0) b.setProgress(100, pct, false);
        else          b.setProgress(0, 0, true);
        nm.notify(NOTIFICATION_ID, b.build());
    }

    private static void showDone(NotificationManager nm, Context ctx,
                                 File dest, String name, float kbps) {
        nm.cancel(NOTIFICATION_ID);
        Intent open = new Intent(Intent.ACTION_VIEW);
        open.setDataAndType(Uri.fromFile(dest), "video/*");
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String speed = kbps > 1024
                ? String.format(Locale.US, "%.1f MB/s avg", kbps / 1024f)
                : String.format(Locale.US, "%.0f KB/s avg", kbps);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download complete")
                .setContentText(name + " — " + speed)
                .setAutoCancel(true)
                .setContentIntent(pi);
        nm.notify(NOTIFICATION_ID + 1, b.build());
    }
}