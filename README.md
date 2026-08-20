# NoBrowser Clone

A minimal Android "browser" for opening links from other apps — inspired by
[rickgram/NoBrowser](https://github.com/rickgram/NoBrowser), with a few extra behaviors.

## What it does

- Appears in the Android share sheet and "Open with" dialog for links (via
  `ACTION_SEND` / `ACTION_VIEW` intent filters).
- **Not in the app drawer**: there's no `LAUNCHER` intent filter, so it has
  no home-screen/drawer icon. It's still installed and shows up under
  Settings → Apps → All apps, just not launchable from the drawer.
- Shows the current URL in a thin address bar — informational, not editable.
- **Redirects**: ordinary HTTP(S) navigation (redirect chains, meta-refresh,
  JS redirects) is left alone, so the WebView follows it like a normal
  browser tab.
- **App-redirect links** (e.g. `intent://...` links that sites like TikTok
  use to hand off into their app) are intercepted and confirmed with the
  user first — "Open in app?" / "Stay here" — rather than silently failing
  (which is what a plain WebView does with these) or silently jumping out
  of the browser. If the link carries a `browser_fallback_url` and no app
  is installed to handle it, that fallback page is loaded instead.
- **Pull-to-refresh**: swipe down on the page to reload it
  (`SwipeRefreshLayout`).
- **Dedicated back button**: sits to the left of the address bar and steps
  back through the WebView's own page history (`webView.goBack()`). It's
  disabled/greyed out when there's no history to go back to.
- **System back gesture/button**: deliberately *not* wired to page history.
  It always means "leave the app": first press shows a
  "press back again to exit" toast, a second press within ~2 seconds closes
  the app.

## Project layout

```
app/src/main/
├── AndroidManifest.xml          # intent filters for SEND / VIEW (no LAUNCHER)
├── java/com/example/nobrowser/
│   └── MainActivity.kt          # all the logic lives here
└── res/
    ├── layout/activity_main.xml # address bar + SwipeRefreshLayout + WebView
    ├── values/strings.xml
    ├── values/themes.xml
    └── drawable/                # back / refresh / launcher icons (vector)
```

## Building it

Open the project root in Android Studio (Koala or newer) and let it sync,
or from the command line once you have a Gradle wrapper jar:

```
./gradlew assembleDebug
```

The resulting APK will be at
`app/build/outputs/apk/debug/app-debug.apk`.

Since the app has no launcher icon, install it and then open it once via:

```
adb shell am start -a android.intent.action.VIEW -d "https://example.com" com.example.nobrowser
```

or just share a link to it from another app — after that it'll keep
handling links until you uninstall it or clear its defaults.

> Note: the `gradle-wrapper.jar` binary itself isn't included here (binary
> files don't travel well through this channel). Opening the project in
> Android Studio will regenerate it automatically, or you can run
> `gradle wrapper` once if you have Gradle installed locally.

## Things you may want to tweak

- **HTTPS-only**: the manifest currently accepts both `http` and `https`
  schemes; drop the `http` `<data>` entry if you only want secure links.
- **Ad/tracker stripping**: currently none — it's a plain WebView. If you
  want that, it'd go in `shouldInterceptRequest` inside the `WebViewClient`.
- **Address bar tap-to-edit**: right now the URL text is read-only. Wiring
  it up to an editable field + Enter-to-navigate would be a small addition
  to `MainActivity.kt`.
