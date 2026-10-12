package app.blaze.browser;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ((ImageButton) findViewById(R.id.btn_back)).setOnClickListener(v -> finish());
        LinearLayout list = findViewById(R.id.settings_list);
        addRow(list, "Ad Block Setting",    () -> startActivity(new Intent(this, AdSettingsActivity.class)));
        addRow(list, "Browsing Settings",   () -> startActivity(new Intent(this, BrowsingSettingsActivity.class)));
        addRow(list, "Download Settings",   () -> {});
        addRow(list, "Notification Settings", () -> {});
        addRow(list, "Search Settings",     () -> {});
        addRow(list, "Themes",              () -> {});
        addRow(list, "Clear Records", () -> {
            BrowserPrefs.clearAll(this);
            Toast.makeText(this, "History and bookmarks cleared", Toast.LENGTH_SHORT).show();
        });
        addRow(list, "About Blaze", () ->
            new AlertDialog.Builder(this)
                .setTitle("Blaze Browser")
                .setMessage("Version 1.5\nPrivacy-focused browser\nExpanded tracker blocking + anti-fingerprinting")
                .setPositiveButton("OK", null).show()
        );
    }

    private void addRow(LinearLayout list, String label, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setMinimumHeight(dp(64));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), 0, dp(20), 0);
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        TextView lbl = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lbl.setLayoutParams(lp);
        lbl.setText(label);
        lbl.setTextSize(15);
        lbl.setTextColor(0xFF16181C);
        row.addView(lbl);
        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextSize(22);
        chevron.setTextColor(0xFF9AA0A6);
        row.addView(chevron);
        row.setOnClickListener(v -> action.run());
        list.addView(row);
        View divider = new View(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dlp.setMarginStart(dp(20));
        divider.setLayoutParams(dlp);
        divider.setBackgroundColor(0xFFEBEBEB);
        list.addView(divider);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}