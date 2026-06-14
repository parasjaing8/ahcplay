# Project Status

- Branch: `main` at `231d7cc` (pushed to both `chaitraparas/ahcplay` and `parasjaing8/ahcplay`)
- Play Store readiness: P0 (blockers) and all 10 P1 audit items complete - TMDB key removed
  (confirmed absent from dex), AHC TLS TOFU-pinned, release signing configured (`release.jks` +
  `keystore.properties`, gitignored, **user must back up**), SecurePrefs consolidation, lazy
  storage permissions, rescan progress, LibVLC leak fix, a11y spot-fixes. Audit doc:
  `kb/ahcAudit14June.md`.
- New: internal/USB local storage source type (DB v7, `sources.enabled`, `LocalFileBrowser`,
  `LibraryScanner`, Flow-based Home library stats).
- Explore/Browse TV UX modernization: implemented, reviewed, unused imports cleaned up
- Who's Watching / Home screen redesign (JioHotstar-style profile picker): implemented, installed and verified on Fire TV
- Settings: macOS-style sidebar/detail layout (Sources, Data, TMDB, About) with user-configurable TMDB API key; "Rescan" moved into TMDB pane with live progress count. AhcTextField D-pad focus fix applied app-wide. Open: confirm Save/Clear button taps work via real remote (not just ADB).
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (installed on 192.168.0.214:5555)
- Release APK: `app/build/outputs/apk/release/app-release.apk` (now signed)
- Known tooling issue: Android lint crashes in Lifecycle/Compose detectors (Kotlin analysis API
  incompatibility) - worked around with `lint { disable += "NullSafeMutableLiveData" }`.
- Full a11y pass not done (lowest-severity items only - explicitly not a Play blocker per audit).
- Follow-up: split `BrowseScreen.kt` (1377 lines) into smaller files (hero/rails/overlay).
- Remotes: `origin` = `chaitraparas/ahcplay` (private, push requires `gh auth switch --hostname github.com --user chaitraparas`); `parasjaing8/ahcplay` (public) mirrors the same branches.

## Next: Play Store submission prep
- Back up `release.jks` + `keystore.properties` somewhere safe (not in git).
- Write store listing: short/full description, screenshots (TV banner 1280x720, feature graphic,
  phone/TV screenshots), privacy policy URL (app talks to user's own AHC NAS + TMDB - needs a
  privacy policy page).
- Create signed release bundle: `./gradlew bundleRelease` -> `app-release.aab`.
- Set up Play Console: Android TV form factor, content rating questionnaire, data safety section
  (declare TMDB network calls + AHC NAS connection, no PII collected).
- Internal testing track first, then closed/open testing before production.
- Optional: finish BrowseScreen split, full a11y pass, e2e D-pad walkthrough of new
  internal/USB source flow on Fire TV.

