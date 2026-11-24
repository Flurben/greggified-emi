# Greggified EMI

A client-side Forge 1.20.1 addon for [EMI](https://github.com/emilyploszaj/emi) that makes EMI play like
**GTNH 2.9.1's NotEnoughItems** (NotEnoughItems 2.8.175-GTNH): NEI's bookmark panel with crafting chains and
auto-crafting, the crafting tree window, the history panel, item zoom, NEI's keybinds and NEI's search syntax. Built and
tested for GregTech packs such as TerraFirmaGreg-Modern.

Requirements: Minecraft 1.20.1, Forge 47+, EMI 1.1.22–1.1.x (built against 1.1.24). Client-side only; only item chat
links (`Ctrl + L`) need the mod on the server too.

The full NEI-by-NEI comparison is in [docs/PARITY.md](docs/PARITY.md), performance numbers in
[docs/PERFORMANCE.md](docs/PERFORMANCE.md), and the design in [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md).

## What you get

- **Bookmark panel** (EMI's favorites sidebar, left by default): NEI's groups with the bracket gutter, crafting chains
  with calculated amounts (`xN`), to-do list and default views, collapsible recipes and groups, the "Crafting Chain"
  tooltip (Shift: against your inventory), bookmark namespaces (`<` `>` pages), drag to sort and to group.
- **Auto-crafting** (`Shift + C`): crafts a bookmarked recipe or a whole chain, sub-components first, using EMI's own
  recipe filling (GregTech tools with any wear or NBT count; works with FastWorkbench).
- **Crafting tree** (`T` over a chain group): NEI 2.8's tree window — pan, zoom, inventory snapshot colouring, smart
  collapse, stats strip, search, and "Use for recipe tree" to swap a step's recipe from EMI's recipe view.
- **History panel**: the items you looked up, in two rows under the item list (EMI's lookup history, added without
  touching `emi.css`).
- **Item zoom** (`Shift + Z`): the hovered item drawn large beside your inventory.
- **Present-item highlight**: hovering a recipe's `+` button tints what you already have green (EMI only tints missing).
- **NEI search syntax**: `@mod`, `#tooltip`, `$oredict` (tags), `&identifier`, `-exclude`, `a|b`, `"exact"`, `/regex/`.
- **NEI keybinds** everywhere, including the recipe view (`←/→` pages, `↑/↓` categories).
- Bookmarks from *GTNH Bookmarks for EMI* are imported on first start (don't install both).

## Controls

Everything is rebindable under Controls → **Greggified EMI (NEI)**. Hold **Alt** over any item, bookmark or group
bracket to see the hotkeys that apply there, as in NEI.

**Anywhere in a GUI**

| Key | Action |
| --- | --- |
| `O` | Hide / show EMI (also while hidden) |
| `F` | Focus the search bar (`↑`/`↓` there: search history) |
| `B` | Hide / show the bookmark panel |
| `Page Up` / `Page Down` | Previous / next page of the panel under the mouse |
| `Shift + Z` | Toggle item zoom |

**Any hovered item** (item list, history, recipe view, inventory, bookmark)

| Key | Action |
| --- | --- |
| `R` / `U`, `LMB` / `RMB` | Recipes / uses |
| `A` | Bookmark the item (over a bookmark: remove it) |
| `Ctrl + A` | Bookmark with its amount |
| `Shift + A` / `Ctrl + Shift + A` | Bookmark the recipe (results and ingredients) at quantity 0 / 1 |
| `Shift + F` | In the item list or history: save the item's favorite recipe tree as a crafting chain |
| `S` | Show the recipe as an overlay on the crafting grid (`S` over nothing clears it) |
| `Shift + S` / `Ctrl + Shift + S` | Fill the crafting grid once / with the bookmarked quantity |
| `Shift + C` / `Ctrl + Shift + C` | Craft the item's recipe / craft only what is missing |
| `V` / `Shift + V` | With storage open: pull the item into your inventory / pull only what is missing |
| `Ctrl + C`, `Ctrl + D`, `Ctrl + X` | Copy the name, tags (NEI's ore dictionary) or id |
| `Ctrl + L` / `Ctrl + Shift + L` | Send an item link / item with recipe to chat |

**On a bookmark**: `Ctrl + Scroll` quantity (`Ctrl + Alt + Scroll`: by a stack), `Shift + Scroll` accepted item,
`Alt + LMB` collapse/expand a recipe, `Shift + LMB + Drag` move, `LMB + Drag`/`MMB` pick up.

**On a group bracket** (or the page label for the default group): `LMB` view mode, `RMB` crafting chain on/off,
`Alt + LMB` collapse, `LMB + Drag` / `RMB + Drag` include / exclude rows, `Shift + LMB + Drag` move,
`Ctrl + Scroll` quantity, `Shift + A` remove, `T` crafting tree, `Shift + C` / `Ctrl + Shift + C` craft,
`V` / `Shift + V` pull.

**In the recipe view**: `←` / `→` previous / next page, `↑` / `↓` previous / next category, `Backspace` back.

**In the crafting tree**: drag to pan, wheel to scroll (`Shift`: sideways, `Ctrl`: zoom), `LMB` on a crafted item to
pick a different recipe ("Use for recipe tree"), `Alt + LMB` collapse a recipe, the arrows under an item collapse its
branch, `Ctrl + Scroll` on a result changes the quantity, the corner badge removes a step, `Enter` / `Shift + Enter`
in the search field jump between matches, click a stats entry to highlight it in the tree.

## Settings

`config/greggifiedemi-client.toml` (all defaults are NEI's):

- **bookmarks**: per-world bookmarks, items keep their recipe, recipe quantity on add, recipe tooltip and marker modes,
  category icon, animation, import EMI favorites.
- **favorites**: depth of `Shift + F` favorite trees (NEI: 9).
- **layout**: NEI search syntax, NEI recipe keys, history panel and its rows, present-item highlight.
- **itemzoom**: enabled, EMI panels only, preferred side, help text, zoom amount, name and colour.
- auto-crafting on/off, hotkey list on/off, "Hold ALT" hint on/off.

Bookmarks are saved to `greggifiedemi/bookmarks.json` in the game directory (or `greggifiedemi/worlds/<world>/` when
per-world).

## Building and testing

```
./gradlew build                                  # jar in build/libs, runs the unit tests
./gradlew runClient -Pselftest                   # scripted in-game test (114 checks), then quits
./gradlew runClient -Pbenchmark [-Pbaseline] [-Pbenchsize=600]   # frame timings, with and without the mod
./gradlew testkitJar                             # dev-only pack test harness (see docs/PERFORMANCE.md)
```

`runClient` opens a Minecraft window for a minute or two. The self-test, benchmark and testkit are never part of the
mod jar.

## How it hooks into EMI

EMI has no public API for sidebars, so small mixins target EMI's own classes (the favorites sidebar, input, the search
compiler, the config writer). The mod therefore declares EMI `[1.1.22,1.2)`. It also fixes an EMI race in tag searches
(`EmiTagKey` cache used from the search thread) that otherwise shows stale results.

## License and credits

The bookmark grid, chain math, crafting tree, item zoom and related logic are ported from GTNewHorizons'
[NotEnoughItems](https://github.com/GTNewHorizons/NotEnoughItems) (LGPL-3.0), including its crafting tree texture, so this
addon is LGPL-3.0-or-later as well (see `LICENSE`). It grew out of *GTNH Bookmarks for EMI*. EMI is by Emi (MIT).
