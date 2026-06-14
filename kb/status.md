# Project Status

- Branch: `main` at `41f8c0e` (`ahcCodex` last synced at `027e68a`)
- Explore/Browse TV UX modernization: implemented, reviewed, unused imports cleaned up
- Who's Watching / Home screen redesign (JioHotstar-style profile picker): implemented, installed and verified on Fire TV
- Settings: macOS-style sidebar/detail layout (Sources, Data, TMDB, About) with user-configurable TMDB API key; "Rescan" (metadata cache clear) moved into TMDB pane. AhcTextField D-pad focus fix (highlight-only on focus, DPAD_CENTER opens keyboard) applied app-wide. Installed on Fire TV, not e2e-tested per new workflow rule. Open: confirm Save/Clear button taps work via real remote (not just ADB).
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
- Known tooling issue: Android lint crashes in Lifecycle and Compose detectors due to a Kotlin analysis API incompatibility.
- Follow-up: split `BrowseScreen.kt` (1377 lines) into smaller files (hero/rails/overlay).
- Remotes: `origin` = `chaitraparas/ahcplay` (private, push requires `gh auth switch --hostname github.com --user chaitraparas`); `parasjaing8/ahcplay` (public) mirrors the same branches.

