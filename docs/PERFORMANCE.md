# Greggified EMI — performance

Goal: EMI with Greggified EMI must be at least as fast as EMI alone. Two independent measurements:

1. **Dev benchmark** (`./gradlew runClient -Pbenchmark [-Pbaseline] [-Pbenchsize=600]`): vanilla + EMI 1.1.24 with 60 or
   600 bookmarked recipes; `-Pbaseline` strips every hook of this mod (mixins, client setup, EMI plugin) and uses EMI
   favorites instead. Per-frame times from `GameRenderer.render`.
2. **Pack testkit** in **TerraFirmaGreg-Modern 0.13.17** (22,959 index stacks, 177,087 recipes, EMI + TMRV + ReMI +
   reliable_recipes and ~270 mods): the same dev-only testkit mod runs identical scenarios with and without the
   Greggified EMI jar, three runs each, alternating. Frame time from Forge's render tick; search cost times EMI's own
   query compile + filter over the whole index.

Hardware: i7-13700K, 32 GB, Windows 11, Java 21 (pack) / Java 17 (dev).

## In TerraFirmaGreg (3 runs each, mean of run means, range in brackets)

| Scenario | EMI alone (ms) | With Greggified EMI (ms) | Change |
| --- | --- | --- | --- |
| Inventory, mouse idle | 3.945 (3.848–4.033) | 4.238 (4.186–4.295) | +7.4% |
| …same, NEI history panel off | 3.945 | **3.943** (3.793–4.093) | **±0%** |
| Inventory, hovering an index stack | 3.856 (3.758–3.915) | 4.050 (3.979–4.100) | +5.0% |
| …same, NEI item zoom off | 3.856 | **3.738** (3.618–3.840) | **−3.1%** |
| Inventory, paging through the index | 4.667 (4.636–4.695) | 4.344 (4.281–4.465) | **−6.9%** |
| Recipe view, idle | 3.671 (3.602–3.727) | 3.133 (3.096–3.156) | **−14.7%** |
| Opening recipes (10 heaviest stacks), per open | 8.321 (8.087–8.651) | 8.070 (7.543–8.741) | **−3.0%** |
| Search: compile + filter 22,959 stacks, per query | 40.867 (40.459–41.304) | 40.680 (40.487–41.043) | −0.5% (same matches) |
| EMI ready after joining the world | 31.5 s | 31.1 s | −1.0% (noise) |

Reading it:
- **EMI's own work is as fast or faster** with the mod: paging, the recipe view, opening recipes and search. Search
  returns exactly the same matches for every query.
- The **only** added frame cost is two NEI features that draw extra things, both on by default because NEI has them on:
  the **history panel** (EMI drawing its own lookup-history subpanel: +0.29 ms/frame) and **item zoom** (drawing the
  hovered item large: +0.3 ms while hovering). With both switched off (`historyPanel = false`, `itemzoom.enabled = false`
  in `config/greggifiedemi-client.toml`, or `Shift + Z` for zoom), every measured scenario is equal to or faster than EMI
  alone.
- Per-run raw data: `docs/benchmarks/tfg/*.json`; table generator: `tools/aggregate_testkit.py`.

## Dev benchmark (vanilla + EMI, single runs)

Full tables: [benchmarks/dev-tables.md](benchmarks/dev-tables.md). Highlights:

| Scenario | 60 recipes | 600 recipes |
| --- | --- | --- |
| Inventory, mouse idle | −12.8% | −7.3% |
| Hovering a bookmarked recipe | −17.0% | −12.0% |
| Recipe view (uses of stick), idle | −15.1% | −2.7% |
| Open uses: call + first frame | −39.5% | −44.1% |
| Open recipes: call + first frame | −39.7% | −46.0% |
| Hovering + Alt (NEI hotkey list, which EMI does not have) | −0.9% … +7.8% | |
| Hovering a chain bracket (NEI chain tooltip; empty space in the baseline) | +3.0% | +13.8% (+0.16 ms) |
| Input events (key, click, scroll) | +1–2 µs per event | |

The bookmark panel replaces EMI's favorites rendering and is cheaper than it; the remaining positive numbers are NEI
tooltips that EMI alone never draws.

## EMI fix included

EMI's tag search (`#tag`, NEI's `$tag`) fills `EmiTagKey.CACHE`, a plain `HashMap`, from its search thread while the
render thread may fill it too; the resulting `ConcurrentModificationException` silently leaves stale search results.
`EmiTagKeyMixin` makes that cache concurrent.

## Note on the owner's perf-test instance

The `TFG-0.13.17-perftest` Prism instance also contains the experimental `tfgperffixes-1.0.0` mod. Its
`AsyncSuffixArrays` parallel bake leaves a future that never completes: EMI name searches then wait forever
(observed with and without Greggified EMI: EMI's search thread never returns, and a search on the render thread froze
the game in `AsyncSuffixArrays.await`). The measurements above were taken on a copy of that instance with only
`tfgperffixes` removed, i.e. TFG 0.13.17 as shipped.
