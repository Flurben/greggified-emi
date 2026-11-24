package com.greggifiedemi;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * NEI's key bindings (NEIClientConfig.setDefaultKeyBindings in NEI 2.8.175), with NEI's ids and default keys, under
 * Controls > "Greggified EMI (NEI)". Like NEI, modifiers select variants of an action (Shift + C crafts, Ctrl + C copies
 * the name), so bindings are matched on the key alone and handlers read the modifiers. Adding and removing bookmarks
 * uses EMI's "favorite" key (A), and recipes/uses/back use EMI's keys (R, U, Backspace), which have NEI's defaults.
 */
public final class Keys {

    private static final String CATEGORY = "key.categories.greggifiedemi";

    // NEI Inventory (gui.*)
    public static final KeyMapping HIDE_ALL = key("gui.hide", GLFW.GLFW_KEY_O);
    public static final KeyMapping SEARCH = key("gui.search", GLFW.GLFW_KEY_F);
    public static final KeyMapping PREV_PAGE = key("gui.prev", GLFW.GLFW_KEY_PAGE_UP);
    public static final KeyMapping NEXT_PAGE = key("gui.next", GLFW.GLFW_KEY_PAGE_DOWN);
    public static final KeyMapping OVERLAY = key("gui.overlay", GLFW.GLFW_KEY_S);
    public static final KeyMapping CRAFT_ITEMS = key("gui.craft_items", GLFW.GLFW_KEY_C);

    // NEI Recipe (recipe.*); recipes, usages and back are EMI's own keys (R, U, Backspace)
    public static final KeyMapping PREV_MACHINE = key("recipe.prev_machine", GLFW.GLFW_KEY_UP);
    public static final KeyMapping NEXT_MACHINE = key("recipe.next_machine", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping PREV_RECIPE = key("recipe.prev_recipe", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping NEXT_RECIPE = key("recipe.next_recipe", GLFW.GLFW_KEY_RIGHT);

    // NEI Bookmarks (bookmark.*); "add" is EMI's favorite key (A)
    public static final KeyMapping FAVORITE = key("bookmark.favorite", GLFW.GLFW_KEY_F);
    public static final KeyMapping PULL_ITEMS = key("bookmark.pull_items", GLFW.GLFW_KEY_V);
    public static final KeyMapping CHAT_LINK = key("bookmark.chat_link", GLFW.GLFW_KEY_L);
    public static final KeyMapping OPEN_CRAFTING_TREE = key("bookmark.open_crafting_tree", GLFW.GLFW_KEY_T);
    public static final KeyMapping HIDE = key("bookmark.hide", GLFW.GLFW_KEY_B);

    // NEI Item Zoom (itemzoom.*)
    public static final KeyMapping ZOOM_TOGGLE = key("itemzoom.toggle", GLFW.GLFW_KEY_Z);
    public static final KeyMapping ZOOM_HOLD = key("itemzoom.hold", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping ZOOM_IN = key("itemzoom.zoom_in", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping ZOOM_OUT = key("itemzoom.zoom_out", GLFW.GLFW_KEY_UNKNOWN);

    // NEI Copy (copy.*)
    public static final KeyMapping COPY_NAME = key("copy.name", GLFW.GLFW_KEY_C);
    public static final KeyMapping COPY_OREDICT = key("copy.oredict", GLFW.GLFW_KEY_D);
    public static final KeyMapping COPY_IDENTIFIER = key("copy.identifier", GLFW.GLFW_KEY_X);

    public static final List<KeyMapping> ALL = List.of(HIDE_ALL, SEARCH, PREV_PAGE, NEXT_PAGE, OVERLAY, CRAFT_ITEMS,
            PREV_MACHINE, NEXT_MACHINE, PREV_RECIPE, NEXT_RECIPE, FAVORITE, PULL_ITEMS, CHAT_LINK, OPEN_CRAFTING_TREE,
            HIDE, ZOOM_TOGGLE, ZOOM_HOLD, ZOOM_IN, ZOOM_OUT, COPY_NAME, COPY_OREDICT, COPY_IDENTIFIER);

    private Keys() {}

    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.greggifiedemi." + name, KeyConflictContext.GUI, InputConstants.Type.KEYSYM, key,
                CATEGORY);
    }

    /** Whether a key event is this binding, ignoring held modifiers (they select variants of the action). */
    public static boolean matches(KeyMapping mapping, int keyCode, int scanCode) {
        final InputConstants.Key key = mapping.getKey();

        if (key.equals(InputConstants.UNKNOWN)) {
            return false;
        }

        return key.getType() == InputConstants.Type.KEYSYM ? key.getValue() == keyCode
                : key.getType() == InputConstants.Type.SCANCODE && key.getValue() == scanCode;
    }

    /** Whether a held binding is down right now (for "hold" bindings such as item zoom's). */
    public static boolean isDown(KeyMapping mapping) {
        final InputConstants.Key key = mapping.getKey();

        if (key.equals(InputConstants.UNKNOWN)) {
            return false;
        }

        final long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        return key.getType() == InputConstants.Type.MOUSE
                ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS
                : key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, key.getValue());
    }

    /** NEI's isHashDown: the key with exactly these modifiers held. */
    public static boolean matchesExactly(KeyMapping mapping, int keyCode, int scanCode, boolean ctrl, boolean shift,
            boolean alt) {
        return matches(mapping, keyCode, scanCode)
                && net.minecraft.client.gui.screens.Screen.hasControlDown() == ctrl
                && net.minecraft.client.gui.screens.Screen.hasShiftDown() == shift
                && net.minecraft.client.gui.screens.Screen.hasAltDown() == alt;
    }

    public static boolean isBound(KeyMapping mapping) {
        return !mapping.getKey().equals(InputConstants.UNKNOWN);
    }

    /** The key's name as NEI writes it in hotkey lists ("C", "PAGE UP"). */
    public static String name(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString().toUpperCase();
    }

}
