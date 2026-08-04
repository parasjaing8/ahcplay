# Play Store TV submission — what is ready, and what needs you

**Phase 3 milestone 5.** Status: **prepared, not submitted.**

I have taken this to the edge of submission and stopped there deliberately. Publishing creates a
public listing under your developer identity, and the remaining steps are attestations about how
this app handles data — those are yours to make, not mine to guess. Everything below the line is
verified; everything above it needs you.

---

## Needs you

| Item | Why it cannot be automated |
|---|---|
| **Privacy policy URL** | Play requires a reachable URL. The app talks only to a server on the user's own LAN, which is unusually easy to describe honestly — but it has to be published somewhere and be your statement. |
| **Data safety form** | A declaration of what is collected and shared, legally attributable to you. The truthful answer here is close to "nothing leaves the device or the home network", and it should be answered from that, not from a template. |
| **Content rating questionnaire** | Answers about user-generated content and media playback. An app that plays arbitrary user files needs care here. |
| **Target audience & app category** | Affects policy obligations, including families policy. |
| **Store listing copy and screenshots** | Draft copy below; TV screenshots need capturing from a real session, and any frame from this household's library shows personal media. |
| **The submission itself** | Outward-facing and hard to reverse. |

---

## Verified ready

Measured from the manifest and build on 2026-08-05, not assumed:

| Requirement | State |
|---|---|
| `LEANBACK_LAUNCHER` intent category | Present |
| `android:banner` on `<application>` | Present |
| Banner asset **exactly 320×180** | `drawable-xhdpi/banner.png` — 320×180, compliant |
| `uses-feature leanback` `required="false"` | Present — keeps the phone/tablet listing valid |
| Touchscreen declared not required | Present — mandatory for TV, since TVs have none |
| No portrait/orientation lock | Confirmed absent |
| `INTERNET` permission | Present |
| `targetSdk` | 35 |
| `minSdk` | 23 — deliberate, see `PLATFORM_SUPPORT.md` |
| Signing config | Present in `app/build.gradle` |

**The app itself is verified working on real TV hardware.** Full path exercised on the household
Fire TV Stick 4K (API 25) on 2026-08-05: discover → profile → browse three levels → play → seek
→ exit → resume. libVLC decoded 1920×1088 with audio active, both seeks landed, and reopening
resumed at 82.6 s rather than restarting.

---

## Blocking, and easy to miss

**`versionCode` is 1 and `versionName` is "1.0".** These are the defaults and have never been
incremented. Play permanently rejects a `versionCode` it has already seen, so the first upload
fixes this number forever. Decide it deliberately before the first upload rather than after.

**No TV screenshots exist yet.** Play requires at least one 16:9 TV screenshot. They must not
contain personal media — capture against a folder with impersonal content, or seed a demo
library. The `entertainment/Anime` folder on the test board is empty and would serve, once it
has neutral content in it.

---

## Draft listing copy

Yours to edit — written to be accurate rather than promotional, and it deliberately does not
claim Fire TV support, which is sideload-only.

**Title:** AHC Player

**Short description (80 char max):**
> Play the videos on your own home server. Nothing leaves your network.

**Full description:**
> AHC Player plays the video already stored on your own AiHomeCloud server or SMB share, on your
> television.
>
> Your library stays where it is. There is no cloud account, no upload step, and no copy of your
> media on anyone else's computer — the app connects to a server on your own network and streams
> from it directly.
>
> • Browse by profile, so each person in the house sees their own library
> • Resume where you left off
> • Full playback control — seek, audio and subtitle tracks, playback speed
> • Works with AiHomeCloud servers and plain SMB shares
> • Also runs on phones and tablets from the same install
>
> A profile can be protected with a PIN. Profiles that are protected are marked on the selection
> screen so it is clear at a glance which are which.

**Category:** Entertainment · **Content rating:** to be determined by the questionnaire

---

## Order of operations, when you pick this up

1. Set a deliberate `versionCode` / `versionName`.
2. Capture TV screenshots against impersonal content.
3. Publish a privacy policy and complete the data-safety and content-rating forms.
4. Create the Play listing, upload a signed release build, target the **TV category only**.
5. Internal test track first. The main AiHomeCloud app's open-testing track is currently blocked
   on a country-availability setting; check this listing's track countries at creation rather
   than discovering the same problem after upload.

---

# Correction 2026-08-05 — this is not a one-week task

Written before we knew the developer account is subject to Google's closed-testing requirement
for personal accounts created after 2023-11-13:

> Before you can apply for production access, you need to run a closed test which meets our
> criteria: publish a closed testing release, have at least **12 testers opted in**, and run
> that test for at least **14 days**.

**This applies per app.** `ahcplayer` is a separate listing on the same account, so it needs its
own closed test with its own 12 testers for its own 14 days — the AiHomeCloud app's progress
does not carry over.

That also means **open testing is unavailable for this app** until its production access is
granted, exactly as on the phone app.

## Revised order of operations

1. Set a deliberate `versionCode` / `versionName` (still 1 / "1.0").
2. Capture TV screenshots free of personal media.
3. Complete App content: privacy policy, data safety, content rating, target audience.
4. Create the listing and publish to **internal testing** — unrestricted, immediate.
5. Publish a **closed test** and recruit **12 testers who actually opt in**.
6. Wait **14 continuous days** with those 12 opted in.
7. Apply for production access.
8. Only then are open testing and production reachable.

The engineering work is finished and verified on real hardware. **What remains is a recruitment
and waiting problem**, and the 14-day clock cannot start until the twelfth tester opts in.

Worth deciding deliberately: whether this app needs a public listing at all in the near term, or
whether internal testing plus sideloading to the household Fire TV is sufficient for now. The
latter is available immediately and costs nothing.
