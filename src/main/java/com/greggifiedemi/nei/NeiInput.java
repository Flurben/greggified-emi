package com.greggifiedemi.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import com.greggifiedemi.Keys;
import com.greggifiedemi.bookmark.BookmarkPanel;

import dev.emi.emi.EmiPort;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;

/**
 * Key input over EMI in NEI's order (LayoutManager.onHotkeyPressed, then the item shortcuts, then the panel widgets):
 * <ol>
 * <li>{@code gui.hide} (O) toggles EMI, also while it is hidden;</li>
 * <li>the bookmark panel and every shortcut on the hovered item or group;</li>
 * <li>item zoom (Shift + Z, zoom keys);</li>
 * <li>{@code gui.search} (F, no modifiers) focuses the search bar;</li>
 * <li>{@code gui.prev}/{@code gui.next} (Page Up/Down) page the EMI panel under the mouse.</li>
 * </ol>
 * Nothing here runs while a text field has focus, so typing is never stolen.
 */
public final class NeiInput {

    private NeiInput() {}

    public static boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!EmiReloadManager.isLoaded() || EmiScreenBase.getCurrent().isEmpty() || isTyping()) {
            return false;
        }

        if (Keys.matches(Keys.HIDE_ALL, keyCode, scanCode)) {
            EmiScreenManager.toggleVisibility(true);
            return true;
        }

        if (EmiScreenManager.isDisabled()) {
            return false;
        }

        if (BookmarkPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        final double mouseX = EmiScreenManager.lastMouseX;
        final double mouseY = EmiScreenManager.lastMouseY;

        if (ItemZoom.keyPressed(keyCode, scanCode, mouseX, mouseY)) {
            return true;
        }

        if (Keys.matchesExactly(Keys.SEARCH, keyCode, scanCode, false, false, false)) {
            final Screen screen = Minecraft.getInstance().screen;

            if (screen != null) {
                screen.setFocused(EmiScreenManager.search);
                EmiPort.focus(EmiScreenManager.search, true);
                return true;
            }
        }

        final boolean next = Keys.matches(Keys.NEXT_PAGE, keyCode, scanCode);

        if (next || Keys.matches(Keys.PREV_PAGE, keyCode, scanCode)) {
            final EmiScreenManager.ScreenSpace space = EmiScreenManager.getHoveredSpace((int) mouseX, (int) mouseY);

            if (space != null && space.getType() != SidebarType.FAVORITES) {
                final EmiScreenManager.SidebarPanel panel = EmiScreenManager.getPanelFor(space.getType());

                if (panel != null) {
                    panel.scroll(next ? 1 : -1);
                    return true;
                }
            }
        }

        return false;
    }

    /** EMI's search bar or another text field of the screen has focus. */
    public static boolean isTyping() {
        final Screen screen = Minecraft.getInstance().screen;
        return EmiScreenManager.search.canConsumeInput() || screen != null
                && screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox box
                && box.canConsumeInput();
    }

}
