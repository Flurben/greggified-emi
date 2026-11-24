package com.greggifiedemi.nei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;

import com.greggifiedemi.Config;
import com.greggifiedemi.Keys;
import com.greggifiedemi.bookmark.BookmarkPanel;
import com.greggifiedemi.bookmark.ItemShortcuts;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.search.EmiSearch;

/**
 * NEI's item zoom (ItemZoom): the hovered item drawn large in the free space beside the GUI, with its name and the
 * toggle hint. Shift + Z toggles it, the "hold" key shows it while held, zoom in/out change its size. Like NEI it is
 * sized in real pixels so the item is drawn at a whole multiple of its texture.
 */
public final class ItemZoom {

    public static final int MIN_ZOOM = 0;
    public static final int MAX_ZOOM = 1000;
    private static final int MIN_SCALE = 2;
    private static final int SLOT_SIZE = 16;

    private ItemZoom() {}

    private static boolean active() {
        return Config.itemZoom() || Keys.isDown(Keys.ZOOM_HOLD);
    }

    /** Shift + Z toggles; zoom in/out resize while something is zoomed. Returns whether the key was used. */
    public static boolean keyPressed(int keyCode, int scanCode, double mouseX, double mouseY) {
        if (Keys.matchesExactly(Keys.ZOOM_TOGGLE, keyCode, scanCode, false, true, false)) {
            Config.setItemZoom(!Config.itemZoom());
            return true;
        }

        final Screen screen = Minecraft.getInstance().screen;

        if (screen == null) {
            return false;
        }

        if (Keys.matches(Keys.ZOOM_IN, keyCode, scanCode) && !hovered(screen, mouseX, mouseY).isEmpty()) {
            changeZoom(screen, 1);
            return true;
        }

        if (Keys.matches(Keys.ZOOM_OUT, keyCode, scanCode) && !hovered(screen, mouseX, mouseY).isEmpty()) {
            changeZoom(screen, -1);
            return true;
        }

        return Keys.matches(Keys.ZOOM_HOLD, keyCode, scanCode) && !hovered(screen, mouseX, mouseY).isEmpty();
    }

    private static double guiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    /** NEI's getPointSize: one zoom unit in real pixels. */
    private static float pointSize(Screen screen, int guiWidth) {
        final float availableAreaWidth = (float) ((screen.width - (guiWidth + screen.width) / 2) * guiScale());
        return Math.max(0.0001f, availableAreaWidth / MAX_ZOOM);
    }

    private static int zoomAmount(Screen screen, int guiWidth) {
        return Math.max(Math.round(SLOT_SIZE * MIN_SCALE / pointSize(screen, guiWidth)), Config.itemZoomAmount());
    }

    private static void changeZoom(Screen screen, int direction) {
        final int guiWidth = guiBounds(screen).width();
        final float pointSize = pointSize(screen, guiWidth);
        final float scale = Math.round(zoomAmount(screen, guiWidth) * pointSize / SLOT_SIZE);
        final float next = direction > 0 ? scale + 1 : Math.max(MIN_SCALE, scale - 1);
        Config.setItemZoomAmount(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.round(next / (pointSize / SLOT_SIZE)))));
    }

    private static Bounds guiBounds(Screen screen) {
        final Bounds bounds = EmiScreenBase.of(screen).bounds();
        return bounds == null || bounds.empty() ? new Bounds(screen.width / 2 - 88, 0, 176, screen.height) : bounds;
    }

    /** NEI's getStackMouseOver: EMI's panels first, then (unless neiOnly) the inventory slot. */
    private static EmiStack hovered(Screen screen, double mouseX, double mouseY) {
        final EmiStack emi = ItemShortcuts.hoveredStack(mouseX, mouseY);

        if (!emi.isEmpty() || Config.itemZoomNeiOnly()) {
            return emi;
        }

        if (screen instanceof AbstractContainerScreen<?> container) {
            final Slot slot = container.getSlotUnderMouse();

            if (slot != null && slot.hasItem()) {
                return EmiStack.of(slot.getItem());
            }
        }

        return EmiStack.EMPTY;
    }

    private static boolean preferLeftSide() {
        return switch (Config.itemZoomPreferredSide()) {
            case 1 -> true;
            case 2 -> false;
            default -> {
                final int left = Config.bookmarksHidden() ? 0 : BookmarkPanel.grid().size();
                final List<? extends EmiIngredient> index = EmiSearch.stacks;
                yield left <= (index == null ? 0 : index.size());
            }
        };
    }

    public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!active() || EmiScreenBase.of(screen).isEmpty()) {
            return;
        }

        if (screen instanceof AbstractContainerScreen<?> container && !container.getMenu().getCarried().isEmpty()) {
            return;
        }

        final EmiStack stack = hovered(screen, mouseX, mouseY);

        if (stack.isEmpty()) {
            return;
        }

        final Bounds gui = guiBounds(screen);
        final double guiScale = guiScale();
        // NEI works in real pixels: the free area beside the GUI, the item centred in it
        final float areaWidthPx = (float) ((screen.width - gui.width()) / 2 * guiScale);
        final float areaHeightPx = (float) (screen.height * guiScale);
        final int areaWidth = (int) (areaWidthPx / guiScale);

        if (areaWidth < SLOT_SIZE * 2) {
            return;
        }

        final float scale = Math.max(1,
                Math.round(zoomAmount(screen, gui.width()) * pointSize(screen, gui.width()) / SLOT_SIZE));
        final float sizePx = SLOT_SIZE * scale;
        float xPx = (areaWidthPx - sizePx) / 2;
        final float yPx = (areaHeightPx - sizePx) / 2;

        final boolean leftSideFree = areaWidth < mouseX;
        final boolean rightSideFree = mouseX < screen.width - areaWidth;
        final boolean showOnRight = leftSideFree && rightSideFree ? !preferLeftSide() : !leftSideFree;

        if (showOnRight) {
            xPx += (float) (screen.width * guiScale - areaWidthPx);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.pose().scale((float) (1 / guiScale), (float) (1 / guiScale), 1);
        graphics.pose().translate(xPx, yPx, 0);
        graphics.pose().scale(scale, scale, 1);
        stack.render(graphics, 0, 0, delta, EmiIngredient.RENDER_ICON);
        graphics.pose().popPose();

        final Font font = Minecraft.getInstance().font;
        final int centerX = (int) ((xPx + sizePx / 2) / guiScale);
        int textY = (int) ((yPx + sizePx) / guiScale) + 10;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);

        if (Config.itemZoomShowName()) {
            final String name = font.plainSubstrByWidth(stack.getName().getString(), areaWidth);
            graphics.drawCenteredString(font, name, centerX, textY, Config.itemZoomNameColor());
            textY += font.lineHeight;
        }

        if (Config.itemZoom() && Config.itemZoomHelpText() && Keys.isBound(Keys.ZOOM_TOGGLE)) {
            final Component help = Component.translatable("greggifiedemi.itemzoom.toggle",
                    "SHIFT + " + Keys.name(Keys.ZOOM_TOGGLE));

            for (FormattedCharSequence line : font.split(help, areaWidth)) {
                graphics.drawString(font, line, centerX - font.width(line) / 2, textY + 10, 0x66555555, false);
                textY += font.lineHeight;
            }
        }

        graphics.pose().popPose();
    }

}
