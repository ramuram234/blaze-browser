package app.blaze.browser;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;

public class AdSettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ad_settings);
        ((ImageButton) findViewById(R.id.btn_back)).setOnClickListener(v -> finish());
        Switch swAd         = findViewById(R.id.switch_ad_blocker);
        Switch swPowerful   = findViewById(R.id.switch_powerful_ads);
        Switch swFingerprint= findViewById(R.id.switch_fingerprint);
        swAd.setChecked(BrowserPrefs.adsEnabled(this));
        swPowerful.setChecked(BrowserPrefs.powerfulAds(this));
        swFingerprint.setChecked(BrowserPrefs.antiFingerprint(this));
        swAd.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setAdsEnabled(this, on));
        swPowerful.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setPowerfulAds(this, on));
        swFingerprint.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setAntiFingerprint(this, on));
        Switch swMobileWarn = findViewById(R.id.switch_mobile_warn);
        swMobileWarn.setChecked(BrowserPrefs.warnMobileData(this));
        swMobileWarn.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setWarnMobileData(this, on));
    }
}