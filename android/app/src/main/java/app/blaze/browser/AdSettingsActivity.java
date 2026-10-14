package app.blaze.browser;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * UC Browser AdBlockerSettingWindow equivalent.
 * 
 * UC Browser settings:
 *   EnableAdBlock       -> switch_ad_blocker
 *   EnablePowerFulADBlock -> switch_powerful_ads
 *   enable_eyeo_acceptable_rule -> switch_eyeo (AdBlock Plus)
 *   Anti-fingerprint     -> switch_fingerprint
 *   Mobile data warning  -> switch_mobile_warn
 *   Block 3rd party cookies -> switch_block_3p
 */
public class AdSettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ad_settings);
        ImageButton back = (ImageButton) findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> {
            if (BrowserPrefs.animationsEnabled(this))
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            finish();
        });

        // UC Browser: EnableAdBlock
        Switch swAd = (Switch) findViewById(R.id.switch_ad_blocker);
        if (swAd != null) {
            swAd.setChecked(BrowserPrefs.adsEnabled(this));
            swAd.setOnCheckedChangeListener((b, on) -> {
                BrowserPrefs.setAdsEnabled(this, on);
                Toast.makeText(this, on ? "Ad Blocker ON" : "Ad Blocker OFF", Toast.LENGTH_SHORT).show();
            });
        }

        // UC Browser: EnablePowerFulADBlock
        Switch swPowerful = (Switch) findViewById(R.id.switch_powerful_ads);
        if (swPowerful != null) {
            swPowerful.setChecked(BrowserPrefs.powerfulAds(this));
            swPowerful.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setPowerfulAds(this, on));
        }

        // UC Browser: enable_eyeo_acceptable_rule (AdBlock Plus acceptable ads)
        Switch swEyeo = (Switch) findViewById(R.id.switch_eyeo);
        if (swEyeo != null) {
            swEyeo.setChecked(BrowserPrefs.acceptableAds(this));
            swEyeo.setOnCheckedChangeListener((b, on) -> {
                BrowserPrefs.setAcceptableAds(this, on);
                Toast.makeText(this, on ? "Acceptable Ads ON (less aggressive blocking)" : "Acceptable Ads OFF", Toast.LENGTH_SHORT).show();
            });
        }

        // Anti-fingerprinting
        Switch swFingerprint = (Switch) findViewById(R.id.switch_fingerprint);
        if (swFingerprint != null) {
            swFingerprint.setChecked(BrowserPrefs.antiFingerprint(this));
            swFingerprint.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setAntiFingerprint(this, on));
        }

        // Mobile data warning
        Switch swMobileWarn = (Switch) findViewById(R.id.switch_mobile_warn);
        if (swMobileWarn != null) {
            swMobileWarn.setChecked(BrowserPrefs.warnMobileData(this));
            swMobileWarn.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setWarnMobileData(this, on));
        }

        // Block 3rd party cookies
        Switch swBlock3p = (Switch) findViewById(R.id.switch_block_3p);
        if (swBlock3p != null) {
            swBlock3p.setChecked(BrowserPrefs.blockThirdPartyCookies(this));
            swBlock3p.setOnCheckedChangeListener((b, on) -> BrowserPrefs.setBlockThirdPartyCookies(this, on));
        }
    }
}
