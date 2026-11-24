package com.greggifiedemi.mixin.perf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.searchtree.SuffixArray;

import com.greggifiedemi.Config;
import com.greggifiedemi.perf.ParallelSuffixArray;

import dev.emi.emi.search.EmiSearch;

/**
 * EMI's search bake creates four {@link SuffixArray}s and sorts them one after another; this hands it
 * {@link ParallelSuffixArray}s instead, which sort concurrently. Only the constructor call is redirected (class names
 * are the same in development and production), so no Minecraft method name is targeted.
 */
@Mixin(value = EmiSearch.class, remap = false)
public abstract class EmiSearchBakeMixin {

    @Redirect(method = "bake", at = @At(value = "NEW", target = "net/minecraft/client/searchtree/SuffixArray"),
            require = 0)
    private static SuffixArray<?> greggifiedemi$parallelSuffixArray() {
        return Config.emiParallelSearch() ? new ParallelSuffixArray<>() : new SuffixArray<>();
    }

}
