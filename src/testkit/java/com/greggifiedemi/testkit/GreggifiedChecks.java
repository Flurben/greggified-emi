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

    private GreggifiedChecks() {}

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
                Testkit.check(BookmarkStorage.grid().size() == before + 1, "A added a bookmark");
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
                Testkit.check(minecraft.screen instanceof InventoryScreen, "closing the tree returns to the inventory");
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
