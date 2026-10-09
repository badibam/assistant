#!/usr/bin/env python3
"""Copy the Go module's dependencies into the repository, with the fix Tailscale lacks on Android.

The app's Tailscale node (app/src/main/go, docs/design/funnel-access.md) builds from
app/src/main/go/vendor, committed, so that the build never downloads anything: F-Droid
builds offline and from the repository alone. That copy carries one change to Tailscale:
the local API's certificate route, which `tsnet` uses to serve the Funnel's HTTPS, is left
out of Android builds -- the Funnel opens, then every connection fails for want of a
certificate (docs/design/funnel-poc.md). Taking `android` out of the two build constraints
below is the whole fix.

Updating Tailscale = change its version in go.mod, run this script, read the diff, commit.

    ./scripts/vendor_go.py          rebuild vendor/ and apply the fix
    ./scripts/vendor_go.py --check  only check that the committed copy carries the fix

The check runs in `./run test` and needs no Go: a copy refreshed by hand with `go mod
vendor` loses the fix in silence, and nothing else would fail until a phone tries.

Go is found as the build finds it: `go.dir` in local.properties, or `go` on the PATH.
"""

import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULE = ROOT / "app" / "src" / "main" / "go"
VENDOR = MODULE / "vendor"

# The fix, as (file under vendor/, line as Tailscale writes it, line as the app needs it).
FIX = [
    ("tailscale.com/ipn/localapi/cert.go",
     "//go:build !ios && !android && !js && !ts_omit_acme",
     "//go:build !ios && !js && !ts_omit_acme"),
    ("tailscale.com/ipn/localapi/disabled_stubs.go",
     "//go:build ios || android || js",
     "//go:build ios || js"),
]


def go_binary():
    """The go command: go.dir from local.properties, else the PATH; None when neither has it."""
    props = ROOT / "local.properties"
    if props.is_file():
        for line in props.read_text(encoding="utf-8").splitlines():
            key, _, value = line.partition("=")
            if key.strip() == "go.dir" and value.strip():
                candidate = Path(value.strip()) / "bin" / "go"
                return str(candidate) if candidate.is_file() else None
    return shutil.which("go")


def check():
    """Problems with the committed copy, as messages; empty when it carries the fix."""
    if not VENDOR.is_dir():
        return [f"{VENDOR.relative_to(ROOT)} is missing -- run ./scripts/vendor_go.py"]
    problems = []
    for name, original, fixed in FIX:
        path = VENDOR / name
        if not path.is_file():
            problems.append(f"{name} not in the copy -- Tailscale moved it: the fix needs reading again")
            continue
        lines = path.read_text(encoding="utf-8").splitlines()
        if fixed not in lines:
            problems.append(f"{name} lacks the Android fix -- run ./scripts/vendor_go.py")
    return problems


def apply_fix():
    """Applies the fix to a fresh copy; returns the messages for what could not be applied."""
    problems = []
    for name, original, fixed in FIX:
        path = VENDOR / name
        if not path.is_file():
            problems.append(f"{name} not in the copy -- Tailscale moved it: the fix needs reading again")
            continue
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines(keepends=True)
        hits = [i for i, line in enumerate(lines) if line.rstrip("\n") == original]
        if len(hits) != 1:
            problems.append(f"{name}: the line to fix is not there as expected -- Tailscale changed it, "
                            f"read the file before changing the fix (perhaps Tailscale fixed it itself)")
            continue
        lines[hits[0]] = fixed + "\n"
        path.write_text("".join(lines), encoding="utf-8")
    return problems


def main():
    if "--check" in sys.argv[1:]:
        problems = check()
        for p in problems:
            print(f"go vendor: {p}")
        return 1 if problems else 0

    go = go_binary()
    if go is None:
        print("go vendor: no Go -- set go.dir in local.properties, or put go on the PATH")
        return 1
    # The module is built for Android with cgo: tidy and vendor see the files that build reads
    env = dict(os.environ, GOOS="android", GOARCH="arm64", CGO_ENABLED="1", GOFLAGS="-modcacherw")
    for args in (["mod", "tidy"], ["mod", "vendor"]):
        if subprocess.run([go, *args], cwd=MODULE, env=env).returncode != 0:
            print(f"go vendor: go {' '.join(args)} failed")
            return 1
    problems = apply_fix() + check()
    for p in problems:
        print(f"go vendor: {p}")
    if problems:
        return 1
    files = sum(1 for p in VENDOR.rglob("*") if p.is_file())
    print(f"go vendor: {files} files in {VENDOR.relative_to(ROOT)}, Android fix applied")
    return 0


if __name__ == "__main__":
    sys.exit(main())
