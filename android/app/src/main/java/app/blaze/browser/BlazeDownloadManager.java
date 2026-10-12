package app.blaze.browser;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Singleton that manages all DownloadTask instances and notifications. */
public final class BlazeDownloadManager {

    private static final String CHANNEL_ID = "blaze_dl";
    private static volatile BlazeDownloadManager INSTANCE;

    private final List<DownloadTask> tasks = new CopyOnWriteArrayList<>();
    private Context appContext;
    private NotificationManager nm;

    // Listener for the DownloadsActivity to refresh its UI
    public interface UpdateListener { void onDownloadsChanged(); }
    private volatile UpdateListener uiListener;

    private BlazeDownloadManager() {}

    public static BlazeDownloadManager get() {
        if (INSTANCE == null) {
            synchronized (BlazeDownloadManager.class) {
                if (INSTANCE == null) INSTANCE = new BlazeDownloadManager();
            }
        }
        return INSTANCE;
    }

    public void init(Context ctx) {
        appContext = ctx.getApplicationContext();
        nm = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
    }

    public void setUiListener(UpdateListener l) { uiListener = l; }

    /** Start a new download. Returns the task. */
    public DownloadTask enqueue(String url, String referer, String ua, String title) {
        DownloadTask task = new DownloadTask(url, referer, ua);
        if (title != null && !title.isEmpty()) task.filename = title;
        tasks.add(0, task); // newest first

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists()) dir.mkdirs();

        task.setListener(t -> {
            showNotification(t);
            notifyUi();
        });

        task.start(dir);
        notifyUi();
        return task;
    }

    public List<DownloadTask> getTasks() { return Collections.unmodifiableList(tasks); }

    public void onComplete(DownloadTask t) {
        showDoneNotification(t);
        notifyUi();
        // Broadcast to media scanner
        if (t.file != null && appContext != null) {
            appContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(t.file)));
        }
    }

    public void removeTask(DownloadTask t) {
        t.cancel();
        tasks.remove(t);
        notifyUi();
    }

    // ── Notification helpers ─────────────────────────────────────────────

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
    }

    private void showNotification(DownloadTask t) {
        if (nm == null || appContext == null) return;
        int id = Math.abs(t.id.hashCode());

        switch (t.status) {
            case DOWNLOADING: {
                int pct = t.progressPercent();
                String info = (pct >= 0 ? pct + "% — " : "") + t.speedLabel()
                        + "\n" + t.sizeLabel();
                NotificationCompat.Builder b = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle(t.filename)
                        .setContentText(t.speedLabel() + "  " + t.sizeLabel())
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(info))
                        .setOngoing(true)
                        .setOnlyAlertOnce(true);
                if (pct >= 0) b.setProgress(100, pct, false);
                else          b.setProgress(0, 0, true);
                nm.notify(id, b.build());
                break;
            }
            case PAUSED: {
                nm.cancel(id);
                NotificationCompat.Builder b = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_media_pause)
                        .setContentTitle(t.filename + " — Paused")
                        .setContentText(t.sizeLabel() + " downloaded")
                        .setOngoing(false);
                nm.notify(id, b.build());
                break;
            }
            case CANCELLED:
            case FAILED:
                nm.cancel(id);
                break;
        }
    }

    private void showDoneNotification(DownloadTask t) {
        if (nm == null || appContext == null || t.file == null) return;
        int id = Math.abs(t.id.hashCode());
        nm.cancel(id);

        Intent open = new Intent(Intent.ACTION_VIEW);
        open.setDataAndType(Uri.fromFile(t.file), "video/*");
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(appContext, id, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder b = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download complete")
                .setContentText(t.filename + "  " + t.sizeLabel())
                .setAutoCancel(true)
                .setContentIntent(pi);
        nm.notify(id + 10000, b.build());
    }

    private void notifyUi() {
        UpdateListener l = uiListener;
        if (l != null) l.onDownloadsChanged();
    }
}