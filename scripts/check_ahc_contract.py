#!/usr/bin/env python3
"""
Check this app's AiHomeCloud client against the vendored API contract.

Why this exists rather than generated DTOs: the phone app in the backend repo was not saved
from drift by generated code, it was saved by a check like this one. Its `SystemInfo` declared
`hostname` for a field the server has always sent as `name`, so it deserialised to null on every
board and every call site quietly fell back to a constant. Nothing failed. Tests passed. A
comment in the file recorded the mismatch and left it unfixed for months, because a comment in
a file nobody re-reads is not a reminder. A failing build is.

Three classes of finding, only one of which fails the build:

  EXTRA   — the client declares a field the server never sends. This is the dangerous one and
            the only hard failure. The field is silently null or default forever, and the
            symptom appears far from the cause.

  UNKNOWN QUERY PARAM — the client sends a query parameter the server does not accept. FastAPI
            discards unknown params without complaint, so a renamed parameter looks like the
            feature quietly reverting to defaults. Also a hard failure.

  MISSING — the server sends a field the client does not read. Legitimate and common; a TV app
            has no use for most of the payload. Reported, never fatal.

Usage:  python3 scripts/check_ahc_contract.py
Exit:   0 clean, 1 findings, 2 could not run
"""

from __future__ import annotations

import io
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "contracts" / "openapi.json"
SERVICE = (
    ROOT / "app" / "src" / "main" / "kotlin" / "com" / "aihomecloud" / "ahcplayer"
    / "data" / "ahc" / "AhcApiService.kt"
)

# The HTTP annotation that opens a declaration. Everything up to the *next* one is that
# declaration's block — parameters, their own annotations and all.
HTTP_ANNOTATION = re.compile(r'@(GET|POST|PUT|DELETE|PATCH)\("([^"]+)"\)')
# `): ReturnType` closing a declaration.
RETURN_TYPE = re.compile(r'\)\s*:\s*([A-Za-z0-9_<>, ?]+)')
QUERY = re.compile(r'@Query\("([^"]+)"\)')
# `data class Name(` ... matching close paren
DATACLASS = re.compile(r'data class (\w+)\s*\(', re.S)
# a property line, optionally preceded by @SerializedName("wire_name")
PROPERTY = re.compile(
    r'(?:@SerializedName\("([^"]+)"\)\s*)?(?:val|var)\s+(\w+)\s*:',
)


def die(msg: str) -> None:
    print(f"cannot run: {msg}", file=sys.stderr)
    sys.exit(2)


def load_spec() -> dict:
    if not SPEC.exists():
        die(f"no vendored contract at {SPEC}. See contracts/README.md.")
    return json.loads(io.open(SPEC, encoding="utf-8-sig").read())


def resolve(schema: dict, components: dict) -> dict:
    seen = 0
    while "$ref" in schema:
        seen += 1
        if seen > 20:
            die("circular $ref in contract")
        schema = components[schema["$ref"].split("/")[-1]]
    return schema


def response_props(spec: dict, method: str, path: str) -> dict | None:
    """Property names of the 2xx JSON object this endpoint returns, if it declares one."""
    components = spec.get("components", {}).get("schemas", {})
    item = spec.get("paths", {}).get(path, {}).get(method.lower())
    if not item:
        return None
    for code in ("200", "201"):
        content = item.get("responses", {}).get(code, {}).get("content", {})
        schema = content.get("application/json", {}).get("schema")
        if schema:
            return resolve(schema, components).get("properties")
    return None


def accepted_query_params(spec: dict, method: str, path: str) -> set[str]:
    item = spec.get("paths", {}).get(path, {}).get(method.lower(), {})
    return {p["name"] for p in item.get("parameters", []) if p.get("in") == "query"}


def kotlin_data_classes(src: str) -> dict[str, set[str]]:
    """Class name -> the wire names of its properties (honouring @SerializedName)."""
    out: dict[str, set[str]] = {}
    for m in DATACLASS.finditer(src):
        name = m.group(1)
        # walk to the matching close paren of the constructor
        depth, i = 1, m.end()
        while i < len(src) and depth:
            depth += (src[i] == "(") - (src[i] == ")")
            i += 1
        body = src[m.end():i - 1]
        out[name] = {
            (serialized or prop) for serialized, prop in PROPERTY.findall(body)
        }
    return out


def unwrap(kotlin_type: str) -> str:
    """`List<AhcFileItem>` -> `AhcFileItem`; strips nullability."""
    t = kotlin_type.strip().rstrip("?")
    inner = re.match(r"^(?:List|Array|Set)<(.+)>$", t)
    return unwrap(inner.group(1)) if inner else t


def main() -> int:
    if not SERVICE.exists():
        die(f"no API service at {SERVICE}")
    spec = load_spec()
    src = io.open(SERVICE, encoding="utf-8-sig").read()
    classes = kotlin_data_classes(src)

    failures: list[str] = []
    notes: list[str] = []
    checked = 0

    declarations = list(HTTP_ANNOTATION.finditer(src))
    for idx, ann in enumerate(declarations):
        method, raw_path = ann.group(1), ann.group(2)
        # The block runs to the next HTTP annotation, or to the end of the interface.
        block_end = declarations[idx + 1].start() if idx + 1 < len(declarations) else len(src)
        block = src[ann.end():block_end]
        ret_match = RETURN_TYPE.search(block)
        if not ret_match:
            failures.append(f"{method} {raw_path} — could not parse a return type; "
                            f"this checker refuses to skip a declaration silently")
            continue
        ret = ret_match.group(1)
        path = "/" + raw_path.lstrip("/")
        checked += 1

        if path not in spec.get("paths", {}) or method.lower() not in spec["paths"][path]:
            failures.append(f"{method} {path} — not in the contract at all")
            continue

        # --- query parameters the server will silently ignore --------------
        accepted = accepted_query_params(spec, method, path)
        for param in QUERY.findall(block):
            if param not in accepted:
                failures.append(
                    f"{method} {path} — sends query param '{param}', which the server does not "
                    f"accept (it accepts: {', '.join(sorted(accepted)) or 'none'}). "
                    f"FastAPI discards it silently."
                )

        # --- response fields ------------------------------------------------
        props = response_props(spec, method, path)
        if props is None:
            notes.append(f"{method} {path} — contract declares no typed response body; skipped")
            continue

        cls = unwrap(ret)
        if cls not in classes:
            notes.append(f"{method} {path} — returns {cls}, not a local data class; skipped")
            continue

        server = set(props.keys())
        client = classes[cls]
        for field in sorted(client - server):
            failures.append(
                f"{method} {path} — {cls}.{field} is never sent by the server "
                f"(server sends: {', '.join(sorted(server))}). It will be null or default forever."
            )
        for field in sorted(server - client):
            notes.append(f"{method} {path} — {cls} does not read '{field}' (fine, but visible)")

    # Validate the instrument before trusting its verdict. An earlier version of this regex
    # silently matched only the 2 declarations that had no parameter annotations and then
    # printed "clean" — a checker that quietly inspects a subset is worse than none, because
    # it manufactures confidence. If these disagree, the parser is broken, not the code.
    declared = len(declarations)
    if checked != declared:
        print(f"parser bug: found {declared} HTTP annotations but only checked {checked}. "
              f"Refusing to report a verdict.", file=sys.stderr)
        return 2

    print(f"checked all {checked} endpoint declarations against {SPEC.relative_to(ROOT)}")
    for n in notes:
        print(f"  note: {n}")
    if failures:
        print()
        for f in failures:
            print(f"  FAIL: {f}")
        print(f"\n{len(failures)} finding(s). See the docstring for why each class matters.")
        return 1
    print("clean — no field or query-parameter drift")
    return 0


if __name__ == "__main__":
    sys.exit(main())
