# Render Dragon — AnimePahe Extension for Aniyomi

An Aniyomi anime extension that scrapes **AnimePahe** (animepahe.ru).

## Features

- Browse airing / popular anime
- Search by title
- Full episode lists (paginated across all pages)
- Multi-quality streams via kwik.cx (360p / 480p / 720p / 1080p)
- Automatic Cloudflare bypass (WebView solver built into Aniyomi)
- kwik.cx JS unpacker — extracts the real `.m3u8` stream URL
- Quality preference setting inside Aniyomi

---

## What the scraper provides to Aniyomi

The video **player UI is handled entirely by Aniyomi** — the extension only supplies data:

| What Aniyomi asks for | What the extension returns |
|---|---|
| Anime list (browse/latest) | `SAnime` — title, thumbnail, status, URL |
| Anime detail page | `SAnime` — full description, genres, type, year, season, score, status |
| Episode list | `SEpisode` — name, number, air date, audio tag (sub/dub) |
| Video sources | `Video` — `.m3u8` URL, quality label, headers (Referer) |

Aniyomi takes that `Video` list, picks the user's preferred quality, and plays it in its own player. The extension never touches the player.

---

## How to Build (Android command-line tools only — no full IDE needed)

You only need:
- **JDK 17** — download from [Adoptium](https://adoptium.net/temurin/releases/?version=17) (Windows `.msi` installer)
- **Android command-line tools** — you already have these (the minimal install)

### Step 1 — Install required SDK components

Open a terminal and run:

```bat
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

If `sdkmanager` is not on your PATH, find it at:
```
C:\Users\<YourName>\AppData\Local\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
```

### Step 2 — Set ANDROID_HOME

In the same terminal session (or add it to System Environment Variables permanently):

```bat
set ANDROID_HOME=C:\Users\<YourName>\AppData\Local\Android\Sdk
```

Replace `<YourName>` with your actual Windows username.

### Step 3 — Build the APK

Navigate to this folder and run the Gradle wrapper:

```bat
cd render-dragon
gradlew.bat assembleRelease
```

First run downloads Gradle (~120 MB) and dependencies (~80 MB) — takes 3–5 minutes.
Subsequent builds are fast (~15 seconds).

Output APK:
```
render-dragon\app\build\outputs\apk\release\app-release-unsigned.apk
```

---

## How to Install into Aniyomi

1. Transfer the APK to your Android device (USB cable, Google Drive, Telegram Saved Messages, etc.)
2. Open **Aniyomi** on your device
3. Go to **Settings → Browse → Install extension from file**
4. Select the APK
5. Tap **Install** on the Android dialog
6. The extension **"AnimePahe"** will appear in **Browse → Extensions → Installed**

> Enable "Install from unknown sources" for your file manager in Android settings if prompted.

---

## How Cloudflare Bypass Works

AnimePahe sits behind **Cloudflare Under Attack Mode** — a JavaScript challenge that runs before any page loads. The extension uses Aniyomi's built-in `cloudflareClient`:

1. On the first request, the client detects a 503/1020 Cloudflare response
2. It opens a hidden WebView in the background
3. The WebView runs the JS challenge and receives a `cf_clearance` cookie
4. That cookie is injected into all future requests automatically
5. When the cookie expires (typically every 24–48 h), the process repeats silently

Nothing to configure — it's fully automatic.

---

## How kwik.cx Stream Extraction Works

AnimePahe hosts videos on **kwik.cx**. The stream URL is hidden inside obfuscated JavaScript packed with the `p,a,c,k,e,d` algorithm:

```javascript
// This is what kwik.cx serves — the real URL is encoded inside
eval(function(p,a,c,k,e,d){ ... }('encoded_payload', 62, N, 'symbol|table|...'))
```

`KwikExtractor.kt` decodes it in pure Kotlin — no JS engine dependency:

1. Extract the encoded payload, base (usually 62), and symbol table
2. Tokenize the payload — each token is a base-62 number
3. Map every token to its symbol in the lookup table
4. Scan the decoded output for `source='https://...m3u8'`
5. For newer kwik variants that require a POST token, submit the form first

---

## Project Structure

```
render-dragon/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── kotlin/eu/kanade/tachiyomi/animeextension/en/animepahe/
│   │       ├── AnimePahe.kt       — main source class
│   │       ├── AnimePaheDto.kt    — JSON data models
│   │       └── KwikExtractor.kt   — stream URL extractor
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew.bat                    — Windows build script
└── gradle/wrapper/
    └── gradle-wrapper.properties
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `JAVA_HOME is not set` | Install JDK 17 from Adoptium. After installing, open a new terminal — it sets JAVA_HOME automatically. |
| `ANDROID_HOME is not set` | Run `set ANDROID_HOME=C:\Users\<YourName>\AppData\Local\Android\Sdk` in the terminal before building. |
| `Could not resolve platforms;android-34` | Run `sdkmanager "platforms;android-34" "build-tools;34.0.0"` first. |
| `Could not resolve com.github.aniyomiorg` | You need internet access. JitPack downloads the extension stubs on first build. |
| Extension not showing in Aniyomi | Make sure you're using **Aniyomi**, not Tachiyomi — they use different extension APIs. |
| Videos not loading (403) | The `cf_clearance` cookie expired. Open any anime in the extension — it will silently refresh. |
| Only one quality showing | Some AnimePahe episodes only have one resolution encoded. Check the episode on the website directly. |
| `animePahe.ru` not loading | AnimePahe has changed domains before. Update `baseUrl` in `AnimePahe.kt` to the new domain. |
