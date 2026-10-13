# Requirements Document

## Introduction

Blaze Browser v2.0.0 was built replicating UC Browser's feature set, but several UI/UX details from UC Browser's decompiled
resources have not yet been ported. This spec covers the six remaining missing features identified from UC Browser's
`colors.xml`, `dimens.xml`, `styles.xml`, and layout XML files: brand color alignment, native dark-mode chrome, activity
slide transitions, address-bar hide-on-scroll, in-page video controls overlay, and an improved search-bar style matching
UC Browser's `dimens.xml` measurements.

All changes are confined to the existing single-Activity `MainActivity.java` and its supporting resource files within
the `blaze-browser` Android project.

## Glossary

- **Address_Bar**: The `LinearLayout` (`@id/address_bar`) at the top of `activity_main.xml` that contains the address
  `EditText` and action buttons.
- **BlazeBridge**: The `@JavascriptInterface`-annotated inner class in `MainActivity` that exposes Java methods to
  injected JavaScript running in a `WebView`.
- **Bottom_Nav**: The `LinearLayout` at the bottom of `activity_main.xml` containing Back, Forward, Menu, Tabs, and Home buttons.
- **BrowserPrefs**: The `SharedPreferences` helper class (`BrowserPrefs.java`) that persists all user settings.
- **Dark_Theme**: The Android `Style` resource named `Theme.Blaze.Dark` that defines night-mode colors for the browser chrome.
- **Menu_Sheet**: The `FrameLayout` (`@id/menu_sheet_scrim`) bottom-sheet overlay shown when the user taps the Menu button.
- **Play_Chip**: The `TextView` (`@id/btn_play`) floating pill shown when a video is detected on the current page.
- **Progress_Bar**: The `ProgressBar` (`@id/progress`) that fills horizontally under the Address_Bar during page load.
- **Sniff_Bar**: The `LinearLayout` (`@id/video_sniff_bar_root`) bottom-anchored bar shown when a video URL is detected.
- **Video_Controls_Overlay**: A new `LinearLayout` added to `activity_main.xml` that floats at the top-right of the web
  content area and presents PiP, download, and audio-only action buttons when a video is playing.
- **WebStack**: The `FrameLayout` (`@id/web_stack`) that hosts all `WebView` instances.

---

## Requirements

### Requirement 1: UC Browser Brand Color Theme

**User Story:** As a user, I want Blaze Browser to use UC Browser's authentic orange brand color for interactive
elements, so that the app visually matches the UC Browser experience we are replicating.

#### Acceptance Criteria

1. THE `colors.xml` resource file SHALL define a color named `uc_orange` with the value `#FF9C38`.
2. WHEN the Progress_Bar is visible, THE Progress_Bar SHALL display its fill using the `uc_orange` color.
3. WHEN the Sniff_Bar action buttons ("Play" and "Save") are rendered, THE Sniff_Bar buttons SHALL use `uc_orange` as their background tint instead of the current blue `#441A73E8`.
4. WHEN the Play_Chip is visible, THE Play_Chip SHALL use `uc_orange` as its background color.
5. THE `colors.xml` resource file SHALL retain the existing `primary` color `#1A73E8` for link colors and secondary UI elements.
6. WHEN the download speed badge (`@id/dl_speed_badge`) is visible, THE download speed badge SHALL display its text in `uc_orange`.

---

### Requirement 2: Native Dark Mode for Browser Chrome

**User Story:** As a user, I want the browser's own UI (address bar, bottom nav, menu sheet) to go dark when I enable
Night Mode, so that the entire app feels consistent in low-light conditions instead of just dimming web content.

#### Acceptance Criteria

1. WHEN the user enables Night Mode via `BrowserPrefs.setNightMode(context, true)`, THE Address_Bar background SHALL change to `#1B1B1B` without requiring an app restart.
2. WHEN Night Mode is enabled, THE Bottom_Nav background SHALL change to `#1B1B1B`.
3. WHEN Night Mode is enabled, THE Menu_Sheet background SHALL change to `#242424`.
4. WHEN Night Mode is enabled, THE Address_Bar `EditText` text color SHALL change to `#E0E0E0`.
5. WHEN Night Mode is enabled, THE Address_Bar `EditText` hint color SHALL change to `#888888`.
6. WHEN Night Mode is enabled, THE Bottom_Nav icon tint SHALL change to white (`#FFFFFF`).
7. WHEN Night Mode is disabled, THE Address_Bar, Bottom_Nav, and Menu_Sheet SHALL revert to their default light-mode colors immediately.
8. IF Night Mode is enabled at app launch (persisted via `BrowserPrefs`), THEN THE browser chrome SHALL start in dark colors before the first frame is drawn.

---

### Requirement 3: Activity Slide Animations

**User Story:** As a user, I want screen transitions to feel snappy and directional, matching UC Browser's slide-in/zoom
style, so that navigating between activities feels fluid.

#### Acceptance Criteria

1. WHEN `MainActivity` starts `VideoPlayerActivity`, THE `VideoPlayerActivity` SHALL animate by sliding in from the right (enter animation: `slide_in_from_right.xml`, 250 ms).
2. WHEN `MainActivity` starts `VideoPlayerActivity`, THE `MainActivity` SHALL animate out by zooming away (exit animation: `window_zoom_out.xml`, 250 ms).
3. WHEN any secondary activity (Settings, Downloads, Ad Settings, Browsing Settings) finishes and returns, THE returning activity SHALL slide out to the right (exit animation: `slide_out_to_right.xml`, 250 ms).
4. WHEN any secondary activity finishes, THE `MainActivity` SHALL animate in by zooming back in (enter animation: `window_zoom_in.xml`, 250 ms).
5. THE animation duration for all transitions SHALL be between 200 ms and 300 ms.
6. THE four animation XML files (`slide_in_from_right.xml`, `slide_out_to_right.xml`, `window_zoom_out.xml`, `window_zoom_in.xml`) SHALL be placed under `res/anim/`.

---

### Requirement 4: Address Bar Hide-on-Scroll

**User Story:** As a user, I want the address bar to hide when I scroll down a web page and reappear when I scroll
up, so that I get more vertical space to read content without manually doing anything.

#### Acceptance Criteria

1. WHEN a `WebView` page emits a downward scroll event of cumulative delta greater than or equal to 100 dp, THE Address_Bar SHALL translate off-screen upward with a 200 ms animation.
2. WHEN a `WebView` page emits any upward scroll event, THE Address_Bar SHALL translate back to its fully visible position with a 200 ms animation.
3. WHILE the Address_Bar is hidden, THE WebStack height SHALL expand to fill the space previously occupied by the Address_Bar so web content uses the full available height.
4. WHEN the current page finishes loading (WebViewClient `onPageFinished`), THE Address_Bar SHALL return to its fully visible position.
5. WHEN the user taps the Address_Bar `EditText`, THE Address_Bar SHALL return to its fully visible position if hidden.
6. THE Address_Bar hide/show behavior SHALL be implemented by injecting a scroll-listener JavaScript snippet into each `WebView` via `BlazeBridge` using a new `onScroll(int dy)` method.
7. IF the `WebView` is scrolled while a page is loading, THEN THE Address_Bar SHALL remain visible.

---

### Requirement 5: In-Page Video Controls Overlay

**User Story:** As a user, I want quick-access video action buttons to appear near playing web videos, so that I can
enter PiP, download, or go audio-only without navigating away from the page.

#### Acceptance Criteria

1. WHEN `BlazeBridge.onPageVideo(width, height)` is called and a video is detected playing in the current `WebView`, THE Video_Controls_Overlay SHALL become visible at the top-right of the WebStack area.
2. THE Video_Controls_Overlay SHALL contain exactly three action buttons: a PiP button (⛶), a download button (⬇), and an audio-only button (🔊).
3. WHEN the user taps the PiP button in the Video_Controls_Overlay, THE system SHALL call `enterPip()` to enter Picture-in-Picture mode.
4. WHEN the user taps the download button in the Video_Controls_Overlay, THE system SHALL enqueue the current tab's `sniffedUrl` for download via `BlazeDownloadManager`.
5. WHEN the user taps the audio-only button in the Video_Controls_Overlay, THE system SHALL toggle `BrowserPrefs.setBackgroundAudio(context, true)` and show a confirmation Toast.
6. WHEN the Play_Chip (`@id/btn_play`) visibility is set to `GONE`, THE Video_Controls_Overlay SHALL also be hidden automatically.
7. WHEN the current page navigates to a new URL (WebViewClient `onPageStarted`), THE Video_Controls_Overlay SHALL be hidden.
8. THE Video_Controls_Overlay SHALL be positioned using `android:layout_gravity="top|end"` inside the web content `FrameLayout` with a top margin of 8 dp and end margin of 8 dp.

---

### Requirement 6: Improved Address Bar Styling

**User Story:** As a user, I want the address bar to show a lock/warning security icon, the page favicon, and a clear
button while typing, matching UC Browser's address bar style, so that I always have useful context about the page I am on.

#### Acceptance Criteria

1. THE Address_Bar outer container height SHALL be 50 dp, matching UC Browser's `dimens.xml` value.
2. THE Address_Bar `EditText` text size SHALL be 14 sp for URL display and 14 sp for hint text.
3. WHEN the current page URL begins with `https://`, THE Address_Bar SHALL display a lock icon (🔒) as a left compound drawable or small `ImageView` at the start of the address field.
4. WHEN the current page URL begins with `http://` (not HTTPS), THE Address_Bar SHALL display a warning icon (⚠️) at the start of the address field.
5. WHEN a page finishes loading, THE Address_Bar SHALL attempt to load the page favicon from `https://www.google.com/s2/favicons?domain={host}&sz=32` and display it as a leading icon replacing the security icon.
6. IF the favicon load fails or the URL is the local home page (`file://`), THEN THE Address_Bar SHALL fall back to displaying the security icon (lock or warning).
7. WHEN the user begins typing in the Address_Bar `EditText` (text is non-empty), THE Address_Bar SHALL show a clear button (✕) at the right end of the field.
8. WHEN the user taps the clear button, THE Address_Bar `EditText` SHALL be cleared and keyboard focus SHALL remain on the field.
9. THE Address_Bar `EditText` inner container SHALL use a pill/rounded-rectangle background with 18 dp corner radius.
