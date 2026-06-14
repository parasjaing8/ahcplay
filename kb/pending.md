# Pending Tasks

## Play Store submission
- [ ] **Play Console setup** — create app listing (manual web UI, no API for new apps),
  Android TV form factor, content rating questionnaire, data safety section (declare
  TMDB network calls + AHC NAS connection, no PII collected). Can draft data-safety
  answers from `kb/store/listing.md` + privacy policy ahead of time.
- [ ] **Internal testing track** — upload `app-release.aab` (rebuilt 2026-06-14 with
  the Base64 fix), add internal testers, before closed/open testing -> production.

## Code quality (not Play Store blockers)
- [ ] **Split `BrowseScreen.kt`** (1377 lines) into hero/rails/overlay files
  (>500-line CLAUDE.md guideline).
- [ ] **Full accessibility pass** — `contentDescription` for icon-only buttons
  (play/pause, rewind, fast-forward, audio/sub track, search toggle, poster cards,
  settings sidebar items).

## Testing
- [ ] **E2E D-pad walkthrough** of internal/USB storage source flow on Fire TV
  (postponed 2026-06-14; storage-permission dialog + local library browse not yet
  verified end-to-end).
