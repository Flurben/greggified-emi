package com.greggifiedemi.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.greggifiedemi.chain.ChainItem;
import com.greggifiedemi.chain.ItemContainerResolver;
import com.greggifiedemi.chain.RecipeChainMath;
import com.greggifiedemi.chain.RecipeKey;

/**
 * The chain math, recording every calculation step as a tree node (port of NEI's CraftingTreeMath). Results of the
 * chain become roots; every ingredient becomes a child of the result that needed it, and a sub-recipe's ingredients
 * become children of that ingredient.
 */
public class CraftingTreeMath extends RecipeChainMath {

    public final List<TreeSlot> rootSlots = new ArrayList<>();
    public final List<TreeSlot> items = new ArrayList<>();
    public final Map<TreeSlot, TreeSlot> parentOf = new HashMap<>();
    public final Map<TreeSlot, List<TreeSlot>> childrenOf = new HashMap<>();

    private final Deque<TreeSlot> parentStack = new ArrayDeque<>();
    private TreeSlot lastItem = null;

    public CraftingTreeMath(List<ChainItem> chainItems, Set<RecipeKey> collapsedRecipes) {
        super(chainItems, collapsedRecipes, ItemContainerResolver.INSTANCE);
    }

    @Override
    public RecipeChainMath refresh() {
        this.rootSlots.clear();
        this.items.clear();
        this.parentOf.clear();
        this.childrenOf.clear();
        this.parentStack.clear();
        this.lastItem = null;
        return super.refresh();
    }

    @Override
    protected void calculationStepTrigger(ChainItem ingrItem, ChainItem prefItem, long requestedAmount,
            long containerAmount, long inventoryAmount, long ingrAmount, long produced) {

        if (ingrItem.type == ChainItem.Type.RESULT) {
            final long required = this.requiredAmount.getOrDefault(prefItem, 0L);
            final TreeSlot slot = new TreeSlot(ingrItem, Math.max(0, required - requestedAmount), requestedAmount,
                    required, 0, prefItem);
            this.rootSlots.add(slot);
            this.items.add(slot);
            this.lastItem = slot;
        } else {
            addChild(new TreeSlot(ingrItem, 0, requestedAmount, produced, containerAmount, prefItem));
        }
    }

    private void addChild(TreeSlot slot) {
        final TreeSlot parent = this.parentStack.peek();
        this.items.add(slot);
        this.parentOf.put(slot, parent);
        this.childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(slot);
        this.lastItem = slot;
    }

    @Override
    protected void prepareIngredients(RecipeKey recipe, long multiplier, List<RecipeKey> visited) {
        this.parentStack.push(this.lastItem);
        super.prepareIngredients(recipe, multiplier, visited);
        this.lastItem = this.parentStack.pop();
    }

    @Override
    protected void calculateSuitableRecipe(ChainItem ingrItem, long ingrMultiplier, List<RecipeKey> visited) {
        final ChainItem prefItem = this.preferredItems.get(ingrItem);
        super.calculateSuitableRecipe(ingrItem, ingrMultiplier, visited);

        // a sub-recipe that needed nothing this time still shows its ingredients (with zero amounts)
        if (prefItem != null && !isForeignBoxBoundary(ingrItem) && !visited.contains(prefItem.recipe)
                && !this.childrenOf.containsKey(this.lastItem)) {
            visited.add(prefItem.recipe);
            prepareEmptyIngredients(prefItem.recipe, visited);
            visited.remove(prefItem.recipe);
        }
    }

    private void prepareEmptyIngredients(RecipeKey recipe, List<RecipeKey> visited) {
        this.parentStack.push(this.lastItem);

        for (ChainItem item : this.ingredientsByRecipe.getOrDefault(recipe, Collections.emptyList())) {
            if (!item.emptyFactor()) {
                calculateEmptyRecipe(item, visited);
            }
        }

        this.lastItem = this.parentStack.pop();
    }

    private void calculateEmptyRecipe(ChainItem ingrItem, List<RecipeKey> visited) {
        final ChainItem prefItem = this.preferredItems.get(ingrItem);
        addChild(new TreeSlot(ingrItem, 0, 0, 0, 0, prefItem));

        if (prefItem != null && !isForeignBoxBoundary(ingrItem) && !visited.contains(prefItem.recipe)) {
            visited.add(prefItem.recipe);
            prepareEmptyIngredients(prefItem.recipe, visited);
            visited.remove(prefItem.recipe);
        }
    }

}
