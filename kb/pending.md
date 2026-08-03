# Pending Tasks

## Play Store submission
- [ ] **Play Console setup** — create app listing (manual web UI, no API for new apps),
  Android TV form factor, content rating questionnaire, data safety section (declare
  TMDB network calls + AHC NAS connection, no PII collected). Can draft data-safety
  answers from `kb/store/listing.md` + privacy policy ahead of time.
- [ ] **Internal testing track** — upload `app-release.aab` (rebuilt 2026-06-14 with
  the Base64 fix), add internal testers, before closed/open testing -> production.

## Code quality (not Play Store blockers)
- [x] **Split `BrowseScreen.kt`** (1377 lines) into hero/rails/overlay files
  (>500-line CLAUDE.md guideline). Done 2026-06-15 on
  `refactor/browsescreen-split-a11y` (`3f64d07`): `BrowseScreen.kt` (502),
  `BrowseScreenHero.kt` (287), `BrowseScreenOverlay.kt` (183),
  `BrowseScreenRails.kt` (563). Note: `BrowseScreen.kt`/`BrowseScreenRails.kt`
  still marginally over 500 lines — optional further split of
  `OtherFilesRail`/`StaticFileCard`/`PosterImage` into `BrowseScreenStatic.kt`
  if it matters later.
- [x] **Full accessibility pass** — `contentDescription` for icon-only buttons
  (play/pause, rewind, fast-forward, audio/sub track, search toggle, poster cards,
  settings sidebar items). Done 2026-06-15 on `refactor/browsescreen-split-a11y`
  (`d834065`): 5 player `ImageButton`s in `activity_player.xml` got
  `contentDescription` (play/pause synced dynamically), `PosterCard`/
  `FolderCard`/`ContinueCard` got `Modifier.semantics { contentDescription }`.
  Search toggle/settings sidebar already had visible text labels — no action
  needed there.

## Testing
- [ ] **E2E D-pad walkthrough** of internal/USB storage source flow on Fire TV
  (postponed 2026-06-14; storage-permission dialog + local library browse not yet
  verified end-to-end).

## Open bugs (2026-08-03)
### Found on Fire TV AFTMM (now at 192.168.0.62:5555, was .214), API 25
- [ ] **Host/IP field unreachable by D-pad on Add SMB Source.** Once focus reaches
  the Share field, UP does not move to Host. Verified with `uiautomator dump`:
  Host is `focusable=true focused=false` while Share holds focus, and focus is
  unchanged after an isolated UP press — so it is a traversal failure, not an
  unfocusable field. This blocks adding an SMB source on TV. Needs explicit
  `focusProperties { up = ... }` wiring in `AhcTextField` / SetupScreen.
- [ ] **RIGHT does not cross from the left column to the right panel** on the
  Add Source screen; "Add SMB Source" is only reachable via DOWN-then-RIGHT.
- [ ] **Nothing has focus on entering the SMB form** — Compose does not auto-focus;
  needs `FocusRequester` + `LaunchedEffect` per TV screen.
- [ ] **Old AHC discovery reports "No devices found" on Fire TV**, while the new
  `LanScanner` on Home finds four AHC hosts on the same network at the same moment.
  `DiscoverViewModel.probeHost` (port 8443) is failing where a plain TCP connect
  succeeds — likely the TLS probe, not reachability. Consider replacing that scan
  with `LanScanner`.
- [ ] **Fire TV full-screen IME traps text entry.** ENTER activates the highlighted
  on-screen key instead of the field's imeAction, so `input text` lands in whichever
  field opened the IME. Affects automated testing, and means the IME "Next" button
  is the only reliable field advance.

- [ ] **Discovery cards do not prefill.** "Tap to add" on a discovered host opens the
  Add Source screen without carrying the IP across, so the user still types it.
  Needs the host plumbed through the nav route into `SetupScreen`.

## Findings from the 2026-08-04 autonomous block
- [ ] **Debug builds silently wipe on a bad migration.** `AppDatabase` applies
  `fallbackToDestructiveMigration()` when `BuildConfig.DEBUG`, so a broken migration
  destroys sources/history/metadata instead of throwing. That means a migration bug
  looks like success during development and only surfaces in release. Left as-is
  deliberately — removing it makes debug builds crash on schema mismatch, which is
  correct but is a workflow change worth a conscious decision. Decide, don't drift.
- [ ] **Discovered AHC hosts now route to the SMB form.** `LanHost` carries `hasAhc`,
  and a host with `hasAhc = true` was previously reachable via the Discover screen's
  AHC path (-> ProfileSelect). Tapping its card on Home now sends it to the SMB form
  instead. Should branch on `hasAhc` and route AHC hosts to the profile flow.
- [ ] **D-pad fixes are compile-verified only** — needs a Fire TV pass on
  Name/Host/Share/Connect traversal both directions, and RIGHT from a device card
  into "Add SMB Source".
- [ ] **v7 -> v8 upgrade never run on a populated device database.** Verified by SQL
  execution and by matching Room's generated expectations, but not in place on hardware.
- [ ] **Server thumbnails are wired but not consumed.** `AhcImageLoaders` +
  `ahcThumbnailUrl` + `AhcRepository.imageClientFor` exist; the browse layer does not
  yet request them, so posters remain letter tiles. This is the replacement for TMDB.
