package app.blaze.browser;

import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.net.*;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Represents one download with pause/resume/cancel support.
 * Boost mode: 8 threads + work-stealing + 256KB buffers + DNS prefetch.
 * Normal mode: 4 threads + 64KB buffers.
 */
public class DownloadTask {

    public enum Status { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }

    public interface Listener { void onUpdate(DownloadTask task); }

    // ── Public fields ──────────────────────────────────────────────────────
    public final  String        id;
    public final  String        url;
    public final  String        referer;
    public final  String        ua;
    public        String        filename   = "download";
    public        long          totalBytes = -1;
    public final  AtomicLong    downloaded = new AtomicLong(0);
    public volatile Status      status     = Status.QUEUED;
    public volatile float       speedKbps  = 0;
    public volatile String      errorMsg   = "";
    public         File         file;
    public         long         startTime;
    public         boolean      boostEnabled = false;

    // ── Private ────────────────────────────────────────────────────────────
    private volatile boolean    cancelled  = false;
    private volatile boolean    paused     = false;
    private ExecutorService     pool;
    private Listener            listener;

    // Boost config
    private static final int    THREADS_NORMAL = 4;
    private static final int    THREADS_BOOST  = 8;
    private static final int    BUF_NORMAL     = 64  * 1024;  //  64 KB
    private static final int    BUF_BOOST      = 256 * 1024;  // 256 KB
    private static final int    CHUNK_RETRIES  = 3;
    private static final String UA_DEF =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36";

    // Work-stealing: each slot tracks [currentByte, endByte] for its thread
    private final AtomicLong[] slotCurrent;
    private final AtomicLong[] slotEnd;
    private static final int   MAX_SLOTS = 16;

    public DownloadTask(String url, String referer, String ua) {
        this.id      = UUID.randomUUID().toString().substring(0, 8);
        this.url     = url;
        this.referer = referer;
        this.ua      = (ua != null && !ua.isEmpty()) ? ua : UA_DEF;
        slotCurrent  = new AtomicLong[MAX_SLOTS];
        slotEnd      = new AtomicLong[MAX_SLOTS];
        for (int i = 0; i < MAX_SLOTS; i++) {
            slotCurrent[i] = new AtomicLong(0);
            slotEnd[i]     = new AtomicLong(0);
        }
    }

    public void setListener(Listener l) { this.listener = l; }

    // ── Control ────────────────────────────────────────────────────────────

    public void start(File downloadsDir) {
        if (status == Status.COMPLETED || status == Status.CANCELLED) return;
        cancelled = false;
        paused    = false;
        status    = Status.DOWNLOADING;
        startTime = System.currentTimeMillis();
        notify(this);

        new Thread(() -> {
            try {
                // ── DNS prefetch (boost mode) ─────────────────────────────
                if (boostEnabled) prefetchDns(url);

                // ── HEAD request ──────────────────────────────────────────
                HttpURLConnection head = open(url);
                head.setRequestMethod("HEAD");
                head.setConnectTimeout(8000);
                head.setReadTimeout(8000);
                head.connect();

                totalBytes = head.getContentLengthLong();
                boolean rangesOk = "bytes".equalsIgnoreCase(
                        head.getHeaderField("Accept-Ranges"));
                String cd = head.getHeaderField("Content-Disposition");
                String ct = head.getContentType();
                head.disconnect();

                filename = guessName(url, cd, ct);
                if (file == null) file = uniqueFile(downloadsDir, filename);
                notify(this);

                // ── Choose strategy ───────────────────────────────────────
                int threads = boostEnabled ? THREADS_BOOST : THREADS_NORMAL;

                if (rangesOk && totalBytes > threads * 512 * 1024L) {
                    parallelDownload(threads);
                } else {
                    singleDownload();
                }

                if (!cancelled && !paused && status == Status.DOWNLOADING) {
                    status    = Status.COMPLETED;
                    speedKbps = 0;
                    BlazeDownloadManager.get().onComplete(this);
                }
            } catch (Exception e) {
                if (!cancelled && !paused) {
                    status   = Status.FAILED;
                    errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                }
            }
            notify(this);
        }).start();
    }

    public void pause() {
        paused = true;
        status = Status.PAUSED;
        if (pool != null) pool.shutdownNow();
        notify(this);
    }

    public void resume(File downloadsDir) {
        if (status != Status.PAUSED && status != Status.FAILED) return;
        start(downloadsDir);
    }

    public void cancel() {
        cancelled = true;
        status    = Status.CANCELLED;
        if (pool != null) pool.shutdownNow();
        if (file != null) file.delete();
        notify(this);
    }

    // ── Download engines ────────────────────────────────────────────────────

    /**
     * Parallel chunked download with work stealing.
     * Threads that finish early steal remaining work from the slowest thread.
     */
    private void parallelDownload(int numThreads) throws Exception {
        long alreadyDl  = downloaded.get();
        long remaining  = totalBytes - alreadyDl;
        long chunkSize  = remaining / numThreads;

        // Pre-allocate file
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(totalBytes);
        }

        // Initialise work slots
        for (int t = 0; t < numThreads; t++) {
            long from = alreadyDl + (t * chunkSize);
            long to   = (t == numThreads - 1) ? totalBytes - 1 : from + chunkSize - 1;
            slotCurrent[t].set(from);
            slotEnd[t].set(to);
        }

        pool = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            final int slot = t;
            pool.execute(() -> {
                try {
                    workerLoop(slot, numThreads);
                } catch (Exception e) {
                    if (!paused && !cancelled) errorMsg = e.getMessage();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Progress heartbeat
        long lastDl   = downloaded.get();
        long lastTime = System.currentTimeMillis();
        while (latch.getCount() > 0 && !cancelled && !paused) {
            Thread.sleep(400);
            long now     = downloaded.get();
            long elapsed = System.currentTimeMillis() - lastTime;
            speedKbps    = elapsed > 0 ? (now - lastDl) / (float) elapsed : 0;
            lastDl       = now;
            lastTime     = System.currentTimeMillis();
            notify(this);
        }

        latch.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();
        pool = null;
    }

    /**
     * Worker loop: downloads its assigned range, then tries to steal from the
     * slowest remaining thread (the one with the most bytes left).
     */
    private void workerLoop(int mySlot, int numThreads) throws IOException {
        int bufSize = boostEnabled ? BUF_BOOST : BUF_NORMAL;

        while (!cancelled && !paused) {
            long myStart = slotCurrent[mySlot].get();
            long myEnd   = slotEnd[mySlot].get();
            if (myStart > myEnd) {
                // My range is done — try to steal from the slowest thread
                int victimSlot = findSlowestSlot(mySlot, numThreads);
                if (victimSlot < 0) break; // everyone is done, we're done

                long vStart = slotCurrent[victimSlot].get();
                long vEnd   = slotEnd[victimSlot].get();
                long vLeft  = vEnd - vStart;
                if (vLeft < 2 * 512 * 1024L) break; // too small to steal

                // Steal the second half of victim's remaining work
                long stealFrom = vStart + vLeft / 2;
                // Atomically shrink victim's range
                if (!slotEnd[victimSlot].compareAndSet(vEnd, stealFrom - 1)) continue;
                // Take ownership of stolen range
                slotCurrent[mySlot].set(stealFrom);
                slotEnd[mySlot].set(vEnd);
                continue; // re-enter loop with our new range
            }

            downloadRangeWithRetry(mySlot, myStart, myEnd, bufSize);
            break;
        }
    }

    private void downloadRangeWithRetry(int slot, long from, long to, int bufSize)
            throws IOException {
        IOException lastEx = null;
        for (int attempt = 0; attempt < CHUNK_RETRIES; attempt++) {
            if (cancelled || paused) return;
            try {
                downloadRange(slot, slotCurrent[slot].get(), to, bufSize);
                return; // success
            } catch (IOException e) {
                lastEx = e;
                try { Thread.sleep(300L * (attempt + 1)); } catch (InterruptedException ignored) {}
            }
        }
        if (lastEx != null) throw lastEx;
    }

    private void downloadRange(int slot, long from, long to, int bufSize) throws IOException {
        HttpURLConnection conn = open(url);
        conn.setRequestProperty("Range", "bytes=" + from + "-" + to);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.connect();

        int code = conn.getResponseCode();
        if (code != 206 && code != 200) {
            conn.disconnect();
            throw new IOException("HTTP " + code);
        }

        byte[] buf = new byte[bufSize];
        try (InputStream in = conn.getInputStream();
             RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(from);
            int read;
            while ((read = in.read(buf)) != -1) {
                if (cancelled || paused) return;
                raf.write(buf, 0, read);
                downloaded.addAndGet(read);
                slotCurrent[slot].addAndGet(read);
            }
        } finally {
            conn.disconnect();
        }
    }

    /** Returns the slot index with the most remaining work, or -1 if none. */
    private int findSlowestSlot(int excludeSlot, int numThreads) {
        int best = -1;
        long mostLeft = 512 * 1024L; // minimum threshold to steal
        for (int i = 0; i < numThreads; i++) {
            if (i == excludeSlot) continue;
            long left = slotEnd[i].get() - slotCurrent[i].get();
            if (left > mostLeft) { mostLeft = left; best = i; }
        }
        return best;
    }

    private void singleDownload() throws IOException {
        long startByte = downloaded.get();
        HttpURLConnection conn = open(url);
        if (startByte > 0) conn.setRequestProperty("Range", "bytes=" + startByte + "-");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.connect();

        int code = conn.getResponseCode();
        if (code < 200 || code >= 400) { conn.disconnect(); return; }

        int bufSize = boostEnabled ? BUF_BOOST : BUF_NORMAL;
        byte[] buf  = new byte[bufSize];
        startTime   = System.currentTimeMillis();
        long lastDl = 0;

        try (InputStream in = conn.getInputStream();
             RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(startByte);
            int read;
            while ((read = in.read(buf)) != -1) {
                if (cancelled || paused) break;
                raf.write(buf, 0, read);
                downloaded.addAndGet(read);
                long now     = downloaded.get();
                long elapsed = System.currentTimeMillis() - startTime;
                speedKbps    = elapsed > 0 ? (now - lastDl) / (float) elapsed : 0;
                lastDl       = now;
                notify(this);
            }
        } finally {
            conn.disconnect();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Pre-resolve DNS so first connection is faster. */
    private static void prefetchDns(String urlStr) {
        try {
            String host = new URL(urlStr).getHost();
            InetAddress.getAllByName(host); // triggers DNS resolution
        } catch (Exception ignored) {}
    }

    private HttpURLConnection open(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestProperty("User-Agent", ua);
        c.setRequestProperty("Accept", "*/*");
        c.setRequestProperty("Accept-Encoding", "identity");
        if (referer != null && referer.startsWith("http"))
            c.setRequestProperty("Referer", referer);
        String cookie = CookieManager.getInstance().getCookie(u);
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    private void notify(DownloadTask t) {
        if (listener != null) listener.onUpdate(t);
    }

    // ── Display helpers ────────────────────────────────────────────────────

    public int progressPercent() {
        if (totalBytes <= 0) return -1;
        return (int) Math.min(100, downloaded.get() * 100 / totalBytes);
    }

    public String speedLabel() {
        if (speedKbps <= 0) return "";
        if (speedKbps > 1024) return String.format(Locale.US, "%.1f MB/s", speedKbps / 1024f);
        return String.format(Locale.US, "%.0f KB/s", speedKbps);
    }

    public String sizeLabel() {
        long dl = downloaded.get();
        if (totalBytes > 0) return fmtSize(dl) + " / " + fmtSize(totalBytes);
        return fmtSize(dl);
    }

    private static String fmtSize(long b) {
        if (b > 1024 * 1024) return String.format(Locale.US, "%.1f MB", b / 1024f / 1024f);
        if (b > 1024)        return String.format(Locale.US, "%.0f KB", b / 1024f);
        return b + " B";
    }

    // ── File helpers ───────────────────────────────────────────────────────

    private static String guessName(String url, String cd, String ct) {
        if (cd != null) {
            int i = cd.indexOf("filename=");
            if (i >= 0) {
                String n = cd.substring(i + 9).replace("\"", "").trim();
                if (!n.isEmpty()) return sanitize(n);
            }
        }
        try {
            String path = new URL(url).getPath();
            String name = path.substring(path.lastIndexOf('/') + 1).split("\\?")[0];
            if (name.contains(".")) return sanitize(name);
        } catch (Exception ignored) {}
        String ext = "mp4";
        if (ct != null && ct.contains("/")) {
            String g = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(ct.split(";")[0].trim());
            if (g != null) ext = g;
        }
        return "video_" + System.currentTimeMillis() + "." + ext;
    }

    private static String sanitize(String n) {
        return n.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext  = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 100; i++) {
            f = new File(dir, base + "_" + i + ext);
            if (!f.exists()) return f;
        }
        return new File(dir, "blaze_" + System.currentTimeMillis() + ext);
    }
}