# Pogo Keyboard: vetting report

Written 2026-10-27 at the end of the first overnight build. Each claim below says how it was
checked: **measured** (run on an emulator or in a test), **reviewed** (read in code, by Claude
and/or the Codex rival reviewer), or **not verified**.

## Verdict

Good for daily sideloaded use, with one real unknown: it hasn't been tried inside Pokémon GO
itself (see "Not verified"). The app is small, has no dependencies or permissions, and survived
three Codex review rounds. Round 1 found three real bugs, round 2 found none, and round 3 found
three bugs in the new slide gesture plus three overstated claims in this report. All are fixed;
every code fix has a regression test.

## Footprint (measured)

| Metric                                  | Value                                             |
| --------------------------------------- | ------------------------------------------------- |
| Release APK                             | **58.6 KB** (53 KB download)                      |
| Play bundle (`.aab`)                    | 103 KB                                            |
| Runtime dependencies                    | **0** (tests only: JUnit, org.json, Robolectric)  |
| Permissions                             | **0**                                             |
| Network access                          | None (no `INTERNET` permission)                   |
| Background work / services              | None besides the IME itself                       |
| Memory, keyboard open (debug build)     | 52 MB PSS · 11 MB Java heap · Gboard on the same emulator: 135 MB PSS |
| Leak check: 60 show/hide/tab-switch cycles | Views and Java heap flat (92 views, ~10.9 MB heap) |

Most of that 52 MB is the fixed cost of any Android app process. What Pogo adds on top is roughly
the keyboard's ~50–90 live Views and a 7 KB parsed catalog.

## Performance (measured, with caveats)

* **Keyboard build:** `onCreateInputView` takes 10–14 ms in a warm process and 35 ms right after
  a force-stop. That's a debug build on an emulator, so a release build on a real phone should be
  faster. The view is built once and reused on every later show; it's rebuilt only after a
  settings change, or when recents changed and you're on the Symbols tab.
* **Drawing:** the symbol grid is a single Canvas View, not ~70 child Views. `onDraw` allocates
  nothing; text measurement and ellipsizing happen at layout time.
* **Data:** the 7 KB of JSON is parsed once per process, on first use, and shared by the
  keyboard and the settings screen.
* **Frame timing** from the emulator (software GPU) isn't meaningful and wasn't used as evidence.
  To measure on a real phone: `adb shell dumpsys gfxinfo app.pogopaste.keyboard reset`, use the
  keyboard, then run `dumpsys gfxinfo` again.

## Robustness and resilience (measured + reviewed)

* **Bad data can't brick the keyboard.** Malformed JSON, missing fields and empty symbols are
  skipped per entry (unit-tested). An empty catalog shows a note instead of crashing.
* **Unicode-safe.** Alternates and user symbols split by grapheme, so surrogate pairs, emoji and
  combining marks work (unit-tested). Backspace deletes one whole grapheme, replaces a selection
  if there is one, and falls back to a `DEL` key event for editors that hide their text.
* **Null `InputConnection`:** every commit and delete path checks for it. Codex added 21
  temporary Robolectric checks in round 2 (null connections, selection replacement, null editor
  text), and all passed.
* **Slow editors: not handled specially.** Backspace and `&` joining read the text before the
  cursor, which is a synchronous IPC. Codex measured an editor that answers in 150 ms blocking the
  key for that long. Every keyboard pays this cost, and normal editors answer in a few ms, but it
  hasn't been profiled inside Pokémon GO.
* **Slide-to-pick:** only the finger that started the press can finish it. Only an alternate
  that's actually visible in the strip, and under or just below it, can be picked. A cancelled
  gesture inserts nothing and clears its highlight. Covered by Robolectric tests.
* **Lifecycle:** backspace auto-repeat and the long-press timer stop when the view detaches or the
  press ends, so nothing can type into the next field. Settings changed mid-session apply on the
  next fresh show, never mid-typing.
* **Android versions:** run on API 24 (7.0, minSdk), 28 (9) and 36 (16) emulators, in portrait,
  landscape, light and dark. One real bug turned up and was fixed: a blank strip under the keys on
  Android 7–9, caused by the legacy inset API.
* **Edge-to-edge (Android 15+):** the keyboard pads for the nav bar using typed insets, and the
  settings screen pads for system bars and the keyboard.
* **Landscape:** the keyboard is capped at ~55% of screen height so the text field stays visible.
* **Switching:** 🌐 goes to the previous keyboard. On Android 9+ it falls back to "next keyboard"
  and then the picker when there's no history; on 7–8 it falls back straight to the picker. A full Gboard → Pogo → Gboard round trip was verified on
  API 36 (one tap each way) and on API 24 (legacy token path).
* **Accessibility:** every key is a TalkBack virtual node with focus, click, long-click and hover
  hit-testing, and Delete is clickable. Covered by Robolectric tests on API 24/28/35, but not yet
  tried with TalkBack on a device.

## Tests

* 36 JVM/Robolectric test runs (the Robolectric suites run on API 24, 28 and 35). They cover data
  parsing, user entries, grapheme handling, `&` joining, every layout × tab building, paged
  overflow, accessibility focus/click, the Delete click, and slide-to-pick (commit, lift outside
  the strip, second finger, cancel). The slide tests were confirmed to fail on the pre-fix code.
* `lintDebug`: 0 errors. The 5 remaining warnings are version nags (newer Gradle, compileSdk 37,
  newer test libs).

## Not verified, and what to try first

1. **Inside Pokémon GO.** The emulators have no Play Store or game. PoGo's text fields are native
   Android `EditText` overlays on top of Unity, so `commitText` should behave normally. Check:
   * the nickname field: insert, backspace, the 12-character limit with multi-codepoint symbols;
   * the storage search field: tap a search string, Enter/Search, `&` joining.
2. **A physical phone:** feel, haptics, frame timing.
3. **TalkBack**, live.

## Before the Play Store

* **Name and icon (biggest risk).** "Pogo" and the ball-style icon point at Pokémon/Niantic
  trademarks. Play's impersonation and IP policies make this a likely rejection. Pick a neutral
  name and icon for the store listing; sideloading has no such issue.
* **Account:** a $25 Play developer account plus identity verification. New personal accounts must
  run a closed test with 12+ testers for 14 days before production.
* **Signing:** create an upload keystore (`android/keystore.properties`, see README), enroll in
  Play App Signing, then upload `./gradlew bundleRelease`.
* **Listing:** a privacy policy URL (one paragraph on the Netlify site: "collects nothing, no
  network"), a Data safety form (no data collected), and screenshots (`docs/layouts.png` is a
  start).
* Android shows its standard "this keyboard may be able to collect all the text you type" warning
  for every third-party keyboard. That's expected. Pogo's zero permissions are the answer to it.

## Sideloading in the future

Google's developer-verification requirement (announced 2025) is rolling out country by country
from late 2026. It will require apps installed on certified Android devices to come from a
registered developer, with a free tier for hobbyists. As announced, `adb install` from your own
computer stays exempt, so the workflow in the README keeps working. Re-check the current rules
before handing the APK to friends.
