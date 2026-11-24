package com.greggifiedemi.testkit;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import com.greggifiedemi.Config;
import com.greggifiedemi.bookmark.BookmarkGroup;
import com.greggifiedemi.bookmark.BookmarkStorage;
import com.greggifiedemi.bookmark.BookmarkViewMode;
import com.greggifiedemi.bookmark.RecipeBookmarks;
import com.greggifiedemi.mixin.RecipeScreenAccessor;
import com.greggifiedemi.tree.CraftingTreeScreen;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.search.EmiSearch;

/**
 * Greggified EMI's features against the pack's own recipes (only loaded when the mod is present). Runs as a small
 * state machine over client ticks because EMI's search and screen changes complete asynchronously.
 */
final class GreggifiedChecks {

    private static int step = -1;
    private static int wait;
    private static int group = -1;
    private static List<? extends EmiIngredient> before;
    private static dev.emi.emi.api.widget.Bounds scaleBounds;
    private static int scalePageSize;

    private GreggifiedChecks() {}

    static void setHistoryPanel(boolean enabled) {
        Config.setHistoryPanel(enabled);
        com.greggifiedemi.nei.HistoryPanel.sync();
    }

    static void setItemZoom(boolean enabled) {
        Config.setItemZoom(enabled);
    }

    static void run(Minecraft minecraft) {
        step = 0;
        wait = 0;
    }

    private static void shot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, "testkit-" + name + ".png", minecraft.getMainRenderTarget(),
                message -> Testkit.LOGGER.info("[testkit] {}", message.getString()));
    }

    /** One step per call; returns true when finished. */
    static boolean done(Minecraft minecraft) {
        if (step < 0) {
            return true;
        }

        if (wait-- > 0) {
            return false;
        }

        wait = 20;

        switch (step++) {
            case 0 -> {
                // EMI load-time fixes (when switched on in the config)
                if (Config.emiParallelSearch()) {
                    Testkit.check(EmiSearch.names instanceof com.greggifiedemi.perf.ParallelSuffixArray<?>,
                            "EMI's search index is built in parallel");
                }
                if (Config.emiFastRegexFilters()) {
                    Testkit.check(com.greggifiedemi.perf.FastRegex.fastMatches() > 0,
                            "EMI's recipe filter regexes took the linear path ("
                                    + com.greggifiedemi.perf.FastRegex.fastMatches() + " matches)");
                }
                Testkit.check(EmiConfig.rightSidebarSubpanels.subpanels.stream()
                        .anyMatch(sub -> sub.type == SidebarType.LOOKUP_HISTORY)
                        || EmiConfig.leftSidebarSubpanels.subpanels.stream()
                                .anyMatch(sub -> sub.type == SidebarType.LOOKUP_HISTORY),
                        "history panel added under the index");
                Testkit.check(!dev.emi.emi.runtime.EmiSidebars.lookupHistory.isEmpty(),
                        "looking up recipes filled the history");
                EmiScreenManager.search.setValue("&minecraft:");
            }
            case 1 -> {
                final var found = EmiSearch.stacks;
                Testkit.check(!found.isEmpty() && found.stream().allMatch(stack -> !stack.getEmiStacks().isEmpty()
                        && String.valueOf(stack.getEmiStacks().get(0).getId()).startsWith("minecraft:")),
                        "&minecraft: finds only minecraft ids (" + found.size() + ")");
                EmiScreenManager.search.setValue("$forge:ingots");
            }
            case 2 -> {
                final var found = EmiSearch.stacks;
                Testkit.check(found.size() > 10, "$forge:ingots finds the ingots tag (" + found.size() + ")");
                before = EmiSearch.stacks;
                EmiScreenManager.search.setValue("#tfc");
                wait = 100;
            }
            case 3 -> {
                Testkit.check(EmiSearch.stacks != before && !EmiSearch.stacks.isEmpty(),
                        "#tooltip search finished with results (" + EmiSearch.stacks.size() + ")");
                EmiScreenManager.search.setValue("");
                minecraft.setScreen(new InventoryScreen(minecraft.player));
            }
            case 4 -> {
                // A over the first index stack adds a bookmark
                final var right = EmiScreenManager.getPanelFor(SidebarSide.RIGHT);
                Testkit.moveMouse(right.space.tx + 9, right.space.ty + 9);
                final int before = BookmarkStorage.grid().size();
                Testkit.check(EmiScreenManager.keyPressed(GLFW.GLFW_KEY_A, 30, 0), "A over the index consumed");
                // NEI toggles: A adds the stack, or removes it if an earlier run already bookmarked it
                Testkit.check(Math.abs(BookmarkStorage.grid().size() - before) == 1, "A toggled the bookmark");
                // a two-level crafting chain from the pack's own crafting recipes
                final EmiRecipe[] chain = findChain();
                Testkit.check(chain != null, "found a two-level crafting chain in the pack");
                if (chain != null) {
                    final var grid = BookmarkStorage.grid();
                    group = grid.addGroup(new BookmarkGroup(BookmarkViewMode.TODO_LIST, true));
                    grid.addRecipe(RecipeBookmarks.build(chain[0], group, 2, null), group);
                    grid.addRecipe(RecipeBookmarks.build(chain[1], group, 0, null), group);
                    Testkit.LOGGER.info("[testkit] chain {} <- {}", chain[0].getId(), chain[1].getId());
                }
                Testkit.moveMouse(5, 5);
            }
            case 5 -> {
                shot(minecraft, "inventory");
                if (group != -1) {
                    Testkit.check(CraftingTreeScreen.open(BookmarkStorage.grid(), group), "crafting tree opened");
                }
            }
            case 6 -> {
                if (minecraft.screen instanceof CraftingTreeScreen tree) {
                    Testkit.check(tree.nodeCount() >= 2 && tree.rootCount() >= 1,
                            "tree has nodes (" + tree.nodeCount() + ") and roots (" + tree.rootCount() + ")");
                    // cast: the testkit's reobf does not know CraftingTreeScreen is a Screen
                    final net.minecraft.client.gui.screens.Screen screen = tree;
                    Testkit.moveMouse(screen.width / 2.0, screen.height / 2.0);
                }
            }
            case 7 -> {
                shot(minecraft, "crafting-tree");
                if (minecraft.screen instanceof CraftingTreeScreen) {
                    minecraft.screen.onClose();
                }
                // creative mode swaps the inventory for the creative screen; either is the screen the tree came from
                Testkit.check(minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>,
                        "closing the tree returns to the inventory");
                final EmiStack heavy = heaviest();
                EmiApi.displayRecipes(heavy);
            }
            case 8 -> {
                Testkit.check(minecraft.screen instanceof RecipeScreen, "recipe view opened");
                if (minecraft.screen instanceof RecipeScreen screen) {
                    final RecipeScreenAccessor accessor = (RecipeScreenAccessor) screen;
                    final long window = minecraft.getWindow().getWindow();
                    final int tab = accessor.greggifiedemi$getTab();
                    final int page = accessor.greggifiedemi$getPage();
                    minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_RIGHT, 333, GLFW.GLFW_PRESS, 0);
                    minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_RIGHT, 333, GLFW.GLFW_RELEASE, 0);
                    Testkit.check(accessor.greggifiedemi$getTab() == tab
                            && (accessor.greggifiedemi$getPage() != page || page == 0),
                            "Right turns the recipe page in the pack");
                    minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_DOWN, 336, GLFW.GLFW_PRESS, 0);
                    minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_DOWN, 336, GLFW.GLFW_RELEASE, 0);
                    Testkit.check(accessor.greggifiedemi$getPage() == 0, "Down switches the category in the pack");
                }
            }
            case 9 -> {
                shot(minecraft, "recipe-view");
                minecraft.setScreen(new InventoryScreen(minecraft.player));
                Testkit.check(Config.itemZoom(), "item zoom is on by default");
                if (group != -1) {
                    BookmarkStorage.grid().removeGroup(group);
                }
            }
            case 10 -> {
                // detached EMI GUI scale: options in EMI's config screen, then a smaller EMI scale
                final net.minecraft.client.gui.screens.Screen screen = minecraft.screen;
                scaleBounds = dev.emi.emi.screen.EmiScreenBase.of(screen).bounds();
                scalePageSize = EmiScreenManager.getPanelFor(SidebarSide.RIGHT).space.pageSize;
                minecraft.setScreen(new dev.emi.emi.screen.ConfigScreen(screen));
            }
            case 11 -> {
                final var config = (dev.emi.emi.screen.ConfigScreen) minecraft.screen;
                Testkit.check(config.list.children().stream()
                        .filter(e -> e instanceof dev.emi.emi.screen.widget.config.ConfigEntryWidget)
                        .map(e -> ((dev.emi.emi.screen.widget.config.ConfigEntryWidget) e).getSearchableText())
                        .filter(t -> t.equals("Detach GUI Scale") || t.equals("EMI GUI Scale")).count() == 2,
                        "EMI's config screen lists the GUI scale options");
                final int game = (int) minecraft.getWindow().getGuiScale();
                Testkit.check(game > 1, "game GUI scale " + game + " leaves room for a smaller EMI scale");
                Config.setGuiScale(true, game - 1);
                final net.minecraft.client.gui.screens.Screen screen = config;
                screen.onClose();
            }
            case 12 -> {
                final net.minecraft.client.gui.screens.Screen screen = minecraft.screen;
                final var right = EmiScreenManager.getPanelFor(SidebarSide.RIGHT);
                final double k = (double) com.greggifiedemi.scale.EmiScale.emiScale()
                        / minecraft.getWindow().getGuiScale();
                Testkit.check(com.greggifiedemi.scale.EmiScale.isScaled(), "EMI uses its own GUI scale in the pack");
                Testkit.check(screen.width == minecraft.getWindow().getGuiScaledWidth()
                        && dev.emi.emi.screen.EmiScreenBase.of(screen).bounds().equals(scaleBounds),
                        "the inventory kept the game's GUI scale in the pack");
                Testkit.check(right.space.pageSize > scalePageSize,
                        "the item list shows more stacks (" + scalePageSize + " -> " + right.space.pageSize + ")");
                final var first = right.space.getPage(right.page).get(0);
                Testkit.moveMouse((right.space.tx + 9) * k, (right.space.ty + 9) * k);
                Testkit.check(!first.isEmpty() && EmiApi.getHoveredStack(false).getStack().getEmiStacks().get(0)
                        .isEqual(first.getEmiStacks().get(0)), "the scaled item list stack under the cursor is hovered");
            }
            case 13 -> {
                shot(minecraft, "detached-scale");
                EmiApi.displayRecipes(heaviest());
            }
            case 14 -> {
                Testkit.check(minecraft.screen instanceof RecipeScreen, "recipe view opened with the detached scale");
                final net.minecraft.client.gui.screens.Screen screen = minecraft.screen;
                Testkit.check(screen.width == minecraft.getWindow().getGuiScaledWidth(),
                        "the recipe view kept the game's GUI scale in the pack");
            }
            case 15 -> {
                shot(minecraft, "detached-scale-recipes");
                Config.setGuiScale(false, 0);
                minecraft.setScreen(new InventoryScreen(minecraft.player));
            }
            case 16 -> Testkit.check(!com.greggifiedemi.scale.EmiScale.isScaled()
                    && EmiScreenManager.getPanelFor(SidebarSide.RIGHT).space.pageSize == scalePageSize,
                    "attaching the scale restored the layout in the pack");
            default -> {
                step = -1;
                return true;
            }
        }

        return false;
    }

    private static EmiStack heaviest() {
        EmiStack best = EmiStack.EMPTY;
        int most = -1;

        for (EmiIngredient ingredient : dev.emi.emi.registry.EmiStackList.stacks) {
            if (ingredient instanceof EmiStack stack) {
                final int count = EmiApi.getRecipeManager().getRecipesByOutput(stack).size();
                if (count > most) {
                    most = count;
                    best = stack;
                }
            }
        }

        return best;
    }

    /** A crafting recipe and a crafting recipe for one of its ingredients. */
    private static EmiRecipe[] findChain() {
        final List<EmiRecipe> crafting = EmiApi.getRecipeManager().getRecipes(VanillaEmiRecipeCategories.CRAFTING);

        for (EmiRecipe top : crafting) {
            if (top.getId() == null || top.getInputs().size() < 2 || top.getOutputs().isEmpty()) {
                continue;
            }

            for (EmiIngredient input : top.getInputs()) {
                if (input.isEmpty() || input.getEmiStacks().size() != 1) {
                    continue;
                }

                for (EmiRecipe sub : EmiApi.getRecipeManager().getRecipesByOutput(input.getEmiStacks().get(0))) {
                    if (sub.getId() != null && sub.getCategory() == VanillaEmiRecipeCategories.CRAFTING
                            && sub != top) {
                        return new EmiRecipe[] { top, sub };
                    }
                }
            }
        }

        return null;
    }

}
