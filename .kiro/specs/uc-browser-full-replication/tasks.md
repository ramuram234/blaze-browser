# Implementation Plan: UC Browser Full Replication — Blaze Browser

## Overview

Eight UC Browser features are implemented across existing Java source files and Android XML resources. No new Gradle dependencies are added. All changes are in Java + Android XML. Tasks are ordered so that each step can be compiled and run incrementally. Tasks that add new files (anim XMLs, drawables) come first; logic changes in `MainActivity.java` are staged sequentially because many sub-features modify the same file.

---

## Tasks

- [ ] 1. Create all new resource files (animations, colour token, drawables)
  - [ ] 1.1 Create `res/anim/` directory with six animation XML files
    - `slide_in_right.xml`: `<translate fromXDelta="100%p" toXDelta="0%p" duration="250"/>`
    - `slide_out_left.xml`: `<translate fromXDelta="0%p" toXDelta="-30%p" duration="250"/>`
    - `slide_in_left.xml`: `<translate fromXDelta="-30%p" toXDelta="0%p" duration="250"/>`
    - `slide_out_right.xml`: `<translate fromXDelta="0%p" toXDelta="100%p" duration="250"/>`
    - `zoom_in.xml`: `<scale fromXScale="0.9" toXScale="1.0" fromYScale="0.9" toYScale="1.0" pivotX="50%" pivotY="50%" duration="250"/>`
    - `zoom_out.xml`: `<scale fromXScale="1.0" toXScale="0.9" fromYScale="1.0" toYScale="0.9" pivotX="50%" pivotY="50%" duration="250"/>`
    - All six wrapped in `<set android:interpolator="@android:anim/decelerate_interpolator">`
    - _Requirements: 1.1_

  - [ ] 1.2 Add `uc_orange` colour token to `res/values/colors.xml`
    - Append `<color name="uc_orange">#FF9C38</color>` to `colors.xml`
    - _Requirements: 2.1_

  - [ ] 1.3 Add eight new vector/shape drawable files to `res/drawable/`
    - `ic_lock_green.xml` — vector path for a padlock, fill `#4CAF50`
    - `ic_warning_orange.xml` — vector path for a triangle warning, fill `#FF9C38`
    - `hint_bg.xml` — shape `rectangle` with `cornerRadius="8dp"`, fill `#CC000000`, `padding="12dp"`
    - `overlay_bar_bg.xml` — shape `rectangle` with `cornerRadius="18dp"`, fill `#BF000000`
    - `ic_rotate_portrait.xml` — vector for portrait-lock icon (phone + lock), fill `#FFFFFF`
    - `ic_rotate_landscape.xml` — vector for landscape-lock icon (phone rotated + lock), fill `#FFFFFF`
    - `ic_rotate_auto.xml` — vector for auto-rotate icon (circular arrows), fill `#FFFFFF`
    - `ic_watch_later.xml` — vector for bookmark-add icon (flag + plus), fill `#FFFFFF`
    - _Requirements: 5.1, 6.4, 8.1_

- [ ] 2. Extend BrowserPrefs with all new preference methods
  - [ ] 2.1 Add six new preference method pairs to `BrowserPrefs.java`
    - `animationsEnabled(Context)` / `setAnimationsEnabled(Context, boolean)` — key `"animations"`, default `true`
    - `autoRotateVideo(Context)` / `setAutoRotateVideo(Context, boolean)` — key `"auto_rotate"`, default `false`
    - `hardwareAccel(Context)` / `setHardwareAccel(Context, boolean)` — key `"hw_accel"`, default `true`
    - `fontSizePercent(Context)` / `setFontSizePercent(Context, int)` — key `"font_size"`, clamp `Math.max(75, Math.min(200, value))`, default `100`
    - `swipeNavEnabled(Context)` / `setSwipeNavEnabled(Context, boolean)` — key `"swipe_nav"`, default `true`
    - `blockImages(Context)` / `setBlockImages(Context, boolean)` — key `"block_images"`, default `false`
    - Follow the exact existing pattern (single `prefs(context).getBoolean/getInt` call per getter)
    - _Requirements: 1.5, 7.1_

  - [ ]* 2.2 Write unit tests for the new BrowserPrefs methods
    - In `BrowserPrefsTest.java` (create in `src/test/` alongside existing tests or create the test class if absent)
    - Test `fontSizePercent`: input `50` → stored/returned as `75`; input `250` → `200`; input `100` → `100`
    - Test `animationsEnabled` default is `true` before any write
    - Test `blockImages` round-trip: set `true`, read back `true`; set `false`, read back `false`
    - _Requirements: 7.1_

- [ ] 3. Activity transition animations
  - [ ] 3.1 Add `startWithAnim(Intent)` and `finishWithAnim()` helpers to `MainActivity.java`
    - Private helper `startWithAnim(Intent i)`: calls `startActivity(i)`; if `BrowserPrefs.animationsEnabled(this)` is true, calls `overridePendingTransition(R.anim.slide_in_right, R.anim.zoom_out)`; else `overridePendingTransition(0,0)`
    - Private helper `finishWithAnim()`: calls `finish()`; if animations enabled, calls `overridePendingTransition(R.anim.zoom_in, R.anim.slide_out_right)`; else `overridePendingTransition(0,0)`
    - Replace every existing `startActivity(new Intent(this, SettingsActivity.class))` and equivalent calls in `MainActivity` with `startWithAnim(…)`
    - Replace every `finish()` call in `MainActivity` that returns to the browser with `finishWithAnim()`
    - _Requirements: 1.2, 1.3, 1.4_

  - [ ] 3.2 Apply animation helpers in `SettingsActivity`, `BrowsingSettingsActivity`, and `VideoPlayerActivity`
    - In `SettingsActivity`: override `finish()` replacement — call `super.finish(); overridePendingTransition(R.anim.zoom_in, R.anim.slide_out_right)` in `onBackPressed` equivalent (use `OnBackPressedCallback` or override `finish()`); check `BrowserPrefs.animationsEnabled`
    - In `BrowsingSettingsActivity`: same back-navigation animation
    - In `VideoPlayerActivity`: same back-navigation animation; wrap `startActivity` for sub-screens similarly
    - Add an `animateBack()` helper private method to each Activity to avoid duplicating the conditional logic
    - _Requirements: 1.2, 1.3_

- [ ] 4. Update `activity_main.xml` with address bar icons and video overlay
  - [ ] 4.1 Modify the address bar in `activity_main.xml` for favicon and security indicator
    - Locate the `LinearLayout` (or `RelativeLayout`) that contains the address `EditText`
    - Prepend an `ImageView` with `id="@+id/favicon_view"`, `layout_width="20dp"`, `layout_height="20dp"`, `scaleType="centerCrop"`, `src="@drawable/ic_launcher"`, `layout_marginEnd="4dp"`
    - Between `FaviconView` and `EditText`, insert an `ImageView` with `id="@+id/security_indicator"`, `layout_width="16dp"`, `layout_height="16dp"`, `visibility="gone"`, `layout_marginEnd="4dp"`
    - Set `android:clipChildren="false"` and `android:clipToPadding="false"` on the WebView parent `FrameLayout` (the `FrameLayout` that contains `id/stack`)
    - _Requirements: 6.1, 6.4, 4.5_

  - [ ] 4.2 Add `VideoOverlayBar` to `activity_main.xml`
    - Inside the `FrameLayout` that contains the WebView stack, add a `LinearLayout` with:
      - `id="@+id/video_overlay_bar"`, `orientation="vertical"`, `layout_gravity="top|end"`, `layout_margin="8dp"`, `visibility="gone"`, `elevation="4dp"`, `background="@drawable/overlay_bar_bg"`, `padding="4dp"`
    - Inside it add three `ImageButton`s, each `36dp × 36dp`, `background="?android:attr/selectableItemBackgroundBorderless"`:
      - `id="@+id/btn_overlay_pip"`, `src="@android:drawable/ic_menu_slideshow"`, `contentDescription="Enter PiP"`
      - `id="@+id/btn_overlay_dl"`, `src="@android:drawable/stat_sys_download"`, `contentDescription="Download video"`
      - `id="@+id/btn_overlay_audio"`, `src="@android:drawable/ic_lock_silent_mode_off"`, `contentDescription="Toggle audio"`
    - _Requirements: 8.1_

- [ ] 5. MainActivity — dark chrome and address bar scroll hide
  - [ ] 5.1 Add `applyChrome(boolean night)` to `MainActivity.java`
    - Implement the colour constants described in the design (`0xFF14181E`, `0xFFF7F7F8`, `0xFF1B1B1B`, `0xFF222327`)
    - Call `getWindow().setStatusBarColor(...)` and `getWindow().setNavigationBarColor(...)` (guard with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP` even though minSdk covers it)
    - Set background colour on: `addressBarContainer` (look up by `R.id.address_bar` or the container id), `bottomNav` (bottom bar view), `tabsPanel`
    - Call `applyChrome(BrowserPrefs.nightMode(this))` at the end of `onResume`
    - Also call `applyChrome(on)` inside the night-mode toggle listener wherever `BrowserPrefs.setNightMode` is called
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

  - [ ] 5.2 Add address bar scroll-hide logic to `MainActivity.java`
    - Add fields: `private int lastScrollY = 0`, `private ObjectAnimator barAnimator`, `private boolean barHidden = false`
    - In `BlazeBridge` (existing inner class), add `@JavascriptInterface public void onScroll(int scrollY)` — post to main thread via `runOnUiThread`, compare `scrollY` vs `lastScrollY`, hide bar if down > 100 px, show bar if scroll up, guard with `Math.abs(target - current) < 2` skip, cancel previous animator
    - Compute `barHeightPx` once from `addressBarContainer.getHeight()` (or use `TypedValue.applyDimension(COMPLEX_UNIT_DIP, 52, ...)` as fallback)
    - In the existing page-load JS injection point (where `sniffJs` or fingerprint JS is injected), append the scroll-detection script (defined in the design Component 2)
    - In `WebViewClient.onPageStarted`, add `addressBarContainer.setTranslationY(0); barHidden = false; lastScrollY = 0`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.6_

  - [ ] 5.3 Implement favicon loader and security indicator in `MainActivity.java`
    - Find references to `FaviconView` (`R.id.favicon_view`) and `SecurityIndicator` (`R.id.security_indicator`) in `onCreate`
    - Write private method `loadFavicon(String host)`: spawns a `Thread` that opens `HttpURLConnection` to `https://www.google.com/s2/favicons?domain={host}&sz=32`, sets `connectTimeout(3000)`, `readTimeout(3000)`, decodes the response stream with `BitmapFactory.decodeStream`; on success, posts `faviconView.setImageBitmap(bm)` via `runOnUiThread`; on any exception, does nothing (leaves default icon)
    - In `WebViewClient.onPageFinished`: if URL is not `HOME`, parse `Uri.parse(url).getHost()`, call `loadFavicon(host)`, set `securityIndicator.setVisibility(VISIBLE)`, set drawable to `ic_lock_green` for `https` or `ic_warning_orange` for `http`
    - In `WebViewClient.onPageStarted`: if URL equals `HOME`, set `faviconView.setImageResource(R.drawable.ic_launcher)` and `securityIndicator.setVisibility(GONE)`
    - Set `securityIndicator.setContentDescription(...)` to `"Secure connection"` or `"Insecure connection"` accordingly
    - _Requirements: 6.2, 6.3, 6.5, 6.6, 6.7_

  - [ ] 5.4 Implement `VideoOverlayBar` visibility and button logic in `MainActivity.java`
    - Declare fields: `private LinearLayout videoOverlayBar`, `private boolean audioMuted = false`
    - In `onCreate`, bind `videoOverlayBar = findViewById(R.id.video_overlay_bar)`; bind the three buttons
    - Find the existing locations in the code where `playChip.setVisibility(VISIBLE)` and `playChip.setVisibility(GONE)` are called; mirror those calls with `videoOverlayBar.setVisibility(VISIBLE/GONE)`
    - In `WebViewClient.onPageStarted`: add `videoOverlayBar.setVisibility(View.GONE); audioMuted = false`
    - `btn_overlay_pip` click: check `Build.VERSION.SDK_INT >= 26` AND `getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)`; if true, call `enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build())`; else `Toast.makeText(this,"PiP not supported on this device",Toast.LENGTH_SHORT).show()`
    - `btn_overlay_dl` click: call the same download-trigger logic used by the existing `playChip` download action (extract to a shared private `triggerSniffedDownload()` method if needed)
    - `btn_overlay_audio` click: inject JS `"document.querySelectorAll('video').forEach(function(v){v.muted=!v.muted;});"` into current WebView; toggle `audioMuted`; swap `btn_overlay_audio` image resource between `ic_lock_silent_mode_off` and `ic_lock_silent_mode`
    - _Requirements: 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8_

- [ ] 6. Checkpoint — Core visual features build and run
  - Run `./gradlew assembleDebug` from the `android/` directory. Fix any compilation errors before proceeding.
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. Video player layout additions (`activity_player.xml`)
  - [ ] 7.1 Add `VolumeBrightnessHintView` to `activity_player.xml`
    - Inside the root `FrameLayout` of `activity_player.xml`, add a `LinearLayout` with:
      - `id="@+id/volume_brightness_hint"`, `orientation="vertical"`, `layout_gravity="center"`, `visibility="gone"`, `background="@drawable/hint_bg"`, `gravity="center"`, `padding="16dp"`, `minWidth="120dp"`
    - Inside it:
      - `ImageView id="@+id/hint_icon"`, `layout_width="32dp"`, `layout_height="32dp"`, `tint="#FFFFFFFF"`
      - `ProgressBar id="@+id/hint_progress"`, horizontal style, `layout_width="100dp"`, `layout_height="4dp"`, `max="100"`, `layout_marginTop="8dp"`
    - _Requirements: 5.1_

  - [ ] 7.2 Add rotation and watch-later buttons to the bottom controls bar in `activity_player.xml`
    - Locate the existing bottom controls `LinearLayout` (contains `SeekBar`, play/pause, time labels, speed button)
    - Add `ImageButton id="@+id/btn_rotate"`, `layout_width="36dp"`, `layout_height="36dp"`, `src="@drawable/ic_rotate_auto"`, `background="?android:attr/selectableItemBackgroundBorderless"`, `tint="#FFFFFFFF"`
    - Add `ImageButton id="@+id/btn_watch_later"`, same dimensions, `src="@drawable/ic_watch_later"`, same bg/tint
    - Both positioned to the right of the existing action buttons, before the end margin
    - _Requirements: 5.4, 5.6_

- [ ] 8. VideoPlayerActivity logic
  - [ ] 8.1 Replace text HUD with `VolumeBrightnessHintView` in `VideoPlayerActivity.java`
    - Declare fields: `private LinearLayout volumeBrightnessHint`, `private ImageView hintIcon`, `private ProgressBar hintProgress`
    - In `onCreate`, bind these views; the existing `hint` `TextView` (if present) can be hidden or removed — the `VolumeBrightnessHintView` replaces it
    - Write private method `showHint(boolean isVolume, int value0to100)`:
      - Set `hintIcon` resource to `android.R.drawable.ic_lock_silent_mode_off` for volume or `android.R.drawable.btn_star` for brightness (or use system drawable names; replace with better icons if available)
      - Set `hintProgress.setProgress(value0to100)`
      - Set `volumeBrightnessHint.setVisibility(VISIBLE)`
      - Cancel any pending hide callback; post `hideHintRunnable` with 1 000 ms delay via `handler.postDelayed`
    - In the existing vertical-swipe gesture handling (the code blocks that call `AudioManager.setStreamVolume` and `Settings.System.putInt(SCREEN_BRIGHTNESS)`), replace the `hint.setText(...)` / `hint.setVisibility(VISIBLE)` calls with `showHint(isVolumeSwipe, currentValueAs0to100)`
    - `hideHintRunnable`: sets `volumeBrightnessHint.setVisibility(GONE)`
    - _Requirements: 5.1, 5.2, 5.3_

  - [ ] 8.2 Implement rotation button cycling in `VideoPlayerActivity.java`
    - Declare `private static final int[] ORIENTATIONS = { ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_SENSOR }`
    - Declare `private static final int[] ROTATE_ICONS = { R.drawable.ic_rotate_portrait, R.drawable.ic_rotate_landscape, R.drawable.ic_rotate_auto }`
    - Declare `private int rotateIndex = 2` (start at SENSOR / auto)
    - In `onCreate`, bind `btn_rotate`; set initial icon to `ROTATE_ICONS[rotateIndex]`
    - `btn_rotate` click: `rotateIndex = (rotateIndex + 1) % 3`; `setRequestedOrientation(ORIENTATIONS[rotateIndex])`; `btnRotate.setImageResource(ROTATE_ICONS[rotateIndex])`
    - _Requirements: 5.4, 5.5_

  - [ ] 8.3 Implement `WatchLaterButton` in `VideoPlayerActivity.java`
    - In `onCreate`, bind `btn_watch_later`
    - `btn_watch_later` click:
      - Retrieve `String pageUrl = getIntent().getStringExtra("page_url")`; if null, use the first video URL from `getIntent().getStringArrayListExtra(EXTRA_URLS)`
      - Retrieve `String title = getIntent().getStringExtra(EXTRA_TITLE)`; default to `"Saved video"` if null
      - Call `BrowserPrefs.toggleBookmark(this, title, pageUrl)`
      - Show `Toast`: `"Saved to bookmarks"` if `BrowserPrefs.isBookmarked(this, pageUrl)` is now true, else `"Removed from bookmarks"`
    - _Requirements: 5.6, 5.7_

  - [ ] 8.4 Fix `SeekBar` seek-touch lock in `VideoPlayerActivity.java`
    - Locate the `SeekBar.OnSeekBarChangeListener` set on `seekBar`
    - In `onStartTrackingTouch`: set `seeking = true` (field already declared in the class)
    - In `onStopTrackingTouch`: call `player.seekTo((long) seekBar.getProgress() * 1000L)` (convert seconds to ms if seekBar progress is in seconds, matching the existing tick logic), then set `seeking = false`
    - Confirm that the existing progress-ticker `Runnable` (the one calling `seekBar.setProgress(...)`) is already guarded by `if (!seeking)` — if not, add that guard
    - _Requirements: 5.8, 5.9_

  - [ ]* 8.5 Write unit test for rotation state cycling
    - Create `VideoPlayerRotationTest.java` in `src/test/`
    - Test that cycling `rotateIndex` three times returns to the original value: `(0+1+1+1) % 3 == 0`
    - Test that all three `ORIENTATIONS` values are distinct
    - Use plain JUnit 4 (no Android instrumentation needed for this pure-logic test)
    - _Requirements: 5.5_

- [ ] 9. Enhanced settings — layout
  - [ ] 9.1 Add five new rows to `activity_browsing_settings.xml`
    - Add a `Switch` row with `id="@+id/switch_auto_rotate"` and label `"Auto-rotate in video player"` — follow the exact pattern of existing `switch_night` rows
    - Add a `Switch` row with `id="@+id/switch_hw_accel"` and label `"Hardware Acceleration"`
    - Add a font-size `SeekBar` row: wrapping `LinearLayout` with `id="@+id/row_font_size"`, label `"Font Size"`, `SeekBar id="@+id/seek_font_size"` (`android:min="75"` on API ≥ 26; set `progress = stored - 75` on older API), and `TextView id="@+id/label_font_size"` showing current `%`
    - Add a `Switch` row with `id="@+id/switch_swipe_nav"` and label `"Swipe Forward / Back"`
    - Add a `Switch` row with `id="@+id/switch_block_images"` and label `"Block Images (Data Saver)"`
    - _Requirements: 7.2, 7.3, 7.4, 7.5, 7.6_

  - [ ] 9.2 Wire new rows in `BrowsingSettingsActivity.java`
    - Bind all five new views in `onCreate`
    - Read initial states from `BrowserPrefs` and set `Switch.setChecked(...)` / `SeekBar.setProgress(...)`
    - Set `switch_auto_rotate` listener → `BrowserPrefs.setAutoRotateVideo(this, on)` (consumed by `VideoPlayerActivity` on next launch)
    - Set `switch_hw_accel` listener → `BrowserPrefs.setHardwareAccel(this, on)` + `Toast.makeText(this, "Restart browser for hardware acceleration change", Toast.LENGTH_SHORT).show()`
    - Set `seek_font_size` `onProgressChanged` → update `label_font_size` live; on `onStopTrackingTouch` → `BrowserPrefs.setFontSizePercent(this, progress + 75)` (add 75 offset if needed); set `label_font_size.setText(value + "%")`
    - Set `switch_swipe_nav` listener → `BrowserPrefs.setSwipeNavEnabled(this, on)`
    - Set `switch_block_images` listener → `BrowserPrefs.setBlockImages(this, on)`
    - Set the Activity result to `RESULT_OK` in `onPause` or on each setting change so `MainActivity.onActivityResult` can re-apply settings
    - _Requirements: 7.2, 7.3, 7.4, 7.5, 7.6_

- [ ] 10. Apply new settings in MainActivity and add data-management actions
  - [ ] 10.1 Add "Clear Cookies" and "Clear Form Data" rows to `SettingsActivity.java`
    - In `onCreate`, add two new `addRow` calls before the existing `"Clear Records"` row:
      - `"Clear Cookies"` → `CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush(); Toast.makeText(this,"Cookies cleared",Toast.LENGTH_SHORT).show()`
      - `"Clear Form Data"` → `setResult(200); finish()` where `200` is a constant `RESULT_CLEAR_FORM_DATA = 200` defined in the class; play back animation via `animateBack()`
    - Ensure `SettingsActivity` is started via `startActivityForResult(intent, REQUEST_SETTINGS)` in `MainActivity` (see task 10.2)
    - _Requirements: 7.10, 7.11_

  - [ ] 10.2 Add `applyWebSettings(WebView)` to `MainActivity.java`; wire settings results
    - Add private method `applyWebSettings(WebView wv)`:
      ```java
      WebSettings s = wv.getSettings();
      s.setTextZoom(BrowserPrefs.fontSizePercent(this));
      boolean block = BrowserPrefs.blockImages(this);
      s.setBlockNetworkImage(block);
      s.setLoadsImagesAutomatically(!block);
      if (!BrowserPrefs.hardwareAccel(this))
          wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
      else
          wv.setLayerType(View.LAYER_TYPE_HARDWARE, null);
      ```
    - Call `applyWebSettings(wv)` in the existing method that creates/initialises a new `WebView` tab, after all other `WebSettings` setup
    - Change the call to `startActivity(new Intent(this, SettingsActivity.class))` to `startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SETTINGS)` (declare `private static final int REQUEST_SETTINGS = 101`)
    - Override `onActivityResult(int requestCode, int resultCode, Intent data)`:
      - If `requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK`: iterate `tabs` list, call `applyWebSettings(tab)` on each
      - If `resultCode == 200` (RESULT_CLEAR_FORM_DATA): iterate `tabs`, call `tab.clearFormData()`; show `Toast.makeText(this,"Form data cleared",Toast.LENGTH_SHORT).show()`
    - _Requirements: 7.7, 7.8, 7.9, 7.11_

- [ ] 11. Update DownloadProgressRing and VideoSniffBar to use uc_orange
  - [ ] 11.1 Update `DownloadProgressRing.java` to use `uc_orange` colour resource
    - In `DownloadProgressRing.java`, find where the arc `Paint` colour is hard-coded (look for `setColor(0xFF…)` or a colour constant)
    - Replace it with `getResources().getColor(R.color.uc_orange, getContext().getTheme())` stored in a field initialised in `init()` or the constructor
    - _Requirements: 2.2, 2.5_

  - [ ] 11.2 Update `VideoSniffBar` button tint and download speed badge in `MainActivity.java`
    - Find where `playChip` action buttons (the play/download icons shown alongside the sniff bar chip) have their background tint set — update those calls to `ContextCompat.getColor(this, R.color.uc_orange)`
    - Find the `TextView` or `View` that displays the download speed (the badge in the downloads list — likely in `DownloadsActivity.java` or in the download notification row layout) — set its text colour to `R.color.uc_orange`
    - If the speed badge is inside `activity_downloads.xml` or a row layout, add `android:textColor="@color/uc_orange"` to that `TextView`
    - _Requirements: 2.3, 2.4_

- [ ] 12. Final checkpoint — Full build verification
  - Run `./gradlew assembleDebug` from `android/` and confirm zero compilation errors.
  - Ensure all tests pass, ask the user if questions arise.

---

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster build — they are unit tests that validate logic but are not required for the APK to compile.
- Tasks are structured so that every task except the checkpoints writes directly runnable, compilable code. No task leaves orphan methods or layout IDs unreferenced.
- All `MainActivity.java` changes are strictly sequential (5.1 → 5.2 → 5.3 → 5.4 → 10.2 → 11.2) because they all write to the same file.
- `VideoPlayerActivity.java` changes are similarly sequential (8.1 → 8.2 → 8.3 → 8.4).
- The `DownloadsActivity.java` speed badge change in task 11.2 is a best-effort search — if the speed badge is rendered differently (e.g., set programmatically in `FastDownloader.java`), update the colour at the point of `setText` call instead.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "2.1", "4.1", "7.1", "9.1"] },
    { "id": 1, "tasks": ["2.2", "3.1", "4.2", "7.2", "9.2"] },
    { "id": 2, "tasks": ["3.2", "5.1"] },
    { "id": 3, "tasks": ["5.2", "8.1"] },
    { "id": 4, "tasks": ["5.3", "8.2"] },
    { "id": 5, "tasks": ["5.4", "8.3", "8.5"] },
    { "id": 6, "tasks": ["8.4", "10.1"] },
    { "id": 7, "tasks": ["10.2", "11.1"] },
    { "id": 8, "tasks": ["11.2"] }
  ]
}
```
