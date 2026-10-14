package app.blaze.browser;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

/**
 * Download manager screen with two-tab layout: Downloading | Finished.
 * UC Browser-inspired design with per-task speed display and progress bars.
 */
public class DownloadsActivity extends AppCompatActivity
        implements BlazeDownloadManager.UpdateListener {

    private LinearLayout list;
    private TextView emptyLabel;
    private TextView tabDownloading, tabFinished;
    private View tabIndicatorLeft, tabIndicatorRight;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean showingDownloading = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        list = findViewById(R.id.downloads_list);
        emptyLabel = findViewById(R.id.empty_label);
        tabDownloading = (TextView) findViewById(R.id.tab_downloading);
        tabFinished    = (TextView) findViewById(R.id.tab_finished);

        // Tab indicator (manual approach - no TabLayout dependency needed)
        View indicator = findViewById(R.id.tab_indicator);

        tabDownloading.setOnClickListener(v -> {
            showingDownloading = true;
            updateTabStyle();
            refresh();
        });
        tabFinished.setOnClickListener(v -> {
            showingDownloading = false;
            updateTabStyle();
            refresh();
        });

        updateTabStyle();

        ImageButton back = (ImageButton) findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        Button clearAll = (Button) findViewById(R.id.btn_clear_all);
        if (clearAll != null) clearAll.setOnClickListener(v -> clearCompleted());

        BlazeDownloadManager.get().setUiListener(this);
        refresh();
    }

    @Override protected void onDestroy() {
        BlazeDownloadManager.get().setUiListener(null);
        super.onDestroy();
    }

    @Override public void onDownloadsChanged() { handler.post(this::refresh); }

    private void updateTabStyle() {
        int active = Color.parseColor("#1A73E8");
        int inactive = Color.parseColor("#666666");
        if (tabDownloading != null) {
            tabDownloading.setTextColor(showingDownloading ? active : inactive);
            tabDownloading.setTextSize(showingDownloading ? 14 : 13);
        }
        if (tabFinished != null) {
            tabFinished.setTextColor(showingDownloading ? inactive : active);
            tabFinished.setTextSize(showingDownloading ? 13 : 14);
        }
    }

    /** Tasks in Downloading tab: QUEUED, DOWNLOADING, PAUSED. */
    static List<DownloadTask> activeFilter(List<DownloadTask> all) {
        List<DownloadTask> out = new ArrayList<>();
        for (DownloadTask t : all)
            if (t.status == DownloadTask.Status.QUEUED
                    || t.status == DownloadTask.Status.DOWNLOADING
                    || t.status == DownloadTask.Status.PAUSED)
                out.add(t);
        return out;
    }

    /** Tasks in Finished tab: COMPLETED, FAILED, CANCELLED. */
    static List<DownloadTask> finishedFilter(List<DownloadTask> all) {
        List<DownloadTask> out = new ArrayList<>();
        for (DownloadTask t : all)
            if (t.status == DownloadTask.Status.COMPLETED
                    || t.status == DownloadTask.Status.FAILED
                    || t.status == DownloadTask.Status.CANCELLED)
                out.add(t);
        return out;
    }

    private void refresh() {
        if (list == null) return;
        list.removeAllViews();
        List<DownloadTask> allTasks = BlazeDownloadManager.get().getTasks();
        List<DownloadTask> filtered = showingDownloading
                ? activeFilter(allTasks) : finishedFilter(allTasks);

        if (filtered.isEmpty()) {
            if (emptyLabel != null) emptyLabel.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
            return;
        }
        if (emptyLabel != null) emptyLabel.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);

        for (DownloadTask t : filtered) {
            list.addView(buildRow(t));
            // Divider
            View div = new View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            dlp.setMarginStart(dp(16));
            div.setLayoutParams(dlp);
            div.setBackgroundColor(0xFFEBEBEB);
            list.addView(div);
        }
    }

    private View buildRow(final DownloadTask t) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(12));
        root.setBackgroundColor(Color.WHITE);

        // Row 1: filename
        TextView name = new TextView(this);
        name.setText(t.filename);  // UC Browser: download_task_name
        name.setTextSize(14);
        name.setTextColor(Color.parseColor("#111111"));
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(name);

        // Row 2: progress bar + percentage
        boolean hasProgress = t.totalBytes > 0;
        if (hasProgress || t.status == DownloadTask.Status.DOWNLOADING) {
            LinearLayout progRow = new LinearLayout(this);
            progRow.setOrientation(LinearLayout.HORIZONTAL);
            progRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp(6);
            progRow.setLayoutParams(rowLp);

            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            int pct = t.progressPercent();
            pb.setProgress(pct >= 0 ? pct : 0);
            pb.setIndeterminate(pct < 0);
            // Amber tint for paused
            if (t.status == DownloadTask.Status.PAUSED) {
                pb.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF9500")));
            } else {
                pb.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1A73E8")));
            }
            LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(0, dp(6), 1f);
            pb.setLayoutParams(pbLp);
            progRow.addView(pb);

            TextView pctTv = new TextView(this);
            pctTv.setText(pct >= 0 ? pct + "%" : "");
            pctTv.setTextSize(11);
            pctTv.setTextColor(Color.parseColor("#888888"));
            pctTv.setPadding(dp(8), 0, 0, 0);
            pctTv.setMinWidth(dp(36));
            pctTv.setGravity(android.view.Gravity.END);
            progRow.addView(pctTv);
            root.addView(progRow);
        }

        // Row 3: speed + size
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = dp(4);
        infoRow.setLayoutParams(infoLp);

        TextView speedTv = new TextView(this);
        speedTv.setText(t.speedLabel());
        speedTv.setTextSize(12);
        speedTv.setTextColor(Color.parseColor("#1A73E8"));
        LinearLayout.LayoutParams speedLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        speedTv.setLayoutParams(speedLp);
        infoRow.addView(speedTv);

        TextView sizeTv = new TextView(this);
        sizeTv.setText(t.sizeLabel());
        sizeTv.setTextSize(12);
        sizeTv.setTextColor(Color.parseColor("#888888"));
        infoRow.addView(sizeTv);
        root.addView(infoRow);

        // Row 4: status badge
        TextView statusBadge = new TextView(this);
        statusBadge.setText(t.status.name());
        statusBadge.setTextSize(11);
        int badgeColor;
        switch (t.status) {
            case DOWNLOADING: badgeColor = Color.parseColor("#1A73E8"); break;
            case COMPLETED:   badgeColor = Color.parseColor("#0F9D58"); break;
            case FAILED:      badgeColor = Color.parseColor("#D32F2F"); break;
            case PAUSED:      badgeColor = Color.parseColor("#FF9500"); break;
            default:          badgeColor = Color.parseColor("#888888");
        }
        statusBadge.setTextColor(badgeColor);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.topMargin = dp(4);
        statusBadge.setLayoutParams(badgeLp);
        root.addView(statusBadge);

        // Row 5: action buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRowLp.topMargin = dp(8);
        btnRow.setLayoutParams(btnRowLp);

        if (t.status == DownloadTask.Status.DOWNLOADING) {
            Button pauseBtn = makeBtn("Pause");
            pauseBtn.setOnClickListener(v -> { t.pause(); refresh(); });
            btnRow.addView(pauseBtn);
            Button cancelBtn = makeBtn("Cancel");
            cancelBtn.setOnClickListener(v -> confirmCancel(t));
            cancelBtn.setPadding(dp(8), 0, 0, 0);
            btnRow.addView(cancelBtn);
        } else if (t.status == DownloadTask.Status.PAUSED) {
            Button resumeBtn = makeBtn("Resume");
            resumeBtn.setOnClickListener(v -> {
                t.resume(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
                refresh();
            });
            btnRow.addView(resumeBtn);
            Button cancelBtn = makeBtn("Cancel");
            cancelBtn.setOnClickListener(v -> confirmCancel(t));
            btnRow.addView(cancelBtn);
        } else if (t.status == DownloadTask.Status.COMPLETED && t.file != null) {
            Button openBtn = makeBtn("Open");
            openBtn.setOnClickListener(v -> {
                Intent i2 = new Intent(Intent.ACTION_VIEW);
                i2.setDataAndType(Uri.fromFile(t.file), "video/*");
                i2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i2); } catch (Exception ex) {
                    Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show(); }
            });
            btnRow.addView(openBtn);
        } else if (t.status == DownloadTask.Status.FAILED) {
            TextView errTv = new TextView(this);
            errTv.setText(t.errorMsg != null ? t.errorMsg : "Failed");
            errTv.setTextSize(11);
            errTv.setTextColor(Color.parseColor("#D32F2F"));
            errTv.setMaxLines(1);
            errTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            errTv.setLayoutParams(errLp);
            btnRow.addView(errTv);
            Button retryBtn = makeBtn("Retry");
            retryBtn.setOnClickListener(v -> {
                t.downloaded.set(0);
                t.start(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
                refresh();
            });
            btnRow.addView(retryBtn);
        }

        root.addView(btnRow);
        return root;
    }

    private Button makeBtn(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setPadding(dp(8), dp(4), dp(8), dp(4));
        b.setBackground(null);
        b.setTextColor(Color.parseColor("#1A73E8"));
        return b;
    }

    private void confirmCancel(DownloadTask t) {
        new AlertDialog.Builder(this)
                .setTitle("Cancel download?")
                .setMessage(t.filename)
                .setPositiveButton("Cancel download", (d, w) -> {
                    BlazeDownloadManager.get().removeTask(t);
                    refresh();
                })
                .setNegativeButton("Keep", null)
                .show();
    }

    private void clearCompleted() {
        List<DownloadTask> tasks = new ArrayList<>(BlazeDownloadManager.get().getTasks());
        for (DownloadTask t : tasks) {
            if (t.status == DownloadTask.Status.COMPLETED
                    || t.status == DownloadTask.Status.FAILED
                    || t.status == DownloadTask.Status.CANCELLED) {
                BlazeDownloadManager.get().removeTask(t);
            }
        }
        refresh();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
