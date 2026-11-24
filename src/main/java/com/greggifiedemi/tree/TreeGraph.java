package com.greggifiedemi.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.greggifiedemi.chain.RecipeKey;

/** The tree's nodes and links, plus the derived states used to colour slots (port of NEI's CraftingTreeGraph). */
public final class TreeGraph {

    public final List<TreeSlot> roots = new ArrayList<>();
    public final List<TreeSlot> items = new ArrayList<>();
    public final Map<TreeSlot, TreeSlot> parentOf = new HashMap<>();
    public final Map<TreeSlot, List<TreeSlot>> childrenOf = new HashMap<>();
    public final Set<TreeSlot> subtreeMissingCache = new HashSet<>();
    public final Set<TreeSlot> childrenNeedCraftingCache = new HashSet<>();

    public void rebuild(CraftingTreeMath math) {
        this.subtreeMissingCache.clear();
        this.childrenNeedCraftingCache.clear();

        math.refresh();

        this.roots.clear();
        this.roots.addAll(math.rootSlots);
        this.items.clear();
        this.items.addAll(math.items);
        this.parentOf.clear();
        this.parentOf.putAll(math.parentOf);
        this.childrenOf.clear();
        this.childrenOf.putAll(math.childrenOf);

        for (TreeSlot root : this.roots) {
            computeSubtreeMissing(root);
        }

        for (Map.Entry<TreeSlot, List<TreeSlot>> entry : this.childrenOf.entrySet()) {
            if (entry.getValue().stream().anyMatch(child -> child.leftAmount != 0)) {
                this.childrenNeedCraftingCache.add(entry.getKey());
            }
        }
    }

    /** The recipes only this node's sub-tree uses, which go away with it. */
    public Set<RecipeKey> collectUnusedRecipes(TreeSlot node) {
        final Map<RecipeKey, Set<RecipeKey>> children = new HashMap<>();
        final Map<RecipeKey, Set<RecipeKey>> consumers = new HashMap<>();
        int rootCount = 0;

        for (TreeSlot item : this.items) {
            if (item.prefItem != null && item.ingrItem.recipe != null) {
                children.computeIfAbsent(item.ingrItem.recipe, k -> new HashSet<>()).add(item.prefItem.recipe);
                consumers.computeIfAbsent(item.prefItem.recipe, k -> new HashSet<>()).add(item.ingrItem.recipe);

                if (item.ingrItem.recipe.equals(node.prefItem.recipe)) {
                    rootCount++;
                }
            }
        }

        final Set<RecipeKey> unused = new HashSet<>();
        final RecipeKey root = node.prefItem.recipe;

        if (rootCount <= 1) {
            unused.add(root);
            collectSubtree(root, children, consumers, unused);
        }

        return unused;
    }

    private void collectSubtree(RecipeKey recipe, Map<RecipeKey, Set<RecipeKey>> children,
            Map<RecipeKey, Set<RecipeKey>> consumers, Set<RecipeKey> unused) {
        for (RecipeKey child : children.getOrDefault(recipe, Collections.emptySet())) {
            if (consumers.get(child).size() == 1 && unused.add(child)) {
                collectSubtree(child, children, consumers, unused);
            }
        }
    }

    private boolean computeSubtreeMissing(TreeSlot node) {
        boolean hasMissing = !this.childrenOf.containsKey(node);

        for (TreeSlot child : this.childrenOf.getOrDefault(node, Collections.emptyList())) {
            hasMissing |= child.leftAmount != 0 && computeSubtreeMissing(child);
        }

        if (hasMissing) {
            this.subtreeMissingCache.add(node);
        }

        return hasMissing;
    }

}
