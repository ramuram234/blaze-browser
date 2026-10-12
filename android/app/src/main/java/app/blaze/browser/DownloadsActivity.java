package app.blaze.browser;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class DownloadsActivity extends AppCompatActivity
        implements BlazeDownloadManager.UpdateListener {

    private LinearLayout list;
    private TextView     emptyLabel;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        list       = findViewById(R.id.downloads_list);
        emptyLabel = findViewById(R.id.empty_label);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_clear_all).setOnClickListener(v -> clearCompleted());

        BlazeDownloadManager.get().setUiListener(this);
        refresh();
    }

    @Override
    protected void onDestroy() {
        BlazeDownloadManager.get().setUiListener(null);
        super.onDestroy();
    }

    @Override
    public void onDownloadsChanged() {
        handler.post(this::refresh);
    }

    private void refresh() {
        list.removeAllViews();
        List<DownloadTask> tasks = BlazeDownloadManager.get().getTasks();

        if (tasks.isEmpty()) {
            emptyLabel.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
            return;
        }
        emptyLabel.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);

        for (DownloadTask t : tasks) {
            list.addView(buildRow(t));
            View div = new View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            dlp.setMarginStart(dp(16));
            div.setLayoutParams(dlp);
            div.setBackgroundColor(0xFFEBEBEB);
            list.addView(div);
        }
    }

    private View buildRow(DownloadTask t) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(10));
        root.setBackgroundColor(Color.WHITE);

        // ── Row 1: icon + filename + status badge ─────────────────────────
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        // File icon
        TextView icon = new TextView(this);
        icon.setText("🎬");
        icon.setTextSize(20);
        icon.setPadding(0, 0, dp(10), 0);
        top.addView(icon);

        // Filename + status
        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        nameCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView name = new TextView(this);
        name.setText(t.filename);
        name.setTextColor(0xFF16181C);
        name.setTextSize(14);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        nameCol.addView(name);

        TextView info = new TextView(this);
        info.setTextSize(12);
        info.setTextColor(0xFF66707A);
        String infoStr = statusLabel(t);
        if (!t.speedLabel().isEmpty() && t.status == DownloadTask.Status.DOWNLOADING)
            infoStr += "  " + t.speedLabel();
        if (!t.sizeLabel().isEmpty()) infoStr += "  " + t.sizeLabel();
        info.setText(infoStr);
        nameCol.addView(info);
        top.addView(nameCol);

        root.addView(top);

        // ── Row 2: progress bar ───────────────────────────────────────────
        if (t.status == DownloadTask.Status.DOWNLOADING
                || t.status == DownloadTask.Status.PAUSED) {
            ProgressBar pb = new ProgressBar(this, null,
                    android.R.attr.progressBarStyleHorizontal);
            LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
            pblp.topMargin = dp(8);
            pb.setLayoutParams(pblp);
            pb.setMax(100);
            int pct = t.progressPercent();
            if (pct >= 0) {
                pb.setIndeterminate(false);
                pb.setProgress(pct);
                pb.setProgressTintList(android.content.res.ColorStateList.valueOf(
                        t.status == DownloadTask.Status.PAUSED ? 0xFFFF9500 : 0xFF1A73E8));
            } else {
                pb.setIndeterminate(true);
            }
            root.addView(pb);
        }

        // ── Row 3: action buttons ─────────────────────────────────────────
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(8);
        actions.setLayoutParams(alp);

        switch (t.status) {
            case DOWNLOADING:
                addBtn(actions, "⏸  Pause",  0xFF1A73E8, () -> t.pause());
                addBtn(actions, "✕  Cancel", 0xFFDD3333, () -> confirmCancel(t));
                break;
            case PAUSED:
                addBtn(actions, "▶  Resume", 0xFF1A73E8, () -> {
                    t.resume(android.os.Environment
                            .getExternalStoragePublicDirectory(
                                    android.os.Environment.DIRECTORY_DOWNLOADS));
                });
                addBtn(actions, "✕  Cancel", 0xFFDD3333, () -> confirmCancel(t));
                break;
            case COMPLETED:
                addBtn(actions, "▶  Open",   0xFF00BFA5, () -> openFile(t));
                addBtn(actions, "✕  Remove", 0xFFAAAAAA, () -> {
                    BlazeDownloadManager.get().getTasks()
                            .stream().filter(x -> x.id.equals(t.id))
                            .findFirst().ifPresent(x ->
                                    BlazeDownloadManager.get().removeTask(x));
                });
                break;
            case FAILED:
                addBtn(actions, "↺  Retry",  0xFFFF9500, () -> {
                    t.downloaded.set(0);
                    t.resume(android.os.Environment
                            .getExternalStoragePublicDirectory(
                                    android.os.Environment.DIRECTORY_DOWNLOADS));
                });
                addBtn(actions, "✕  Remove", 0xFFAAAAAA, () ->
                        BlazeDownloadManager.get().removeTask(t));
                break;
            case CANCELLED:
                addBtn(actions, "✕  Remove", 0xFFAAAAAA, () ->
                        BlazeDownloadManager.get().removeTask(t));
                break;
        }

        root.addView(actions);
        return root;
    }

    private void addBtn(LinearLayout parent, String label, int color, Runnable action) {
        TextView btn = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(12));
        btn.setLayoutParams(lp);
        btn.setText(label);
        btn.setTextColor(color);
        btn.setTextSize(12);
        btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btn.setPadding(0, dp(4), 0, dp(4));
        btn.setOnClickListener(v -> { action.run(); refresh(); });
        parent.addView(btn);
    }

    private void confirmCancel(DownloadTask t) {
        new AlertDialog.Builder(this)
                .setTitle("Cancel download?")
                .setMessage("Cancel downloading " + t.filename + "?")
                .setPositiveButton("Cancel download", (d, w) -> {
                    t.cancel();
                    refresh();
                })
                .setNegativeButton("Keep", null)
                .show();
    }

    private void openFile(DownloadTask t) {
        if (t.file == null || !t.file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.fromFile(t.file), "video/*");
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void clearCompleted() {
        List<DownloadTask> tasks = BlazeDownloadManager.get().getTasks();
        for (DownloadTask t : tasks) {
            if (t.status == DownloadTask.Status.COMPLETED
                    || t.status == DownloadTask.Status.CANCELLED
                    || t.status == DownloadTask.Status.FAILED) {
                BlazeDownloadManager.get().removeTask(t);
            }
        }
        refresh();
    }

    private static String statusLabel(DownloadTask t) {
        switch (t.status) {
            case QUEUED:      return "Queued";
            case DOWNLOADING: return "Downloading";
            case PAUSED:      return "Paused";
            case COMPLETED:   return "Complete";
            case FAILED:      return "Failed: " + t.errorMsg;
            case CANCELLED:   return "Cancelled";
            default:          return "";
        }
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }
}