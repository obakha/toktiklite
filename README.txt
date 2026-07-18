# ROLE

You are a Senior Android Engineer, Android WebView expert, Kotlin architect, and code reviewer.

Do NOT make incremental patches.

Treat this as a production application rewrite.

Your objective is to transform this repository into a stable Android wrapper for TikTok Mobile Web.

Repository:

https://github.com/obakha/toktiklite

Original upstream:

https://github.com/gohenderson/toktiklite

Read the ENTIRE repository before making any changes.

Do not assume MainActivity is the only issue.

---

# Goal

Build a lightweight Android browser dedicated to TikTok.

NOT an embed player.

NOT a URL converter.

NOT a deep-link router.

The application should behave like Chrome opening TikTok.

---

# Requirements

First perform a complete architecture audit.

Review:

* MainActivity
* AndroidManifest
* Gradle
* activity_main.xml
* layouts
* permissions
* WebView configuration
* Cookie handling
* session persistence
* JavaScript
* GitHub Actions
* build scripts
* package structure

Document every architectural issue before changing code.

---

# Important

Current implementation converts video URLs into:

https://www.tiktok.com/embed/v2/

Remove this entire approach.

No embed URLs anywhere.

No regex that rewrites TikTok URLs.

No VIDEO_URL_REGEX.

No toEmbedUrl().

No forced navigation to embed pages.

TikTok should navigate naturally.

---

# Deep Links

Support opening:

https://www.tiktok.com/...

https://tiktok.com/...

https://vm.tiktok.com/...

https://vt.tiktok.com/...

tiktok://

snssdk://

but convert native intents into normal TikTok web URLs only when necessary.

Never convert normal TikTok web URLs into embed URLs.

---

# WebView

Configure WebView according to current Android best practices.

Enable:

JavaScript

DOM Storage

Database

Cookies

Third-party cookies

Media playback

Cache

Proper mobile Chrome user agent

Safe Browsing

WebChromeClient

Downloads

Uploads

Camera permissions

Microphone permissions

File chooser

Geolocation permissions

Back navigation

State restoration

Session persistence

Multiple windows if required

Handle SSL errors correctly.

Handle renderer crashes.

Handle process death.

Do not use deprecated APIs unless absolutely necessary.

---

# Browser Architecture

Refactor into proper classes.

Suggested structure:

BrowserActivity

BrowserWebViewClient

BrowserChromeClient

IntentRouter

WebViewConfigurator

DownloadHandler

PermissionManager

Constants

Do NOT leave everything inside MainActivity.

---

# UI

Modern Material 3.

Edge-to-edge.

Splash screen.

Loading indicator.

Offline page.

Error page.

Dark mode.

Pull-to-refresh if appropriate.

---

# Android

Target latest stable SDK.

Support Android 10+.

Follow current Android recommendations.

Use AndroidX.

---

# Performance

Avoid memory leaks.

Avoid unnecessary allocations.

Avoid reload loops.

Preserve cookies.

Preserve login session.

Do not recreate WebView unnecessarily.

---

# Security

Disable dangerous settings.

Only allow what TikTok actually needs.

Review every WebView setting.

Explain each decision.

---

# GitHub

Review GitHub Actions.

Improve workflow.

Automatically build:

Debug APK

Release APK

Artifacts

GitHub Release

Version tagging

Release notes

---

# Research

Before coding:

Search GitHub.

Search Reddit.

Search Android documentation.

Search Chromium documentation.

Search WebView documentation.

Search for recent TikTok WebView issues.

Search for recent TikTok anti-WebView behavior.

Use the latest recommendations, not outdated StackOverflow answers.

---

# Deliverables

Do NOT output patches.

Instead:

1. Explain the problems found.

2. Produce a migration plan.

3. Rewrite the project properly.

4. Output COMPLETE replacement files.

Never output partial code.

Each file must be complete.

Each file must compile.

Never leave TODOs.

Never leave placeholders.

Never say "implement later."

---

# Workflow

Work file-by-file.

When one file is finished, continue automatically to the next.

Continue until the entire project has been rewritten.

At the end:

Explain every architectural decision.

Explain why it is better.

Explain how it avoids TikTok reload problems.

Finally provide:

* Git commands
* Commit messages
* Push instructions
* GitHub Actions verification
* APK testing checklist

The objective is to end with a production-quality TikTok Android wrapper, not just a fixed MainActivity.
