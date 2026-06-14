# AHC Player — Pre-Play-Store Production Readiness Audit

**Date:** 2026-06-14
**Package:** `com.aihomecloud.ahcplayer` · versionCode=1 / versionName="1.0"
**Scope:** read-only audit of 33 Kotlin files + manifest/gradle/res, branch `main` with uncommitted same-day feature work
**Verdict:** **NOT shippable as-is.** Multiple hard release blockers (no signing config, TLS verification globally disabled, TMDB key compiled into the binary, ProGuard rules will strip Retrofit/Gson reflection models and crash release builds).

---

## 1. Executive Summary

AHC Player is functionally rich and the UX layer is unusually polished for a v1 (the Browse hero/rail composition, JioHotstar-style "Who's Watching" profile ring, per-screen border-highlight focus). But the app is **not in a submittable state**, and several blockers are of the "release build crashes on launch or never builds at all" variety, not cosmetic. The single most dangerous class of issue is the gap between what works in `debug` (where `minifyEnabled=false`) and what ships in `release` (where R8 runs against a 4-line keep file that does not cover Retrofit/Gson/OkHttp/Coil). There is currently **no `signingConfig`** anywhere in `app/build.gradle`, so a release AAB cannot even be signed for upload without adding one — this alone forces a build-config change before submission.

On security, three issues compound. (1) The TMDB v3 key is wired from `local.properties` into `BuildConfig.TMDB_API_KEY` at build time (`app/build.gradle:26`) and is therefore embedded in the shipped APK/AAB — trivially extractable with `apktool`/`jadx`. The user has explicitly flagged this as a blocker. (2) Far more serious and **not previously on the radar**: `buildUnsafeTrustingClient()` in `AhcApiService.kt:90` installs a trust-all `X509TrustManager` plus a `hostnameVerifier { _, _ -> true }` for **every** call to the AiHomeCloud backend — pairing, profile login, PIN auth, and file listing. This disables TLS authentication entirely and makes NAS pairing tokens and PINs MITM-interceptable on any shared network. Google Play actively scans for exactly this pattern (unsafe `TrustManager`/`HostnameVerifier`) and issues policy warnings/rejections for it. (3) The `EncryptedSharedPreferences` token store silently downgrades to plaintext on any exception (`AhcRepository.kt:22`).

Crash-safety and data-correctness are mostly acceptable but have sharp edges: `fallbackToDestructiveMigration()` is active in production (`AppDatabase.kt:51`), so any future un-migrated schema bump silently wipes the user's sources/history/metadata; the storage-permission flow has an empty result callback (`MainActivity.kt:48`) and requests at `onCreate` with no rationale and no denied-state handling; and a `MediaMetadataDao.deleteAll()` runs `DELETE FROM media_metadata` inside `MIGRATION_5_6` (correct here, but note all metadata is intentionally dropped on that upgrade). Store-listing readiness is the easiest bucket to close: assets are single-density (`banner.png` 320×180, `ic_launcher.png` 96×96), there is no privacy policy, and the Data Safety form has not been considered despite the app sending filenames to TMDB and pairing/credentials to the NAS.

**Bottom line:** roughly 5 P0 fixes (signing, TLS, TMDB key, ProGuard, destructive-migration) gate submission. Budget a focused day for P0 + the listing assets/privacy-policy/Data-Safety paperwork, plus a release-variant smoke test on the Fire TV (critical — debug has never exercised R8).

---

## 2. P0 — Must Fix Before Submission

### P0-1 · No signing config → release AAB cannot be produced for Play
**Where:** `app/build.gradle:29-38` (no `signingConfigs {}` block; `release` buildType has no `signingConfig`)
**Why it blocks:** Play requires a signed AAB (or Play App Signing with an upload key). Currently `./gradlew bundleRelease` produces an unsigned artifact that the Play Console will reject at upload. There is also no `bundle {}`/AAB intent and the project only documents APK builds.
**Fix:**
- Add a `signingConfigs.release` reading keystore path/passwords from `local.properties` or env (never commit the keystore or passwords).
- Wire `signingConfig signingConfigs.release` into the `release` buildType.
- Enrol in **Play App Signing**; keep the upload key offline/backed up.
- Build with `./gradlew bundleRelease` (AAB) for Play, not `assembleRelease` (APK). Per the project's own CLAUDE.md, prefer `expo prebuild`-free pure-Gradle here since this is native Kotlin (no Expo).

### P0-2 · TLS verification globally disabled for the AHC backend (MITM)
**Where:** `AhcApiService.kt:90-108` (`buildUnsafeTrustingClient`), used by `buildAhcRetrofit` (`:110`) on every AHC call — pairing (`AhcRepository.kt:50`), profile login (`:79`), token fetch, file listing (`:93`), subnet probe (`:60`).
**Why it blocks:** A trust-all `X509TrustManager` (empty `checkServerTrusted`) plus `hostnameVerifier { _, _ -> true }` means any device on the LAN/Wi-Fi can MITM the HTTPS :8443 channel and capture **pairing tokens, profile access tokens, and user PINs** (PIN is POSTed in `AhcLoginRequest`). This is both a real credential-theft vector and a Play "unsafe SSL/TLS implementation" policy flag that Google's pre-launch scanners catch.
**Fix (pick one, in order of preference):**
- **Best:** ship the NAS's self-signed CA / leaf cert as a `res/raw` pin and configure a Network Security Config (`<certificates src="@raw/ahc_ca"/>`) or OkHttp `CertificatePinner`. The NAS serial is already known at pair time — pin per-device if the cert is per-device.
- **Acceptable interim:** since the backend is a known self-signed appliance, install a custom `TrustManager` that validates against the **bundled** NAS cert only (not trust-all) and keep proper hostname verification. Trust-all + hostname bypass must both go.
- Whatever path: remove `hostnameVerifier { _, _ -> true }` unconditionally.

### P0-3 · TMDB API key compiled into the shipped binary
**Where:** `app/build.gradle:26` → `BuildConfig.TMDB_API_KEY`; consumed as fallback in `MetadataRepository.kt:27` and surfaced in `SettingsScreen.kt:99,362`.
**Why it blocks:** Even though `local.properties` is correctly gitignored (`.gitignore:3,10`) and not in git history, the key string is embedded in the release AAB and extractable via `jadx`. The user has explicitly mandated it not be hardcoded. A leaked TMDB key can be rate-limited/revoked, degrading every user.
**Audience-tuned remediation (tech-savvy NAS owners):**
- **Recommended:** **drop the built-in default entirely.** Set `buildConfigField` to `""` for release, and require each user to paste their own free TMDB v3 key on first run. The Settings TMDB pane (`SettingsScreen.kt:345-386`) and verify badge already exist — promote it to a first-run gate when no key is present, and update the "Using built-in default key" status string (`:362`) which becomes dead once the default is removed. This audience can obtain a free key in two minutes; zero server cost.
- **Alternative:** proxy TMDB `search/multi` + `authentication` through the AiHomeCloud NAS backend (the app already talks to it). The key lives server-side, never ships, and you gain a caching layer. Costs a small backend endpoint; best long-term but more work.
- Do **not** rely on obfuscation/string-splitting — that is not a real mitigation for an extractable secret.

### P0-4 · ProGuard/R8 keep rules insufficient → release build will crash on JSON/HTTP paths
**Where:** `proguard-rules.pro` (4 lines); `app/build.gradle:35` (`minifyEnabled true`, release only). Debug has `minifyEnabled=false`, so **this has never been exercised**.
**Why it blocks:** R8 will strip/rename reflection-accessed members. The current keeps only cover `org.videolan.**` and `com.aihomecloud.ahcplayer.data.**`. That `data.**` keep does cover the AHC + TMDB model classes (they live under `data/ahc` and `data/tmdb`), which is good — but Retrofit/OkHttp/Gson themselves need rules, and Gson's `@SerializedName` reflection on those models needs the annotation+field kept. Without proper rules, expect `IllegalArgumentException`/`JsonSyntaxException`/missing-converter crashes on the first network call in a release build.
**Fix — add (R2.11 / OkHttp 4.12 / Gson 2.11):**
```
# Retrofit 2.11
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# OkHttp / Okio
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Gson — keep @SerializedName fields on model classes
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
# Coil (uses OkHttp; mostly fine but quiet warnings)
-dontwarn coil.**
```
Room and Compose-TV ship their own consumer ProGuard rules and don't need additions. **Mandatory validation:** build `bundleRelease`/`assembleRelease`, install on the Fire TV, and exercise AHC pairing + TMDB lookup + SMB browse before submission. The debug build proves nothing here.

### P0-5 · `fallbackToDestructiveMigration()` active in production
**Where:** `AppDatabase.kt:51`
**Why it blocks:** Currently masked because all migrations 4→7 are present, but it's a latent landmine: any future schema bump shipped without a matching `Migration` silently drops the whole DB (sources, watch history, metadata cache) on update with no warning. For a shipping app this is a data-loss bug waiting for the next release.
**Fix:** Remove `.fallbackToDestructiveMigration()` for release builds. If you want a safety net during early development, gate it behind `BuildConfig.DEBUG`. Also flip `exportSchema = true` (`AppDatabase.kt:13`) and commit the generated schema JSON so future migrations are diffable and testable.

---

## 3. P1 — Should Fix Soon

### P1-1 · EncryptedSharedPreferences silently falls back to plaintext
**Where:** `AhcRepository.kt:15-25`
**Detail:** On any exception during keystore/`EncryptedSharedPreferences` init, the code logs `Log.w` and returns a plaintext `MODE_PRIVATE` store holding NAS pairing + profile tokens. On a healthy Fire TV (API 25) this path is unlikely, but a corrupted keystore would silently persist bearer tokens in cleartext under `/data/data/.../shared_prefs/ahc_tokens.xml`.
**Fix:** Don't silently downgrade security. Either fail closed (surface "secure storage unavailable, cannot pair") or, at minimum, set a flag and show a one-time warning. Tokens are long-lived credentials, not cache.

### P1-2 · Storage permission flow: empty callback, no rationale, no denial handling
**Where:** `MainActivity.kt:47-48` (empty `registerForActivityResult` lambda), `:52` (request fired in `onCreate`), `:76-85`.
**Detail:** The permission is requested immediately at launch with no rationale UI, and the result is discarded. If the user denies `READ_MEDIA_VIDEO`/`READ_EXTERNAL_STORAGE`, INTERNAL/USB browsing (`LocalFileBrowser`, `StorageHelper`) will silently return empty lists with no explanation. On Android TV the system permission dialog is also awkward; many TV media apps defer the request until the user actually opens a local source.
**Fix:** Request lazily when the user first selects an INTERNAL/USB source; handle the denied branch with a visible "grant storage access to browse local files" state and a re-request affordance. Note `READ_MEDIA_VIDEO` justification will be required on the Data Safety/permissions declaration (see P0 listing checklist).

### P1-3 · TMDB key stored in plaintext SharedPreferences
**Where:** `AppPreferences.kt:9` (`MODE_PRIVATE`, unencrypted), set from `SettingsViewModel.saveTmdbApiKey()`.
**Detail:** Lower severity than the tokens (a TMDB read key is low-value and user-owned), but once P0-3 makes user-supplied keys the norm, this becomes the primary place the key lives. Acceptable to leave, but prefer moving it into the same `EncryptedSharedPreferences` mechanism for consistency.

### P1-4 · `logging-interceptor` dependency present; verify it never leaks the key
**Where:** `app/build.gradle:88`; `TmdbApi.kt:111-113` sets `Level.NONE` (good).
**Detail:** Currently the only `HttpLoggingInterceptor` is on the TMDB client and is hard-set to `NONE`, so the `api_key` query param is not logged. The AHC client (`buildUnsafeTrustingClient`) has no logging interceptor at all. So there's no active leak today — but the dependency is shipped and a future `Level.BODY`/`BASIC` toggle would dump the TMDB key (it's a `@Query` param) and AHC bearer tokens. Guard any logging level behind `BuildConfig.DEBUG` and consider dropping the dependency if unused.

### P1-5 · `fetchMetadata` mutates a `Map` via copy-on-write under concurrent coroutines
**Where:** `BrowseViewModel.kt:87-89` launches one child coroutine per video item, each calling `fetchMetadata` (`:99-102`) which does `_metadata.value = _metadata.value + (filename to meta)`.
**Detail:** N concurrent `read-modify-write`s on a `StateFlow<Map>` without synchronization will drop updates (last-writer-wins races), so some posters intermittently won't appear until re-browse. On a large folder this also creates N simultaneous TMDB calls with no concurrency cap or debounce — rate-limit risk against TMDB and a burst of work on first open.
**Fix:** Accumulate via `MutableStateFlow.update { it + (k to v) }` (atomic) or collect into a `ConcurrentHashMap`; cap concurrency with a `Semaphore` (the Discover scan already uses this pattern at `DiscoverViewModel.kt:53`).

### P1-6 · Library scan is unbounded depth-6 recursion with spinner-only feedback
**Where:** `LibraryScanner.kt:9,28` (MAX_DEPTH=6, recursive), triggered by `SettingsViewModel.rescanMetadata()` (`:146`).
**Detail:** On a large AHC/SMB library this walks every directory to depth 6 and fires a (serialized) TMDB lookup per video file via `metaRepo.get`. UX is a single `CircularProgressIndicator` (`SettingsScreen.kt:276-291`) with no count/progress and no cancel. On a big NAS this can run for minutes; the user has no signal it's progressing or how far. Also note: `rescanMetadata` does `deleteAll()` first (`:150`) then rescans — if the scan fails midway, the user is left with fewer posters than before.
**Fix:** Add a progress count (files scanned / found) and a cancel action; consider moving the scan to a `WorkManager` job so it survives navigation. Don't `deleteAll()` until the new scan has produced at least one row, or upsert-in-place instead of delete-then-fill.

### P1-7 · `StorageHelper.getUsbVolumes()` does blocking `File` I/O and may NPE-ish on `listFiles`
**Where:** `StorageHelper.kt:9-12`, called synchronously from `SettingsViewModel.getUsbVolumes()` (`:132`) on the main thread (it's invoked directly in the `UsbVolumePickerDialog` composition path, `SettingsScreen.kt:323`).
**Detail:** `File("/storage").listFiles { ... }` plus a nested `!f.listFiles().isNullOrEmpty()` per candidate is filesystem I/O on the UI thread; on a slow/again-mounting USB volume this can jank or ANR. The filter also calls `listFiles()` twice per dir.
**Fix:** Move to `Dispatchers.IO` (wrap in a suspend function / `viewModelScope` + state), and cache the single `listFiles()` result.

### P1-8 · `SmbBrowser` instantiates a fresh `LibVLC` per browse call
**Where:** `SmbBrowser.kt:19,42,51`
**Detail:** Each SMB directory listing creates and releases a whole `LibVLC` instance. Correct lifecycle-wise (released on `onBrowseEnd`/cancellation), but heavy — repeated navigation churns native instances. Also `onBrowseEnd` releases `libVlc` but if `browse` errors before `onBrowseEnd`, only the cancellation path releases it; a thrown exception inside the listener would leak. Lower priority since it's bounded by the 15s timeout.
**Fix:** Consider a shared/pooled `LibVLC` for browsing, or at least ensure release in all terminal paths.

### P1-9 · ExitDialog "No" button is not focusable / unreachable by D-pad
**Where:** `MainActivity.kt:218-231` — the "No" `Box` has `onFocusChanged`/`clickable` but no `FocusRequester`, while "Yes" grabs initial focus (`:235`). Neither Box is wrapped in a focusable container with explicit focus order.
**Detail:** With Compose `clickable`, the Box is focusable, so D-pad-right *should* reach "No" — but there's no guaranteed focus order and `noFocused` styling implies it's meant to be reachable. Verify on-device; if "No" can't be focused, back-to-cancel still works (`:132`) but the visible button is dead.
**Fix:** Confirm D-pad traversal Yes↔No on the Fire TV; add explicit `focusProperties { right = ... }` if traversal is unreliable.

### P1-10 · No accessibility content descriptions; icons are Unicode glyphs
**Where:** all `contentDescription = null` (`HomeScreen.kt:206`, `BrowseScreen.kt:328,1217`); glyph "buttons" like `"⚙ Settings"` (`HomeScreen.kt:136`), `"▶"` (`:478`), `"+"` (`:541`), `"✓"`/`"✕"` badges (`SettingsScreen.kt:287,400,406`), PIN `"⌫"` (`PinAuthScreen.kt:129`), `"🔒"`/`"📁"` (`HomeScreen.kt:344,357`).
**Detail:** TalkBack on Android TV reads nothing meaningful for these. Not a Play blocker, but a real accessibility gap and a content-rating/quality consideration. Decorative backdrop images correctly use `null`; interactive controls should not.
**Fix:** Add `contentDescription` / `Modifier.semantics { }` to interactive controls and poster images (e.g. the movie title).

---

## 4. P2 — Nice to Have / Tech Debt

- **Duplicated `mapSource()` logic** between `SettingsViewModel.mapSource` (`SettingsScreen.kt:60-67`) and the inline `when` in `HomeViewModel.sources` (`HomeViewModel.kt:33-42`) — same `SourceType` mapping written twice. Extract to a single `SourceEntity.toMediaSource()` extension in the `data` layer.
- **Dead/unused composable:** `LibraryGrid` (`BrowseScreen.kt:927`) is only referenced by `LibraryWithoutHero` (`:962`) — fine — but `OtherFilesRail`'s `StaticFileCard` is `clickable(enabled = false)` (`:912`), so it's focusable-but-inert; either make it non-focusable or give it an action.
- **`deleteSource` reconstructs a partial `SourceEntity`** (`SettingsScreen.kt:117-119`) omitting `enabled`/`createdAt`; works because `@Delete` matches on primary key, but fragile. Prefer deleting by id.
- **`AhcPairQrResponse.key` parses via `substringAfter("key=")`** (`AhcApiService.kt:23`) — brittle string parsing of a URL; use `Uri.parse(...).getQueryParameter("key")`.
- **`probeHost` swallows all exceptions to `null`** (`AhcRepository.kt:63`) — correct for a subnet sweep, but the 254-host scan with `Semaphore(30)` (`DiscoverViewModel.kt:53`) on Wi-Fi can be slow; consider mDNS/serial-based discovery if the NAS advertises.
- **`MetadataRepository.fallback` upserts a row even on TMDB failure** (`MetadataRepository.kt:51-53,56`) so a transient network error permanently caches a poster-less entry until a manual rescan. Consider not caching negative results, or caching with a short TTL (the `cachedAt` field exists but is unused for expiry).
- **`versionName "1.0"`** is fine; consider `1.0.0` for semver clarity. `applicationId` is sane and matches namespace.
- **`android:supportsRtl="false"`** (`AndroidManifest.xml:20`) — acceptable for a TV app but worth a conscious decision.
- **No `WatchHistory.id`/metadata `cachedAt` TTL**, `qualityLabel` substring matching can false-positive (e.g. "720p" inside an unrelated token) — minor.
- **`.kotlin/` build dir is untracked** (appears in `git status`) — ensure it's gitignored.

---

## 5. Play Store Submission Checklist

**Build & signing**
- [ ] Add `signingConfigs.release` (keystore creds from `local.properties`/env, never committed) and reference it from the `release` buildType. *(P0-1)*
- [ ] Enrol in Play App Signing; back up the upload key offline.
- [ ] Add the full Retrofit/OkHttp/Gson keep rules; build a **release** AAB and smoke-test AHC pair + TMDB + SMB on the Fire TV. *(P0-4)*
- [ ] Ship `bundleRelease` (AAB), not APK.
- [ ] Remove `fallbackToDestructiveMigration()` (or gate behind `BuildConfig.DEBUG`); set `exportSchema=true` and commit schema. *(P0-5)*
- [ ] Set release `BuildConfig.TMDB_API_KEY` to empty and gate first-run on user-supplied key (or proxy via NAS). *(P0-3)*

**Security (Play scanners will flag these)**
- [ ] Replace trust-all `TrustManager` + `hostnameVerifier{true}` with cert pinning / Network Security Config. *(P0-2)*
- [ ] Stop silent plaintext fallback for token storage. *(P1-1)*
- [ ] Confirm no logging interceptor leaks key/tokens in release. *(P1-4)*

**Data Safety form (mandatory)**
- Data collected/shipped off-device:
  - **To TMDB (`api.themoviedb.org`):** parsed **filenames** (as search queries) — `MetadataRepository.kt:32`. Disclose as "App activity / other" sent to a third party; not linked to identity; user can disable by not supplying a key (after P0-3).
  - **To the user's own AiHomeCloud NAS:** pairing handshake, profile name, **PIN**, bearer tokens, file paths. This is the user's own server, but the transmission still must be disclosed (credentials/personal info). Encryption-in-transit answer must be truthful — currently TLS is **not verified** (fix P0-2 before claiming "data encrypted in transit").
  - No analytics/ads SDKs present (good — verify no transitive trackers via the dependency report).
- [ ] Declare data is **not** sold/shared for ads; describe deletion (uninstall clears local DB/prefs).

**Privacy policy**
- [ ] Required because data is sent to a third party (TMDB) and credentials are handled. Host a short policy URL (the user owns `aihomecloud-website` / Hostinger) covering: what's sent to TMDB, that NAS credentials stay between app and the user's device, no third-party analytics, local-only storage.

**Content rating questionnaire (IARC)**
- [ ] App displays **TMDB-sourced posters and overviews**, which can include mature imagery/synopses for adult titles the user owns. Answer "user-generated / third-party content displayed" honestly; likely Teen+ depending on responses. The app itself ships no content.

**Store listing assets (currently incomplete)**
- [ ] **TV banner** is 320×180 (`drawable-xhdpi/banner.png`) — Play requires a **1280×720** TV banner for the Android TV listing. Provide it.
- [ ] **Launcher icon** is a single 96×96 `mipmap-xhdpi/ic_launcher.png`; no adaptive icon (`mipmap-anydpi-v26`) and no other densities. Add the **512×512** high-res icon for the listing, and ideally adaptive icon + mdpi–xxxhdpi densities. *(item 6)*
- [ ] **Screenshots:** minimum **1 TV screenshot** (1280×720 or 1920×1080) required; provide several (Home/Who's-Watching, Browse hero, Player, Settings).
- [ ] Feature graphic (1024×500) if using the standard listing.
- [ ] `strings.xml` has only `app_name` — fine for the app, but write the store description/short description separately.

**Manifest / config sanity (verified OK)**
- [x] `MainActivity exported=true` with only `LEANBACK_LAUNCHER` filter — correct & required for TV.
- [x] `PlayerActivity exported=false` — correct.
- [x] No `usesCleartextTraffic`, no `http://` literals — backend is HTTPS :8443 (but see P0-2: HTTPS without verification ≠ secure).
- [x] `leanback` required, `touchscreen` not required — correct for TV-only.
- [ ] `targetSdk 35` meets the current Play target-API requirement; `minSdk 23` is fine. No change needed.
- [ ] `versionCode=1` correct for first upload.

**Runtime permission UX**
- [ ] Defer the storage permission request to first local-source use; handle denial with a visible state. *(P1-2)*

**Testing (currently zero coverage — `app/src/test` and `app/src/androidTest` do not exist)**
Recommended minimum viable additions (do not block submission, but add before second release):
- [ ] Unit: `TitleParser.parse` (filename→title/year), `AhcRepository.nasPathToSmb` & `parseAhcUri`, `BrowseItem.isVideo`, `WatchHistory.progressFraction`, `qualityLabel`.
- [ ] Room migration test (`MigrationTestHelper`) for 4→5→6→7 once `exportSchema=true`.
- [ ] Instrumented smoke test: launch `MainActivity`, assert Home renders without AHC; a `PlayerActivity` intent-extras null-guard test (`PlayerActivity.kt:92`).

---

### Files referenced (absolute paths)
- `/Users/parasjain/life/dev/apps/ahcplay/app/build.gradle`
- `/Users/parasjain/life/dev/apps/ahcplay/app/proguard-rules.pro`
- `/Users/parasjain/life/dev/apps/ahcplay/app/src/main/AndroidManifest.xml`
- `/Users/parasjain/life/dev/apps/ahcplay/app/src/main/kotlin/com/aihomecloud/ahcplayer/data/ahc/AhcApiService.kt`
- `/Users/parasjain/life/dev/apps/ahcplay/app/src/main/kotlin/com/aihomecloud/ahcplayer/data/ahc/AhcRepository.kt`
- `/Users/parasjain/life/dev/apps/ahcplay/app/src/main/kotlin/com/aihomecloud/ahcplayer/data/prefs/AppPreferences.kt`
- `/Users/parasjain/life/dev/apps/ahcplay/app/src/main/kotlin/com/aihomecloud/ahcplayer/data/db/AppDatabase.kt`
- `/Users/parasjain/life/dev/apps/ahcplay/app/src/main/kotlin/com/aihomecloud/ahcplayer/data/tmdb/{TmdbApi,MetadataRepository}.kt`
- `/Users/parasjain/life/dev/apps/ahcplay/app/src/main/kotlin/com/aihomecloud/ahcplayer/data/scan/LibraryScanner.kt`
- `/Users/parasjain/life/dev/apps/ahcplay/app/src/main/kotlin/com/aihomecloud/ahcplayer/data/source/{StorageHelper,SmbBrowser,LocalFileBrowser}.kt`
- `/Users/parasjain/life/dev/apps/ahcplay/app/src/main/kotlin/com/aihomecloud/ahcplayer/MainActivity.kt`
- `/Users/parasjain/life/dev/apps/ahcplay/app/src/main/kotlin/com/aihomecloud/ahcplayer/ui/{settings/SettingsScreen,home/HomeViewModel,browse/BrowseViewModel,browse/BrowseScreen}.kt`
- `/Users/parasjain/life/dev/apps/ahcplay/app/src/main/res/values/strings.xml`, `app/src/main/res/{drawable-xhdpi/banner.png,mipmap-xhdpi/ic_launcher.png}`
</content>
</invoke>
