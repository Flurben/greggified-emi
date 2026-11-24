package com.greggifiedemi.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiPredicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import com.greggifiedemi.GreggifiedEmi;
import com.greggifiedemi.bookmark.AmountText;
import com.greggifiedemi.bookmark.RecipeBookmarks;
import com.greggifiedemi.chain.ChainItem;
import com.greggifiedemi.chain.RecipeKey;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;

/**
 * Lays out and draws the tree (port of NEI's CraftingTreeCanvas): a tidy-tree layout with contours so sub-trees pack
 * tightly, roots side by side, elbow lines to the parent, slot backgrounds coloured by state, amounts, a recipe
 * category badge and the collapse arrows.
 */
public final class TreeCanvas {

    public static final ResourceLocation TEXTURE = new ResourceLocation(GreggifiedEmi.MOD_ID,
            "textures/gui/craftingtree.png");

    public static final int SLOT_SIZE = 18;
    public static final int X_SPACING = 24;
    public static final int Y_SPACING = 14;
    public static final int ROOT_SPACING = 36;
    private static final float FIT_MARGIN = 8f;
    private static final int LINE_COLOR = 0xFF888888;
    private static final int HOVER_COLOR = 0x66888888;
    private static final float BADGE_SCALE = 0.6f;
    private static final int ICON_SIZE = 5;

    /** Slot backgrounds in the texture (u at v = 2). */
    public static final int SLOT_DEFAULT = 68;
    public static final int SLOT_MISSING = 92;
    public static final int SLOT_CRAFTING = 116;
    public static final int SLOT_DISABLED = 140;
    public static final int SLOT_HIGHLIGHTED = 164;

    public final TreeGraph graph = new TreeGraph();
    private final Camera camera;
    private final Map<TreeSlot, int[]> pointByNode = new HashMap<>();
    private final TreeMap<Integer, List<TreeSlot>> rows = new TreeMap<>();
    private final Set<RecipeKey> collapsedRecipes = new HashSet<>();
    private int treeWidth;
    private int treeHeight;

    public TreeCanvas(Camera camera) {
        this.camera = camera;
    }

    public Camera camera() {
        return this.camera;
    }

    public void fitToView() {
        final float firstResultCenterX = this.graph.roots.isEmpty() || !this.pointByNode.containsKey(this.graph.roots.get(0))
                ? this.treeWidth / 2f
                : this.pointByNode.get(this.graph.roots.get(0))[0] + SLOT_SIZE / 2f;

        this.camera.zoomLevel = 1.0f;
        this.camera.scrollX = this.treeWidth <= this.camera.widgetW - FIT_MARGIN
                ? (this.treeWidth - this.camera.widgetW) / 2f
                : firstResultCenterX - this.camera.widgetW / 2f;
        this.camera.scrollY = this.treeHeight <= this.camera.widgetH - FIT_MARGIN
                ? (this.treeHeight - this.camera.widgetH) / 2f
                : -FIT_MARGIN;
    }

    public void scrollTo(@Nullable TreeSlot node) {
        final int[] point = node != null ? this.pointByNode.get(node) : null;

        if (point != null) {
            this.camera.centerOn(point[0] + SLOT_SIZE / 2f, point[1] + SLOT_SIZE / 2f);
        }
    }

    public @Nullable TreeSlot nodeAt(double mouseX, double mouseY) {
        return find(mouseX, mouseY, (node, local) -> {
            final int[] p = this.pointByNode.get(node);
            return local[0] >= p[0] && local[0] < p[0] + SLOT_SIZE && local[1] >= p[1] && local[1] < p[1] + SLOT_SIZE;
        });
    }

    /** The recipe badge in a node's top-left corner (hovering it offers to remove the recipe). */
    public @Nullable TreeSlot badgeAt(double mouseX, double mouseY) {
        return find(mouseX, mouseY, (node, local) -> {
            if (node.prefItem == null || !this.graph.childrenOf.containsKey(node)) {
                return false;
            }

            final float[] badge = badgeRect(this.pointByNode.get(node));
            return local[0] >= badge[0] && local[0] < badge[0] + badge[2] && local[1] >= badge[1]
                    && local[1] < badge[1] + badge[3];
        });
    }

    public @Nullable TreeSlot collapseArrowAt(double mouseX, double mouseY) {
        return find(mouseX, mouseY, (node, local) -> {
            if (!hasCollapseArrow(node)) {
                return false;
            }

            final int[] p = this.pointByNode.get(node);
            final float iconX = p[0] + SLOT_SIZE / 2f - ICON_SIZE - 1;
            final float iconY = p[1] + SLOT_SIZE + 1;
            return local[0] >= iconX && local[0] < iconX + ICON_SIZE && local[1] >= iconY
                    && local[1] < iconY + ICON_SIZE;
        });
    }

    private boolean hasCollapseArrow(TreeSlot node) {
        return this.graph.childrenOf.containsKey(node) && node.prefItem != null
                && !(this.collapsedRecipes.contains(node.prefItem.recipe) && !this.graph.roots.contains(node));
    }

    private @Nullable TreeSlot find(double mouseX, double mouseY, BiPredicate<TreeSlot, double[]> hit) {
        if (!this.camera.isInWidget(mouseX, mouseY)) {
            return null;
        }

        final double[] local = this.camera.toWorld(mouseX, mouseY);

        for (List<TreeSlot> row : this.rows.subMap(this.camera.visibleRowMin(), this.camera.visibleRowMax())
                .values()) {
            for (TreeSlot node : row) {
                if (hit.test(node, local)) {
                    return node;
                }
            }
        }

        return null;
    }

    public void draw(GuiGraphics graphics, Set<ChainItem> collapsedItems, @Nullable SearchResult search,
            boolean useInventorySnapshot, @Nullable TreeSlot hovered, @Nullable TreeSlot deleteHover,
            double mouseX, double mouseY, float delta) {
        if (this.camera.isDragging()) {
            this.camera.drag(mouseX, mouseY);
        }

        this.camera.clampToBounds(this.treeWidth, this.treeHeight);

        final float zoom = this.camera.zoomLevel;
        final float zScrollX = this.camera.scrollX * zoom;
        final float zScrollY = this.camera.scrollY * zoom;
        final int cropXMin = (int) ((zScrollX - 32) / zoom);
        final int cropXMax = (int) ((zScrollX + 32) / zoom) + (int) (this.camera.widgetW / zoom);
        final TreeSlot collapseHover = this.camera.isDragging() ? null : collapseArrowAt(mouseX, mouseY);

        graphics.enableScissor(this.camera.widgetX, this.camera.widgetY, this.camera.widgetX + this.camera.widgetW,
                this.camera.widgetY + this.camera.widgetH);
        graphics.pose().pushPose();
        graphics.pose().translate(this.camera.widgetX - zScrollX, this.camera.widgetY - zScrollY, 0);
        graphics.pose().scale(zoom, zoom, 1);

        for (List<TreeSlot> row : this.rows.subMap(this.camera.visibleRowMin(), this.camera.visibleRowMax())
                .values()) {
            for (TreeSlot node : row) {
                final int[] p = this.pointByNode.get(node);

                drawParentLine(graphics, node);

                if (p[0] < cropXMin || p[0] > cropXMax) {
                    continue;
                }

                drawSlot(graphics, node, useInventorySnapshot, search, collapsedItems, node == hovered, delta);
                drawCollapseIcon(graphics, node, collapsedItems, node == collapseHover);

                if (node == hovered) {
                    graphics.fill(p[0] + 1, p[1] + 1, p[0] + SLOT_SIZE - 1, p[1] + SLOT_SIZE - 1, 300, HOVER_COLOR);
                }

                if (node.prefItem != null && this.graph.childrenOf.containsKey(node)) {
                    drawBadge(graphics, node, p, node == deleteHover, delta);
                }
            }
        }

        graphics.pose().popPose();
        graphics.disableScissor();

        drawScrollbars(graphics);
    }

    private void drawSlot(GuiGraphics graphics, TreeSlot node, boolean useInventorySnapshot,
            @Nullable SearchResult search, Set<ChainItem> collapsedItems, boolean hovered, float delta) {
        final int[] p = this.pointByNode.get(node);
        final int u;

        if (search != null) {
            u = node == search.highlightNode ? SLOT_HIGHLIGHTED
                    : search.matches.contains(node) ? SLOT_DEFAULT : SLOT_DISABLED;
        } else if (useInventorySnapshot) {

            if (node.leftAmount == 0) {
                final TreeSlot parent = this.graph.parentOf.get(node);
                u = parent != null && parent.leftAmount == 0 || node.requestedAmount == 0 ? SLOT_DISABLED
                        : SLOT_DEFAULT;
            } else if (!this.graph.childrenOf.containsKey(node)) {
                u = SLOT_MISSING;
            } else if (collapsedItems.contains(node.ingrItem)) {
                u = this.graph.subtreeMissingCache.contains(node) ? SLOT_MISSING : SLOT_CRAFTING;
            } else if (!this.graph.childrenNeedCraftingCache.contains(node)) {
                u = SLOT_CRAFTING;
            } else {
                u = SLOT_DEFAULT;
            }

        } else {
            u = SLOT_DEFAULT;
        }

        graphics.blit(TEXTURE, p[0], p[1], u, 2, SLOT_SIZE, SLOT_SIZE);
        node.stack.render(graphics, p[0] + 1, p[1] + 1, delta, EmiIngredient.RENDER_ICON);

        if (node.requestedAmount > 0 && node.leftAmount == 0) {
            drawStackSize(graphics, node, node.requestedAmount, p, true);
        } else if (node.requestedAmount > 0) {
            final long amount = hovered && node.prefItem != null && this.graph.roots.contains(node)
                    && this.collapsedRecipes.contains(node.prefItem.recipe) && Screen.hasControlDown()
                            ? node.currentRequestedAmount
                            : node.requestedAmount;
            drawStackSize(graphics, node, amount, p, false);
        }
    }

    private static void drawStackSize(GuiGraphics graphics, TreeSlot node, long stackSize, int[] p,
            boolean resolved) {
        String text = AmountText.compact(stackSize);

        if (node.isFluid) {
            text += "L";
        }

        final boolean outputChance = node.prefItem != null && node.prefItem.chance != ChainItem.CHANCE_FULL;
        int color = 0xFFFFFF;

        if (node.ingrItem.chance != ChainItem.CHANCE_FULL || outputChance || node.isContainerItem) {
            text = "~" + text;
            color = 0xFFAA00;
        }

        if (resolved) {
            color = 0x55FF55;
        }

        AmountText.draw(graphics, text, p[0] + 1, p[1] + 1, color,
                node.isFluid ? AmountText.Alignment.BOTTOM_LEFT : AmountText.Alignment.BOTTOM_RIGHT);
    }

    private void drawBadge(GuiGraphics graphics, TreeSlot node, int[] p, boolean deleteHover, float delta) {
        final float[] badge = badgeRect(p);

        graphics.pose().pushPose();
        graphics.pose().translate(badge[0], badge[1], deleteHover ? 350 : 250);
        graphics.pose().scale(BADGE_SCALE, BADGE_SCALE, 1);

        if (deleteHover) {
            graphics.blit(TEXTURE, 0, 0, 85, 48, 16, 16);
        } else {
            final EmiRecipeCategory category = RecipeBookmarks.categoryOf(node.prefItem.recipe);

            if (category != null) {
                category.render(graphics, 0, 0, delta);
            } else {
                graphics.blit(TEXTURE, 0, 0, 67, 48, 16, 16);
            }
        }

        graphics.pose().popPose();
    }

    private void drawCollapseIcon(GuiGraphics graphics, TreeSlot node, Set<ChainItem> collapsedItems, boolean hover) {
        if (!hasCollapseArrow(node)) {
            return;
        }

        final int[] p = this.pointByNode.get(node);
        final int u = hover ? 11 : 4;
        final int v = collapsedItems.contains(node.ingrItem) ? 88 : 95;
        graphics.blit(TEXTURE, p[0] + SLOT_SIZE / 2 - ICON_SIZE - 1, p[1] + SLOT_SIZE + 1, u, v, ICON_SIZE, ICON_SIZE);
    }

    private static float[] badgeRect(int[] p) {
        final float size = 16 * BADGE_SCALE;
        return new float[] { p[0] - size / 2 + 1, p[1] - size / 2 + 1, size, size };
    }

    private void drawParentLine(GuiGraphics graphics, TreeSlot node) {
        final TreeSlot parent = this.graph.parentOf.get(node);

        if (parent == null || !this.pointByNode.containsKey(parent)) {
            return;
        }

        final int[] p = this.pointByNode.get(node);
        final int[] pp = this.pointByNode.get(parent);
        final int x0 = p[0] + SLOT_SIZE / 2;
        final int y0 = p[1] - 1;
        final int x1 = pp[0] + SLOT_SIZE / 2;
        final int y1 = pp[1] + SLOT_SIZE;

        if (x0 != x1) {
            final int midY = (y0 + y1) / 2;
            graphics.fill(x0, Math.min(y0, midY), x0 + 1, Math.max(y0, midY) + 1, LINE_COLOR);
            graphics.fill(Math.min(x0, x1), midY, Math.max(x0, x1) + 1, midY + 1, LINE_COLOR);
            graphics.fill(x1, Math.min(midY, y1), x1 + 1, Math.max(midY, y1) + 1, LINE_COLOR);
        } else {
            graphics.fill(x0, Math.min(y0, y1), x0 + 1, Math.max(y0, y1) + 1, LINE_COLOR);
        }
    }

    private void drawScrollbars(GuiGraphics graphics) {
        if (this.treeWidth <= 0 || this.treeHeight <= 0) {
            return;
        }

        final float scrollXPct = Camera.clamp(this.camera.scrollX / this.treeWidth, 0, 1);
        final float scrollYPct = Camera.clamp(this.camera.scrollY / this.treeHeight, 0, 1);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.blit(TEXTURE, this.camera.widgetX + this.camera.widgetW - 3,
                (int) (this.camera.widgetY + scrollYPct * (this.camera.widgetH - 24)), 0, 88, 2, 24);
        graphics.blit(TEXTURE, (int) (this.camera.widgetX + scrollXPct * (this.camera.widgetW - 24)),
                this.camera.widgetY + this.camera.widgetH - 3, 0, 84, 24, 2);
        graphics.pose().popPose();
    }

    /** Lays the tree out again, keeping {@code anchor} where it was on screen. */
    public void recalculateCoordinates(Set<RecipeKey> collapsedRecipes, Set<ChainItem> collapsedItems,
            @Nullable TreeSlot anchor) {
        final Map<TreeSlot, Integer> offsetFromParent = new HashMap<>();
        final int[] anchorOld = anchor != null ? resolveAnchor(anchor) : null;

        this.collapsedRecipes.clear();
        this.collapsedRecipes.addAll(collapsedRecipes);
        this.pointByNode.clear();
        this.rows.clear();
        this.treeWidth = 0;
        this.treeHeight = 0;

        for (TreeSlot node : this.graph.items) {
            this.pointByNode.put(node, new int[] { 0, 0 });
        }

        final List<int[]> contour = new ArrayList<>();

        for (TreeSlot root : this.graph.roots) {
            final List<int[]> layout = computeLayout(root, collapsedItems, offsetFromParent);
            final int shift = contour.isEmpty() ? 0 : requiredRootShift(contour, layout);

            mergeContour(contour, layout, shift);
            assignPositions(root, shift, 0, collapsedItems, offsetFromParent);
        }

        int minX = 0;

        for (int[] point : this.pointByNode.values()) {
            minX = Math.min(minX, point[0]);
        }

        if (minX < 0) {
            for (int[] point : this.pointByNode.values()) {
                point[0] -= minX;
            }
        }

        for (List<TreeSlot> row : this.rows.values()) {
            for (TreeSlot node : row) {
                final int[] p = this.pointByNode.get(node);
                this.treeWidth = Math.max(this.treeWidth, p[0] + SLOT_SIZE);
                this.treeHeight = Math.max(this.treeHeight, p[1] + SLOT_SIZE);
            }
        }

        if (anchorOld != null) {
            final int[] anchorNew = resolveAnchor(anchor);

            if (anchorNew != null) {
                this.camera.scrollX += anchorNew[0] - anchorOld[0];
                this.camera.scrollY += anchorNew[1] - anchorOld[1];
            }
        }
    }

    private int @Nullable [] resolveAnchor(TreeSlot anchor) {
        final int[] point = this.pointByNode.get(anchor);

        if (point != null) {
            return point.clone();
        }

        for (Map.Entry<TreeSlot, int[]> entry : this.pointByNode.entrySet()) {
            if (entry.getKey().ingrItem.equals(anchor.ingrItem)) {
                return entry.getValue().clone();
            }
        }

        return null;
    }

    private List<int[]> computeLayout(TreeSlot node, Set<ChainItem> collapsedItems,
            Map<TreeSlot, Integer> offsetFromParent) {
        final List<TreeSlot> children = collapsedItems.contains(node.ingrItem) ? Collections.emptyList()
                : this.graph.childrenOf.getOrDefault(node, Collections.emptyList());

        if (children.isEmpty()) {
            final List<int[]> rows = new ArrayList<>();
            rows.add(new int[] { 0, 0 });
            return rows;
        }

        final int[] offsets = new int[children.size()];
        final List<int[]> combined = new ArrayList<>();

        for (int i = 0; i < children.size(); i++) {
            final List<int[]> childRows = computeLayout(children.get(i), collapsedItems, offsetFromParent);
            final int shift = i == 0 ? 0 : requiredShift(combined, childRows, X_SPACING);

            offsets[i] = shift;
            mergeContour(combined, childRows, shift);
        }

        final int selfX = (offsets[0] + offsets[children.size() - 1]) / 2;

        for (int i = 0; i < children.size(); i++) {
            offsetFromParent.put(children.get(i), offsets[i] - selfX);
        }

        for (int[] row : combined) {
            row[0] -= selfX;
            row[1] -= selfX;
        }

        final List<int[]> rows = new ArrayList<>(combined.size() + 1);
        rows.add(new int[] { 0, 0 });
        rows.addAll(combined);
        return rows;
    }

    private static int requiredShift(List<int[]> existing, List<int[]> incoming, int spacing) {
        int shift = 0;
        final int rows = Math.min(existing.size(), incoming.size());

        for (int r = 0; r < rows; r++) {
            shift = Math.max(shift, existing.get(r)[1] + spacing - incoming.get(r)[0]);
        }

        return shift;
    }

    private static int requiredRootShift(List<int[]> existing, List<int[]> incoming) {
        int existingRight = Integer.MIN_VALUE;

        for (int[] row : existing) {
            existingRight = Math.max(existingRight, row[1]);
        }

        int incomingLeft = Integer.MAX_VALUE;

        for (int[] row : incoming) {
            incomingLeft = Math.min(incomingLeft, row[0]);
        }

        return Math.max(0, existingRight + ROOT_SPACING - incomingLeft);
    }

    private static void mergeContour(List<int[]> target, List<int[]> source, int shift) {
        for (int r = 0; r < source.size(); r++) {
            final int left = source.get(r)[0] + shift;
            final int right = source.get(r)[1] + shift;

            if (r < target.size()) {
                target.get(r)[0] = Math.min(target.get(r)[0], left);
                target.get(r)[1] = Math.max(target.get(r)[1], right);
            } else {
                target.add(new int[] { left, right });
            }
        }
    }

    private void assignPositions(TreeSlot node, int x, int y, Set<ChainItem> collapsedItems,
            Map<TreeSlot, Integer> offsetFromParent) {
        this.rows.computeIfAbsent(y, ignored -> new ArrayList<>()).add(node);
        this.pointByNode.put(node, new int[] { x, y });

        if (!collapsedItems.contains(node.ingrItem)) {
            final int childY = y + SLOT_SIZE + Y_SPACING;

            for (TreeSlot child : this.graph.childrenOf.getOrDefault(node, Collections.emptyList())) {
                assignPositions(child, x + offsetFromParent.get(child), childY, collapsedItems, offsetFromParent);
            }
        }
    }

}
