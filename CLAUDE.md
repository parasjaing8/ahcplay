# AHC Player — Project Rules

## What this app is

One universal Android app — phone, tablet, TV — for browsing and playing video.
Primary path: video served over **SMB** from an AiHomeCloud NAS. Secondary path:
fully standalone playback of local device storage, with no server at all.

UX target: Netflix/JioHotstar-grade browsing, VLC-grade player. Both, in one app.

**Universal, not per-device forks.** One APK, one Play listing. Layout adapts by
window size class and input model, never by maintaining parallel apps.

---

## Non-negotiables

1. **No user data leaves this machine.** Never send file names, library contents,
   media, NAS hostnames/IPs, share names, credentials, tokens or screenshots of
   personal content to any external LLM, API or SaaS. Research prompts to external
   models must be *generic technical questions* only. Local models (`:8083`) are the
   exception and are still not a licence to ship personal data anywhere.
2. **No copyrighted artwork redistribution.** Posters and stills are owned works.
   Do not scrape image search results and ship them. Use a licensed metadata API on
   the user's own device+key, or generate thumbnails locally from the file itself.
3. **libVLC stays LGPL-linked.** Never paste VLC's GPLv2+ *app-layer* code (layouts,
   menus, UI classes) into this repo — it would force the whole app to GPL and kill a
   closed-source Play release. Reimplement behaviour instead.
4. **Secrets never enter git.** `release.jks`, `keystore.properties`, `local.properties`
   stay ignored. Run a secrets grep over the diff before any commit that touches
   auth, credentials, or distribution config.

---

## Device testing protocol (mandatory)

Physical devices are shared, real hardware — treat them accordingly.

| Device | Address | Notes |
|---|---|---|
| Lenovo tablet TB311FU | `HNQ018GD` (USB) | no PIN, USB debugging on — primary test device |
| Fire TV AFTMM | `192.168.0.62:5555` | API 25 (Android 7.1.2, `mantis`), D-pad reference device. Address corrected 2026-08-05 — `.214` was documented but `.62` is what answers; DHCP presumably moved it, so re-check with `adb devices` rather than trusting either number. |

**Open:** `adb devices` → confirm target → `input keyevent 224` (WAKEUP, explicit;
never rely on `26` which toggles).

**Close out — every session, without exception:**
1. `adb shell am force-stop <pkg>`
2. `adb shell input keyevent 26` (blank screen)
3. `adb shell dumpsys power | grep mWakefulness=` → must read `Asleep`/`Dozing`
4. `ps -A | grep <pkg>` → nothing left running

A screen left awake heats the device for hours. The task is not done when the code
works; it is done when the device is back at rest.

**Efficiency rules:**
- Batch taps on-device when racing an auto-hide timer:
  `adb shell "input keyevent 23; sleep 0.6; input tap X Y"`. Per-command ADB latency
  loses the race otherwise.
- `uiautomator dump` fails with *"could not get idle state"* against continuously
  animating UI, and a stale local XML may be read instead. Verify the dump actually
  succeeded before trusting it; fall back to screencap.
- Prefer `dumpsys` state assertions over long screenshot chains — fewer round trips,
  less screen-on time.

---

## Verification standard

A change is "done" only when all four hold:

1. `./gradlew assembleDebug` green (use `compileDebugKotlin` for fast type-check).
2. Installed on a real device.
3. The **actual user path** exercised end to end — not just the screen that changed.
   For playback work that means: browse → select → play → seek → resume.
4. Device returned to rest per the protocol above.

Green tests are not verification. Every genuinely valuable bug this project has found
— the touch-input gate, the tap-to-show-controls gap, the manifest form-factor
filter — was invisible to compilation and only surfaced on hardware.

Report honestly: if a device was unavailable (e.g. Fire TV offline → no D-pad
coverage), say so explicitly rather than implying full verification.

---

## Resource use

Run the delegate-or-do gate before dispatching anything (see global `CLAUDE.md`).

- **Inline** — ≤ ~15 lines, judgment-heavy work, exact-fidelity edits, anything
  touching architecture or security.
- **`smart-dispatch`** — bulk mechanical, multi-file, build/test-checkable. Near-zero
  marginal cost, separate billing. Default for volume.
- **Claude subagents / forks** — cost this subscription's tokens. Reserve for work
  genuinely needing inherited context, or a multi-model council the user asked for.

Check worker capacity before long autonomous runs:
`cat ~/life/dev/infra/llm-gateway/ledger.json`.

---

## Build

```bash
./gradlew assembleDebug            # APK -> app/build/outputs/apk/debug/
./gradlew compileDebugKotlin       # fast type-check only
adb -s HNQ018GD install -r app/build/outputs/apk/debug/app-debug.apk
```

- Package: `com.aihomecloud.ahcplayer` (`.debug` suffix on debug builds)
- minSdk **23** — always check new `java.*` APIs against it. `java.util.Base64` is
  API 26 and has already caused one release-blocking crash.
- Gate anything API 26+ (PiP, etc.) on both SDK level *and* the relevant
  `PackageManager` feature — TV devices lack features phones have.

## Commit protocol

commit → push → append `kb/session_logs.md` → update `kb/status.md`. Never batch,
never push without logging. Also log the session to `~/life/claudelogs/` and append a
row to `SESSIONS.md`.

Keep files under 500 lines. Read before editing. Remove only the dead code your own
change created.
