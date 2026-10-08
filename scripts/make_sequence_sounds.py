#!/usr/bin/env python3
"""Make the session tool's three signals from marimba notes copied in third_party/vsco2-marimba/.

The three come from the same instrument and the same notes (a low C, the G above, a high C),
so they sound of a piece:
- the countdown, the high C cut short, on each of the last 3 seconds of a timed step;
- the step change, the low C then the G, a rising fifth;
- the session's start and end, the three notes rolled into one chord that rings.

Each is written to res/raw/sequence_*.flac. The output is committed, as the icons are: the
build never needs this script nor sox. Their source and licence are credited in
tools/sequence/sounds.json.

Updating the notes = replacing third_party/vsco2-marimba/, running this script again, committing.

Usage:
    ./scripts/make_sequence_sounds.py
"""

import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "third_party" / "vsco2-marimba"
RAW = ROOT / "app" / "src" / "main" / "res" / "raw"


def note(name):
    return SOURCE / f"Marimba_hit_Outrigger_{name}_loud_01.wav"


def sox(*args):
    subprocess.run(["sox", *map(str, args)], check=True)


def cut(source, out, length, fade, delay=0.0):
    """The note's first `length` seconds, fading out over the last `fade`, after `delay` of silence."""
    sox(source, out, "trim", 0, length, "fade", "t", 0, length, fade, "pad", delay)


def countdown(out):
    sox(note("C6"), out, "trim", 0, 0.25, "fade", "t", 0, 0.25, 0.18, "gain", "-n", -6)


def step_change(out, work):
    low, high = work / "low.wav", work / "high.wav"
    cut(note("C4"), low, 1.4, 0.9)
    cut(note("G4"), high, 1.4, 0.9, delay=0.12)
    sox("-m", low, high, out, "gain", "-n", -3)


def session_edge(out, work):
    parts = []
    for i, (name, delay) in enumerate([("C4", 0.0), ("G4", 0.06), ("C6", 0.12)]):
        part = work / f"edge{i}.wav"
        sox(note(name), part, "pad", delay)
        parts.append(part)
    sox("-m", *parts, out, "trim", 0, 3.5, "fade", "t", 0, 3.5, 2, "gain", "-n", -3)


def main():
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        countdown(RAW / "sequence_countdown.flac")
        step_change(RAW / "sequence_step_change.flac", work)
        session_edge(RAW / "sequence_session_edge.flac", work)
    for name in ("countdown", "step_change", "session_edge"):
        print(RAW / f"sequence_{name}.flac")


if __name__ == "__main__":
    main()
