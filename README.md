# Blaze

A UC Browser–style mobile browser: orange chrome, turbo pages, tabs, news, videos, and a gesture media player.

## Android APK

Installable build: [releases/blaze-1.1.0.apk](releases/blaze-1.1.0.apk)

Package `app.blaze.browser`, version 1.1.0. This is a real Android browser, not a Play Store listing.

On your phone:

1. Uninstall the old Blaze app if it is still installed
2. Download `blaze-1.1.0.apk`
3. Allow installs from your browser or Files app
4. Open the file and install **Blaze**

What this version does:

- Google is the search engine
- Windows list only: open, open in a new tab, or copy the link. No tab groups
- Common ad and popup pages are blocked
- When a site has a real video, tap the orange **Play in Blaze** button
- In that player, swipe left or right to seek, the right side changes volume, the left side changes brightness, and the next clip is buffered ahead
- Touching a link starts preloading it so the next page opens faster

Rebuild from source (Android SDK 34 and JDK 17):

```bash
cd android
gradle assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

## Web app

The same product also runs in the browser.

```bash
npm install
npm run dev
```

```bash
npm run typecheck
npm run build
```

## Stack

- Android: WebView, Java, Android SDK 34
- Web: TanStack Start, React, Tailwind CSS, Zustand

Not affiliated with UC Browser.
