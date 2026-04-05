# TokTikLite

A minimal Android app that opens TikTok links from other apps and displays them using TikTok's embed player, with no feed, no algorithm, and no scrolling.

## How it works

When you tap a TikTok link anywhere on your phone, TokTikLite intercepts it and loads the video using TikTok's official embed URL — a clean single-video player. No For You page, no recommended videos, no account required.

Supported link formats:
- `https://www.tiktok.com/@user/video/ID`
- `https://vm.tiktok.com/...` (short links)
- `https://vt.tiktok.com/...` (short links)
- `https://m.tiktok.com/v/ID`

## Building

1. Open the project in [Android Studio](https://developer.android.com/studio)
2. Connect your Android device with USB debugging enabled
3. Press **Run**

Requires Android 8.0+ (API 26).

## Setting as default link handler

After installing, you may need to set TokTikLite as the default handler for TikTok links:

- **Settings → Apps → Default Apps → Opening links → TokTikLite** and enable it, or
- Tap a TikTok link, choose TokTikLite, and select **Always**

If the TikTok app is installed, you may also need to disable its link handling under its App Info settings.
