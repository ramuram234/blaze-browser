package app.blaze.browser;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class BrowsingSettingsActivity extends AppCompatActivity {
    private static final String[] QUALITY = {"Off (Data saver)", "Medium", "High"};
    private static final long[]   CACHE_MB = {100, 250, 500, 1000};
    private static final String[] CACHE_LABELS = {"100 MB", "250 MB", "500 MB", "1 GB"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browsing_settings);
        ((ImageButton) findViewById(R.id.btn_back)).setOnClickListener(v -> finish());
        wirePreloadRow(findViewById(R.id.label_preload_mode), findViewById(R.id.label_preload_desc));

        Switch swNight   = findViewById(R.id.switch_night);
        Switch swDesktop = findViewById(R.id.switch_desktop);
        Switch swPreload = findViewById(R.id.switch_preload);
        Switch swBoost   = findViewById(R.id.switch_boost);
        Switch swBgAudio = findViewById(R.id.switch_bg_audio);
        Switch swDlBoost = findViewById(R.id.switch_dl_boost);
        TextView lblQ    = findViewById(R.id.label_image_quality);
        TextView lblCache = findViewById(R.id.label_cache_size);

        swNight.setChecked(BrowserPrefs.nightMode(this));
        swDesktop.setChecked(BrowserPrefs.desktop(this));
        swPreload.setChecked(BrowserPrefs.preload(this));
        swBoost.setChecked(BrowserPrefs.boost(this));
        swBgAudio.setChecked(BrowserPrefs.backgroundAudio(this));
        swDlBoost.setChecked(BrowserPrefs.downloadBoost(this));
        lblQ.setText(QUALITY[BrowserPrefs.imageQuality(this)]);
        lblCache.setText(cacheSizeLabel());

        swNight.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setNightMode(this, on));
        swDesktop.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setDesktop(this, on));
        swPreload.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setPreload(this, on));
        swBoost.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setBoost(this, on));
        swBgAudio.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setBackgroundAudio(this, on));
        swDlBoost.setOnCheckedChangeListener((b, on) -> {
            BrowserPrefs.setDownloadBoost(this, on);
            android.widget.Toast.makeText(this,
                    on ? "Download Boost ON — 8 threads + buffer save"
                       : "Download Boost OFF — 4 threads",
                    android.widget.Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.row_image_quality).setOnClickListener(v -> {
            int next = (BrowserPrefs.imageQuality(this) + 1) % 3;
            BrowserPrefs.setImageQuality(this, next);
            lblQ.setText(QUALITY[next]);
        });
        findViewById(R.id.row_cache_size).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Video Buffer Size")
                .setMessage("Sets how much disk space is used to buffer videos "
                    + "while watching. Larger = more videos cached = instant download.")
                .setItems(CACHE_LABELS, (d, i) -> {
                    BrowserPrefs.setVideoCacheMb(this, CACHE_MB[i]);
                    lblCache.setText(CACHE_LABELS[i]);
                    // Re-init cache with new size
                android.widget.Toast.makeText(this,
                            "Buffer size: " + CACHE_LABELS[i], android.widget.Toast.LENGTH_SHORT).show();
                })
                .show()
        );
    }

    private static final String[] P_DESC = {"5s - min data", "5 min - balanced", "15 min - max", "Custom"};
    private static final String[] P_MODE = {"Off", "Normal (5 min)", "Full (15 min)", "Custom"};
    private static final String[] P_DIALOG = {
        "Off — 5 seconds  (strict data saver)",
        "Normal — 5 minutes  [recommended]",
        "Full — 15 minutes  (download while watching)",
        "Custom — pick your own limit"
    };

    private void wirePreloadRow(TextView modeLabel, TextView descLabel) {
        int m = BrowserPrefs.videoPreloadMode(this);
        modeLabel.setText(P_MODE[m]);
        descLabel.setText(P_DESC[m]);
        findViewById(R.id.row_video_preload).setOnClickListener(v ->
            new android.app.AlertDialog.Builder(this)
                .setTitle("Video Preload")
                .setMessage("Controls how much of the video ExoPlayer downloads ahead of what you are watching.\n\nOff = saves data but may buffer/stutter.\nNormal = balanced (recommended).\nFull = smoothest playback but uses most data.")
                .setItems(P_DIALOG, (d, i) -> {
                    if (i == 3) {
                        showCustomPicker(modeLabel, descLabel);
                    } else {
                        BrowserPrefs.setVideoPreloadMode(this, i);
                        updatePreloadUI(modeLabel, descLabel);
                    }
                })
                .show()
        );
    }

    private void updatePreloadUI(TextView modeLabel, TextView descLabel) {
        int m = BrowserPrefs.videoPreloadMode(this);
        if (m == 3) {
            int sec = BrowserPrefs.videoPreloadCustomSec(this);
            modeLabel.setText("Custom (" + fmtSec(sec) + ")");
            descLabel.setText(fmtSec(sec) + " buffer");
        } else {
            String[] descs = {"5s — minimum data", "5 min — balanced", "15 min — download while watching"};
            modeLabel.setText(P_MODE[m]);
            if (m < descs.length) descLabel.setText(descs[m]);
        }
    }

    private void showCustomPicker(TextView modeLabel, TextView descLabel) {
        // Options: 1, 2, 3, 5, 8, 10, 15 minutes
        int[] secs   = {60, 120, 180, 300, 480, 600, 900};
        String[] labels = {"1 minute", "2 minutes", "3 minutes", "5 minutes",
                           "8 minutes", "10 minutes", "15 minutes (max)"};
        new android.app.AlertDialog.Builder(this)
            .setTitle("Custom Buffer Duration")
            .setMessage("ExoPlayer will buffer this much video ahead of your playback position.\n\n"
                + "5 min = good balance between data use and smooth playback.\n"
                + "15 min = downloads most of a movie while you watch.")
            .setItems(labels, (d, i) -> {
                BrowserPrefs.setVideoPreloadMode(this, 3);
                BrowserPrefs.setVideoPreloadCustomSec(this, secs[i]);
                updatePreloadUI(modeLabel, descLabel);
                android.widget.Toast.makeText(this,
                    "Buffer: " + labels[i], android.widget.Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private static String fmtSec(int sec) {
        if (sec < 60) return sec + "s";
        int min = sec / 60;
        int s   = sec % 60;
        return s == 0 ? min + " min" : min + ":" + String.format("%02d", s);
    }

    private String cacheSizeLabel() {
        long mb = BrowserPrefs.videoCacheSize(this) / 1024 / 1024;
        return mb >= 1000 ? (mb / 1000) + " GB" : mb + " MB";
    }
}