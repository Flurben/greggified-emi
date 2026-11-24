# GTNH Bookmarks for EMI — agent guide

A client-side Forge 1.20.1 addon for EMI. It ports the **bookmark panel** of GTNewHorizons' NotEnoughItems (NEI)
into EMI's favorites sidebar. That covers groups, crafting chains with calculated amounts, view modes, collapsing,
NEI's hotkeys, auto-crafting and persistence. `README.md` is the user-facing manual with every control. Read it before
changing behaviour.

- **Repo:** https://github.com/Flurben/gtnh-bookmarks-for-emi (branch `main`, releases tagged `vX.Y.Z`).
- **License:** LGPL-3.0-or-later. Much of the code is ported from NEI, which is LGPL.
- **Reference source:** https://github.com/GTNewHorizons/NotEnoughItems. The classes that matter are `BookmarkPanel`,
  `BookmarkGrid`, `BookmarkGridGenerator`, `RecipeChainMath`, `RecipeChainDetails`, `ShortcutInputHandler`,
  `AutoCraftingManager` and `NEIClientConfig` (for default keys). Most classes here say in their Javadoc which NEI
  class they port.

## Product rules (from the owner)

1. **Behaviour must be exactly NEI's** (click semantics, default keys, tooltips, hotkey-list wording, colours and
   ordering). Check the NEI source before changing any interaction. The owner's reference screenshots predate NEI's
   crafting-tree window and group chat links. Where newer NEI source differs, match the screenshots, and don't port
   those newer features.
2. **Everything stays inside the sidebar panel.** A separate "crafting tree" window was built, explicitly rejected and
   removed. Don't add new screens unless asked.
3. **Deliberate deviations from NEI.** Keep them:
   - To-do-list view (one row per recipe or item, result first) is the default for every group. Saves older than
     storage version 3 are migrated to it once.
   - `A` only ever adds the item. `Shift + A` adds the recipe. This applies everywhere, including EMI's recipe view.
   - In EMI's item list, `Shift + A` uses the favorite recipe: EMI's default recipe, else the item's only recipe, else
     EMI's preferred recipe. It never adds the bare item.
   - Filling the grid is `Shift + click` on a bookmark (`Ctrl + Shift + click` fills the bookmarked quantity), not
     NEI's `Shift + S`. Plain `S` is still the overlay.
   - Auto-crafting reuses **EMI's own recipe filling** (`EmiRecipeFiller.performFill`). Don't write a custom
     slot-clicking crafter: one was tried and broke in the owner's pack. EMI ignores NBT by default, so worn or renamed
     tools and any accepted substitute work.
   - "A" is EMI's favorite key standing in for NEI's bookmark key. All other keys are NEI defaults, under Controls →
     "EMI Bookmarks".
4. **Every modifier shortcut (Shift, Ctrl, Alt) must be covered by the in-game self-test.** Most keybinds use
   modifiers, so this is a hard requirement.
5. **Test against the owner's pack too.** The owner plays TerraFirmaGreg Modern (GregTech CEu tools, FastWorkbench).
   Auto-crafting must keep working with FastWorkbench (`-Pfastbench`, see below).

## Build, test, run

You need JDK 17. Gradle runs with `--offline` once dependencies are cached. See `CLAUDE.local.md` for the JAVA_HOME
on the owner's machine.

| Command | What it does |
| --- | --- |
| `./gradlew build` | Compiles, runs the unit tests, and writes `build/libs/gtnh-bookmarks-for-emi-<version>.jar`. |
| `./gradlew test` | Unit tests only (18): `RecipeChainMathTest` and `BookmarkGridTest`, pure Java, no Minecraft. |
| `./gradlew runClient -Pselftest` | The scripted in-game test, which then quits. Check `run/logs/latest.log` for `[selftest]` lines and the final `[selftest] done, N failure(s)`. Currently 88 checks; screenshots go to `run/screenshots/selftest-*.png`. |
| `... -Pselftest -Pfastbench` | The same with FastWorkbench and Placebo loaded. Their jars go in the git-ignored `compat-libs/` (names in `build.gradle`). |
| `./gradlew runClient -Pbenchmark` | Frame and input timings to `run/benchmark/mod.json`. Add `-Pbaseline` for EMI alone (`baseline.json`), `-Pbenchsize=600` for a large list, and `-Pjfr` for a JFR profile. Log lines are tagged `[bench:<mode>]`. |

- **Desktop window:** `runClient` opens a real Minecraft window on the user's desktop for about a minute. Tell the user
  before running it.
- **Where the dev-only code lives:** the self-test, the benchmark and their mixins are in the `selftest` source set
  (`src/selftest`). They are only added with `-Pselftest`/`-Pbenchmark` and are never in the jar.
- **How the self-test fakes modifiers:** `TestInput` plus `InputConstantsMixin` make GLFW report the held keys as down.
- **Benchmark baseline:** `-Pbaseline` leaves out this mod's mixin config, and the dev-only `ClientSetupMixin`
  cancels `ClientSetup.init`, so EMI runs as if the mod were absent.
- **Spurious compile errors:** `clean build` once failed with hundreds of compile errors right after the clean, and
  passed when run again. If that happens, rerun before debugging.

Before calling a change done, run the build (unit tests). If the change touches input, rendering, auto-crafting or
storage, also run the self-test, and run it with `-Pfastbench` for auto-crafting. Add self-test checks for new
interactions.

## Code map (`src/main/java/com/greggifiedemi`)

- **Entry:** `GreggifiedEmi` (the `@Mod`; the mod ID is `greggifiedemi`) and `ClientSetup` (config, keys, tick,
  and the overlay drawn on `ScreenEvent.Render.Post`, priority LOWEST).
- **Settings:** `Config` writes `config/greggifiedemi-client.toml`. `Keys` holds NEI's key mappings, matched on the
  key alone because modifiers select variants.
- **`chain/`**: chain math with no Minecraft dependency, unit-tested.
  - `ChainItem`: one bookmark entry. It has `groupId`, a type (`RESULT`, `INGREDIENT` or `ITEM`), a `RecipeKey`, a
    `ChainStack`, permutations, `factor * multiplier` and `chance`.
  - `RecipeChainMath`: resolves a group's recipes into a chain.
  - `RecipeKey`: equality is by EMI recipe id, and the recipe is resolved lazily through a resolver set in
    `ClientSetup`.
  - `ContainerResolver` / `ItemContainerResolver`: crafting remainders such as buckets and damaged tools.
  - `Stacks`: same-type checks that ignore durability.
- **`bookmark/`**: the panel.
  - **Data:**
    - `BookmarkGrid` holds the flat item list plus groups. A recipe is stored as its results followed by its
      ingredients. Group 0 is the default group.
    - `BookmarkGroup` holds a group's view mode, its crafting state (`RecipeChainDetails`) and its collapsed recipes.
  - **Layout and drawing:**
    - `GridLayout`: per-geometry placement, port of NEI's `BookmarkGridGenerator`. Slot indexes are absolute across
      pages, and collapsed recipes use negative "shadow" item indexes.
    - `GridGeometry`: EMI's favorites space plus the 7px gutter.
    - `BookmarksGridSlot`: one shown slot.
    - `GroupGutter`: the brackets. `NamespaceFooter`: the `<` `>` and `P` footer.
    - `AmountText`: the amount labels.
  - **Input and UI:**
    - `BookmarkPanel` is the hub: drawing, mouse and keys over the grid, gutter and footer, plus cached tooltips.
    - `ItemShortcuts` handles key shortcuts on any hovered item (item list, recipe view, inventory, bookmark).
    - `Hotkeys` builds the Alt hotkey lists. They are memoized: `Hotkeys.Memo` keys on the inputs plus Alt, the screen
      and cheat mode, with a 1 s maximum age.
    - Drags: `SortableItem` and `SortableGroup` (Shift + drag), `GroupingItem` (gutter drag to group or ungroup),
      `HeldStack` (pick up and drop).
  - **Tooltips:** `BookmarkSlotStack` extends EMI's `EmiFavorite` so EMI's recipe and use keys and tooltips work on
    bookmarks. `ChainTooltip` and `ItemsTooltipComponent` draw the "Crafting Chain" group tooltip.
  - **Features:**
    - `AutoCrafting` (Shift + C) and `InventoryStacks`.
    - `ItemPuller` (V, P).
    - `RecipeOverlay` (S).
    - `RecipeBookmarks` (EMI recipe → bookmark items).
    - `FavoriteRecipes` (NEI favorites mapped to EMI default recipes).
    - `StatusMessage` (top-of-screen notes when an action can't run).
  - **Persistence:** `BookmarkStorage`.
    - It writes `greggifiedemi/bookmarks.json` in the game directory, or `greggifiedemi/worlds/<world>/` when
      `worldSpecific` is set.
    - It keeps one `BookmarkGrid` per namespace (NEI's bookmark pages).
    - Saves are debounced by 500 ms. The format version is `VERSION = 3`, and migrations live in the load code.
    - Stacks use EMI's serializers, so loading waits until EMI has loaded.
- **`mixin/`**: all mixins target EMI internals (`remap = false`, no refmap).
  - `EmiScreenManagerMixin` gives the panel first look at mouse and key input, and widens the favorites space by the
    gutter.
  - `ScreenSpaceMixin` replaces the drawing of the favorites list.
  - `EmiSidebarsMixin` makes EMI's favorites stack list come from the grid's slots, so paging and hover follow it.
  - `SidebarPanelMixin` re-lays out on sidebar switches and draws the page label.
  - `EmiFavoritesMixin` turns EMI's own favorite adds into bookmarks.
  - `SlotWidgetMixin` adds hotkey hints in the recipe view. `RecipeScreenAccessor` finds the recipe under the mouse.
- **`network/`**: `ChatLinks` and `ChatLinksClient`, for Ctrl + L item links. The channel is optional, and this is
  the only feature that needs the mod on the server.

## Things that are easy to get wrong

- **EMI version coupling.** The mixins rely on EMI internals, so `mods.toml` pins EMI to `[1.1.22,1.2)`. The build
  uses 1.1.24. If you bump EMI, check every mixin target, including the `require = 0` `@ModifyVariable`s, which fail
  silently.
- **Recomputing after changes.** Every data change must go through `BookmarkGrid.onItemsChanged()`. It increments
  `revision()`, clears cached layouts and schedules a save. Caches such as the hotkey memo and the chain tooltip key
  on `revision()`. `onGridChanged()` re-lays out without a data change.
- **EMI reloads.** A reload replaces EMI's recipe manager. `BookmarkPanel.currentRecipeGeneration()` detects this and
  lays the grid out again.
- **Sorting is performance-critical.** `BookmarkGrid.sortIngredients()` runs on every edit. It is a linear rewrite of
  NEI's quadratic algorithm, and `BookmarkGridTest.sortingMatchesNei` checks it against the original on 500 random
  lists. Keep that test passing when you change the sort.
- **`ChainItem.hashCode`** includes `stack.key`. Without it, deduplication degrades badly on large lists.
- **Auto-crafting with FastWorkbench.** FastWorkbench computes the crafting result a tick later, so auto-crafting
  works in steps: EMI fills the grid, auto-crafting waits for the result, then shift-clicks it out. Filling and taking
  the result in the same instant takes nothing.
- **Item counting** (chain tooltip, auto-crafting) must match items the way EMI does: any accepted substitute counts,
  and tool NBT and wear are ignored. GregTech tools carry heavy NBT.
- **Lag when opening uses.** It is EMI's own cost on items with thousands of uses. The benchmark shows the mod makes
  opening faster. Don't chase it in this mod without a profile.
- **Keeping the mod ID.** The jar is named `gtnh-bookmarks-for-emi-*` (`mod_file_name` in `gradle.properties`), but
  the mod ID, package, config file and save folder are all still `greggifiedemi`. Renaming them would orphan users'
  bookmarks and settings.

## Style

- **Java:** Java 17, Mojang official mappings, 4-space indent, `final` locals, Javadoc on every class saying what it
  does and which NEI class it ports.
- **Comments:** explain *why*, especially wherever behaviour copies or deviates from NEI. Match the density of the
  existing code.
- **Releasing:**
  1. Bump `mod_version` in `gradle.properties`.
  2. Build.
  3. Commit, tag `vX.Y.Z` and push.
  4. Run `gh release create` with the jar from `build/libs/`.
  5. Update `README.md` whenever controls, settings or requirements change.
