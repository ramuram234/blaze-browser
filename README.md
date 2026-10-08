# Blaze

A UC Browser–style mobile browser: orange chrome, turbo pages, tabs, news, videos, and a gesture media player.

## Android APK

Installable build: [releases/blaze-1.0.0.apk](releases/blaze-1.0.0.apk)

Package `app.blaze.browser`, version 1.0.0. This is a real Android browser (WebView), not a Play Store listing.

On your phone:

1. Download `blaze-1.0.0.apk`
2. Allow installs from your browser or Files app
3. Open the APK and install **Blaze**
4. Search or tap a shortcut. While a video is playing:
   - swipe left or right to seek
   - right side up or down for volume
   - left side up or down for brightness
   - the next clip preloads when a video starts

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
