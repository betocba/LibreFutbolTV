package com.librefutbol.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private WebView webView;
    private static final String HOME_URL = "file:///android_asset/www/index.html";

    // ── Blocked domains (network-request level) ───────────────────────────────
    private static final Set<String> BLOCKED_DOMAINS = new HashSet<>(Arrays.asList(
        // Google ads / analytics
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google", "googletagmanager.com", "google-analytics.com",
        // Programmatic / RTB networks
        "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net",
        "smartadserver.com", "criteo.com", "bidvertiser.com", "revcontent.com",
        "media.net", "adcash.com", "bidx.com", "adbull.com", "a-ads.com",
        "richads.com", "rich-ads.com", "cpmnetwork.com", "zeydoo.com",
        "clickadu.com", "taboola.com", "outbrain.com", "mgid.com",
        "advertising.com", "adskeeper.com", "adspyglass.com",
        // Streaming / piracy site ad networks
        "aclib.net", "exoclick.com", "trafficjunky.com", "juicyads.com",
        "adsterra.com", "hilltopads.net", "propellerads.com",
        "popads.net", "popcash.net", "zonky.me", "traffichunt.com",
        "pushground.com", "evadav.com", "trafficstars.com", "clickaine.com",
        "plugrush.com", "ero-advertising.com", "adnium.com", "trafmag.com",
        "trafficfactory.biz", "tsyndicate.com", "adtng.com", "jads.co",
        "tubecash.com", "voluumdsp.com", "etarget.net", "popmyads.com",
        "oxpush.com", "adxcg.com", "dt00.net", "dt07.net", "dt010.net",
        "dpclk.com", "jdoqocy.com", "lnk.su", "oio.la", "adf.ly",
        "revbidder.com", "visitweb.com", "sexad.net", "camads.net",
        "xtendmedia.com", "trackcpa.com",
        // Adult destinations (block redirects to these entirely)
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com",
        "youporn.com", "redtube.com", "spankbang.com", "motherless.com",
        "livejasmin.com", "myfreecams.com", "streamate.com", "sexier.com",
        "livestrip.com", "luckycrush.live", "chaturbate.com",
        "bongacams.com", "cam4.com", "camsoda.com"
    ));

    // Block entire TLDs used exclusively for adult content
    private static final Set<String> BLOCKED_TLDS = new HashSet<>(Arrays.asList(
        ".xxx", ".sex", ".porn", ".adult"
    ));

    private static final WebResourceResponse EMPTY =
        new WebResourceResponse("text/plain", "utf-8",
            new ByteArrayInputStream(new byte[0]));

    // Injected on external stream pages to autoplay the video
    private static final String AUTOPLAY_JS =
        "(function(){"
        + "function tryPlay(){"
        // Play all HTML5 video elements; try unmuted first, fall back to muted
        + "  document.querySelectorAll('video').forEach(function(v){"
        + "    try{v.muted=false;v.play();}catch(e){try{v.muted=true;v.play();}catch(e2){}}"
        + "  });"
        // JWPlayer API
        + "  try{if(typeof jwplayer==='function'){var p=jwplayer();"
        + "    if(p&&p.getState&&p.getState()!=='playing')p.play();}}catch(e){}"
        // VideoJS API
        + "  try{if(typeof videojs==='function'){"
        + "    document.querySelectorAll('.video-js').forEach(function(el){"
        + "      try{videojs(el).play();}catch(e){}});"
        + "  }}catch(e){}"
        // Click common overlay play buttons
        + "  ['.jw-icon-playback',\"[aria-label='Play']\",\".fp-play\","
        + "   '.plyr__control--overlaid','.vjs-big-play-button',"
        + "   'button[title=Play]','.play-button'].forEach(function(s){"
        + "    var b=document.querySelector(s);if(b)try{b.click();}catch(e){}"
        + "  });"
        + "}"
        + "tryPlay();"
        + "setTimeout(tryPlay,1000);"
        + "setTimeout(tryPlay,3000);"
        + "setTimeout(tryPlay,6000);"
        // Watch for video elements added dynamically (lazy-loaded players)
        + "new MutationObserver(function(ms){"
        + "  var found=ms.some(function(m){"
        + "    return[].slice.call(m.addedNodes).some(function(n){"
        + "      return n.nodeType===1&&(n.tagName==='VIDEO'||!!n.querySelector('video'));"
        + "    });"
        + "  });"
        + "  if(found)setTimeout(tryPlay,300);"
        + "}).observe(document.documentElement,{childList:true,subtree:true});"
        + "})();";

    // Injected into every page after load
    // Removes invisible click-hijack overlays and kills window.open
    private static final String INJECT_JS =
        "(function(){"
        // 1. Kill all new-window attempts
        + "window.open=function(){return null;};"
        // 2. Remove large transparent overlays that hijack clicks
        + "function rmOverlays(){"
        + "  try{"
        + "    var els=document.querySelectorAll('body>*');"
        + "    for(var i=0;i<els.length;i++){"
        + "      var el=els[i];"
        + "      if(el.tagName==='VIDEO'||el.tagName==='CANVAS') continue;"
        + "      if(el.querySelector('video,canvas')) continue;"
        + "      var s=window.getComputedStyle(el);"
        + "      var pos=s.position;"
        + "      if(pos!=='fixed'&&pos!=='absolute') continue;"
        + "      var w=el.offsetWidth,h=el.offsetHeight;"
        + "      if(w>window.innerWidth*0.35&&h>window.innerHeight*0.35){"
        + "        el.remove();"
        + "      }"
        + "    }"
        + "  }catch(e){}"
        + "}"
        + "rmOverlays();"
        + "setTimeout(rmOverlays,800);"
        + "setTimeout(rmOverlays,2500);"
        + "new MutationObserver(function(ms,ob){"
        + "  ob.disconnect();" // pause observer while we clean up
        + "  rmOverlays();"
        + "  ob.observe(document.documentElement,{childList:true,subtree:true});"
        + "}).observe(document.documentElement,{childList:true,subtree:true});"
        + "})();";

    @SuppressLint({"SetJavaScriptEnabled", "WebViewApiAvailability"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);
        setupWebView();
        webView.loadUrl(HOME_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(true); // needed so onCreateWindow fires
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 9; AFTMM Build/PS7233) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"
        );

        webView.setWebViewClient(new WebViewClient() {

            // ── 1. Block ad requests before any bytes are fetched ─────────
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                              WebResourceRequest request) {
                if (isBlocked(request.getUrl())) return EMPTY;
                return super.shouldInterceptRequest(view, request);
            }

            // ── 2. Block page-level navigations to ad / adult domains ─────
            //       AND lock stream pages to their own domain so JS
            //       redirects (the main ad vector) cannot escape.
            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                                                    WebResourceRequest request) {
                Uri target = request.getUrl();

                // Always block known ad/adult URLs
                if (isBlocked(target)) return true;

                String currentUrl = view.getUrl();
                boolean onHomePage = currentUrl == null
                        || currentUrl.startsWith("file:///");

                // librefutboltv.su is the agenda/eventos site — it legitimately
                // redirects to external stream domains, so never lock it down.
                String currentHost = getHost(currentUrl);
                boolean onAgendaSite = currentHost != null
                        && currentHost.contains("librefutboltv.su");

                if (!onHomePage && !onAgendaSite) {
                    // ── Stream-page lockdown ─────────────────────────────
                    // While watching an external stream, any navigation that
                    // leaves the current domain is almost certainly an ad.
                    // Block it. The user can return home via the Back button.
                    String targetHost = target.getHost();

                    if (currentHost != null && targetHost != null
                            && !isSameDomain(currentHost, targetHost)) {
                        return true; // block cross-domain navigation
                    }
                }

                android.util.Log.d("LibreFutbol", "NAV → " + target.toString());
                if (onHomePage) {
                    // Navigation from home = user picked a stream.
                    // Add Referer so stream servers (which check it) accept the request.
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Referer", "https://librefutboltv.su/");
                    view.loadUrl(target.toString(), headers);
                } else {
                    view.loadUrl(target.toString());
                }
                return true;
            }

            // ── 3. Inject JS after every page load ────────────────────────
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Ad-killer on every page
                view.evaluateJavascript(INJECT_JS, null);
                // Autoplay on external stream pages (not home, not agenda site)
                if (url != null && !url.startsWith("file:///")) {
                    String host = getHost(url);
                    boolean isAgendaSite = host != null
                            && host.contains("librefutboltv.su");
                    if (!isAgendaSite) {
                        view.evaluateJavascript(AUTOPLAY_JS, null);
                    }
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            // Block all pop-up / pop-under windows
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                return false;
            }
            // Dismiss JS dialogs used by ad scripts
            @Override
            public boolean onJsAlert(WebView v, String u, String m, JsResult r) {
                r.cancel(); return true;
            }
            @Override
            public boolean onJsConfirm(WebView v, String u, String m, JsResult r) {
                r.cancel(); return true;
            }
            @Override
            public boolean onJsPrompt(WebView v, String u, String m,
                                      String d, JsPromptResult r) {
                r.cancel(); return true;
            }
        });

        WebView.setWebContentsDebuggingEnabled(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isBlocked(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        String h = host.toLowerCase();
        for (String tld : BLOCKED_TLDS) {
            if (h.endsWith(tld)) return true;
        }
        for (String domain : BLOCKED_DOMAINS) {
            if (h.equals(domain) || h.endsWith("." + domain)) return true;
        }
        return false;
    }

    private static String getHost(String url) {
        try { return Uri.parse(url).getHost(); } catch (Exception e) { return null; }
    }

    /** Returns true if the two hosts share the same registrable domain. */
    private static boolean isSameDomain(String a, String b) {
        a = a.toLowerCase();
        b = b.toLowerCase();
        if (a.equals(b)) return true;
        if (a.endsWith("." + b) || b.endsWith("." + a)) return true;
        // Share the same eTLD+1 (simple two-part check)
        String ra = rootDomain(a);
        String rb = rootDomain(b);
        return ra != null && ra.equals(rb);
    }

    private static String rootDomain(String host) {
        String[] parts = host.split("\\.");
        if (parts.length < 2) return null;
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    // ── D-pad / remote key handling ───────────────────────────────────────────

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return webView.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_BACK:
                if (webView.canGoBack()) { webView.goBack(); return true; }
                moveTaskToBack(true);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                webView.evaluateJavascript(
                    "document.dispatchEvent(new CustomEvent('tv-media-key'," +
                    "{detail:{keyCode:" + keyCode + "}}))", null);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override protected void onResume() { super.onResume(); webView.onResume(); }
    @Override protected void onPause()  { super.onPause();  webView.onPause();  }
    @Override protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
