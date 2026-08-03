# AHC Player — Roadmap to production

Synthesised from three independent research tracks (2026-08-03): universal-app
architecture, metadata/poster sourcing, and production player gap analysis.

---

## Architecture verdict

**One APK. Two UI layers. Shared ViewModels.**

The instinct to build a single universal app is correct and is what Google
explicitly recommends: *"We recommend that you have a single app that supports both
mobile devices and TV devices."* ([developer.android.com/training/tv/start/start](https://developer.android.com/training/tv/start/start))

But **one APK does not mean one UI**, and that distinction decides the whole design:

- `androidx.tv.material3.MaterialTheme` and `androidx.compose.material3.MaterialTheme`
  are **different, non-interoperable objects**. Google warns that mixing them "can
  result in unexpected behavior". A single shared component library that renders
  correctly on both is not achievable.
- **`WindowSizeClass` cannot detect a TV.** A 1080p TV at xhdpi reports ~960×540dp —
  Expanded width, byte-identical to a landscape tablet. Adaptive layout APIs alone
  will never tell the two apart. Detection must use `FEATURE_LEANBACK`.

What the field actually ships:

| App | Packages | Structure |
|---|---|---|
| VLC | `org.videolan.vlc` (one) | 1 APK, 2 UI library modules, runtime routing |
| Kodi | `org.xbmc.kodi` (one) | 1 APK, one UI everywhere — landscape-locked |
| Plex | one | unified codebase, deliberate rewrite |
| Jellyfin | mobile + androidtv | **two apps** — webview vs native, irreconcilable |
| Netflix | mediaclient + ninja | **two apps** — DRM/certification |

The split correlates with **UI-stack divergence, not form factor**. Our stack is
homogeneous (Compose), so one APK is right. Kodi is the cautionary case: one shared
UI is why Kodi on a phone feels like a TV app.

### VLC's model, which we should copy

Verified from `github.com/videolan/vlc-android`:
- One `com.android.application` module depending on two `com.android.library` UI
  modules (`:vlc-android` mobile, `:television` TV). Only flavor dimension is ABI.
- One transparent, no-UI trampoline `StartActivity` carrying **both** `LAUNCHER` and
  `LEANBACK_LAUNCHER`, routing via `setClassName` to the TV or mobile Activity.
- Detection runs **once** (`FEATURE_LEANBACK`, plus `!hasTouchscreen`), is then
  **persisted as a user-toggleable `tv_ui` preference**, and thereafter only the
  preference is read. A phone user can force TV UI and vice versa.

That persisted-override design is the part worth stealing: when a Fire TV or OEM box
misreports its capabilities, the user flips a switch instead of waiting for a hotfix.

Note VLC has **not** adopted Compose for TV — still Leanback, which is now deprecated
in favour of `androidx.tv`. We are already on Compose for TV, so we're ahead there.

### Target module layout

```
:core:data          repositories, SMB/AHC sources, DB          (no UI)
:core:player        libVLC engine, playback state machine      (no UI)
:core:domain        ViewModels, UiState, UiEvent               (form-factor blind)
:core:designtokens  colors/type/spacing as raw values          (NOT MaterialTheme)
:ui:tv              androidx.tv.material3, focus chains, landscape
:ui:mobile          compose.material3 + material3-adaptive, portrait + landscape
:app                single application module; trampoline; depends on both
```

Hard rule: `:core:domain` must not import `androidx.compose.*` or `androidx.tv.*`.

**Be honest about the payoff:** one APK saves distribution, store listing, crash
reporting, CI and the entire data/playback layer. It does **not** save UI work.

---

## Engine verdict

**Keep libVLC as the sole engine. Do not migrate to Media3.**

- libVLC renders ASS/SSA correctly via libass; Media3 has minimal ASS styling and no
  native PGS. libVLC handles AC3/DTS passthrough robustly; Media3 has open AC3/AC4
  decode issues across devices.
- Jellyfin removed libVLC from their TV client and broke hybrid Dolby-Vision+HDR10
  files, because ExoPlayer reads only the DV layer.
- A dual-engine hybrid is real precedent but doubles player-layer surface area (two
  track-selection paths, JNI lifecycle leaks) for edge-case coverage.

Cost of this choice: libVLC is not a Media3 `Player`, so MediaSession/Cast need a
hand-written adapter. Accept that. The one narrow exception worth revisiting later is
using Media3's `CastPlayer` *only* as a remote-cast bridge.

---

## Metadata verdict

**Local-first thumbnails as the baseline; TMDB as an enhancement layer.**

- **Do not scrape image search for posters.** Play's IP policy names "marketing images
  from movies, television, or video games" as a violation category; studio takedowns
  are routine. Runtime-fetch is not a safe harbour — our code selects the image, so it
  reads as direct infringement.
- **TMDB works from this network** (verified live: API 401-with-JSON, CDN 200, egress
  IN). Blocking is per-ISP (Jio/Airtel most often), inconsistent across their domains,
  and fluctuating. Mitigation: fail over to the official `api.tmdb.org` mirror, cache
  aggressively, never link users to `themoviedb.org`.
- **TMDB prohibits commercial use** without a written agreement — ad-supported, paid
  and IAP all count. Free-with-no-ads is fine. If we ever monetise, that's an email to
  TMDB or a move to TheTVDB (free under $50k/yr revenue).
- Local frame extraction has zero licensing exposure, works offline, and is the **only**
  thing that works for home videos no database has indexed.

---

## Phases

### Phase 0 — Prove the seam
Extract ViewModels and repositories into `:core:domain` / `:core:data` with **zero
behavioural change**. Fix any ViewModel holding a `FocusRequester`, list state, or
`LocalConfiguration`. This is the whole risk-reduction step — do not skip it. If a
ViewModel resists extraction, leave it and duplicate later rather than forcing a bad
abstraction.
*Verify:* existing TV app runs unchanged on hardware.

### Phase 1 — Packaging and routing
- Transparent trampoline Activity with both `LAUNCHER` + `LEANBACK_LAUNCHER`.
- Move `screenOrientation` off the shared manifest onto each Activity: TV `landscape`,
  mobile `unspecified`. (Current `sensorLandscape` on both is an interim state.)
- Detection via `FEATURE_LEANBACK` (+ `UI_MODE_TYPE_TELEVISION` secondary, model
  allowlist escape hatch), persisted as a `tv_ui` preference with a settings toggle.
- **CI check that greps the _merged_ manifest** for `required="true"` — a library
  silently reintroducing one removes the app from TV on Play.
*Verify:* single APK installs and launches on both tablet and Fire TV.

### Phase 2 — Fix real bugs (orthogonal, ship independently)
- **Audio focus** (`AudioFocusRequest`, pause on call, duck on notification) and
  **`ACTION_AUDIO_BECOMING_NOISY`** (pause on headphone unplug). Both are outright
  bugs today, not missing polish.
- **SMB buffer tuning.** Default read sizes over WiFi are pathologically small — a
  measured case showed 4,286-byte reads at 0.3 MB/s vs 32 KB reads at 5 MB/s, a 16×
  difference. Confirm our libVLC 3.6.x build actually links `libsmb2` (SMB2/3).
- **Gestures**: swipe volume/brightness, double-tap seek, pinch zoom. The most visible
  "feels amateur" gap versus VLC/MX Player.
- App-level SMB reconnect: catch network-origin errors and reopen the MRL at the last
  position; libVLC will not recover transparently.

### Phase 3 — Metadata foundation
Prerequisite for everything Netflix-shaped. Without it, browse is a prettier file list.
- Local thumbnail generation: seek 10–15% in, 3–5 candidates via `OPTION_CLOSEST_SYNC`
  (keyframe-aligned, avoids inter-frame decode), reject near-black by average luma,
  cache keyed on path+mtime, 2–3 background workers.
- Filename parser: port GuessIt's rule logic. Parse conservatively — title, year,
  season/episode only. Fuzzy-match, don't string-equal.
- TMDB client with `api.tmdb.org` failover, aggressive cache, mandatory attribution.
- Manual "wrong match?" override UI — Radarr/Sonarr still need one in production.
- Episode grouping (this is what unlocks next-episode and episode pickers).

### Phase 4 — Mobile UI layer
Build `:ui:mobile` as new code, screen by screen: **Settings → Browse → Detail →
Player last**. The player diverges most between form factors and is a rewrite, not a
port — budget it separately. Use `material3-adaptive` inside `:ui:mobile` only; it
genuinely earns its keep for phone↔tablet↔foldable and does nothing for TV.

### Phase 5 — Netflix-grade browse
Hero/billboard (rotating recently-added or Continue Watching), rails, skeleton
loading, prefetch next N rows, TV focus-scale via `animateFloatAsState` + `Surface`,
Continue Watching semantics (configurable thresholds — drop >~90–95%, hide <~2–5%),
next-episode autoplay with cancelable countdown, episode picker.

### Phase 6 — Background playback
MediaSession adapter wrapping libVLC's player, notification controls, foreground
service with `foregroundServiceType="mediaPlayback"` and
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`. **Play Console then requires a demo video**
showing background playback — process time, plan it before the release.

### Phase 7 — Play Console
Add Android TV form factor in Advanced settings; upload TV screenshots for **every**
store listing (hard gate). Adopt the dedicated Android TV track so a mobile hotfix
doesn't force a TV re-review. TV review covers the whole listing: if the trampoline can
route a TV device into the mobile UI under any path, TV-DP fails.

### Later / conditional
Chromecast via Media3 `CastPlayer` (before DLNA — jUPnP is legacy). External subtitle
sidecar loading + styling UI. Seek-preview thumbnails — decide on-device vs NAS-side
generation *first*, the naive approach is a battery cost discovered after shipping.
AC3/DTS passthrough tuning only if the library warrants it. OpenSubtitles only after
re-reading current ToS — the legacy free API closed to third parties ~Jan 2026 and the
replacement is non-commercial-only.

---

## Testing notes

- **Drive D-pad tests with `adb shell input keyevent 20/22/23`, never mouse clicks.**
  A mouse click puts the window into touch mode and masks focus bugs entirely.
- Compose gives no visible focus indication by default on TV — `Modifier.clickable` is
  reachable but invisible. `androidx.tv.material3.Surface` (Border/Glow/Scale) is the
  intended focusable primitive.
- Nothing auto-focuses; every TV screen needs `FocusRequester` + `LaunchedEffect`.
- Seekbars intercept D-pad left/right and trap focus — already hit in this project;
  player transport needs a hand-written key handler.
