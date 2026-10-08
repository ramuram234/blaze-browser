# Keep the JS bridge methods.
-keepclassmembers class app.blaze.browser.MainActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
