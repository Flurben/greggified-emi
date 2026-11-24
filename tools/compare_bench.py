"""Compares two benchmark/testkit result files and prints a Markdown table.

Usage: python tools/compare_bench.py <baseline.json> <mod.json> [title]

Frame scenarios ("frames: ...") are compared on mean and p95 frame time (lower is better); other numeric
entries with mean/p50 statistics (open_recipes_ms, search_ms, input timings) are compared on their mean.
"""
import json
import sys


def stat(entry, key="frame_ms"):
    if isinstance(entry, dict) and key in entry and isinstance(entry[key], dict):
        return entry[key]
    if isinstance(entry, dict) and "mean" in entry:
        return entry
    return None


def fmt_delta(base, mod):
    if base is None or mod is None or base == 0:
        return "n/a"
    pct = (mod - base) / base * 100
    return f"{pct:+.1f}%"


def main():
    base = json.load(open(sys.argv[1], encoding="utf-8"))
    mod = json.load(open(sys.argv[2], encoding="utf-8"))
    title = sys.argv[3] if len(sys.argv) > 3 else ""

    if title:
        print(f"### {title}\n")

    print("| Scenario | EMI alone mean (p95) ms | With Greggified EMI mean (p95) ms | Mean change |")
    print("| --- | --- | --- | --- |")

    for name, entry in base.items():
        if name not in mod:
            continue
        b = stat(entry)
        m = stat(mod[name])
        if b is None or m is None or "mean" not in b or "mean" not in m:
            continue
        b95 = b.get("p95", float("nan"))
        m95 = m.get("p95", float("nan"))
        print(f"| {name} | {b['mean']:.3f} ({b95:.3f}) | {m['mean']:.3f} ({m95:.3f}) | {fmt_delta(b['mean'], m['mean'])} |")

    for name in ("emi_ready_after_join_ms", "index_size", "recipe_count"):
        if name in base and name in mod:
            print(f"| {name} | {base[name]} | {mod[name]} | {fmt_delta(base[name], mod[name])} |")


if __name__ == "__main__":
    main()
