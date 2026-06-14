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

