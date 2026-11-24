package com.greggifiedemi.tree;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import com.greggifiedemi.Config;
import com.greggifiedemi.bookmark.AmountText;
import com.greggifiedemi.bookmark.BookmarkGrid;
import com.greggifiedemi.bookmark.BookmarkGroup;
import com.greggifiedemi.bookmark.Hotkeys;
import com.greggifiedemi.bookmark.InventoryStacks;
import com.greggifiedemi.chain.ChainItem;
import com.greggifiedemi.chain.ChainStack;
import com.greggifiedemi.chain.RecipeKey;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiHistory;

/**
 * NEI's crafting tree window (GuiCraftingTree, opened with T over a crafting-chain group): the chain drawn as a tree
 * with a pannable, zoomable canvas, slot colours showing what the inventory covers, a toolbar, a search field and the
 * stats strip. Clicking a crafted node opens its recipes; "Use for recipe tree" on one of them swaps it into the chain.
 */
public final class CraftingTreeScreen extends Screen {

    private static final int MARGIN = 4;
    private static final int PADDING = 9;
    private static final int PADDING_TOP = 22;
    private static final int BUTTON_SIZE = 16;
    private static final int BUTTON_SPACING = 20;

    enum CollapseMode {
        EXPANDED,
        SMART,
        ALL
    }

    /** What NEI keeps per group while the game runs (CraftingTreeState). */
    static final class State {

        final Camera camera = new Camera();
        final Set<ChainItem> collapsedItems = new HashSet<>();
        final Set<StatsPanel.Section> sections = EnumSet.allOf(StatsPanel.Section.class);
        boolean initialized;
        boolean statsVisible = true;
        boolean useInventorySnapshot;
        CollapseMode collapseMode = CollapseMode.EXPANDED;
        @Nullable TreeSlot replaceNode;
        @Nullable SearchResult search;
    }

    private record Button(int id, int x, int y) {}

    private static final Map<BookmarkGroup, State> STATES = new WeakHashMap<>();

    /** The tree waiting for a recipe picked in EMI's recipe view ("Use for recipe tree"). */
    private static @Nullable CraftingTreeScreen pending;

    private final Screen previous;
    private final BookmarkGrid grid;
    private final int groupId;
    private final State state;
    private final TreeCanvas canvas;
    private final StatsPanel stats = new StatsPanel();
    private final List<Button> buttons = new ArrayList<>();
    private CraftingTreeMath math;
    private EditBox searchField;
    private boolean statsShown;
    private @Nullable TreeSlot hoveredNode;
    private @Nullable TreeSlot hoveredBadge;

    private CraftingTreeScreen(Screen previous, BookmarkGrid grid, int groupId) {
        super(Component.translatable("greggifiedemi.tree.title"));
        this.previous = previous;
        this.grid = grid;
        this.groupId = groupId;
        this.state = STATES.computeIfAbsent(grid.getGroup(groupId), g -> new State());
        this.canvas = new TreeCanvas(this.state.camera);
    }

    /** NEI's openCraftingTreeGui. */
    public static boolean open(BookmarkGrid grid, int groupId) {
        if (grid.getGroup(groupId) == null || !grid.isCraftingMode(groupId)) {
            return false;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new CraftingTreeScreen(minecraft.screen, grid, groupId));
        return true;
    }

    /** Whether a tree is waiting for a recipe from EMI's recipe view. */
    public static boolean isPicking() {
        return pending != null && pending.state.replaceNode != null;
    }

    /** NEI's addRecipe: the recipe replaces the picked node's recipe, and the tree opens again. */
    public static void usePickedRecipe(EmiRecipe recipe) {
        final CraftingTreeScreen tree = pending;
        pending = null;

        if (tree == null) {
            return;
        }

        tree.addRecipe(recipe);
        Minecraft.getInstance().setScreen(tree);
    }

    private void addRecipe(EmiRecipe recipe) {
        final TreeSlot replace = this.state.replaceNode;
        this.state.replaceNode = null;

        if (recipe.getId() == null || this.grid.getGroup(this.groupId) == null) {
            return;
        }

        final RecipeKey key = RecipeKey.of(recipe);

        if (replace != null && replace.prefItem != null) {
            final Set<RecipeKey> unused = this.canvas.graph.collectUnusedRecipes(replace);
            unused.remove(key);

            for (RecipeKey unusedKey : unused) {
                this.grid.removeRecipe(unusedKey, this.groupId);
            }
        }

        if (!this.grid.existsRecipe(key, this.groupId)) {
            this.grid.addRecipe(com.greggifiedemi.bookmark.RecipeBookmarks.build(recipe, this.groupId, 1, null),
                    this.groupId);
        }

        this.grid.onItemsChanged();
    }

    @Override
    protected void init() {
        if (this.grid.getGroup(this.groupId) == null) {
            this.minecraft.setScreen(this.previous);
            return;
        }

        final int innerW = this.width - MARGIN * 2 - PADDING * 2;
        this.searchField = new EditBox(this.font, this.width - MARGIN - PADDING - Math.min(140, innerW / 3),
                MARGIN + 5, Math.min(140, innerW / 3), 12, Component.empty());
        this.searchField.setMaxLength(256);
        this.searchField.setResponder(text -> onSearchChanged());
        addWidget(this.searchField);

        this.buttons.clear();

        for (int id = 0; id < 4; id++) {
            this.buttons.add(new Button(id, MARGIN + PADDING + id * BUTTON_SPACING, MARGIN + 4));
        }

        rebuildTree(null);
    }

    private int canvasHeight() {
        return this.height - MARGIN * 2 - PADDING_TOP - PADDING;
    }

    private void rebuildTree(@Nullable TreeSlot anchor) {
        final BookmarkGroup group = this.grid.getGroup(this.groupId);

        if (group == null) {
            return;
        }

        final List<ChainItem> chainItems = group.crafting != null
                ? new ArrayList<>(this.grid.createChainItems(this.groupId).values())
                : List.of();

        this.math = new CraftingTreeMath(chainItems, group.collapsedRecipes);
        this.math.initialItems.clear();

        if (this.state.useInventorySnapshot) {
            final Screen inventoryScreen = this.previous instanceof AbstractContainerScreen<?> ? this.previous : null;
            final Map<String, InventoryStacks.Entry> inventory = InventoryStacks
                    .forChain(InventoryStacks.of(inventoryScreen), chainItems);

            for (InventoryStacks.Entry entry : inventory.values()) {
                this.math.initialItems.add(ChainItem.ofItem(-1, entry.stack, entry.amount));
            }
        }

        this.canvas.graph.rebuild(this.math);

        if (!this.state.initialized) {
            updateCollapsedItems();
        } else {
            this.canvas.recalculateCoordinates(group.collapsedRecipes, this.state.collapsedItems, anchor);
        }

        rebuildStats();

        if (!this.state.initialized) {
            this.state.initialized = true;
            this.canvas.fitToView();
        }
    }

    private void rebuildStats() {
        final int x = MARGIN + PADDING;
        final int y = MARGIN + PADDING_TOP;
        final int w = this.width - MARGIN * 2 - PADDING * 2;
        final int h = canvasHeight();

        this.stats.rebuild(this.math, this.canvas.graph, this.state.useInventorySnapshot, this.state.sections, x, y, w,
                h);
        this.statsShown = this.state.statsVisible && !this.stats.isEmpty();
        this.canvas.camera().setViewport(x, y, w, h - (this.statsShown ? this.stats.h : 0));
        onSearchChanged();
    }

    private void updateCollapsedItems() {
        final BookmarkGroup group = this.grid.getGroup(this.groupId);
        this.state.collapsedItems.clear();

        for (TreeSlot node : this.canvas.graph.childrenOf.keySet()) {
            if (node == null || this.canvas.graph.roots.contains(node)) {
                continue;
            }

            final boolean collapse = switch (this.state.collapseMode) {
                case EXPANDED -> false;
                case SMART -> !this.canvas.graph.childrenNeedCraftingCache.contains(node);
                case ALL -> true;
            };

            if (collapse) {
                this.state.collapsedItems.add(node.ingrItem);
            }
        }

        this.canvas.recalculateCoordinates(group.collapsedRecipes, this.state.collapsedItems, null);
    }

    private void onSearchChanged() {
        final String text = this.searchField == null ? "" : this.searchField.getValue().trim().toLowerCase();

        if (text.isEmpty()) {
            if (this.state.search != null && this.state.search.key instanceof String) {
                this.state.search = null;
            }
            return;
        }

        final SearchResult result = new SearchResult(text);

        for (TreeSlot node : this.canvas.graph.items) {
            if (!node.stack.isEmpty() && node.stack.getName().getString().toLowerCase().contains(text)) {
                result.matches.add(node);
            }
        }

        this.state.search = result;
        this.stats.setHighlight(null);
        goTo(result, true);
    }

    private void highlightStack(String key, boolean forward) {
        if (this.state.search == null || !key.equals(this.state.search.key)) {
            final SearchResult result = new SearchResult(key);

            for (TreeSlot node : this.canvas.graph.items) {
                final ChainItem shown = node.prefItem != null ? node.prefItem : node.ingrItem;

                if (shown.stack.key.equals(key) || node.ingrItem.stack.key.equals(key)
                        || key.startsWith("category:") && node.prefItem != null
                                && key.equals("category:" + categoryId(node))) {
                    result.matches.add(node);
                }
            }

            this.state.search = result;
            this.stats.setHighlight(key);
        }

        goTo(this.state.search, forward);
    }

    private static String categoryId(TreeSlot node) {
        final var category = com.greggifiedemi.bookmark.RecipeBookmarks.categoryOf(node.prefItem.recipe);
        return category == null ? "" : String.valueOf(category.getId());
    }

    /** NEI's searchGoTo: the next (or previous) match becomes the highlighted node and is scrolled into view. */
    private void goTo(@Nullable SearchResult result, boolean forward) {
        if (result == null || result.matches.isEmpty()) {
            return;
        }

        final List<TreeSlot> matches = new ArrayList<>(result.matches);
        int index = result.highlightNode == null ? -1 : matches.indexOf(result.highlightNode);
        index = forward ? (index + 1) % matches.size() : (index - 1 + matches.size()) % matches.size();
        result.highlightNode = matches.get(index);

        // expand collapsed ancestors so the match can be seen
        boolean changed = false;

        for (TreeSlot parent = this.canvas.graph.parentOf.get(result.highlightNode); parent != null;
                parent = this.canvas.graph.parentOf.get(parent)) {
            changed |= this.state.collapsedItems.remove(parent.ingrItem);
        }

        if (changed) {
            this.canvas.recalculateCoordinates(this.grid.getGroup(this.groupId).collapsedRecipes,
                    this.state.collapsedItems, null);
        }

        this.canvas.scrollTo(result.highlightNode);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);

        final boolean canHover = !this.state.camera.isDragging() && this.state.camera.isInWidget(mouseX, mouseY);
        this.hoveredBadge = canHover ? this.canvas.badgeAt(mouseX, mouseY) : null;
        this.hoveredNode = canHover && this.hoveredBadge == null ? this.canvas.nodeAt(mouseX, mouseY) : null;

        drawBackground(graphics);

        if (this.math.outputRecipes.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("greggifiedemi.tree.empty"), this.width / 2,
                    MARGIN + PADDING_TOP + canvasHeight() / 2, 0xFFAAAAAA);
        } else {
            this.canvas.draw(graphics, this.state.collapsedItems, this.state.search, this.state.useInventorySnapshot,
                    this.hoveredNode, this.hoveredBadge, mouseX, mouseY, delta);

            if (this.statsShown) {
                this.stats.draw(graphics, mouseX, mouseY, delta);
            }
        }

        graphics.drawCenteredString(this.font, this.title, this.width / 2,
                MARGIN + PADDING_TOP / 2 - this.font.lineHeight / 2 + 1, 0xFFFFFFFF);

        for (Button button : this.buttons) {
            drawButton(graphics, button, mouseX, mouseY);
        }

        this.searchField.render(graphics, mouseX, mouseY, delta);

        renderTooltips(graphics, mouseX, mouseY);
    }

    /** NEI's 64x64 nine-slice frame (9px sides, 22px top). */
    private void drawBackground(GuiGraphics graphics) {
        final int x = MARGIN;
        final int y = MARGIN;
        final int w = this.width - MARGIN * 2;
        final int h = this.height - MARGIN * 2;
        final int l = PADDING;
        final int r = PADDING;
        final int t = PADDING_TOP;
        final int b = PADDING;
        final int tex = 64;

        // corners
        graphics.blit(TreeCanvas.TEXTURE, x, y, 0, 0, l, t);
        graphics.blit(TreeCanvas.TEXTURE, x + w - r, y, tex - r, 0, r, t);
        graphics.blit(TreeCanvas.TEXTURE, x, y + h - b, 0, tex - b, l, b);
        graphics.blit(TreeCanvas.TEXTURE, x + w - r, y + h - b, tex - r, tex - b, r, b);
        // edges and centre, stretched
        blitStretched(graphics, x + l, y, w - l - r, t, l, 0, tex - l - r, t);
        blitStretched(graphics, x + l, y + h - b, w - l - r, b, l, tex - b, tex - l - r, b);
        blitStretched(graphics, x, y + t, l, h - t - b, 0, t, l, tex - t - b);
        blitStretched(graphics, x + w - r, y + t, r, h - t - b, tex - r, t, r, tex - t - b);
        blitStretched(graphics, x + l, y + t, w - l - r, h - t - b, l, t, tex - l - r, tex - t - b);
    }

    private static void blitStretched(GuiGraphics graphics, int x, int y, int w, int h, int u, int v, int uw, int vh) {
        if (w > 0 && h > 0) {
            graphics.blit(TreeCanvas.TEXTURE, x, y, w, h, u, v, uw, vh, 256, 256);
        }
    }

    private boolean over(Button button, double mouseX, double mouseY) {
        return mouseX >= button.x && mouseX < button.x + BUTTON_SIZE && mouseY >= button.y
                && mouseY < button.y + BUTTON_SIZE;
    }

    private void drawButton(GuiGraphics graphics, Button button, int mouseX, int mouseY) {
        final boolean on = switch (button.id) {
            case 0 -> this.state.useInventorySnapshot;
            case 3 -> this.state.statsVisible;
            default -> false;
        };
        final boolean hover = over(button, mouseX, mouseY);

        graphics.fill(button.x, button.y, button.x + BUTTON_SIZE, button.y + BUTTON_SIZE,
                on ? 0xFF3A5F3A : hover ? 0xFF5A5A5A : 0xFF3A3A3A);
        graphics.renderOutline(button.x, button.y, BUTTON_SIZE, BUTTON_SIZE, hover ? 0xFFFFFFFF : 0xFF101010);

        final int u = switch (button.id) {
            case 0 -> 56;
            case 1 -> 38;
            case 2 -> switch (this.state.collapseMode) {
                case EXPANDED -> 128;
                case SMART -> 110;
                case ALL -> 92;
            };
            default -> this.state.statsVisible ? 2 : 20;
        };

        graphics.pose().pushPose();
        graphics.pose().translate(button.x + 4, button.y + 4, 0);
        graphics.pose().scale(0.5f, 0.5f, 1);
        graphics.blit(TreeCanvas.TEXTURE, 0, 0, u, 66, 16, 16);
        graphics.pose().popPose();
    }

    private Component buttonTooltip(Button button) {
        return Component.translatable(switch (button.id) {
            case 0 -> this.state.useInventorySnapshot ? "greggifiedemi.tree.toolbar.inventory_on"
                    : "greggifiedemi.tree.toolbar.inventory_off";
            case 1 -> "greggifiedemi.tree.toolbar.fit_to_view";
            case 2 -> switch (this.state.collapseMode) {
                case EXPANDED -> "greggifiedemi.tree.toolbar.collapse_resolved";
                case SMART -> "greggifiedemi.tree.toolbar.collapse_all";
                case ALL -> "greggifiedemi.tree.toolbar.expand_all";
            };
            default -> this.state.statsVisible ? "greggifiedemi.tree.toolbar.stats_on"
                    : "greggifiedemi.tree.toolbar.stats_off";
        });
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Button button : this.buttons) {
            if (over(button, mouseX, mouseY)) {
                graphics.renderTooltip(this.font, buttonTooltip(button), mouseX, mouseY);
                return;
            }
        }

        final List<ClientTooltipComponent> lines = new ArrayList<>();
        final Map<String, String> hotkeys = new LinkedHashMap<>();

        if (this.hoveredBadge != null && this.hoveredBadge.prefItem != null) {
            final var category = com.greggifiedemi.bookmark.RecipeBookmarks.categoryOf(this.hoveredBadge.prefItem.recipe);
            lines.add(text(category != null ? category.getName() : Component.literal("?")));
            lines.add(text(Component.literal("x" + this.hoveredBadge.multiplier).withStyle(s -> s.withColor(0xAAAAAA))));
            lines.add(text(Component.translatable("greggifiedemi.remove_recipe").withStyle(s -> s.withColor(0xFF5555))));
        } else if (this.hoveredNode != null && !this.hoveredNode.stack.isEmpty()) {
            final TreeSlot node = this.hoveredNode;
            lines.addAll(node.stackWithAmount().getTooltip());
            addStateLines(lines, node);

            if (node.prefItem != null) {
                hotkeys.put(Hotkeys.combo(Hotkeys.alt(), Hotkeys.word("lmb")), "greggifiedemi.tree.item.toggle_recipe");
            }

            if (this.canvas.graph.roots.contains(node)) {
                hotkeys.put(Hotkeys.combo(Hotkeys.ctrl(), Hotkeys.word("scroll")), "greggifiedemi.change_quantity");
                hotkeys.put(Hotkeys.combo(Hotkeys.ctrl(), Hotkeys.alt(), Hotkeys.word("scroll")),
                        "greggifiedemi.change_quantity_step");
            }

            if (this.canvas.graph.childrenOf.containsKey(node)) {
                hotkeys.put(Hotkeys.word("lmb"), "greggifiedemi.tree.overlay");
            }

            if (node.prefItem == null && node.ingrItem.permutations.size() > 1) {
                hotkeys.put(Hotkeys.combo(Hotkeys.shift(), Hotkeys.word("scroll")), "greggifiedemi.accepts.scroll");
            }
        } else if (this.statsShown) {
            final StatsPanel.Entry entry = this.stats.entryAt(mouseX, mouseY);

            if (entry != null) {
                if (!entry.stack.isEmpty()) {
                    lines.addAll(entry.stack.copy().setAmount(Math.max(1, entry.amount)).getTooltip());
                }

                for (Component extra : entry.extraTooltip) {
                    lines.add(text(extra));
                }

                if (this.state.search != null && entry.key.equals(this.state.search.key)) {
                    hotkeys.put(Hotkeys.word("lmb"), "greggifiedemi.tree.stats.next_highlight");
                    hotkeys.put(Hotkeys.combo(Hotkeys.shift(), Hotkeys.word("lmb")),
                            "greggifiedemi.tree.stats.prev_highlight");
                    hotkeys.put(Hotkeys.word("rmb"), "greggifiedemi.tree.stats.clear_highlight");
                } else {
                    hotkeys.put(Hotkeys.word("lmb"), "greggifiedemi.tree.stats.highlight_handler");
                }
            }
        }

        if (lines.isEmpty()) {
            return;
        }

        if (Config.showHotkeys() && !hotkeys.isEmpty()) {
            lines.addAll(Math.min(1, lines.size()), Hotkeys.format(hotkeys));
        }

        EmiRenderHelper.drawTooltip(this, EmiDrawContext.wrap(graphics), lines, mouseX, mouseY);
    }

    private static ClientTooltipComponent text(Component component) {
        final FormattedCharSequence sequence = component.getVisualOrderText();
        return ClientTooltipComponent.create(sequence);
    }

    private void addStateLines(List<ClientTooltipComponent> lines, TreeSlot node) {
        final List<Component> state = new ArrayList<>();
        final String fluid = node.isFluid ? "L" : "";

        if (this.state.useInventorySnapshot && node.requestedAmount > 0) {
            if (node.leftAmount == 0) {
                state.add(Component.translatable("greggifiedemi.tree.item.state.resolved", node.stack.getName()));
            } else if (node.leftAmount != node.requestedAmount) {
                state.add(Component.translatable("greggifiedemi.tree.item.state.partial",
                        AmountText.compact(node.leftAmount) + fluid));
            }

            if (node.leftAmount > 0 && this.canvas.graph.childrenOf.containsKey(node)
                    && !this.canvas.graph.childrenNeedCraftingCache.contains(node)) {
                state.add(Component.translatable("greggifiedemi.tree.item.state.craftable"));
            }
        }

        if (node.prefItem != null && this.canvas.graph.childrenOf.containsKey(node)) {
            final long surplus = node.prefItem.getAmount(node.multiplier) - node.requestedAmount;

            if (surplus > 0) {
                state.add(Component.translatable("greggifiedemi.tree.item.state.surplus",
                        AmountText.compact(surplus) + fluid));
            }
        }

        int insert = Math.min(1, lines.size());

        for (Component line : state) {
            lines.add(insert++, text(line.copy().withStyle(s -> s.withColor(0xAAAAAA))));
        }
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.searchField.isMouseOver(mouseX, mouseY)) {
            if (button == 1) {
                this.searchField.setValue("");
            }

            this.searchField.setFocused(true);
            setFocused(this.searchField);
            return this.searchField.mouseClicked(mouseX, mouseY, 0) || true;
        }

        this.searchField.setFocused(false);

        for (Button toolbarButton : this.buttons) {
            if (button == 0 && over(toolbarButton, mouseX, mouseY)) {
                click();
                pressButton(toolbarButton.id);
                return true;
            }
        }

        if (this.statsShown && this.stats.contains(mouseX, mouseY)) {
            final StatsPanel.Entry entry = this.stats.entryAt(mouseX, mouseY);

            if (entry != null && button == 1 && this.state.search != null && entry.key.equals(this.state.search.key)) {
                click();
                this.state.search = null;
                this.stats.setHighlight(null);
            } else if (entry != null && button == 0) {
                click();
                highlightStack(entry.key, !hasShiftDown());
            }

            return true;
        }

        if (!this.state.camera.isInWidget(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        final TreeSlot badge = this.canvas.badgeAt(mouseX, mouseY);
        final TreeSlot node = badge != null ? null : this.canvas.nodeAt(mouseX, mouseY);

        if (button == 0 && badge != null && badge.prefItem != null) {
            click();
            removeRecipe(badge);
            return true;
        }

        if (node != null) {
            if (button == 0 && hasAltDown() && node.prefItem != null) {
                click();
                this.grid.toggleCollapsedRecipe(this.groupId, node.prefItem.recipe);
                rebuildTree(node);
                return true;
            }

            if (button == 0 && this.canvas.graph.childrenOf.containsKey(node)) {
                this.state.replaceNode = node;
            }

            if (button == 0 || button == 1) {
                openRecipes(node.stackWithAmount(), button == 1);
                return true;
            }
        }

        final TreeSlot arrow = this.canvas.collapseArrowAt(mouseX, mouseY);

        if (button == 0 && arrow != null) {
            click();

            if (!this.state.collapsedItems.remove(arrow.ingrItem)) {
                this.state.collapsedItems.add(arrow.ingrItem);
            }

            this.canvas.recalculateCoordinates(this.grid.getGroup(this.groupId).collapsedRecipes,
                    this.state.collapsedItems, arrow);
            return true;
        }

        if (button == 0) {
            this.state.camera.startDrag(mouseX, mouseY);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void removeRecipe(TreeSlot badge) {
        final RecipeKey recipe = badge.prefItem.recipe;
        final int itemIndex = this.grid.indexOf(this.groupId, badge.prefItem.stack, recipe, false);

        if (itemIndex != -1) {
            this.grid.removeRecipe(itemIndex, false);
        }

        if (!this.grid.existsRecipe(recipe, this.groupId)) {
            for (RecipeKey unused : this.canvas.graph.collectUnusedRecipes(badge)) {
                this.grid.removeRecipe(unused, this.groupId);
            }
        }

        rebuildTree(null);
    }

    private void pressButton(int id) {
        switch (id) {
            case 0 -> {
                this.state.useInventorySnapshot = !this.state.useInventorySnapshot;
                rebuildTree(null);
            }
            case 1 -> this.canvas.fitToView();
            case 2 -> {
                final CollapseMode[] modes = CollapseMode.values();
                this.state.collapseMode = modes[(this.state.collapseMode.ordinal() + 1) % modes.length];
                updateCollapsedItems();
                this.canvas.fitToView();
            }
            default -> {
                this.state.statsVisible = !this.state.statsVisible;
                rebuildStats();
            }
        }
    }

    /** EMI's recipe view only opens over a container: go back to it, and make EMI's back key return here. */
    private void openRecipes(EmiStack stack, boolean uses) {
        pending = this.state.replaceNode != null ? this : null;

        if (this.previous instanceof AbstractContainerScreen<?>) {
            this.minecraft.setScreen(this.previous);
        }

        if (uses) {
            EmiApi.displayUses(stack);
        } else {
            EmiApi.displayRecipes(stack);
        }

        if (this.minecraft.screen != this && this.minecraft.screen != this.previous) {
            EmiHistory.clear();
            EmiHistory.push(this);
        } else {
            this.minecraft.setScreen(this);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.state.camera.isDragging()) {
            this.state.camera.drag(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.state.camera.endDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        final int direction = amount > 0 ? 1 : -1;

        if (!this.state.camera.isInWidget(mouseX, mouseY)) {
            return true;
        }

        final TreeSlot node = this.canvas.nodeAt(mouseX, mouseY);

        // NEI: Ctrl + scroll on a root changes the chain's quantity (Alt: by a stack)
        if (hasControlDown() && node != null && this.canvas.graph.roots.contains(node)
                && node.ingrItem.recipe != null) {
            final long step = hasAltDown() ? (node.isFluid ? 144 : 64) : 1;
            this.grid.shiftRecipeAmount(this.groupId, node.ingrItem.recipe, step * direction);
            rebuildTree(null);
            return true;
        }

        // NEI: Shift + scroll on an ingredient changes which accepted item it uses
        if (hasShiftDown() && node != null && node.prefItem == null && node.ingrItem.recipe != null
                && node.ingrItem.permutations.size() > 1) {
            cyclePermutation(node, direction);
            return true;
        }

        this.state.camera.scroll(mouseX, mouseY, direction, hasShiftDown(), hasControlDown());
        return true;
    }

    private void cyclePermutation(TreeSlot node, int direction) {
        final ChainItem item = node.ingrItem;
        final List<ChainStack> perms = new ArrayList<>(item.permutations.values());
        int index = 0;

        for (int i = 0; i < perms.size(); i++) {
            if (perms.get(i).sameType(item.stack)) {
                index = i;
                break;
            }
        }

        final ChainStack next = perms.get((index - direction + perms.size()) % perms.size());
        final int itemIndex = this.grid.indexOf(this.groupId, item.stack, item.recipe, true);

        if (itemIndex != -1) {
            this.grid.replaceBookmarkItem(itemIndex, this.grid.getBookmarkItem(itemIndex).copyWithPerm(next));
            rebuildTree(null);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        if (this.searchField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                goTo(this.state.search, !hasShiftDown());
                return true;
            }

            return this.searchField.keyPressed(keyCode, scanCode, modifiers);
        }

        if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }

        // EMI's recipe/usage keys on the hovered node, like NEI's R/U on any item
        if (this.hoveredNode != null && !this.hoveredNode.stack.isEmpty()) {
            if (EmiConfig.viewRecipes.matchesKey(keyCode, scanCode)) {
                openRecipes(this.hoveredNode.stackWithAmount(), false);
                return true;
            }

            if (EmiConfig.viewUses.matchesKey(keyCode, scanCode)) {
                openRecipes(this.hoveredNode.stackWithAmount(), true);
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.searchField.isFocused() ? this.searchField.charTyped(codePoint, modifiers)
                : super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        pending = null;
        this.state.replaceNode = null;
        this.minecraft.setScreen(this.previous);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The bookmark grid this tree shows (for tests). */
    public BookmarkGrid grid() {
        return this.grid;
    }

    /** Number of nodes in the tree (for tests). */
    public int nodeCount() {
        return this.canvas.graph.items.size();
    }

    /** Number of roots, one per chain result (for tests). */
    public int rootCount() {
        return this.canvas.graph.roots.size();
    }

}
