package com.greggifiedemi.tree;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/** Nodes matching the tree's search field or a clicked stats entry; one of them is highlighted (NEI's SearchResult). */
public final class SearchResult {

    public final Object key;
    public final Set<TreeSlot> matches = new LinkedHashSet<>();
    public @Nullable TreeSlot highlightNode;

    public SearchResult(Object key) {
        this.key = key;
    }

}
