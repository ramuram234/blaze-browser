package app.blaze.browser;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * UC Browser-style Settings screen.
 * 
 * Matches UC Browser fragment_setting.xml structure:
 *   setting_head_general -> General section
 *   set_as_default       -> Set as default browser
 *   sound_enhance        -> Sound Enhancement (play settings)
 *   auto_sleep           -> Auto Sleep after video
 *   wifi_only            -> Download on WiFi only
 *   play_setting         -> Play Settings (-> BrowsingSettingsActivity)
 *   feedbacks            -> Feedback
 *   setting_create_shortcut -> Create shortcut
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ImageButton back = (ImageButton) findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> {
            if (BrowserPrefs.animationsEnabled(this))
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            finish();
        });

        // UC Browser: set_as_default - Set as default browser
        View setDefault = findViewById(R.id.set_as_default);
        if (setDefault != null) setDefault.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
            try { startActivity(intent); }
            catch (Exception e) { Toast.makeText(this, "Open System Settings > Apps > Default Apps", Toast.LENGTH_LONG).show(); }
        });

        // Ad Block Settings
        View adSettings = findViewById(R.id.item_ad_settings);
        if (adSettings != null) adSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, AdSettingsActivity.class));
            if (BrowserPrefs.animationsEnabled(this))
                overridePendingTransition(R.anim.slide_in_right, R.anim.zoom_out);
        });

        // Browsing Settings
        View browsingSettings = findViewById(R.id.item_browsing_settings);
        if (browsingSettings != null) browsingSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, BrowsingSettingsActivity.class));
            if (BrowserPrefs.animationsEnabled(this))
                overridePendingTransition(R.anim.slide_in_right, R.anim.zoom_out);
        });

        // UC Browser: sound_enhance - Sound Enhancement
        View soundEnhance = findViewById(R.id.sound_enhance);
        if (soundEnhance != null) soundEnhance.setOnClickListener(v -> {
            boolean on = !BrowserPrefs.boost(this);
            BrowserPrefs.setBoost(this, on);
            TextView txt = (TextView) soundEnhance.findViewById(R.id.sound_enhance_text);
            if (txt != null) txt.setText("Sound Enhancement: " + (on ? "ON" : "OFF"));
            Toast.makeText(this, "Sound Enhancement: " + (on ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        });
        // Init label
        View seTv = findViewById(R.id.sound_enhance_text);
        if (seTv instanceof TextView) ((TextView)seTv).setText("Sound Enhancement: " + (BrowserPrefs.boost(this) ? "ON" : "OFF"));

        // UC Browser: auto_sleep - Auto Sleep
        View autoSleep = findViewById(R.id.auto_sleep);
        if (autoSleep != null) autoSleep.setOnClickListener(v -> {
            String[] options = {"Off", "10 min", "20 min", "30 min", "60 min"};
            new AlertDialog.Builder(this).setTitle("Auto Sleep After Video")
                .setItems(options, (d, which) -> {
                    TextView count = (TextView) autoSleep.findViewById(R.id.auto_sleep_count);
                    if (count != null) count.setText(options[which]);
                    Toast.makeText(this, "Auto sleep: " + options[which], Toast.LENGTH_SHORT).show();
                }).show();
        });

        // UC Browser: wifi_only - WiFi only download
        Switch wifiSwitch = (Switch) findViewById(R.id.wifi_only_toggle);
        if (wifiSwitch != null) {
            wifiSwitch.setChecked(BrowserPrefs.downloadOnWifiOnly(this));
            wifiSwitch.setOnCheckedChangeListener((b, on) -> {
                BrowserPrefs.setDownloadOnWifiOnly(this, on);
                Toast.makeText(this, on ? "Downloads on WiFi only" : "Downloads on any network", Toast.LENGTH_SHORT).show();
            });
        }

        // UC Browser: play_setting - Play Settings -> BrowsingSettingsActivity
        View playSetting = findViewById(R.id.play_setting);
        if (playSetting != null) playSetting.setOnClickListener(v -> {
            startActivity(new Intent(this, BrowsingSettingsActivity.class));
            if (BrowserPrefs.animationsEnabled(this))
                overridePendingTransition(R.anim.slide_in_right, R.anim.zoom_out);
        });

        // Download Settings
        View downloadSettings = findViewById(R.id.item_download_settings);
        if (downloadSettings != null) downloadSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, BrowsingSettingsActivity.class));
            if (BrowserPrefs.animationsEnabled(this))
                overridePendingTransition(R.anim.slide_in_right, R.anim.zoom_out);
        });

        // Clear Records
        View clearRecords = findViewById(R.id.item_clear_records);
        if (clearRecords != null) clearRecords.setOnClickListener(v ->
            new AlertDialog.Builder(this).setTitle("Clear Records")
                .setMessage("This will clear your browsing history and bookmarks.")
                .setPositiveButton("Clear", (d,w) -> {
                    BrowserPrefs.clearAll(this);
                    Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null).show()
        );

        // Clear Cookies
        View clearCookies = findViewById(R.id.item_clear_cookies);
        if (clearCookies != null) clearCookies.setOnClickListener(v -> {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            Toast.makeText(this, "Cookies cleared", Toast.LENGTH_SHORT).show();
        });

        // UC Browser: feedbacks - Feedback
        View feedback = findViewById(R.id.feedbacks);
        if (feedback != null) feedback.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ramuram234/blaze-browser/issues"))); }
            catch (Exception e) { Toast.makeText(this, "Cannot open feedback", Toast.LENGTH_SHORT).show(); }
        });

        // UC Browser: setting_create_shortcut - Create desktop shortcut
        View createShortcut = findViewById(R.id.setting_create_shortcut);
        if (createShortcut != null) createShortcut.setOnClickListener(v -> {
            try {
                Intent shortcutIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ramuram234/blaze-browser"));
                shortcutIntent.setClassName(this, MainActivity.class.getName());
                Intent addShortcut = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
                addShortcut.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
                addShortcut.putExtra(Intent.EXTRA_SHORTCUT_NAME, "Blaze Browser");
                addShortcut.putExtra("duplicate", false);
                sendBroadcast(addShortcut);
                Toast.makeText(this, "Shortcut added to home screen", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Cannot create shortcut", Toast.LENGTH_SHORT).show();
            }
        });

        // About
        View about = findViewById(R.id.item_about);
        if (about != null) about.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Blaze Browser")
                .setMessage("Version 4.0\nFast, private browser\nBuilt from UC Browser source analysis\nAd blocking + Anti-fingerprinting")
                .setPositiveButton("OK", null).show()
        );

        // Update version label
        TextView versionTv = (TextView) findViewById(R.id.about_version);
        if (versionTv != null) versionTv.setText("v4.0");
    }
}
