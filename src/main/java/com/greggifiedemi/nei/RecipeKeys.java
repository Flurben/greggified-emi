package com.greggifiedemi.nei;

import net.minecraftforge.client.event.ScreenEvent;

import com.greggifiedemi.Config;
import com.greggifiedemi.Keys;
import com.greggifiedemi.mixin.RecipeScreenAccessor;

import dev.emi.emi.screen.RecipeScreen;

/**
 * NEI's recipe GUI hotkeys (GuiRecipe.onScreenHotkeyPressed) in EMI's recipe view: Left/Right change the page,
 * Up/Down change the category ("machine"). EMI alone uses Left/Right for categories and has no page keys. Runs on
 * Forge's pre-key event, before the screen sees the key.
 */
public final class RecipeKeys {

    private RecipeKeys() {}

    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof RecipeScreen screen) || !Config.neiRecipeKeys() || NeiInput.isTyping()) {
            return;
        }

        final RecipeScreenAccessor accessor = (RecipeScreenAccessor) screen;
        final int keyCode = event.getKeyCode();
        final int scanCode = event.getScanCode();
        final int tabPage = accessor.greggifiedemi$getTabPage();
        final int tab = accessor.greggifiedemi$getTab();
        final int page = accessor.greggifiedemi$getPage();

        if (Keys.matches(Keys.PREV_RECIPE, keyCode, scanCode)) {
            screen.setPage(tabPage, tab, page - 1);
        } else if (Keys.matches(Keys.NEXT_RECIPE, keyCode, scanCode)) {
            screen.setPage(tabPage, tab, page + 1);
        } else if (Keys.matches(Keys.PREV_MACHINE, keyCode, scanCode)) {
            screen.setPage(tabPage, tab - 1, 0);
        } else if (Keys.matches(Keys.NEXT_MACHINE, keyCode, scanCode)) {
            screen.setPage(tabPage, tab + 1, 0);
        } else {
            return;
        }

        event.setCanceled(true);
    }

}
