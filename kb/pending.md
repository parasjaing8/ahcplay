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
