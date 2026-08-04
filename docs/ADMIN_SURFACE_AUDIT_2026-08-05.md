# TV app — administrative surface audit

**Phase 3 milestone 3.** Date: 2026-08-05. Result: **two findings, both fixed** (`e6c4fe4`
onward). The app is now consumption-only in code, not only in intent.

The architecture decision states the TV app ships "consumption-only, zero administration". This
audit asked whether that is true of the code, not of the screen names.

---

## Method

Screen inventory alone would have passed this audit and been wrong. The finding was in the data
layer. So the audit was run against capability, in three passes:

1. **Every HTTP method the app can issue** against the server — the real boundary, since
   administration is something you do *to the server*.
2. **Administrative vocabulary** across all Kotlin sources (`admin`, `reboot`, `format`, `mount`,
   `systemctl`, `wifi`, `bluetooth`, `firmware`, `backup`, `user create`, `storage`, …).
3. **Trace of every write path** to what actually calls it.

---

## Screens — all seven, all consumption or local configuration

| Screen | Purpose | Administers the server? |
|---|---|---|
| `HomeScreen` | Sources, discovery, entry to settings | No |
| `DiscoverScreen` | Find servers on the LAN | No — local only |
| `SetupScreen` | Add an SMB source (host, share, credentials) | No — stores a local source row |
| `SettingsScreen` | Sources, Display, Data, About | No — all local state |
| `ProfileSelectScreen` | Choose a profile | No |
| `PinAuthScreen` | Authenticate as that profile | No |
| `BrowseScreen` | Browse and play | No |

"Clear Watch History" (Settings → Data) deletes local resume points only. The Display pane added
in milestone 1 changes local input behaviour. Nothing writes to the server.

---

## The server surface, after the fixes

Four declarations, one of which writes:

| | Endpoint | Nature |
|---|---|---|
| GET | `/api/health` | Unauthenticated identity probe |
| GET | `/api/v1/auth/users/names` | Profile list, unauthenticated by server design |
| POST | `/api/v1/auth/login` | Establishes **its own** session |
| GET | `/api/v1/files/list` | Read |

The app cannot modify anything on the server except by logging in as a profile. That is the
strongest form this claim can take, and it is now enforced by the absence of the capability
rather than by the server refusing it.

---

## Finding 1 — an administrative pairing path (fixed)

`AhcRepository.autopair()` fetched `/api/v1/pair/qr` and paired with the key it contained. The
backend's own docstring for that endpoint:

> this response includes the pairing key and a fresh OTP in plaintext, which together are enough
> for a full unconditional-admin pairing

A consumption-only app should not contain that capability in any state.

**I first recorded this as dead code, and that was wrong.** `getOrFetchToken` fell back to it
whenever no profile had been chosen; my grep excluded the file the caller was in, and the
compiler caught it when I deleted the function. It had never *succeeded* — the server serves
that endpoint to loopback only and 403s the LAN — so the branch produced an opaque HTTP error
rather than a token. The administrative intent was real; only its success was missing.

Removed, with the three DTOs that existed only to serve it. The no-profile branch now names the
actual constraint. **The important part is that it survived by the server's guard, not by this
app's design.** Had that loopback check ever been relaxed, a TV in the living room would have
silently held unconditional admin.

## Finding 2 — Discover's sweep was dead, silently (fixed)

`probeHost` called the same loopback-only endpoint, so Discover's subnet sweep got 403 for every
address and returned null for all of them. The screen reported nothing found, indefinitely.

It was invisible because the *home* screen's discovery uses a different mechanism — a plain TCP
port check in `LanScanner` — which works. One discovery path worked, one never could, and they
looked the same from outside.

Now uses `/api/health`. As a side effect the label improves: the user's actual device name
instead of a serial reformatted to resemble one.

## Finding 3 — a backend bug, found from here

`/api/health` and `/` returned the install-time default name while `/api/v1/system/info`
returned the renamed one. A board renamed `radxa-nas` announced itself as `My AiHomeCloud` on
the endpoint whose whole stated purpose is telling devices apart. Fixed in the backend repo and
deployed to all three boards; verified `radxa-nas` / `Cubie1` / `My AiHomeCloud`, the last being
the control that was never renamed.

---

## What this audit does not cover

- **SMB sources.** Credentials for a user-configured SMB share are stored and used to mount that
  share. That is access to a file server, not administration of AiHomeCloud, and it is the
  app's standalone path working as designed.
- **Local device storage.** `StorageHelper` reads USB and internal storage on the TV itself.
- **Whether the server's own authorisation is correct.** This audit covers what the client can
  ask for. What the server permits is the backend's own security review.

## Keeping it true

The capability audit is the part worth repeating, and it is two greps — the HTTP method
inventory and the write-path trace at the top of this document. A new endpoint in
`AhcApiService.kt` is the only way this regresses, and `scripts/check_ahc_contract.py` already
prints every declaration it checks on each run, so the list is visible without extra tooling.
