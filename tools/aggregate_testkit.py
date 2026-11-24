"""Aggregates several pack-testkit runs (greggified-*.json and baseline-*.json) into one Markdown table.

Usage: python tools/aggregate_testkit.py <greggified-testkit dir> [newest N runs per mode]

For each scenario it averages the per-run mean frame time (or the mean of a timing entry) and shows the spread of the
run means, so a difference can be judged against run-to-run noise.
"""
import glob
import json
import os
import statistics
import sys


def load(directory, mode, newest):
    files = sorted(glob.glob(os.path.join(directory, f"{mode}-*.json")))[-newest:]
    return files, [json.load(open(f, encoding="utf-8")) for f in files]


def value(entry):
    if isinstance(entry, dict) and isinstance(entry.get("frame_ms"), dict):
        return entry["frame_ms"].get("mean")
    if isinstance(entry, dict) and "mean" in entry:
        return entry["mean"]
    return None


def main():
    directory = sys.argv[1]
    newest = int(sys.argv[2]) if len(sys.argv) > 2 else 2
    base_files, base = load(directory, "baseline", newest)
    mod_files, mod = load(directory, "greggified", newest)

    print(f"Runs: {len(base)} without the mod, {len(mod)} with it.\n")
    print("| Scenario | EMI alone: mean of runs (range) ms | With Greggified EMI: mean of runs (range) ms | Change |")
    print("| --- | --- | --- | --- |")

    names = []
    for run in mod + base:
        for name in run:
            if name not in names and value(run[name]) is not None:
                names.append(name)

    for name in names:
        b = [value(r[name]) for r in base if name in r and value(r[name]) is not None]
        m = [value(r[name]) for r in mod if name in r and value(r[name]) is not None]
        bs = f"{statistics.mean(b):.3f} ({min(b):.3f}–{max(b):.3f})" if b else "—"
        ms = f"{statistics.mean(m):.3f} ({min(m):.3f}–{max(m):.3f})" if m else "—"
        change = f"{(statistics.mean(m) - statistics.mean(b)) / statistics.mean(b) * 100:+.1f}%" if b and m else ""
        print(f"| {name} | {bs} | {ms} | {change} |")

    fails = [c for r in mod for c in r.get("checks", []) if c.startswith("FAIL")]
    passes = [c for r in mod for c in r.get("checks", []) if c.startswith("PASS")]
    print(f"\nFeature checks with the mod: {len(passes)} passed, {len(fails)} failed.")
    for f in fails:
        print(f"- {f}")


if __name__ == "__main__":
    main()
