package com.greggifiedemi.bookmark;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.greggifiedemi.Config;
import com.greggifiedemi.chain.ChainItem;
import com.greggifiedemi.chain.ChainStack;
import com.greggifiedemi.chain.RecipeKey;
import com.greggifiedemi.chain.Stacks;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;

/**
 * NEI's "Save Recipe Tree" (Shift + F over an item in the item list or history, and the favorite button in NEI's
 * recipe view): walks the favorite recipes of the item's ingredients down to {@code favoritesDepth} levels and
 * bookmarks them all as one new crafting-chain group in to-do list view (port of GuiFavoriteButton.getRecipesTree and
 * BookmarkPanel.addGroup).
 */
public final class FavoriteTree {

    private record Node(EmiRecipe recipe, @Nullable ChainStack result) {}

    private FavoriteTree() {}

    /** Bookmarks the favorite recipe tree of a stack. Returns the new group id, or -1 if it has no favorite recipe. */
    public static int save(EmiStack stack) {
        final EmiRecipe root = FavoriteRecipes.getFavorite(stack);
        return root == null ? -1 : save(root, Stacks.of(stack, 1));
    }

    /** Bookmarks the favorite recipe tree below a recipe (the recipe view's favorite action). */
    public static int save(EmiRecipe root, @Nullable ChainStack result) {
        if (!RecipeBookmarks.canBookmark(root)) {
            return -1;
        }

        final List<Node> nodes = tree(root, result);
        final BookmarkGrid grid = BookmarkPanel.grid();
        final int groupId = grid.addGroup(new BookmarkGroup(BookmarkViewMode.TODO_LIST, true));
        final Set<RecipeKey> unique = new HashSet<>();
        final Set<String> chosen = new HashSet<>();

        for (Node node : nodes) {
            if (!unique.add(RecipeKey.of(node.recipe))) {
                continue;
            }

            final List<ChainItem> items = new ArrayList<>();

            for (ChainItem item : RecipeBookmarks.build(node.recipe, groupId, 1, node.result)) {
                if (item.type == ChainItem.Type.RESULT) {
                    chosen.add(item.stack.key);
                }
                items.add(item);
            }

            grid.addRecipe(selectFavoritePermutations(items, chosen), groupId);
        }

        grid.onItemsChanged();
        return groupId;
    }

    /** Breadth-first walk over favorite recipes, like NEI (depth counts levels below the root). */
    private static List<Node> tree(EmiRecipe root, @Nullable ChainStack rootResult) {
        int depth = Config.favoritesDepth();
        final List<Node> result = new ArrayList<>();
        final Set<String> items = new LinkedHashSet<>();
        List<EmiRecipe> loop = new ArrayList<>();

        loop.add(root);
        result.add(new Node(root, rootResult));

        while (!loop.isEmpty() && depth-- >= 0) {
            final List<EmiRecipe> next = new ArrayList<>();

            for (EmiRecipe recipe : loop) {
                for (ChainItem ingredient : RecipeBookmarks.build(recipe, -1, 1, null)) {
                    if (ingredient.type != ChainItem.Type.INGREDIENT) {
                        continue;
                    }

                    if (ingredient.permutations.keySet().stream().anyMatch(items::contains)) {
                        continue;
                    }

                    ChainStack stack = ingredient.stack;
                    EmiRecipe favorite = stack.emi != null ? FavoriteRecipes.getFavorite(stack.emi) : null;

                    if (favorite == null) {
                        for (ChainStack perm : ingredient.permutations.values()) {
                            if (perm.emi != null && (favorite = FavoriteRecipes.getFavorite(perm.emi)) != null) {
                                stack = perm;
                                break;
                            }
                        }
                    }

                    if (favorite == null || !RecipeBookmarks.canBookmark(favorite)) {
                        continue;
                    }

                    items.add(stack.key);

                    if (depth >= 0) {
                        result.add(new Node(favorite, stack.withAmount(1)));
                        next.add(favorite);
                    }
                }
            }

            loop = next;
        }

        return result;
    }

    /** NEI sets each ingredient's active permutation to the one the tree crafts. */
    private static List<ChainItem> selectFavoritePermutations(List<ChainItem> items, Set<String> crafted) {
        final List<ChainItem> result = new ArrayList<>(items.size());

        for (ChainItem item : items) {
            ChainItem selected = item;

            if (item.type == ChainItem.Type.INGREDIENT && item.permutations.size() > 1
                    && !crafted.contains(item.stack.key)) {
                for (ChainStack perm : item.permutations.values()) {
                    if (crafted.contains(perm.key) || perm.emi != null && FavoriteRecipes.contains(perm.emi)) {
                        selected = perm.sameType(item.stack) ? item : item.copyWithPerm(perm);
                        break;
                    }
                }
            }

            result.add(selected);
        }

        return result;
    }

}
