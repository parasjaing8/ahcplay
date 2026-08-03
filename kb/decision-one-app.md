# Decision: one app, two UI layers, one engine

Date: 2026-08-03. Inputs: four-model council (architecture, UX/IA, dissent, independent
verdict) plus facts verified directly against the devices and codebases.

---

## Verdict

**Merge into the cloud app's package. One app, one Play listing, two UI layers selected
at runtime, libVLC as the single engine, minSdk dropped to 24.**

The player app's code moves in; its package is never published. It becomes a git tag.

---

## Why

**Verified facts that drove it**

| Fact | Source |
|---|---|
| Cloud app `minSdk 26`; the household Fire TV reports API 25 → it cannot install there today | read from both `build.gradle.kts` and `getprop` |
| Cloud app declares no TV support at all — no leanback feature, no leanback launcher, no banner | its `AndroidManifest.xml` |
| The Fire TV is **not** abandonware: Fire OS 6.7.1.1, security patch 2025-11-01, build 2026-03-21 | `getprop` on the device |
| Compose for TV works on that exact stick — D-pad focus on new cards verified visually | on-device test, same day |
| libVLC 3.6.3 ships libsmb2 (SMB2/3) | 636 symbol hits in the AAR |
| 6 MB is Play's per-device split of a 23 MB build | local APK vs Play listing |

**Evidence from the council**

- **Nobody splits admin from player.** Every comparable product — Jellyfin, Plex, Immich,
  Nextcloud, Synology — puts appliance administration in a *web dashboard*, not a second
  mobile app. The axis others split on is device class or content type, never
  management-vs-consumption. So ~13 administrative screens are not an argument for two
  apps; they are an argument that those screens must never render on a TV.
- **Plex split by content type in Sept 2024 and reversed it by July 2025** after backlash.
  The failure mode is instructive: the split-out apps did not exist on TV, so TV users lost
  half the product.
- **VLC is the reference implementation** of exactly this target: one APK, shared logic,
  a distinct TV view layer. Google published it as a case study.
- **Jellyfin's two apps are a technical accident** — its phone client is a WebView wrapper
  around the web UI, which TV boxes cannot run. Not an architecture preference, and not our
  situation.
- **Play supports one package with a dedicated TV track**, so TV ships independently of
  phone without a second listing. This removes the only strong pro-split argument.

**The objection that dissolved.** `androidx.tv.material3.MaterialTheme` and
`androidx.compose.material3.MaterialTheme` being non-interoperable is a constraint on
*mixing inside one composition tree* — not on co-existing in one APK. Two entry Activities,
two theme roots, two source sets: they never meet. That is what makes this merge cheap, and
it is the thing most people get wrong.

**Engine: libVLC only.** Two engines means two seek-bug surfaces, two subtitle pipelines,
two audio-focus implementations. Jellyfin shipped dual-engine and documented the cost (JNI
lifecycle leaks, two track-selection paths). Keep Media3 purely as the *session* surface:
subclass `SimpleBasePlayer` to wrap libVLC and hand it to `MediaSession` — that is precisely
what `SimpleBasePlayer` exists for, and it buys notification controls, Cast and Auto without
a second decoder stack.

**Direction of merge.** The cloud app carries the Play listing, install base, ratings and
update path. Those are the only assets in this problem that cannot be rebuilt by typing.

---

## The dissent, and what survives of it

The dissenting voice argued for two apps over a shared core, and that scope — not
architecture — is the likely killer. Its strongest practical claim, that the Fire TV is near
end-of-support, is **refuted** by the patch data above.

But its central warning stands and is adopted here: the highest-probability failure is not a
wrong technical call, it is one person maintaining six products until nothing ships. Every
stage below is therefore independently shippable and phone-neutral until Stage 5.

Its sequencing point is *rejected* on the architecture voice's reasoning: extracting a clean
shared-core module **before** merging is the most reliable way for this to stall at 60%.
Extract from observed duplication, last, not speculatively first.

---

## Staged plan

**Stage 0 — de-risk (half a day, ships alone).** In the cloud app: `minSdk 24`, enable core
library desugaring, run lint, fix every `NewApi` hit. Zero behaviour change. 24 not 23: it
clears Fire OS 6 with margin, keeps the runtime-permission model as the floor, and does not
reach down to Fire OS 5 (API 22), a tier others have already abandoned.

**Stage 1 — one package, two entry points.** Add a TV launcher Activity with
`LEANBACK_LAUNCHER`, `leanback` and `touchscreen` as `required="false"`, and a TV banner.
Phone launcher untouched. Route on `FEATURE_LEANBACK`, never `WindowSizeClass` — a 1080p TV
at xhdpi is indistinguishable from a landscape tablet. Persist the choice as a user-toggleable
preference (VLC's model) so a misreporting box is fixed by flipping a switch.
*Checkpoint: phone users see no change; the TV shows an icon in the leanback launcher.*

**Stage 2 — port the engine.** libVLC + the finished player move in as a `:player` module.
Write the `SimpleBasePlayer` → libVLC adapter, hook `MediaSession`. Wire the **TV** path to it
first; the phone's Media3 path stays untouched.
*Checkpoint: TV plays an mkv with ASS subs and AC3 audio, with working transport controls.*

**Stage 3 — port the TV UI.** Browse/rails/hero/profile-select land as `:tv-ui`, compiled
against `androidx.tv.material3` only. Enforce with a CI grep that fails the build if
`androidx.compose.material3` appears in that source set, and vice versa — that one guard stops
the theme hazard recurring as a subtle bug.

**Stage 4 — first TV release** on Play's dedicated TV track, same package, same listing.

**Stage 5 — cut phone over to libVLC**, only after the adapter has soaked on TV.

**Stage 6 — extract shared modules**, driven by duplication you can actually see.

---

## What gets cut

- **Admin never renders on TV**: storage, wifi, backup, sync, power, factory reset, installer,
  services, system info, trash. No D-pad work, no TV layouts, no testing. Factory reset must
  not be one wrong click from Continue Watching.
- **No second Play listing.** Two ratings pools and a permanent "which app do I need?" question.
- **No Amazon-specific work** — no Amazon IAP, no Appstore flavour, no Vega port. Amazon has
  confirmed all future Fire TV Sticks run Vega OS, which does not run Android apps and does not
  permit sideloading. Android-on-Fire-TV is a terminal platform: do the cheap thing for the
  stick that exists, target Google TV / Android TV strategically.
- **No rails below ~20 items in a library** — fall back to a chronological grid. Netflix's rail
  grammar signals abundance; a "Comedy" row with three posters reads as broken.
- **No dual-engine production path.**

## Sharing model

Neither Plex Home's model (switching profile makes you *become* that user, including server
settings) nor Immich's partner sharing (all-or-nothing, one-way, timeline duplicates). Use
Jellyfin-style enumerated per-library grants with a **default-private** posture: a new folder
is never auto-shared. Profile switching should *feel* like Netflix and *behave* like Jellyfin.
State in-app that a profile PIN is a convenience lock, not encryption.

## Corrections to earlier claims in this project

- **Media3 does decode PGS natively** (`PgsDecoder`, since ExoPlayer 2.11). The real problems
  are rendering bugs and PGS not surfacing under `MediaSessionService`. The ASS/SSA styling gap
  is confirmed and open since Jan 2021. The engine conclusion is unchanged; the PGS premise was
  overstated.
- The earlier roadmap assumed the player app was the vehicle. It is not — its *code* is.
