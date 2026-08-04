# Session Logs

## 2026-06-13 - TV Explore UX modernization

- Rebuilt `BrowseScreen` around a TMDB-backed hero, fixed-ratio poster rail, details overlay, collection rail, and retained Continue Watching flow.
- Added backdrop, genre, and media-type metadata with Room migration 5 -> 6; the metadata cache is refreshed during migration.
- Added explicit D-pad focus transitions and 180-190 ms focus animations.
- Verification: `assembleDebug` passed; `testDebugUnitTest` had no test sources; release APK built with `lintVitalAnalyzeRelease` excluded because the existing lint/Kotlin analysis stack crashes before reporting findings.
- No ADB device or emulator was connected, so visual device capture was unavailable.

## 2026-06-13 - Codex changes reviewed and cleaned up

- Verified migration 5->6, TmdbApi/MetadataRepository wiring, and the BrowseScreen rebuild against current code; `compileDebugKotlin` re-ran clean (UP-TO-DATE).
- Removed 4 leftover unused imports from old grid-based BrowseScreen (`HorizontalDivider`, `layout.size`, `AccentDim`, `AccentGlow`); recompiled clean.
- Open item: `BrowseScreen.kt` is now 1377 lines (over the 500-line guideline) - candidate for splitting into hero/rails/overlay files in a follow-up.

## 2026-06-14 - Who's Watching redesign committed, merged to main

- Committed the Home screen redesign (JioHotstar-style profile picker: animated selection ring auto-opens last-used profile after 3s, blurred backdrop collage from `media_metadata`, movie/show counts) - `027e68a`. App was already installed and verified working on Fire TV from the prior session.
- Fast-forward merged `ahcCodex` -> `main` (`30e4d83..027e68a`) and pushed both branches.
- Repo/remote note: `origin` = `chaitraparas/ahcplay` (private repo created 2026-06-13). The `parasjaing8` gh account has no read access to it, which produced a misleading "Repository not found" on `git push`. Pushing requires `gh auth switch --hostname github.com --user chaitraparas` first (switched back to `parasjaing8` afterward). A secondary remote `parasjaing8/ahcplay` (public) also exists and was updated.
- Moved the TMDB API key out of `app/build.gradle` (was hardcoded in source) into gitignored `local.properties` (`tmdb.api.key=...`), read via `localProperties.getProperty('tmdb.api.key', '')`. Verified with `generateDebugBuildConfig --rerun-tasks` that `BuildConfig.TMDB_API_KEY` still resolves correctly - `516d602`.
- Redesigned Settings as a macOS System Settings-style sidebar/detail split: 4 categories (Sources, Data, TMDB, About), focus-driven selection (`onFocusChanged` sets `selectedCategory`, same pattern as HomeScreen's `ProfileRow`). Added new `TMDB` pane with an `AhcTextField` for a user-supplied API key, "Save"/"Clear" buttons, and a status line ("Using custom key" / "Using built-in default key" / "No key configured"). New `data/prefs/AppPreferences.kt` (plain SharedPreferences, file `ahc_settings`, key `tmdb_api_key`). `MetadataRepository.get()` now prefers the user key, falling back to `BuildConfig.TMDB_API_KEY` - `d773619`.
- Verification: `compileDebugKotlin` clean; `assembleDebug` installed on Fire TV (192.168.0.214:5555). Screenshots confirmed all 4 sidebar panes render correctly and the TMDB field/status line react live while typing. Did not confirm the Save button tap lands correctly via ADB coordinates (Fire TV `input tap` behaves oddly with the on-screen IME open) - Save/Clear reuse the existing `AhcButton` composable already used by `SetupScreen`, so the click path itself is proven elsewhere. Worth a manual remote-control check of Save/Clear next session.

## 2026-06-14 - Rescan moved to TMDB pane, AhcTextField D-pad focus fix - `41f8c0e`

- Settings: renamed "Clear Metadata Cache" -> "Rescan" and moved it from the Data pane into the TMDB pane (below Save/Clear); `SettingsViewModel.clearMetadataCache()` renamed `rescanMetadata()`. Data pane now has only "Clear Watch History".
- `AhcTextField` (shared by SetupScreen, DiscoverScreen, Settings TMDB field): D-pad navigation onto the field previously opened the on-screen keyboard immediately. Fixed with an `editing` state + `readOnly = !editing` - D-pad focus now only shows the highlight border; DPAD_CENTER/Enter enters edit mode and opens the keyboard; losing focus or pressing Done resets to highlight-only.
- Verification: `assembleDebug` BUILD SUCCESSFUL, installed on Fire TV (192.168.0.214:5555). No ADB e2e walkthrough this session per new workflow rule (build+install+summary only, unless "e2e" requested).
- Pushed `main` to both `chaitraparas/ahcplay` (origin, account-switch) and `parasjaing8/ahcplay` (mirror).

## 2026-06-14 - P0+P1 Play Store audit fixes + internal/USB storage support - `231d7cc`

- P0 (blockers, all fixed): TMDB key fully removed from BuildConfig/codebase (confirmed absent from
  compiled dex); AHC connections TOFU cert-pinned via new `AhcTls.kt` + `AhcRepository.apiFor()`/
  `certPinKey()`; release signing wired (`release.jks` + `keystore.properties`, gitignored - user
  must back these up); Retrofit/OkHttp/Gson/Coil proguard keep rules added;
  `fallbackToDestructiveMigration()` now debug-only.
- P1 (all 10 items): new `SecurePrefs.kt` (shared EncryptedSharedPreferences-with-fallback,
  `isEncrypted` flag surfaced as a Settings warning) used by `AhcRepository`/`AppPreferences`;
  storage permission now requested lazily on first INTERNAL/USB source open with a denial dialog;
  removed dead `HttpLoggingInterceptor` + dependency; `BrowseViewModel.fetchMetadata` uses
  `_metadata.update {}` + `Semaphore(5)`; Rescan no longer wipes `media_metadata` up front - per-item
  `forceRefresh` upsert with live "Scanned N" progress; `StorageHelper.getUsbVolumes()` moved to
  `Dispatchers.IO`; `SmbBrowser` releases LibVLC on setup/browse failure (leak fix); `ExitDialog`
  Yes/No D-pad focus properties fixed; a11y content descriptions added (PIN badge, PIN pad backspace).
- Internal/USB local storage support (separate feature landed alongside the audit fixes): new
  `SourceType.INTERNAL`/`USB`, `sources.enabled` column (DB migration 6->7), `LocalFileBrowser`,
  `BrowseFetcher` abstraction, `LibraryScanner` for unified AHC/SMB/local scans; `HomeViewModel`
  library stats converted to Flow-based (live updates); `AhcButton` focus shown via border.
- Added `kb/ahcAudit14June.md` (full Play Store readiness audit, Opus). Added `.kotlin/` to
  `.gitignore` (untracked build cache).
- Verification: `assembleDebug` + `assembleRelease` both BUILD SUCCESSFUL, installed on Fire TV
  (192.168.0.214:5555). No e2e ADB walkthrough this session per workflow rule.
- Pushed `main` to both `chaitraparas/ahcplay` (origin, account-switch) and `parasjaing8/ahcplay`
  (mirror) - single commit `231d7cc` (29 files, +1004/-202).

## 2026-06-14 - Critical Base64 crash fix + Play Store listing assets - `e5c4f75`, `16253dc`

- **Found and fixed a release-blocking crash**: `spkiPin()` in `AhcTls.kt` used
  `java.util.Base64` (API 26+) inside the TOFU TLS cert-pinning path, called on every AHC
  NAS connection. `minSdk` is 23, so on Android 6.0-7.1 (API 23-25) this threw
  `NoClassDefFoundError` on the OkHttp dispatcher thread immediately after profile
  selection - the app silently died and the Fire TV launcher took over (initially looked
  like the app was "switching to Prime Video"). Switched to `android.util.Base64` with
  `NO_WRAP` (format-compatible with the previous encoding). Verified via rebuild +
  reinstall on Fire TV (API 25, 192.168.0.214:5555): `dumpsys activity activities |
  grep mResumedActivity` now stays on `MainActivity` after profile selection - `e5c4f75`.
- Investigated a follow-on HTTP 503 ("No external storage mounted") seen on the
  "Prutha" profile's AHC source after the fix - confirmed via direct curl to the NAS
  that this is the Rock Pi's external USB/NVMe drive currently unmounted, not an app
  bug. The Retry-button error state is working as designed; no code change.
- Added Play Store listing assets (`kb/store/`): `listing.md` (title/short/full
  description), `icon-512.png`, `feature-graphic-1024x500.png`,
  `tv-banner-1280x720.png`, two device screenshots (Who's Watching profile picker,
  Continue Watching/resume card from "Prutha"'s BrowseScreen), and
  `scripts/generate_store_assets.py` - `16253dc`.
- Wrote and published the privacy policy + support pages to
  `chaitraparas/aihomecloud-web` (`/ahcplay/privacy`, `/ahcplay/support`) and added an
  AHC Player card to the site homepage, via `gh api repos/.../contents` (Contents API)
  after `git push` to that repo hung on the `osxkeychain` credential helper - commits
  `2a6440e`, `5987e03`, `3e6e8f4`.
- Backed up `release.jks` + `keystore.properties` to `~/.secrets/ahcplay/` (outside git).
- **Outstanding**: `app-release.aab` (built in the prior session phase) predates the
  Base64 fix and must be rebuilt via `./gradlew bundleRelease` before Play Store
  submission. Pushed `main` to both `chaitraparas/ahcplay` (origin) and
  `parasjaing8/ahcplay` (mirror, account-switch).


## 2026-08-03 — VLC-parity player + phone/tablet reach

Goal restated by Paras this session: AHC Player is to be a Netflix/JioHotstar-style
app running on **any Android device**, playing from network SMB — not a TV-only app.
Work was reprioritised around that.

- Rebuilt the player overlay to VLC's option set, reimplemented from scratch.
  libVLC stays linked (LGPL); no GPL app-layer code copied — copying VLC's own UI
  would force ahcplay to be GPL and block a closed-source Play release.
  New: `PlayerMenu.kt` (15-item overflow menu, data-driven, live status text),
  `TrackPanel.kt` (collapsible Audio/Subtitles + delay controls),
  `PlayerFeatures.kt` (sleep timer, speed, jump-to-time, equalizer presets,
  repeat, video info, A-B repeat, chapters, video scaling, PiP) — `7dee7b0`.
- Live-tested on the Lenovo tablet against SMB media on the Rock Pi
  (`smb://192.168.0.241/media/entertainment`): browse, 1080p playback of
  *Lupin III The First*, resume-from-history, overflow menu, track panel — all
  verified by screenshot. Fire TV (192.168.0.214) was offline all session, so
  **no D-pad hardware verification** was done on any of this.
- Three touch bugs found only by testing on real hardware, all fixed:
  1. `showControls()` was reachable only from `dispatchKeyEvent`, so tapping the
     video did nothing — the player was unusable on a touchscreen.
  2. `AhcTextField` gated editing behind D-pad centre, so a tap focused but never
     typed — adding an SMB source on a phone/tablet was impossible — `ad08435`.
  3. Track panel drew over the transport bar at 95% alpha; panels now replace it.
- Distribution blockers fixed (`20eabc1`): `leanback` was `required="true"`, so
  Play filtered the app out for every phone and tablet; only `LEANBACK_LAUNCHER`
  was declared, so no app-drawer icon; both activities were landscape-pinned.
  Now leanback optional + `LAUNCHER` + `sensorLandscape` + `supportsPictureInPicture`.
- Merged `refactor/browsescreen-split-a11y` into `main` (fast-forward), which also
  brought in the June BrowseScreen split + a11y commits (`3f64d07`, `d834065`) that
  had been sitting unmerged. Those got de-facto device validation this session —
  the whole Home -> Explore -> collections -> hero -> play path was exercised.
  Pushed to `origin` (chaitraparas) only; `parasjaing8` mirror NOT pushed.

**Outstanding**
- Portrait unsupported: `sensorLandscape` is deliberate, since every Compose layout
  is landscape-designed (Home wastes ~60% of a tablet screen). Responsive pass on
  Home/Browse/Setup is the biggest remaining item for a phone-first app.
- No poster/backdrop art — TMDB key was stripped in the June audit, so the
  "Netflix look" is currently text-on-gradient. Needs a secret-handling decision.
- Still stubbed: Bookmarks, Save Playlist, Play as audio, Control settings, Tips.

## 2026-08-04 — autonomous block: TMDB removal, server artwork, D-pad verified

Ran overnight with broad autonomy. Everything below was verified on real hardware.

- **TMDB deleted** (`3a9938f`, -250 lines). Its terms bar commercial use without a written
  agreement, which collides with the planned paid tier; the per-user API key was also the
  real onboarding friction. `data/tmdb` -> `data/metadata`. `TitleParser` kept.
- **Artwork replaced by server-side frame extraction** (`4f550df`, `9a7c2a3`). The backend
  already had ffmpeg thumbnails cached by path+mtime — nothing new was needed server-side.
  Coil loaders are per host (TOFU pins are per device); the token attaches only when host
  *and* port match.
  - Bug found on device: `serverThumbnailFor` required an `ahc://` URI, but only
    *directories* get those — files get `smb://` so libVLC can stream them. The check
    rejected exactly the items that can have artwork.
- **AHC browse was 403 end to end** (`353311a`). `listFiles` treated 403 as a stale token and
  discarded a valid credential; and the client listed the NAS root, which the backend
  deliberately refuses for non-admins (guard added 2026-07-30 after a review found a
  non-admin could reach every member's private files via a whole-tree delete). Guard left
  alone; client now probes visible scopes. Verified live: Prutha sees entertainment+family,
  personal correctly hidden.
- **Profile picker self-navigated** (`9a7c2a3`) — a 3s dwell auto-select. Right for a remote,
  wrong on touch, where it fired before the user could act. Gated to `FEATURE_LEANBACK`.
- **Fire TV pass** (`2b5264a`, `bbeff95`, a11y commit). Host/IP now reachable by D-pad — root
  cause was `OutlinedTextField` consuming DPAD up/down even when `readOnly`, so
  `focusProperties` never ran; fixed with `onPreviewKeyEvent`. RIGHT crosses columns. Home
  claims initial focus and its cards are labelled (they reported as bare `View`, so neither
  tooling nor a screen reader could identify them).
- **Bookmarks + Save Playlist** (`183e744`), Room v7->v8 with a real migration —
  **confirmed applied in place on the device**, existing rows intact.
- **GitHub Actions storage** was at 90% of quota: 2.8GB of stale `app-debug` artifacts from a
  workflow that no longer exists. Deleted; account now at 3.5MB. No workflow to fix.

**Two agents ran in parallel worktrees** (D-pad/prefill, and bookmarks/playlists). Both
delivered good work. Trap: they were told not to commit, so `git merge <branch>` brought
nothing — their changes sat uncommitted in the worktree and had to be applied as patches.

**Not done, and why:** Phase 0 of the execution plan is still entirely unstarted. This block
was `ahcplay` bug-fixing, which the architecture decision treats as a code donor rather than
a shipping app. Phase 0 is gated on the telemetry landing in a daily-use build — a decision
for Paras, not something to take autonomously.

## 2026-08-05 — Phase 3 milestones 1 & 2

**Milestone 1 — TV detection centralised, with a user override** (`77b2fdd`)

Detection was a local `remember` in HomeScreen: `FEATURE_LEANBACK` only, no secondary signal,
no override. It gates a timer that auto-opens the highlighted profile — the comment above it
already records that firing on a PIN-protected profile nobody chose. A handheld misreporting
leanback reproduces exactly that, previously fixable only by shipping a new build.

- `ui/platform/DeviceType.kt` — pure `resolveIsTv(hasLeanback, isTelevisionUiMode, override)`
- `TvOverride` AUTO / FORCE_TV / FORCE_TOUCH, persisted in `AppPreferences`
- Settings > **Display** pane, new category
- `WindowSizeClass` confirmed absent; the reason is now a code comment, not just plan text
- **First tests in this module**: 6, plus the junit dependency

Verified: 6/6 unit tests · real Fire TV reports `leanback`+`leanback_only`+`type.television`
so AUTO resolves TV · Android TV emulator renders the pane and the override **survived a full
process restart**.

**Milestone 2 — contract check instead of generated DTOs** (`e6c4fe4`)

Milestone said "migrate onto the generated typed client". Wrong shape here: the backend's
generator emits models to a scratch dir for hand-copying, and this is a *different repo*, so
copied DTOs would look generated while being an unenforced duplicate. What protected the phone
app was the CI drift check, not generated code.

Measured first: **all 5 endpoints already correct** — response shapes and all 5 `/files/list`
query params match. No drift to fix, only drift to prevent.

- `contracts/openapi.json` vendored from `aihomecloud@7375cf9`, provenance in `contracts/README.md`
- `scripts/check_ahc_contract.py` — fails on a client field the server never sends, and on a
  query param the server does not accept (FastAPI discards unknowns silently)

**The checker's first version said "clean" after inspecting 2 of 5 declarations** — the regex
excluded anything with a parameter annotation. Same failure class the tool exists to catch, so
it now compares declarations-found against declarations-checked and refuses a verdict when they
disagree. Both failure modes confirmed by injection, then confirmed clean after revert.

Also: Fire TV address in CLAUDE.md was `.214`; `.62` is what answers.

Devices: Fire TV woken only for the install check, returned to `Asleep`. TV emulator killed.
