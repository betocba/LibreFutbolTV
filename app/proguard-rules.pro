# Keep WebView JS interface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
