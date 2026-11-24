package com.greggifiedemi.tree;

import org.jetbrains.annotations.Nullable;

import com.greggifiedemi.chain.ChainItem;
import com.greggifiedemi.chain.ChainStack;
import com.greggifiedemi.chain.ItemContainerResolver;

import dev.emi.emi.api.stack.EmiStack;

/**
 * One node of the crafting tree: an ingredient as it is requested in the chain, and the recipe result (if any) that
 * produces it (port of NEI's ItemTreeSlot).
 */
public final class TreeSlot {

    public final ChainItem ingrItem;
    public final @Nullable ChainItem prefItem;
    public final long inheritRequestedAmount;
    public final long currentRequestedAmount;
    public final long requestedAmount;
    public final long containerAmount;
    public final long producedAmount;
    /** What still has to be produced or found: 0 means the inventory or earlier steps cover it. */
    public final long leftAmount;
    public final EmiStack stack;
    public final boolean isFluid;
    public final boolean isContainerItem;
    public final long multiplier;

    TreeSlot(ChainItem ingrItem, long inheritRequestedAmount, long currentRequestedAmount, long producedAmount,
            long containerAmount, @Nullable ChainItem prefItem) {
        this.ingrItem = ingrItem;
        this.inheritRequestedAmount = inheritRequestedAmount;
        this.currentRequestedAmount = currentRequestedAmount;
        this.requestedAmount = inheritRequestedAmount + currentRequestedAmount;
        this.containerAmount = containerAmount;
        this.producedAmount = producedAmount;
        this.leftAmount = producedAmount + containerAmount;
        this.prefItem = prefItem;

        final ChainItem shown = prefItem != null ? prefItem : ingrItem;
        final ChainStack chainStack = shown.stack;
        this.multiplier = shown.getMultiplierFromAmount(this.requestedAmount);
        this.stack = chainStack.emi != null ? chainStack.emi.copy().setAmount(1) : EmiStack.EMPTY;
        this.isFluid = chainStack.fluid;
        this.isContainerItem = ItemContainerResolver.INSTANCE.hasContainerItem(chainStack);
    }

    /** The stack with the requested amount, for tooltips and EMI's recipe keys. */
    public EmiStack stackWithAmount() {
        return this.stack.isEmpty() ? this.stack : this.stack.copy().setAmount(Math.max(1, this.requestedAmount));
    }

}
