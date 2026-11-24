package com.greggifiedemi.mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.runtime.EmiTagKey;

/**
 * EMI fix: tag searches ({@code #tag} in EMI, NEI's {@code $tag} here) run on EMI's search thread and fill
 * {@code EmiTagKey.CACHE}, a plain HashMap the render thread fills too. Two threads in {@code computeIfAbsent} throw a
 * ConcurrentModificationException and the search silently shows stale results. A concurrent map makes it safe and
 * costs nothing measurable.
 */
@Mixin(value = EmiTagKey.class, remap = false)
public abstract class EmiTagKeyMixin {

    @Shadow
    @Final
    @Mutable
    public static Map<Object, Object> CACHE;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void greggifiedemi$concurrentCache(CallbackInfo ci) {
        CACHE = new ConcurrentHashMap<>(CACHE);
    }

}
