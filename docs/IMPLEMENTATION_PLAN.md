# Greggified EMI — implementation plan

Goal: a client-side Forge 1.20.1 addon for EMI 1.1.x that makes EMI behave like **GTNH 2.9.1's NotEnoughItems
(NotEnoughItems 2.8.175-GTNH)** for a player's hands and eyes — bookmarks, crafting chains, auto-crafting, the crafting
tree, the history panel, item zoom, NEI keybinds and search syntax — while leaving EMI at least as fast as without it.

Reference sources (pinned):

| What | Version | Where |
| --- | --- | --- |
| GTNH modpack | 2.9.1 (manifest `DreamAssemblerXXL/releases/manifests/2.9.1.json`) | pins `NotEnoughItems 2.8.175-GTNH` |
| NEI source | tag `2.8.175-GTNH` | https://github.com/GTNewHorizons/NotEnoughItems |
| EMI source | tag `1.1.24+1.20.1` | https://github.com/emilyploszaj/emi |
| Test pack | TerraFirmaGreg-Modern 0.13.17 (EMI 1.1.24, TMRV 0.8.1, ReMI 4.8.0, Mouse Tweaks, IPN) | Prism instance `TFG-0.13.17-perftest` |

## 1. Starting point

The owner's earlier addon *GTNH Bookmarks for EMI* (`../GTNH Recipe Trees in EMI`, LGPL-3.0, ~12k lines) already ports
NEI's bookmark grid, groups, chain math, sorting, collapsing, Alt hotkey lists, auto-crafting, item pulling, the recipe
overlay, favorites and chat links. Greggified EMI starts from that code with a new identity (mod ID
`greggifiedemi`, package `com.greggifiedemi`) and changes its goal: **strict NEI parity**, not "NEI with tweaks".
Its saved bookmarks are imported on first start.

## 2. Behaviour inventory (NEI 2.8.175) and how each maps onto EMI

Legend: **Port** = new code here, **Revert** = existing code deviated from NEI and is changed back, **EMI** = EMI
already does it identically, **Remap** = EMI has it under a different key/syntax and we add NEI's.

### 2.1 Keybinds (NEIClientConfig.setDefaultKeyBindings)

| NEI id | Default | NEI behaviour | Greggified EMI |
| --- | --- | --- | --- |
| `gui.hide` | O | toggle all NEI panels | Remap → EMI toggle visibility (EMI's Ctrl+O still works) |
| `gui.search` | F | focus search (no modifiers) | Remap → focus EMI search |
| `gui.getprevioussearch` / `next` | Up / Down | search history while search focused | Port (history of committed searches) |
| `gui.prev` / `gui.next` | PgUp / PgDn | page of the panel under the mouse | Port for index + bookmarks |
| `gui.overlay` | S | overlay; Shift fill grid; Ctrl+Shift fill bookmarked qty | Revert (was Shift+LMB) |
| `gui.craft_items` | C | Shift+C craft all, Ctrl+Shift+C craft missing | existing |
| `gui.next_tooltip` | Z | next tooltip page | n/a in EMI (no tooltip pages) — documented gap |
| `recipe.recipe` / `usage` | R / U | recipes / uses | EMI |
| `recipe.back` | Backspace | previous recipe screen | EMI |
| `recipe.prev/next_machine` | Up / Down | previous / next tab | Remap (EMI: no key) |
| `recipe.prev/next_recipe` | Left / Right | previous / next page | Remap (EMI uses Left/Right for tabs) |
| `bookmark.add` | A | add/remove; Shift recipe, Ctrl count | existing (EMI favorite key) |
| `bookmark.favorite` | Shift+F | save favorite recipe tree into bookmarks | Port |
| `bookmark.remove_recipe` | Shift+A on group | remove group | existing |
| `bookmark.pull_items` | V | pull; Shift pull missing | existing |
| `bookmark.chat_link` | Ctrl+L | chat link (Ctrl+Shift: recipe) | existing |
| `bookmark.open_crafting_tree` | T | crafting tree window on a chain group | Port |
| `bookmark.hide` | B | toggle bookmark panel | existing |
| `itemzoom.toggle` | Shift+Z | toggle item zoom | Port |
| `itemzoom.hold` / `zoom_in` / `zoom_out` | unbound | hold to show / resize | Port |
| `copy.name` / `copy.oredict` / `copy.identifier` | Ctrl+C / Ctrl+D / Ctrl+X | clipboard | existing |
| `world.*`, `gui.enchant`, `gui.potion` | various | cheat utilities of 1.7.10 | out of scope (server cheats; EMI has its own cheat mode) |

### 2.2 Panels and widgets

| NEI feature | Greggified EMI |
| --- | --- |
| Bookmark panel (groups, chains, view modes, collapse, drag-sort, grouping, namespaces, xN, tooltips) | existing port, re-checked against 2.8.175 |
| Crafting tree window (`bookmark/tree/*`, T) | Port: canvas with pan/zoom, item states, stats panel |
| History panel (`ItemHistoryPanel`, 2 rows under item panel, dashed separator) | EMI's `lookup-history` subpanel, enabled at runtime under the index (no config file written) |
| Craftables panel (off by default in NEI) | EMI's craftables page already exists |
| Item zoom (`ItemZoom`) | Port |
| Recipe GUI "highlight present item" (`slotHighlightPresent`) | Port: tint recipe-view slots you already have |
| Recipe GUI `?`-hover presence overlay | EMI shows missing items on its `+` hover already |
| Search syntax: `@mod`, `#tooltip`, `$oredict`, `&identifier`, `-exclude`, `|`, `"…"`, `/regex/` | Remap: NEI's `#`/`$` are EMI's `$`/`#`; translate NEI syntax before EMI parses it, add `&id` |
| Collapsible item groups (G) | EMI's index stack groups (EMI) |
| Subsets dropdown, cheat utilities, mob/chunk overlays | out of scope (no EMI equivalent; vanilla F3+G covers chunks) |

## 3. Architecture

- Everything is client-side. Mixins target EMI internals (`remap = false`), so `mods.toml` pins EMI `[1.1.22,1.2)`.
- One key layer, `Keys`, registers NEI's binding names with NEI's defaults under the Controls category
  "Greggified EMI (NEI)". All handlers match the key alone and read modifiers themselves, exactly like NEI's
  `isHashDown` semantics.
- Input order mirrors NEI's `GuiContainerManager`: bookmark panel → item shortcuts → layout hotkeys (hide, search,
  paging, zoom) → recipe-screen hotkeys → EMI.
- New packages: `nei/` (layout hotkeys, search translation, search history, item zoom, presence highlight),
  `tree/` (crafting tree window).
- Persistence stays in `greggifiedemi/` in the game directory; the old `emicraftingtree/` save is copied once.

## 4. Performance budget

EMI must not get slower. Rules:
- No per-frame allocation in hot paths; tooltips and hotkey lists are memoised (already the case).
- The presence highlight caches the inventory count per tick, not per slot draw.
- Search translation is a single string pass, only when the query changes.
- Item zoom renders one item, only when something is hovered.
- Verified with the existing benchmark harness (`-Pbenchmark` vs `-Pbaseline`, 60 and 600 recipes) plus a spark/
  frame-time comparison in the TFG pack.

## 5. Verification

1. Unit tests (chain math, grid sorting vs NEI's algorithm, search translation, search history).
2. Scripted in-game self-test (`-Pselftest`): every keybind and modifier combination in §2.1 is driven through EMI's
   input path and its effect asserted; screenshots for UI parity.
3. Benchmark: mod vs baseline JSON, written to `docs/PERFORMANCE.md`.
4. TFG run: the release jar in the TFG 0.13.17 instance; check the log for mixin/apply errors and conflicts with
   TMRV/ReMI, and compare frame times with and without the mod.
5. Parity report `docs/PARITY.md`: one row per NEI keybind/feature with status and evidence.

## 6. Phases

1. Fork, rename, migrate saves, build. ✅
2. Revert deviations (Shift+S fill, NEI default view mode, NEI favorites Shift+F / Shift+T). ✅
3. Layout & recipe-screen keybinds (O, F, Up/Down search history, PgUp/PgDn, recipe arrows). ✅
4. NEI search syntax translation. ✅
5. Item zoom; presence highlight; history subpanel. ✅
6. Crafting tree window. ✅
7. Tests (25 unit, 117 in-game), dev benchmark, TFG testkit runs, parity and performance reports. ✅

Not ported (see PARITY.md): tooltip pages (Z), group chat links, the tree's stats-section menu / chat link / PNG export,
NEI's 1.7.10 cheat utilities and subsets dropdown.
