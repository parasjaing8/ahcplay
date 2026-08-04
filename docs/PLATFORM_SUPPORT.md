# Platform support and its limits

**Phase 3 milestone 6.** A written acknowledgment, not an engineering plan. No work is implied
by this document — it exists so the boundary is stated somewhere other than in someone's head.

---

## Supported

| Platform | How | Notes |
|---|---|---|
| Android TV / Google TV | Play Store, TV category | The distribution target. |
| Fire TV (Android-based) | Sideload | `minSdk 23` covers API 25. Amazon devices have no Play Store. |
| Android phone / tablet | Play Store | Same APK; layout and input adapt. |

`minSdk` is **23 and deliberately held there.** The household reference device is a Fire TV
Stick 4K (`AFTMM`) on API 25. Raising the floor to match the phone app's 26 would drop the only
television this project can actually test on, in exchange for nothing.

---

## Not supported: Vega OS

Amazon is moving new Fire TV hardware to **Vega OS**, which is not Android. It runs a React
Native / web application model and cannot load an Android APK. This app will not run on it, and
no amount of Android-side work changes that.

**This is a statement of fact, not a roadmap item.** Supporting Vega would mean a second
application in a different language, on a different store, for a platform whose installed base
in this household is currently zero.

### Where that leaves existing devices

Verified on the household device, 2026-08-05:

```
ro.product.model        AFTMM
ro.build.version.sdk    25          (Android 7.1.2)
ro.com.amazon.vega      (empty)     ← not a Vega device
```

Devices already on Android-based Fire OS keep working. Amazon has not indicated it will convert
existing hardware, and it could not do so without breaking every Android app on those devices.
The exposure is to *future* Fire TV purchases, not to what is installed now.

### If it matters later

The trigger to revisit is a Vega device entering the household, or Fire TV mattering enough
commercially to justify a second codebase and a second store relationship. Both are decisions,
not engineering tasks, and neither is close.

---

## Store listing note

The Play listing covers Android TV / Google TV. It should not claim Fire TV support: Fire TV is
reached by sideload, and advertising a platform users cannot install from the listing they are
reading is a support burden with no upside.

Amazon Appstore submission was considered and declined in
`aihomecloud/docs/DECISION_tv_platform_target_2026-08-05.md` — the short version is that it buys
reach on a device class Amazon is winding down.
