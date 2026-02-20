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
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private WebView webView;
    private static final String HOME_URL = "file:///android_asset/www/index.html";

    // ── Multi-stream picker state ────────────────────────────────────────────
    private String[] currentStreamUrls = null;
    private String[] currentStreamNames = null;
    private String currentGameName = "";
    private int currentStreamIndex = 0;
    private boolean pickerVisible = false;

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
        + "      if(el.id==='lftv-picker') continue;"
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

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
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

                android.util.Log.w("LibreFutbol", "NAV → " + target.toString());
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
        webView.addJavascriptInterface(new StreamBridge(), "Android");
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

    // ── JavaScript Interface for multi-stream picker ──────────────────────────

    private class StreamBridge {
        @JavascriptInterface
        public void setGameStreams(String jsonStreams, String gameName) {
            try {
                JSONArray arr = new JSONArray(jsonStreams);
                String[] urls = new String[arr.length()];
                String[] names = new String[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    urls[i] = obj.getString("url");
                    names[i] = obj.optString("name", "Stream " + (i + 1));
                }
                currentStreamUrls = urls;
                currentStreamNames = names;
                currentGameName = gameName != null ? gameName : "";
                currentStreamIndex = 0;
                pickerVisible = false;
            } catch (JSONException e) {
                android.util.Log.e("LibreFutbol", "Failed to parse streams JSON", e);
                currentStreamUrls = null;
                currentStreamNames = null;
            }
        }

        @JavascriptInterface
        public void selectStream(final int index) {
            if (currentStreamUrls == null || index < 0 || index >= currentStreamUrls.length) return;
            currentStreamIndex = index;
            pickerVisible = false;
            final String url = currentStreamUrls[index];
            runOnUiThread(() -> {
                Map<String, String> headers = new HashMap<>();
                headers.put("Referer", "https://librefutboltv.su/");
                webView.loadUrl(url, headers);
            });
        }

        @JavascriptInterface
        public void dismissPicker() {
            pickerVisible = false;
            runOnUiThread(() -> webView.evaluateJavascript(
                "(function(){var p=document.getElementById('lftv-picker');"
                + "if(p)p.remove();})()", null));
        }
    }

    private String buildPickerJs() {
        if (currentStreamUrls == null || currentStreamUrls.length <= 1) return "";

        StringBuilder items = new StringBuilder("[");
        for (int i = 0; i < currentStreamNames.length; i++) {
            if (i > 0) items.append(",");
            items.append(escapeJsString(currentStreamNames[i]));
        }
        items.append("]");

        return "(function(){"
            + "if(document.getElementById('lftv-picker')){"
            +   "document.getElementById('lftv-picker').remove();return;"
            + "}"
            // Backdrop
            + "var bg=document.createElement('div');"
            + "bg.id='lftv-picker';"
            + "bg.style.cssText='position:fixed;inset:0;z-index:999999;"
            +   "background:rgba(0,0,0,0.85);display:flex;align-items:center;"
            +   "justify-content:center;font-family:sans-serif;';"
            // Panel
            + "var panel=document.createElement('div');"
            + "panel.style.cssText='background:#141e14;border:2px solid #00e676;"
            +   "border-radius:8px;padding:32px 40px;min-width:420px;max-width:520px;"
            +   "box-shadow:0 0 60px rgba(0,230,118,0.15);';"
            // Title
            + "var title=document.createElement('div');"
            + "title.textContent=" + escapeJsString(currentGameName) + ";"
            + "title.style.cssText='font-size:22px;color:#e8f5e9;font-weight:700;"
            +   "letter-spacing:2px;margin-bottom:8px;text-align:center;';"
            + "panel.appendChild(title);"
            // Subtitle
            + "var sub=document.createElement('div');"
            + "sub.textContent='Select Stream';"
            + "sub.style.cssText='font-size:13px;color:#6a8f6a;letter-spacing:3px;"
            +   "text-transform:uppercase;text-align:center;margin-bottom:24px;';"
            + "panel.appendChild(sub);"
            // Stream list
            + "var labels=" + items.toString() + ";"
            + "var active=" + currentStreamIndex + ";"
            + "var focused=" + currentStreamIndex + ";"
            + "var btns=[];"
            + "for(var i=0;i<labels.length;i++){"
            +   "var btn=document.createElement('div');"
            +   "btn.textContent=labels[i];"
            +   "btn.dataset.idx=i;"
            +   "btn.style.cssText='padding:14px 24px;margin:4px 0;border-radius:4px;"
            +     "font-size:18px;color:#e8f5e9;cursor:pointer;border:2px solid transparent;"
            +     "transition:all 0.15s;letter-spacing:1px;position:relative;';"
            +   "if(i===active){"
            +     "btn.style.color='#00e676';"
            +     "var dot=document.createElement('span');"
            +     "dot.textContent='\\u25CF NOW PLAYING';"
            +     "dot.style.cssText='font-size:10px;color:#00e676;letter-spacing:2px;"
            +       "margin-left:12px;';"
            +     "btn.appendChild(dot);"
            +   "}"
            +   "panel.appendChild(btn);"
            +   "btns.push(btn);"
            + "}"
            // Hint
            + "var hint=document.createElement('div');"
            + "hint.textContent='\\u2191\\u2193 Navigate  \\u23CE Select  BACK Close';"
            + "hint.style.cssText='font-size:11px;color:#6a8f6a;letter-spacing:2px;"
            +   "text-align:center;margin-top:24px;';"
            + "panel.appendChild(hint);"
            + "bg.appendChild(panel);"
            + "document.body.appendChild(bg);"
            // Focus styling
            + "function updateFocus(){"
            +   "for(var i=0;i<btns.length;i++){"
            +     "btns[i].style.background=i===focused?'rgba(0,230,118,0.15)':'transparent';"
            +     "btns[i].style.borderColor=i===focused?'#00e676':'transparent';"
            +   "}"
            + "}"
            + "updateFocus();"
            // Keyboard handler (capture phase)
            + "function onKey(e){"
            +   "e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();"
            +   "var k=e.key||e.code;"
            +   "if(k==='ArrowDown'){focused=Math.min(focused+1,btns.length-1);updateFocus();}"
            +   "else if(k==='ArrowUp'){focused=Math.max(focused-1,0);updateFocus();}"
            +   "else if(k==='Enter'){close();Android.selectStream(focused);}"
            +   "else if(k==='Escape'||k==='GoBack'){close();Android.dismissPicker();}"
            + "}"
            + "function close(){"
            +   "document.removeEventListener('keydown',onKey,true);"
            +   "bg.remove();"
            + "}"
            + "document.addEventListener('keydown',onKey,true);"
            // Click handlers
            + "btns.forEach(function(b){b.addEventListener('click',function(){"
            +   "close();Android.selectStream(parseInt(b.dataset.idx));"
            + "});});"
            + "bg.addEventListener('click',function(e){"
            +   "if(e.target===bg){close();Android.dismissPicker();}"
            + "});"
            + "})();";
    }

    private static String escapeJsString(String s) {
        if (s == null) return "''";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    // ── D-pad / remote key handling ───────────────────────────────────────────

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // When picker is visible, let the WebView's JS handler navigate it
                    if (pickerVisible) break;
                    // fall through
                case KeyEvent.KEYCODE_MENU: {
                    String menuUrl = webView.getUrl();
                    boolean onStreamPage = menuUrl != null && !menuUrl.startsWith("file:///");
                    if (onStreamPage && currentStreamUrls != null && currentStreamUrls.length > 1) {
                        String pickerJs = buildPickerJs();
                        if (!pickerJs.isEmpty()) {
                            pickerVisible = !pickerVisible;
                            webView.evaluateJavascript(pickerJs, null);
                        }
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_MENU) return true;
                    break;
                }
                case KeyEvent.KEYCODE_BACK:
                    if (pickerVisible) {
                        pickerVisible = false;
                        webView.evaluateJavascript(
                            "(function(){var p=document.getElementById('lftv-picker');"
                            + "if(p)p.remove();})()", null);
                        return true;
                    }
                    if (webView.canGoBack()) {
                        webView.goBack();
                        currentStreamUrls = null;
                        currentStreamNames = null;
                        currentGameName = "";
                        currentStreamIndex = 0;
                        pickerVisible = false;
                        return true;
                    }
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
        }
        return super.dispatchKeyEvent(event);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private boolean wasPaused = false;

    @Override protected void onResume() {
        super.onResume();
        webView.onResume();
        if (wasPaused) {
            wasPaused = false;
            String url = webView.getUrl();
            if (url != null && url.startsWith("file:///")) {
                webView.evaluateJavascript("loadAgenda()", null);
            }
        }
    }
    @Override protected void onPause() {
        super.onPause();
        webView.onPause();
        wasPaused = true;
    }
    @Override protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
