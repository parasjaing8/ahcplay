# Handover — resume point (2026-08-04, autonomous block)

Read `kb/pending.md` for open bugs and `kb/roadmap.md` / `docs/EXECUTION_PLAN_2026-08.md`
(in the aihomecloud repo) for sequencing. This file is only "where to pick up".

## Landed this block (all committed, none pushed)

| Commit | What |
|---|---|
| `3a9938f` | TMDB removed entirely (-250 lines). Package `data/tmdb` -> `data/metadata`. Verified on device: settings pane gone, no crash. |
| `2b5264a` | Fire TV D-pad: Host/IP field now reachable; RIGHT crosses columns; initial focus; discovered host prefills the form. **Compile-verified only.** |
| `183e744` | Bookmarks + Save Playlist. Room 7->8 with a real migration, verified by SQL execution and against Room's generated expectations. |

Also in `aihomecloud`: `eb8844b` disconnection telemetry (Phase 0 milestone 1),
`26e4624` architecture decision + business strategy + 24-month execution plan.

## Next actions, in priority order

1. **Consume the server thumbnails.** This is the unfinished half of removing TMDB —
   posters are currently letter tiles. The pieces already exist and compile:
   `AhcImageLoaders.forHost(...)`, `ahcThumbnailUrl(host, port, nasPath, size)`,
   `AhcRepository.imageClientFor(host)`. What remains: have `BrowseViewModel` set
   `posterUrl` to `ahcThumbnailUrl(...)` for AHC-sourced items, and pass the per-host
   `ImageLoader` into the three `SubcomposeAsyncImage` call sites
   (`BrowseScreenRails.kt:531`, `BrowseScreenHero.kt:84`, `HomeScreen.kt:211`).
   Per-host loaders are deliberate — TOFU pins are per-device, so one shared client
   cannot pin correctly.
2. **Route AHC hosts to the profile flow.** Tapping a discovered card with
   `hasAhc = true` currently lands in the SMB form. Branch on it.
3. **Fire TV D-pad pass.** Fire TV is at `192.168.0.62:5555` (moved from `.214`).
   Test Name/Host/Share/Connect both directions and RIGHT into "Add SMB Source".
   Drive it with `adb shell input keyevent 19/20/22/23` — a mouse click puts the
   window in touch mode and hides focus bugs.
4. **Land the telemetry in a build actually used daily.** It sits in the aihomecloud
   debug build, which nobody opens, so it collects nothing. Phase 0 cannot conclude
   until it ships in the build in real use.

## Environment
- Tablet `HNQ018GD` over USB. Fire TV `192.168.0.62:5555`.
- Close-out is mandatory: force-stop, `input keyevent 26`, confirm
  `dumpsys power | grep mWakefulness=` reads Asleep/Dozing.
- `ahcplay` gradle tasks are plain (`assembleDebug`). `aihomecloud/android` has
  product flavors — use `assemblePlayDebug`, not `assembleDebug`.
- Agent worktrees under `.claude/worktrees/` can be pruned; their work is merged.

## Traps hit this block, do not repeat
- Agents told "do not commit" leave work **uncommitted in the worktree** — merging
  their branch brings nothing. Apply with `git diff --cached` from the worktree instead.
- `mapfile` is bash-only; this shell is zsh.
- `git apply` is atomic — truncating its output with `head` hides the error that
  rolled back the whole patch.
