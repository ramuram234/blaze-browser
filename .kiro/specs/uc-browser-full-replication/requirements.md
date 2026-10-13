# Requirements Document

## Introduction

This document specifies the requirements for replicating eight UC Browser features in the Blaze Browser Android application. The source of truth for each feature is the decompiled UC Browser APK (35,000 files analysed). All implementation targets Java + Android XML with no new runtime dependencies beyond those already declared in `build.gradle` (Media3 ExoPlayer, AndroidX AppCompat).

The eight feature areas are:
1. Activity slide animations (UC `AnimationIsOpen` / `u4_slide_*` transitions)
2. UC Browser orange colour scheme applied to existing UI widgets
3. Programmatic dark mode for browser chrome (status bar, nav bar, address bar, tab panel)
4. Address bar hide-on-scroll behaviour (`KEY_SCROLL_OPT`)
5. Video player improvements (VolumeBrightnessHintView, rotation button, watch-later, seek-touch lock)
6. Favicon and HTTPS/HTTP security indicator in the address bar
7. Enhanced settings matching UC Browser setting keys
8. In-page video controls overlay (PiP, download, audio-only) when a video is sniffed

---

## Glossary

- **AnimationController**: The logic within `MainActivity` that calls `overridePendingTransition()` and applies `ObjectAnimator` transitions between activities.
- **AddressBarController**: The scroll-offset logic within `MainActivity` that shows and hides the address bar in response to WebView scroll events.
- **BlazeBridge**: The `@JavascriptInterface`-annotated inner class in `MainActivity` that receives calls from the injected JavaScript.
- **BrowserPrefs**: The `SharedPreferences` façade class at `app.blaze.browser.BrowserPrefs`.
- **DownloadProgressRing**: The `DownloadProgressRing` custom view in `app.blaze.browser`.
- **SecurityIndicator**: The `ImageView` placed before the address `EditText` inside the address-bar layout that shows lock / warning icons.
- **VideoOverlayBar**: A `LinearLayout` of three circle `ImageButton` views (PiP, download, audio-only) anchored to the top-right of the WebView `FrameLayout`.
- **VideoSniffBar**: The existing `playChip` `TextView` / sniff bar in `MainActivity` that appears when a video URL is detected.
- **VolumeBrightnessHintView**: A centred overlay `LinearLayout` (icon + horizontal `ProgressBar`) shown during vertical swipe gestures in `VideoPlayerActivity`.
- **WatchLaterButton**: An `ImageButton` in `VideoPlayerActivity`'s bottom controls bar that saves the current video URL to bookmarks.

---

## Requirements

### Requirement 1: Activity Slide Animations

**User Story:** As a user, I want smooth slide and zoom transitions between screens, so that the app feels polished and consistent with UC Browser.

#### Acceptance Criteria

1. THE `AnimationController` SHALL define six animation resource files: `anim/slide_in_right.xml`, `anim/slide_out_left.xml`, `anim/slide_in_left.xml`, `anim/slide_out_right.xml`, `anim/zoom_in.xml`, and `anim/zoom_out.xml`, each with a duration of 250 ms.
2. WHEN an `Activity` is started from `MainActivity` or `SettingsActivity`, THE `AnimationController` SHALL call `overridePendingTransition(slide_in_right, zoom_out)` immediately after `startActivity()`.
3. WHEN an `Activity` is finished and returns to `MainActivity`, THE `AnimationController` SHALL call `overridePendingTransition(zoom_in, slide_out_right)` immediately after `finish()`.
4. WHERE the `AnimationIsOpen` preference is `false`, THE `AnimationController` SHALL call `overridePendingTransition(0, 0)` to suppress all transitions.
5. THE `BrowserPrefs` SHALL expose `animationsEnabled(Context)` and `setAnimationsEnabled(Context, boolean)` methods backed by the `"animations"` SharedPreferences key, defaulting to `true`.

---

### Requirement 2: UC Browser Orange Colour Scheme

**User Story:** As a user, I want the browser's accent colour to match UC Browser's brand orange, so that download and media UI elements have a consistent visual identity.

#### Acceptance Criteria

1. THE `colors.xml` SHALL define a colour resource named `uc_orange` with value `#FF9C38`.
2. WHEN a download is in progress, THE `DownloadProgressRing` SHALL render its arc using the `uc_orange` colour resource rather than any hard-coded colour.
3. WHEN a video is sniffed and the `VideoSniffBar` is visible, THE `VideoSniffBar` action buttons SHALL use `uc_orange` as their background tint.
4. WHEN a download speed badge is displayed in the downloads list, THE `DownloadSpeedBadge` text SHALL be rendered in `uc_orange`.
5. THE `DownloadProgressRing` SHALL read its arc colour from `R.color.uc_orange` at construction time so that theme changes take effect without code modification.

---

### Requirement 3: Native Dark Mode for Browser Chrome

**User Story:** As a user, I want the browser UI chrome (status bar, navigation bar, address bar, tab panel) to go dark when Night Mode is enabled, so that browsing in low-light conditions is comfortable.

#### Acceptance Criteria

1. WHEN `BrowserPrefs.nightMode(context)` returns `true`, THE `MainActivity` SHALL set the status bar background colour to `#14181E` using `Window.setStatusBarColor()`.
2. WHEN `BrowserPrefs.nightMode(context)` returns `true`, THE `MainActivity` SHALL set the system navigation bar background colour to `#14181E` using `Window.setNavigationBarColor()`.
3. WHEN `BrowserPrefs.nightMode(context)` returns `true`, THE `MainActivity` SHALL set the address bar container background to `#1B1B1B`.
4. WHEN `BrowserPrefs.nightMode(context)` returns `true`, THE `MainActivity` SHALL set the bottom navigation bar background to `#1B1B1B`.
5. WHEN `BrowserPrefs.nightMode(context)` returns `true`, THE `MainActivity` SHALL set the tab list panel (`tabsPanel`) background to `#14181E`.
6. WHEN `BrowserPrefs.nightMode(context)` returns `false`, THE `MainActivity` SHALL restore each of those surfaces to their default day-mode colours (`#FFFFFF` for bars, `#F7F7F8` for nav).
7. THE dark mode colour application SHALL occur inside a `applyChrome(boolean night)` method called both in `onResume` and when the Night Mode toggle changes.

---

### Requirement 4: Address Bar Hide-on-Scroll

**User Story:** As a user, I want the address bar to hide when I scroll down a page and reappear when I scroll up, so that more of the page content is visible while reading.

#### Acceptance Criteria

1. THE `AddressBarController` SHALL inject JavaScript into every page that calls `window.BlazeBridge.onScroll(scrollY)` on the `scroll` event, throttled so that the call fires at most once per 50 px of scroll delta.
2. WHEN `BlazeBridge.onScroll` receives a `scrollY` value more than 100 px greater than the last recorded position, THE `AddressBarController` SHALL animate the address bar container `translationY` to `-52dp` over 200 ms using `ObjectAnimator`.
3. WHEN `BlazeBridge.onScroll` receives a `scrollY` value less than the last recorded position (scroll up), THE `AddressBarController` SHALL animate the address bar container `translationY` to `0dp` over 200 ms using `ObjectAnimator`.
4. WHEN a new page load begins (`onPageStarted`), THE `AddressBarController` SHALL immediately set the address bar container `translationY` to `0dp` without animation.
5. THE WebView's parent `FrameLayout` SHALL use `clipChildren = false` so that the WebView content fills the space vacated by the hidden address bar without a layout gap.
6. IF the address bar is already in the target translation state, THEN THE `AddressBarController` SHALL skip the `ObjectAnimator` call to avoid redundant animation.

---

### Requirement 5: Video Player Improvements

**User Story:** As a user, I want an improved video player with a brightness/volume HUD, rotation controls, watch-later bookmarking, and a non-jumpy seek bar, so that video playback matches the UC Browser player experience.

#### Acceptance Criteria

1. THE `VideoPlayerActivity` layout SHALL include a `VolumeBrightnessHintView` — a centred `LinearLayout` containing an `ImageView` (icon) and a horizontal `ProgressBar` (range 0–100) — that is `GONE` by default.
2. WHEN a vertical swipe gesture is detected in `VideoPlayerActivity`, THE `VideoPlayerActivity` SHALL set the `VolumeBrightnessHintView` to `VISIBLE` and update the `ProgressBar` progress to reflect the current volume (right half of screen) or brightness (left half of screen) as a value 0–100.
3. WHEN a vertical swipe gesture ends, THE `VideoPlayerActivity` SHALL hide the `VolumeBrightnessHintView` after a 1 000 ms delay using `Handler.postDelayed`.
4. THE `VideoPlayerActivity` bottom controls bar SHALL include a rotation `ImageButton` (`btn_rotate`) that is always visible alongside existing controls.
5. WHEN `btn_rotate` is tapped, THE `VideoPlayerActivity` SHALL cycle through three rotation states in order: portrait-locked (`SCREEN_ORIENTATION_PORTRAIT`), landscape-locked (`SCREEN_ORIENTATION_LANDSCAPE`), and sensor-driven (`SCREEN_ORIENTATION_SENSOR`) — updating the button icon for each state.
6. THE `VideoPlayerActivity` bottom controls bar SHALL include a `WatchLaterButton` (`ImageButton`) that saves the current video page URL to `BrowserPrefs` bookmarks.
7. WHEN the `WatchLaterButton` is tapped, THE `VideoPlayerActivity` SHALL call `BrowserPrefs.toggleBookmark(context, title, url)` with the page URL passed via the launch `Intent` extra `"page_url"`, and SHALL display a `Toast` confirming the action.
8. WHEN the user begins touching the `SeekBar`, THE `VideoPlayerActivity` SHALL set a `seeking` flag to `true` and stop updating the `SeekBar` thumb position from the player progress ticker.
9. WHEN `SeekBar.onStopTrackingTouch` fires, THE `VideoPlayerActivity` SHALL call `player.seekTo(progress)` and reset the `seeking` flag to `false`.

---

### Requirement 6: Favicon and Security Indicator in Address Bar

**User Story:** As a user, I want to see the site's favicon and a security icon in the address bar, so that I can quickly identify the current site and know whether my connection is secure.

#### Acceptance Criteria

1. THE address bar layout SHALL include a `FaviconView` (`ImageView`, 20 × 20 dp) positioned to the left of the `EditText`, initialised with the Blaze "B" logo drawable.
2. WHEN a page finishes loading and the URL is not the home page (`file:///android_asset/www/index.html`), THE `MainActivity` SHALL asynchronously load the favicon from `https://www.google.com/s2/favicons?domain={host}&sz=32` using `WebView.loadUrl` with a data URI approach or a background `HttpURLConnection`, and set the result on `FaviconView`.
3. IF favicon loading fails or times out after 3 000 ms, THEN THE `MainActivity` SHALL leave the `FaviconView` displaying the default Blaze "B" logo drawable without showing an error to the user.
4. THE address bar layout SHALL include a `SecurityIndicator` (`ImageView`, 16 × 16 dp) positioned between the `FaviconView` and the `EditText`.
5. WHEN the current URL scheme is `https`, THE `SecurityIndicator` SHALL display a green lock drawable and its content description SHALL be `"Secure connection"`.
6. WHEN the current URL scheme is `http`, THE `SecurityIndicator` SHALL display an orange warning drawable and its content description SHALL be `"Insecure connection"`.
7. WHEN the user navigates to the home page, THE `MainActivity` SHALL restore the `FaviconView` to the default Blaze "B" logo drawable and hide the `SecurityIndicator`.

---

### Requirement 7: Enhanced Settings

**User Story:** As a user, I want additional browser settings matching UC Browser's feature set, so that I can customise rotation, hardware acceleration, font size, swipe navigation, image blocking, and data cleanup.

#### Acceptance Criteria

1. THE `BrowserPrefs` SHALL expose the following new preference methods: `autoRotateVideo(Context)` / `setAutoRotateVideo(Context, boolean)` (key `"auto_rotate"`, default `false`); `hardwareAccel(Context)` / `setHardwareAccel(Context, boolean)` (key `"hw_accel"`, default `true`); `fontSizePercent(Context)` / `setFontSizePercent(Context, int)` (key `"font_size"`, range 75–200, default 100); `swipeNavEnabled(Context)` / `setSwipeNavEnabled(Context, boolean)` (key `"swipe_nav"`, default `true`); `blockImages(Context)` / `setBlockImages(Context, boolean)` (key `"block_images"`, default `false`).
2. THE `BrowsingSettingsActivity` SHALL display a labelled toggle row for "Auto-rotate in video player" wired to `BrowserPrefs.autoRotateVideo`.
3. THE `BrowsingSettingsActivity` SHALL display a labelled toggle row for "Hardware Acceleration" wired to `BrowserPrefs.hardwareAccel`.
4. THE `BrowsingSettingsActivity` SHALL display a labelled `SeekBar` row for "Font Size" with a range of 75 to 200 (representing percentage) and a live percentage label; tapping the row SHALL open the `SeekBar` picker.
5. THE `BrowsingSettingsActivity` SHALL display a labelled toggle row for "Swipe Forward / Back" wired to `BrowserPrefs.swipeNavEnabled`.
6. THE `BrowsingSettingsActivity` SHALL display a labelled toggle row for "Block Images (Data Saver)" wired to `BrowserPrefs.blockImages`.
7. WHEN `BrowserPrefs.blockImages` is `true`, THE `MainActivity` SHALL call `WebSettings.setBlockNetworkImage(true)` and `WebSettings.setLoadsImagesAutomatically(false)` on each WebView at creation time and when the setting changes.
8. WHEN `BrowserPrefs.hardwareAccel` is `false`, THE `MainActivity` SHALL set the WebView layer type to `LAYER_TYPE_SOFTWARE` at creation time.
9. WHEN `BrowserPrefs.fontSizePercent` is changed, THE `MainActivity` SHALL apply the value to `WebSettings.setTextZoom(percent)` on all open tabs.
10. THE `SettingsActivity` SHALL display a "Clear Cookies" action row that calls `CookieManager.getInstance().removeAllCookies(null)` and shows a confirmation `Toast`.
11. THE `SettingsActivity` SHALL display a "Clear Form Data" action row that calls `WebView.clearFormData()` on the active tab and shows a confirmation `Toast`.

---

### Requirement 8: In-Page Video Controls Overlay

**User Story:** As a user, I want a floating overlay with PiP, download, and audio-only controls to appear when a video is detected on the page, so that I can quickly act on in-page videos without opening the full player.

#### Acceptance Criteria

1. THE `MainActivity` layout SHALL include a `VideoOverlayBar` — a `LinearLayout` of three 36 dp circular `ImageButton` views (PiP `btn_overlay_pip`, download `btn_overlay_dl`, audio-only `btn_overlay_audio`) anchored to the top-right corner of the WebView `FrameLayout`, with `elevation` of 4 dp and a semi-transparent background (alpha 0.75), initially `GONE`.
2. WHEN the `VideoSniffBar` (`playChip`) becomes `VISIBLE`, THE `MainActivity` SHALL set the `VideoOverlayBar` to `VISIBLE`.
3. WHEN the `VideoSniffBar` is hidden, THE `MainActivity` SHALL set the `VideoOverlayBar` to `GONE`.
4. WHEN `btn_overlay_pip` is tapped and the device API level is ≥ 26, THE `MainActivity` SHALL call `enterPictureInPictureMode(PictureInPictureParams)` for the current WebView content using a 16:9 aspect ratio.
5. IF `enterPictureInPictureMode` is not supported (API < 26 or `getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)` returns `false`), THEN THE `MainActivity` SHALL show a `Toast` with the message `"PiP not supported on this device"`.
6. WHEN `btn_overlay_dl` is tapped, THE `MainActivity` SHALL retrieve the last sniffed video URL from the `VideoSniffer` and trigger the existing download flow (same as tapping the `playChip` download button).
7. WHEN `btn_overlay_audio` is tapped, THE `MainActivity` SHALL inject JavaScript `"document.querySelectorAll('video').forEach(v=>v.muted=!v.muted)"` into the active WebView and toggle a muted-state boolean; the `btn_overlay_audio` icon SHALL reflect the muted state.
8. WHEN the active WebView's `onPageStarted` fires for a new URL, THE `MainActivity` SHALL set the `VideoOverlayBar` to `GONE`.
