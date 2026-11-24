package com.greggifiedemi;

import java.util.function.Supplier;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client options, mirroring NEI's bookmark settings. Stored in {@code config/greggifiedemi-client.toml}.
 */
public final class Config {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue WORLD_SPECIFIC;
    private static final ForgeConfigSpec.BooleanValue ITEMS_WITH_RECIPE;
    private static final ForgeConfigSpec.BooleanValue RECIPE_WITH_COUNT;
    private static final ForgeConfigSpec.IntValue RECIPE_MARKER_MODE;
    private static final ForgeConfigSpec.IntValue RECIPE_MARKER_COLOR;
    private static final ForgeConfigSpec.BooleanValue RECIPE_HANDLER_ICON;
    private static final ForgeConfigSpec.BooleanValue ANIMATION;
    private static final ForgeConfigSpec.BooleanValue AUTOCRAFTING;
    private static final ForgeConfigSpec.BooleanValue SHOW_HOTKEYS;
    private static final ForgeConfigSpec.BooleanValue HOTKEYS_HELP_TEXT;
    private static final ForgeConfigSpec.IntValue RECIPE_TOOLTIPS_MODE;
    private static final ForgeConfigSpec.BooleanValue IMPORT_EMI_FAVORITES;
    private static final ForgeConfigSpec.BooleanValue HIDDEN;
    private static final ForgeConfigSpec.IntValue FAVORITES_DEPTH;
    private static final ForgeConfigSpec.BooleanValue NEI_SEARCH_SYNTAX;
    private static final ForgeConfigSpec.BooleanValue NEI_RECIPE_KEYS;
    private static final ForgeConfigSpec.BooleanValue HISTORY_ENABLED;
    private static final ForgeConfigSpec.IntValue HISTORY_ROWS;
    private static final ForgeConfigSpec.BooleanValue HIGHLIGHT_PRESENT;
    private static final ForgeConfigSpec.BooleanValue DETACH_SCALE;
    private static final ForgeConfigSpec.IntValue EMI_SCALE;
    private static final ForgeConfigSpec.BooleanValue ZOOM_ENABLED;
    private static final ForgeConfigSpec.BooleanValue ZOOM_NEI_ONLY;
    private static final ForgeConfigSpec.IntValue ZOOM_PREFERRED_SIDE;
    private static final ForgeConfigSpec.BooleanValue ZOOM_HELP_TEXT;
    private static final ForgeConfigSpec.IntValue ZOOM;
    private static final ForgeConfigSpec.BooleanValue ZOOM_SHOW_NAME;
    private static final ForgeConfigSpec.IntValue ZOOM_NAME_COLOR;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("bookmarks");
        WORLD_SPECIFIC = builder.comment("Keep a separate set of bookmarks for every world/server instead of one "
                + "global set.").define("worldSpecific", false);
        ITEMS_WITH_RECIPE = builder.comment("Bookmarking an item that has a recipe context (for example a recipe "
                + "output in EMI's recipe view) remembers that recipe with the item.").define("itemsWithRecipe", true);
        RECIPE_WITH_COUNT = builder.comment("Recipes bookmarked with Shift + A start with a quantity of 1 instead of 0 "
                + "(Ctrl + Shift + A always starts at 1).").define("recipeWithCount", false);
        RECIPE_TOOLTIPS_MODE = builder.comment("Show recipe tooltips in bookmarks: 0 = none, 1 = in horizontal "
                + "(default) view, 2 = in vertical (to-do list) view, 3 = always.")
                .defineInRange("recipeTooltipsMode", 1, 0, 3);
        RECIPE_MARKER_MODE = builder.comment("Show recipe marker: 0 = none, 1 = text ('R' on every result), "
                + "2 = background behind results.").defineInRange("recipeMarkerMode", 0, 0, 2);
        RECIPE_MARKER_COLOR = builder.comment("Color of the 'xN' quantity marker (RGB).")
                .defineInRange("recipeMarkerColor", 0xADADAD, 0, 0xFFFFFF);
        RECIPE_HANDLER_ICON = builder.comment("Show the recipe category icon on bookmarked recipes.")
                .define("recipeHandlerIcon", false);
        ANIMATION = builder.comment("Pop-in animation for new bookmarks.").define("animation", true);
        HIDDEN = builder.comment("The bookmark panel is hidden (toggled with the 'Toggle Bookmark Panel' key, B).")
                .define("hidden", false);
        IMPORT_EMI_FAVORITES = builder.comment("On first start, copy EMI's existing favorites into the bookmarks.")
                .define("importEmiFavorites", true);
        builder.pop();

        builder.push("favorites");
        FAVORITES_DEPTH = builder.comment("How many levels of favorite recipes Shift + F ('Save Recipe Tree') follows "
                + "below the item (NEI: inventory.favorites.depth).").defineInRange("depth", 9, 0, 100);
        builder.pop();

        builder.push("layout");
        NEI_SEARCH_SYNTAX = builder.comment("Use NEI's search prefixes: #tooltip, $tag (ore dictionary), &identifier, "
                + "@mod. EMI's own are $tooltip and #tag.").define("neiSearchSyntax", true);
        NEI_RECIPE_KEYS = builder.comment("In EMI's recipe view, Left/Right change the page and Up/Down the category "
                + "like NEI (EMI alone: Left/Right change the category).").define("neiRecipeKeys", true);
        HISTORY_ENABLED = builder.comment("Show NEI's history panel (EMI's lookup history) below the item list, "
                + "unless EMI's config already places the lookup history somewhere.").define("historyPanel", true);
        HISTORY_ROWS = builder.comment("Rows used by the history panel (NEI: inventory.history.useRows).")
                .defineInRange("historyRows", 2, 1, 10);
        HIGHLIGHT_PRESENT = builder.comment("In the recipe view, highlight ingredients you already carry "
                + "(NEI: inventory.guirecipe.slotHighlightPresent).").define("slotHighlightPresent", true);
        DETACH_SCALE = builder.comment("Draw EMI's panels (item list, bookmarks, history, search bar) at their own GUI "
                + "scale instead of the game's. Inventories and the recipe view keep the game's GUI scale. Also in "
                + "EMI's config screen.").define("detachGuiScale", false);
        EMI_SCALE = builder.comment("EMI's GUI scale while detached: 0 = Auto (the largest the window allows), "
                + "otherwise like Video Settings > GUI Scale, capped to what the window allows.")
                .defineInRange("guiScale", 0, 0, 16);
        builder.pop();

        builder.push("itemzoom");
        ZOOM_ENABLED = builder.comment("Show the hovered item enlarged beside the GUI (toggle: Shift + Z).")
                .define("enabled", true);
        ZOOM_NEI_ONLY = builder.comment("Only zoom items hovered in EMI's panels, not in the inventory.")
                .define("neiOnly", false);
        ZOOM_PREFERRED_SIDE = builder.comment("0 = the side with fewer items, 1 = left, 2 = right.")
                .defineInRange("preferredSide", 0, 0, 2);
        ZOOM_HELP_TEXT = builder.comment("Show the toggle hint below the zoomed item.").define("helpText", true);
        ZOOM = builder.comment("Zoom amount in percent of the free space (NEI: inventory.itemzoom.zoom).")
                .defineInRange("zoom", 500, 100, 1000);
        ZOOM_SHOW_NAME = builder.comment("Show the item name below the zoomed item.").define("showName", true);
        ZOOM_NAME_COLOR = builder.comment("Color of the name (ARGB).").defineInRange("nameColor", 0xFFFFFFFF,
                Integer.MIN_VALUE, Integer.MAX_VALUE);
        builder.pop();

        AUTOCRAFTING = builder.comment("Allow Shift + C to craft bookmarked recipes and crafting chains.")
                .define("autocrafting", true);
        SHOW_HOTKEYS = builder.comment("List the available hotkeys in tooltips while Alt is held.")
                .define("showHotkeys", true);
        HOTKEYS_HELP_TEXT = builder.comment("Show 'Hold ALT for hotkeys' in tooltips.")
                .define("hotkeysHelpText", true);

        SPEC = builder.build();
    }

    private Config() {}

    private static <T> T get(Supplier<T> value, T fallback) {
        try {
            return SPEC.isLoaded() ? value.get() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static boolean worldSpecific() {
        return get(WORLD_SPECIFIC::get, false);
    }

    public static boolean bookmarkItemsWithRecipe() {
        return get(ITEMS_WITH_RECIPE::get, true);
    }

    public static boolean bookmarkRecipeWithCount() {
        return get(RECIPE_WITH_COUNT::get, false);
    }

    public static int recipeTooltipsMode() {
        return get(RECIPE_TOOLTIPS_MODE::get, 1);
    }

    public static boolean hotkeysHelpText() {
        return get(HOTKEYS_HELP_TEXT::get, true);
    }

    public static int showRecipeMarkerMode() {
        return get(RECIPE_MARKER_MODE::get, 0);
    }

    public static int recipeMarkerColor() {
        return get(RECIPE_MARKER_COLOR::get, 0xADADAD);
    }

    public static boolean showRecipeHandlerIcon() {
        return get(RECIPE_HANDLER_ICON::get, false);
    }

    public static boolean animation() {
        return get(ANIMATION::get, true);
    }

    public static boolean autocrafting() {
        return get(AUTOCRAFTING::get, true);
    }

    public static boolean showHotkeys() {
        return get(SHOW_HOTKEYS::get, true);
    }

    public static boolean bookmarksHidden() {
        return get(HIDDEN::get, false);
    }

    public static void setBookmarksHidden(boolean hidden) {
        if (SPEC.isLoaded()) {
            HIDDEN.set(hidden);
            SPEC.save();
        }
    }

    public static int favoritesDepth() {
        return get(FAVORITES_DEPTH::get, 9);
    }

    public static boolean neiSearchSyntax() {
        return get(NEI_SEARCH_SYNTAX::get, true);
    }

    public static boolean neiRecipeKeys() {
        return get(NEI_RECIPE_KEYS::get, true);
    }

    public static boolean historyPanel() {
        return get(HISTORY_ENABLED::get, true);
    }

    public static void setHistoryPanel(boolean enabled) {
        if (SPEC.isLoaded()) {
            HISTORY_ENABLED.set(enabled);
            SPEC.save();
        }
    }

    public static int historyRows() {
        return get(HISTORY_ROWS::get, 2);
    }

    public static boolean slotHighlightPresent() {
        return get(HIGHLIGHT_PRESENT::get, true);
    }

    public static boolean detachGuiScale() {
        return get(DETACH_SCALE::get, false);
    }

    /** EMI's own GUI scale while detached; 0 = Auto. */
    public static int emiGuiScale() {
        return get(EMI_SCALE::get, 0);
    }

    public static void setGuiScale(boolean detached, int scale) {
        if (SPEC.isLoaded()) {
            DETACH_SCALE.set(detached);
            EMI_SCALE.set(Math.max(0, Math.min(16, scale)));
            SPEC.save();
        }
        com.greggifiedemi.scale.EmiScale.reloadSettings();
    }

    public static boolean itemZoom() {
        return get(ZOOM_ENABLED::get, true);
    }

    public static void setItemZoom(boolean enabled) {
        if (SPEC.isLoaded()) {
            ZOOM_ENABLED.set(enabled);
            SPEC.save();
        }
    }

    public static boolean itemZoomNeiOnly() {
        return get(ZOOM_NEI_ONLY::get, false);
    }

    public static int itemZoomPreferredSide() {
        return get(ZOOM_PREFERRED_SIDE::get, 0);
    }

    public static boolean itemZoomHelpText() {
        return get(ZOOM_HELP_TEXT::get, true);
    }

    public static int itemZoomAmount() {
        return get(ZOOM::get, 500);
    }

    public static void setItemZoomAmount(int zoom) {
        if (SPEC.isLoaded()) {
            ZOOM.set(Math.max(100, Math.min(1000, zoom)));
            SPEC.save();
        }
    }

    public static boolean itemZoomShowName() {
        return get(ZOOM_SHOW_NAME::get, true);
    }

    public static int itemZoomNameColor() {
        return get(ZOOM_NAME_COLOR::get, 0xFFFFFFFF);
    }

    public static boolean importEmiFavorites() {
        return get(IMPORT_EMI_FAVORITES::get, true);
    }

}
