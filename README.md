# PageTune

An Android browser app that hides clutter such as ads and title bars on mobile web pages, and saves colors, start scroll position and previous/next navigation per site as a **Tune** that is applied every time you open the page.

- Web pages and Tunes are stored only on the device. There is no account, and the developer does not collect any data → [Privacy policy](https://hwanyu365.github.io/page-tune/privacy)
- Tunes apply only to pages opened inside PageTune. They don't change other apps or browsers
- UI languages: English, Korean

## What a Tune can do

| Rule | Description |
| --- | --- |
| Hide | Tap an element on the page, such as a banner or title bar, and hide it |
| Color | Change background and text colors |
| Start position | Open the page at the scroll position you choose |
| Prev/Next navigation | Map a large on-screen button, a double tap, or a pull at the top/bottom of the page to the site's own previous and next links |

## How it works

1. Add a web page in the `Pages` tab
2. In the `Tunes` tab, create a Tune, pick elements on the page and choose what to do with them
3. Save the Tune and connect it to the web page → it is applied every time you open the page
4. While viewing, use the quick menu at the top right to turn the Tune on or off or edit it

Sites can change their layout at any time. If a rule no longer finds its element, PageTune marks it so you can pick the element again.

## Development

This repository is developed by Claude Code agents with SDD (Spec-Driven Development) + TDD using [agentic-dev-kit](https://github.com/hwanyu365/agentic-dev-kit). Rules are in [CLAUDE.md](CLAUDE.md), the spec index is [docs/specification.md](docs/specification.md).

- Stack: Kotlin / Android (minSdk 24, targetSdk 36), AndroidX + Material (View-based), Room, WebView + JavaScript injection
- Requires JDK 21 and the Android SDK

```sh
# verification (same command as CI)
./gradlew -p buildSrc test && ./gradlew lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest

# instrumented tests (requires an emulator or device; run on release verification)
./gradlew connectedDebugAndroidTest
```

- Release: pushing a `v*` tag runs release verification, then uploads a signed AAB to the Google Play internal track ([docs/spec_release.md](docs/spec_release.md))

## Feedback

Questions and bug reports: [Issues](https://github.com/hwanyu365/page-tune/issues)
