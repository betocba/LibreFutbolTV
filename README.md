# LibreFútbol TV — Android Studio Project

A FireTV WebView app that bundles your HTML UI and bypasses CORS.

---

## Project Structure

```
LibreFutbolTV/
├── app/src/main/
│   ├── assets/www/
│   │   └── index.html        ← YOUR HTML/CSS/JS lives here
│   ├── java/com/librefutbol/tv/
│   │   └── MainActivity.java ← WebView setup & key handling
│   ├── res/layout/
│   │   └── activity_main.xml
│   └── AndroidManifest.xml
└── README.md
```

---

## Build the APK

### 1. Open in Android Studio
File → Open → select the `LibreFutbolTV` folder.

Wait for Gradle sync to finish (first time downloads dependencies, ~2 min).

### 2. Build a debug APK
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

Or from terminal:
```bash
./gradlew assembleDebug
```

---

## Sideload to FireTV

### One-time FireTV setup
1. Settings → My Fire TV → About → click **Build Number** 7 times
2. Settings → My Fire TV → Developer Options → turn on:
   - **ADB Debugging**
   - **Apps from Unknown Sources**
3. Find your FireTV IP: Settings → My Fire TV → About → Network

### Install via ADB
```bash
# Connect (run once per session)
adb connect YOUR_FIRETV_IP:5555

# Install
adb install app/build/outputs/apk/debug/app-debug.apk

# Reinstall after updates (keeps data)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

App appears under **Your Apps & Channels → All Apps**.

---

## Iteration Workflow

Edit `assets/www/index.html`, then:
```bash
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Or create a one-liner script `deploy.sh`:
```bash
#!/bin/bash
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
echo "✅ Deployed!"
```

---

## Remote Debugging (Chrome DevTools on your TV)

While the app is running on FireTV:
1. Open Chrome on your computer
2. Go to `chrome://inspect/#devices`
3. Your WebView will appear — click **inspect**
4. Full DevTools: console, network, element inspector, everything

This is the killer feature for iterating quickly.

---

## CORS — How It Works Here

`setAllowUniversalAccessFromFileURLs(true)` in MainActivity.java means
pages loaded from `file:///android_asset/` can fetch any URL without
CORS restrictions. The browser's same-origin policy does not apply.

---

## Adding More Files

Put any JS, CSS, images alongside index.html in `assets/www/`:
```html
<script src="scraper.js"></script>
<link rel="stylesheet" href="style.css">
<img src="logo.png">
```
All paths are relative and work as expected.
