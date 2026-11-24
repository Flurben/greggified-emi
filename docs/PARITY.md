# Greggified EMI — parity with GTNH 2.9.1 NotEnoughItems

Reference: **NotEnoughItems 2.8.175-GTNH**, the version GTNH 2.9.1 ships (`DreamAssemblerXXL` manifest
`releases/manifests/2.9.1.json`). "Verified" means an automated check passes: **[S]** the in-game self-test
(`./gradlew runClient -Pselftest`, vanilla + EMI 1.1.24), **[P]** the pack testkit inside TerraFirmaGreg-Modern 0.13.17,
**[U]** a unit test. Status: ✅ same as NEI · ⚠️ same intent, visible difference · ❌ not ported.

## 1. Keybinds (NEIClientConfig.setDefaultKeyBindings)

All of NEI's binds are in Controls → *Greggified EMI (NEI)* with NEI's ids and defaults. Modifiers select variants exactly
like NEI's `isHashDown`, so the bound key is matched alone.

| NEI bind | Default | Behaviour | Status | Verified |
| --- | --- | --- | --- | --- |
| `gui.hide` | O | Toggle the whole overlay (works while hidden) | ✅ (EMI's Ctrl+O also works) | S |
| `gui.search` | F | Focus the search bar (no modifiers) | ✅ | S |
| `gui.getprevioussearch` / `getnextsearch` | ↑ / ↓ | Search history while the search bar has focus | ✅ (EMI's own) | — |
| `gui.prev` / `gui.next` | PgUp / PgDn | Page the panel under the mouse | ✅ index, bookmarks, history | S |
| `gui.overlay` | S | Overlay recipe; **Shift+S** fill grid; **Ctrl+Shift+S** fill with bookmarked quantity; S over nothing clears | ✅ (also from the recipe view, like NEI) | S |
| `gui.craft_items` | C | **Shift+C** craft, **Ctrl+Shift+C** craft only missing (item, bookmark, group) | ✅ | S |
| `gui.next_tooltip` | Z | Next tooltip page | ❌ EMI tooltips have no pages | — |
| `recipe.recipe` / `recipe.usage` | R / U | Recipes / uses | ✅ (EMI's own, same defaults) | S |
| `recipe.back` | Backspace | Previous recipe screen | ✅ (EMI's own) | — |
| `recipe.prev_recipe` / `next_recipe` | ← / → | Previous / next **page** | ✅ (EMI alone: categories) | S, P |
| `recipe.prev_machine` / `next_machine` | ↑ / ↓ | Previous / next **category** | ✅ | S, P |
| `bookmark.add` | A | Add / remove; Shift = with recipe; Ctrl = with count; on a group Shift+A removes it | ✅ (EMI's favorite key) | S, P |
| `bookmark.favorite` | Shift+F | Save the favorite recipe tree as a crafting chain | ✅ | S |
| `bookmark.pull_items` | V | Pull bookmarked items from open storage; Shift = only missing | ✅ | S |
| `bookmark.chat_link` | Ctrl+L | Item link in chat (Ctrl+Shift = with recipe) | ✅ item links ⚠️ group links not ported | S |
| `bookmark.open_crafting_tree` | T | Crafting tree window on a crafting-chain group; in the recipe view **Shift+T** saves the recipe's favorite tree and opens it (NEI's favorite button) | ✅ | S, P |
| `bookmark.hide` | B | Toggle the bookmark panel | ✅ | S |
| `itemzoom.toggle` | Shift+Z | Toggle item zoom | ✅ | S |
| `itemzoom.hold` / `zoom_in` / `zoom_out` | unbound | Hold to show; resize | ✅ | — |
| `copy.name` / `copy.oredict` / `copy.identifier` | Ctrl+C / Ctrl+D / Ctrl+X | Clipboard (oredict → tags) | ✅ | S |
| `world.*`, `gui.enchant`, `gui.potion` | various | 1.7.10 cheat utilities | ❌ out of scope (EMI cheat mode, vanilla F3+G) | — |

Mouse: LMB/RMB recipes/uses, cheat-mode give, Ctrl+scroll quantity (Alt: by a stack), Shift+scroll accepted item,
Shift+drag sort, gutter drag to group/ungroup, Alt+LMB collapse — all as NEI **[S]**.

## 2. Panels and features

| NEI feature | Status | Notes | Verified |
| --- | --- | --- | --- |
| Bookmark panel: groups, gutter brackets, to-do/default view, crafting chains with calculated amounts, collapsing, `xN`, namespaces, Alt hotkey list, chain tooltip (Shift = with inventory) | ✅ | ported NEI algorithms (chain math and grid sort unit-tested against NEI's) | S, U |
| Auto-crafting (Shift+C) incl. GregTech tools, FastWorkbench | ✅ | uses EMI's recipe filling | S |
| Crafting tree window (T): tidy-tree layout, pan/zoom (drag, wheel, Shift+wheel, Ctrl+wheel), state colours with inventory snapshot, collapse arrows, smart collapse modes, category badge with remove, Alt+LMB collapse recipe, Ctrl+wheel quantity, Shift+wheel accepted item, search field with Enter/Shift+Enter, stats strip (ingredients, crafting, results, handlers) with highlight | ✅ | NEI's own texture | S, P |
| "Use for recipe tree" (pick a replacement recipe from the recipe view) | ✅ | small button on each recipe while picking | — |
| Tree toolbar: inventory snapshot, fit, collapse mode, stats on/off | ✅ | | S |
| Tree toolbar: stats-section menu, chat link, save as PNG | ❌ | | — |
| History panel (2 rows under the item list, dashed separator) | ✅ | EMI's lookup history as a subpanel; never written to `emi.css` | S, P |
| Item zoom (Shift+Z) | ✅ | NEI's sizing rules in real pixels, name + hint text | S |
| Highlight present items on the fill button hover | ✅ | EMI only tints missing ones | — |
| Favorites (NEI's star) | ✅ | EMI's "set default recipe" is NEI's manual favorite; single-recipe items are generated favorites | S |
| Search syntax `@mod #tooltip $oredict &identifier -not a|b "exact" /regex/` | ✅ | NEI's `#`/`$` are swapped vs EMI; `&` added | S, P, U |
| Craftables panel | ✅ | EMI's craftables page (off by default in NEI too) | — |
| Collapsible item groups | ✅ | EMI's index stack groups | — |
| Subsets dropdown, mob/chunk overlays, cheat buttons | ❌ | no EMI equivalent; out of scope | — |

## 3. Deliberate differences

- `A` is EMI's favorite key, standing in for NEI's bookmark key; EMI's own Ctrl+O / Ctrl+F still work alongside NEI's O / F.
- Tooltips list EMI's mod name line; NEI's tooltip pages (Z) do not exist in EMI.
- Fluid amounts show in mB with an `L` suffix like NEI.
- Group chat links (sharing a whole group through chat) and the tree's PNG export are not ported yet.

## 4. Migrating from GTNH Bookmarks for EMI

Bookmarks saved by *GTNH Bookmarks for EMI* (`emicraftingtree/`) are imported on first start. Do not install both
mods together. Behaviour that addon changed on purpose is now NEI's again: grid filling is **Shift+S**
(not Shift+click), and Shift+F saves favorite trees.
