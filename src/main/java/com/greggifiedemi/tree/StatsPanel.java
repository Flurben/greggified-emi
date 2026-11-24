package com.greggifiedemi.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.greggifiedemi.bookmark.AmountText;
import com.greggifiedemi.bookmark.RecipeBookmarks;
import com.greggifiedemi.chain.ChainItem;
import com.greggifiedemi.chain.ChainStack;
import com.greggifiedemi.chain.RecipeChainMath;
import com.greggifiedemi.chain.RecipeKey;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

/**
 * The summary strip below the tree (port of NEI's StatsPanel): ingredients needed/available, crafting steps
 * needed/available, results and remainders, and the recipe categories used. Sections sit side by side and wrap; each
 * shows one or two rows of slots.
 */
public final class StatsPanel {

    public enum Section {
        INGREDIENTS_NEEDED,
        INGREDIENTS_AVAILABLE,
        CRAFTING_NEEDED,
        CRAFTING_AVAILABLE,
        RESULTS,
        REMAINDERS,
        HANDLERS
    }

    /** One slot: a stack (or a recipe category) with an amount and a background. */
    public static final class Entry {

        public final EmiStack stack;
        public final @Nullable EmiRecipeCategory category;
        public final long amount;
        public final int background;
        public final @Nullable RecipeKey recipe;
        public final String key;
        public final List<Component> extraTooltip = new ArrayList<>();
        int x;
        int y;

        Entry(EmiStack stack, @Nullable EmiRecipeCategory category, long amount, int background,
                @Nullable RecipeKey recipe, String key) {
            this.stack = stack;
            this.category = category;
            this.amount = amount;
            this.background = background;
            this.recipe = recipe;
            this.key = key;
        }
    }

    private record Block(String label, List<Entry> entries, int x, int y, int columns) {}

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_INLINE_GAP = 2;
    private static final int COLUMN_GAP = 10;
    private static final int LABEL_HEIGHT = 11;

    private final List<Block> blocks = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    public int x;
    public int y;
    public int w;
    public int h;
    private @Nullable String highlightKey;

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public List<Entry> entries() {
        return this.entries;
    }

    public void setHighlight(@Nullable String key) {
        this.highlightKey = key;
    }

    public boolean contains(double mouseX, double mouseY) {
        return !isEmpty() && mouseX >= this.x && mouseX < this.x + this.w && mouseY >= this.y
                && mouseY < this.y + this.h;
    }

    public void rebuild(RecipeChainMath math, TreeGraph graph, boolean useInventorySnapshot, Set<Section> sections,
            int areaX, int areaY, int areaW, int areaH) {
        this.blocks.clear();
        this.entries.clear();

        final List<String> labels = new ArrayList<>();
        final List<List<Entry>> groups = new ArrayList<>();

        final List<Entry> ingredients = new ArrayList<>();
        if (sections.contains(Section.INGREDIENTS_NEEDED)) {
            ingredients.addAll(collectInputs(math, useInventorySnapshot));
        }
        if (sections.contains(Section.INGREDIENTS_AVAILABLE)) {
            ingredients.addAll(collectAvailable(math));
        }
        add(labels, groups, "greggifiedemi.tree.stats.ingredients", ingredients);

        if (sections.contains(Section.CRAFTING_NEEDED) || sections.contains(Section.CRAFTING_AVAILABLE)) {
            add(labels, groups, "greggifiedemi.tree.stats.crafting",
                    collectCrafting(graph, sections.contains(Section.CRAFTING_NEEDED),
                            sections.contains(Section.CRAFTING_AVAILABLE)));
        }

        final List<Entry> results = new ArrayList<>();
        if (sections.contains(Section.RESULTS)) {
            results.addAll(collectResults(math, true));
        }
        if (sections.contains(Section.REMAINDERS)) {
            results.addAll(collectResults(math, false));
        }
        add(labels, groups, "greggifiedemi.tree.stats.results", results);

        if (sections.contains(Section.HANDLERS)) {
            add(labels, groups, "greggifiedemi.tree.stats.handlers", collectHandlers(math));
        }

        layout(labels, groups, areaX, areaY, areaW, areaH);
    }

    private static void add(List<String> labels, List<List<Entry>> groups, String label, List<Entry> entries) {
        if (!entries.isEmpty()) {
            labels.add(Component.translatable(label).getString() + ":");
            groups.add(entries);
        }
    }

    /** NEI packs every section into one or two rows and widens them while there is room; sections wrap. */
    private void layout(List<String> labels, List<List<Entry>> groups, int areaX, int areaY, int areaW, int areaH) {
        final Font font = Minecraft.getInstance().font;
        final int maxRows = areaH / (LABEL_HEIGHT + SLOT_SIZE * 2 + 2) > 3 ? 2 : 1;
        final int[] columns = new int[groups.size()];
        int lineWidth = 0;
        int lineHeight = 0;
        int lineY = 0;
        final List<int[]> placements = new ArrayList<>();

        for (int i = 0; i < groups.size(); i++) {
            final int size = groups.get(i).size();
            columns[i] = Math.max(1, (size + maxRows - 1) / maxRows);
            columns[i] = Math.min(columns[i], Math.max(1, (areaW + SLOT_INLINE_GAP) / (SLOT_SIZE + SLOT_INLINE_GAP)));

            final int rows = (size + columns[i] - 1) / columns[i];
            final int width = Math.max(font.width(labels.get(i)),
                    columns[i] * (SLOT_SIZE + SLOT_INLINE_GAP) - SLOT_INLINE_GAP);
            final int height = LABEL_HEIGHT + rows * (SLOT_SIZE + SLOT_INLINE_GAP);

            if (lineWidth > 0 && lineWidth + COLUMN_GAP + width > areaW) {
                lineY += lineHeight + 4;
                lineWidth = 0;
                lineHeight = 0;
            }

            final int x = lineWidth == 0 ? 0 : lineWidth + COLUMN_GAP;
            placements.add(new int[] { x, lineY });
            lineWidth = x + width;
            lineHeight = Math.max(lineHeight, height);
        }

        final int totalHeight = groups.isEmpty() ? 0 : lineY + lineHeight + 4;

        this.x = areaX;
        this.w = areaW;
        this.h = Math.min(totalHeight, areaH / 2);
        this.y = areaY + areaH - this.h;

        for (int i = 0; i < groups.size(); i++) {
            final int bx = this.x + placements.get(i)[0];
            final int by = this.y + 4 + placements.get(i)[1];
            final List<Entry> group = groups.get(i);

            for (int index = 0; index < group.size(); index++) {
                final Entry entry = group.get(index);
                entry.x = bx + index % columns[i] * (SLOT_SIZE + SLOT_INLINE_GAP);
                entry.y = by + LABEL_HEIGHT + index / columns[i] * (SLOT_SIZE + SLOT_INLINE_GAP);

                if (entry.y + SLOT_SIZE <= this.y + this.h) {
                    this.entries.add(entry);
                }
            }

            this.blocks.add(new Block(labels.get(i), group, bx, by, columns[i]));
        }
    }

    public void draw(GuiGraphics graphics, double mouseX, double mouseY, float delta) {
        if (isEmpty()) {
            return;
        }

        final Font font = Minecraft.getInstance().font;

        // dashed separator like NEI's panels
        for (int dx = this.x; dx < this.x + this.w - 3; dx += 6) {
            graphics.fill(dx, this.y, dx + 3, this.y + 1, 0xFF555555);
        }

        graphics.enableScissor(this.x, this.y, this.x + this.w, this.y + this.h);

        for (Block block : this.blocks) {
            graphics.drawString(font, block.label(), block.x(), block.y(), 0xFFAAAAAA, false);
        }

        for (Entry entry : this.entries) {
            final int u = this.highlightKey != null && this.highlightKey.equals(entry.key) ? TreeCanvas.SLOT_HIGHLIGHTED
                    : entry.background;
            graphics.blit(TreeCanvas.TEXTURE, entry.x, entry.y, u, 2, SLOT_SIZE, SLOT_SIZE);

            if (entry.category != null) {
                entry.category.render(graphics, entry.x + 1, entry.y + 1, delta);
            } else {
                entry.stack.render(graphics, entry.x + 1, entry.y + 1, delta, EmiIngredient.RENDER_ICON);
            }

            if (entry.amount > 0) {
                AmountText.draw(graphics, AmountText.compact(entry.amount) + (isFluid(entry) ? "L" : ""), entry.x + 1,
                        entry.y + 1, 0xFFFFFF, isFluid(entry) ? AmountText.Alignment.BOTTOM_LEFT
                                : AmountText.Alignment.BOTTOM_RIGHT);
            }

            if (mouseX >= entry.x && mouseX < entry.x + SLOT_SIZE && mouseY >= entry.y
                    && mouseY < entry.y + SLOT_SIZE) {
                graphics.fill(entry.x + 1, entry.y + 1, entry.x + SLOT_SIZE - 1, entry.y + SLOT_SIZE - 1, 300,
                        0x66888888);
            }
        }

        graphics.disableScissor();
    }

    private static boolean isFluid(Entry entry) {
        return entry.category == null && !entry.stack.isEmpty() && entry.stack.getItemStack().isEmpty();
    }

    public @Nullable Entry entryAt(double mouseX, double mouseY) {
        for (Entry entry : this.entries) {
            if (mouseX >= entry.x && mouseX < entry.x + SLOT_SIZE && mouseY >= entry.y
                    && mouseY < entry.y + SLOT_SIZE) {
                return entry;
            }
        }
        return null;
    }

    private static Entry stackEntry(ChainStack stack, long amount, int background, @Nullable RecipeKey recipe) {
        final EmiStack emi = stack.emi != null ? stack.emi.copy().setAmount(1) : EmiStack.EMPTY;
        return new Entry(emi, null, amount, background, recipe, stack.key);
    }

    private static void merge(Map<String, Object[]> items, ChainStack stack, long amount) {
        final Object[] entry = items.computeIfAbsent(stack.key, k -> new Object[] { stack, 0L });
        entry[1] = (Long) entry[1] + amount;
    }

    private static List<Entry> collectInputs(RecipeChainMath math, boolean useInventorySnapshot) {
        final Map<String, Object[]> items = new LinkedHashMap<>();

        for (ChainItem item : math.recipeIngredients) {
            final long amount = math.requiredAmount.containsKey(math.preferredItems.get(item)) ? 0
                    : math.requiredAmount.getOrDefault(item, item.getAmount());

            if (amount > 0) {
                merge(items, item.stack, amount);
            }
        }

        return toEntries(items, useInventorySnapshot ? TreeCanvas.SLOT_MISSING : TreeCanvas.SLOT_DEFAULT);
    }

    private static List<Entry> collectAvailable(RecipeChainMath math) {
        final Map<String, Object[]> items = new LinkedHashMap<>();

        for (ChainItem item : math.initialItems) {
            final long amount = math.requiredAmount.getOrDefault(item, 0L);

            if (amount > 0) {
                merge(items, item.stack, amount);
            }
        }

        return toEntries(items, TreeCanvas.SLOT_DEFAULT);
    }

    private static List<Entry> collectResults(RecipeChainMath math, boolean outputs) {
        final Map<String, Object[]> items = new LinkedHashMap<>();

        for (ChainItem item : math.recipeResults) {
            final long amount = item.getAmount() - math.requiredAmount.getOrDefault(item, 0L);

            if (amount > 0 && math.outputRecipes.containsKey(item.recipe) == outputs) {
                merge(items, item.stack, amount);
            }
        }

        return toEntries(items, outputs ? TreeCanvas.SLOT_DEFAULT : TreeCanvas.SLOT_DISABLED);
    }

    private static List<Entry> toEntries(Map<String, Object[]> items, int background) {
        final List<Entry> entries = new ArrayList<>();

        for (Object[] entry : items.values()) {
            if ((Long) entry[1] > 0) {
                entries.add(stackEntry((ChainStack) entry[0], (Long) entry[1], background, null));
            }
        }

        return entries;
    }

    private static List<Entry> collectCrafting(TreeGraph graph, boolean showNeeded, boolean showAvailable) {
        final Map<ChainItem, Boolean> recipeMissing = new LinkedHashMap<>();
        final Map<ChainItem, Integer> recipeLevel = new HashMap<>();

        for (Map.Entry<TreeSlot, List<TreeSlot>> entry : graph.childrenOf.entrySet()) {
            final TreeSlot node = entry.getKey();

            if (node == null || node.prefItem == null) {
                continue;
            }

            recipeMissing.merge(node.prefItem, graph.childrenNeedCraftingCache.contains(node), (a, b) -> a || b);
            recipeLevel.merge(node.prefItem, level(node, graph.parentOf), Math::max);
        }

        final List<Map.Entry<ChainItem, Boolean>> sorted = new ArrayList<>(recipeMissing.entrySet());
        sorted.sort(Comparator.comparingInt(entry -> recipeLevel.get(entry.getKey())));

        final List<Entry> needed = new ArrayList<>();
        final List<Entry> available = new ArrayList<>();

        for (Map.Entry<ChainItem, Boolean> entry : sorted) {
            final ChainItem result = entry.getKey();
            final long amount = result.getAmount();

            if (amount <= 0 || (entry.getValue() ? !showNeeded : !showAvailable)) {
                continue;
            }

            final Entry slot = stackEntry(result.stack, amount,
                    entry.getValue() ? TreeCanvas.SLOT_DEFAULT : TreeCanvas.SLOT_CRAFTING, result.recipe);
            slot.extraTooltip.add(Component.literal("x" + result.getMultiplier()).withStyle(s -> s.withColor(0xAAAAAA)));
            (entry.getValue() ? needed : available).add(slot);
        }

        needed.addAll(available);
        return needed;
    }

    private static List<Entry> collectHandlers(RecipeChainMath math) {
        final Map<EmiRecipeCategory, long[]> stats = new LinkedHashMap<>();
        final Set<RecipeKey> counted = new HashSet<>();

        for (ChainItem item : math.recipeResults) {
            if (item.recipe == null || !counted.add(item.recipe)) {
                continue;
            }

            final EmiRecipeCategory category = RecipeBookmarks.categoryOf(item.recipe);

            if (category != null) {
                final long[] stat = stats.computeIfAbsent(category, k -> new long[2]);
                stat[0] += item.getMultiplier();
                stat[1]++;
            }
        }

        final List<Entry> entries = new ArrayList<>();

        for (Map.Entry<EmiRecipeCategory, long[]> entry : stats.entrySet()) {
            final Entry slot = new Entry(EmiStack.EMPTY, entry.getKey(), 0, TreeCanvas.SLOT_DEFAULT, null,
                    "category:" + entry.getKey().getId());
            slot.extraTooltip.add(entry.getKey().getName());
            slot.extraTooltip.add(Component.translatable("greggifiedemi.tree.stats.handler_recipes", entry.getValue()[1])
                    .withStyle(s -> s.withColor(0xAAAAAA)));
            slot.extraTooltip.add(
                    Component.translatable("greggifiedemi.tree.stats.handler_iterations", entry.getValue()[0])
                            .withStyle(s -> s.withColor(0xAAAAAA)));
            entries.add(slot);
        }

        return entries;
    }

    private static int level(TreeSlot node, Map<TreeSlot, TreeSlot> parentOf) {
        int level = 0;

        for (TreeSlot current = parentOf.get(node); current != null; current = parentOf.get(current)) {
            level++;
        }

        return level;
    }

    public static Set<Section> allSections() {
        return EnumSet.allOf(Section.class);
    }

}
