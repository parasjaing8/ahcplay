# Vendored AiHomeCloud API contract

`openapi.json` is a **copy**, not a source. It is generated from the backend in the
`aihomecloud` repo (`contracts/v1/openapi.json`) and vendored here so this app can be
checked against the API it was built for, without needing that repo checked out.

| | |
|---|---|
| Source repo | `chaitraparas/aihomecloud-mobile` |
| Source path | `contracts/v1/openapi.json` |
| Vendored from commit | `7375cf96748e8b52a3e918c3a7a29b77b5f307b0` |
| Vendored on | 2026-08-05 |

## Why a copy rather than generated DTOs

The backend repo can generate Kotlin DTOs, but only into a scratch directory for hand-copying —
and this is a different repository. Copied DTOs would *look* generated while actually being an
unenforced manual duplicate, which earns more trust than it deserves. What protects the phone
app from drift is not generated code, it is a CI check that compares the hand-written client
against the contract. That check is what is reproduced here.

## Refreshing

    cp ../../aihomecloud/contracts/v1/openapi.json contracts/openapi.json
    python3 scripts/check_ahc_contract.py

Update the commit hash above when you do. A refresh that breaks the check is the check working:
it means the backend changed something this app depends on.
