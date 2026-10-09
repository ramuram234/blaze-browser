# Blaze

A UC Browser–style mobile browser: orange chrome, turbo pages, tabs, news, videos, and a gesture media player.

## Android APK

Installable build: [releases/blaze-1.2.0.apk](releases/blaze-1.2.0.apk)

Package `app.blaze.browser`, version 1.2.0.

On your phone, uninstall the old Blaze app, then install this APK.

- **Menu** has history, bookmarks, share, find, desktop site, ad block, clear cache, and clear history
- **Watch video** or **Fullscreen video** appears when a page is actually playing a video. It uses the site’s own player, full screen
- Wide videos turn landscape. Tall videos stay vertical. **Rotate** flips that
- **PiP** keeps the video in a small window. Leaving the app while a video is full screen does the same
- In the player, swipe left/right to seek, the right side is volume, the left side is brightness, and the buffer bar shows how far the video has loaded
- Direct video files still open in the Blaze player with the same controls

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
