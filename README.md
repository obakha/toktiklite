# TokTikLite

A lightweight Android WebView wrapper dedicated to TikTok's mobile web app (`m.tiktok.com` /
`www.tiktok.com`). It behaves like Chrome opening TikTok — full site, your own login, no
embed-only restriction — while staying a single-purpose app: it only ever navigates within
TikTok's own domains and their required login/CDN partners, handing everything else off to
the system.

## How it works

Tapping any TikTok link (`https://www.tiktok.com/...`, `https://vm.tiktok.com/...`,
`https://vt.tiktok.com/...`, `https://m.tiktok.com/...`, `tiktok://...`, `snssdk://...`)
anywhere on your phone opens it directly, natively, inside the app — the real page, not a
stripped-down embed. Cookies and login state persist across restarts. Anything outside
TikTok's domains (an external link tapped from inside a TikTok page, an unrelated deep link)
is handed to the system instead of being loaded in-app.

## Building

1. Open the project in [Android Studio](https://developer.android.com/studio) (AGP 8.13.0 / JDK 17).
2. Connect your Android device with USB debugging enabled.
3. Press **Run**.

Or from the command line:

```bash
./gradlew assembleDebug
```

Requires Android 10+ (API 29).

## Setting as default link handler

After installing, you may need to set TokTikLite as the default handler for TikTok links:

- **Settings → Apps → Default Apps → Opening links → TokTikLite** and enable it, or
- Tap a TikTok link, choose TokTikLite, and select **Always**.

If the TikTok app is installed, you may also need to disable its link handling under its
App Info settings.

## Releasing

Push a tag matching `v*` (e.g. `v2.0.0`) to trigger a signed release build and GitHub Release
via `.github/workflows/android-build.yml`. Configure these repository secrets first:
`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
