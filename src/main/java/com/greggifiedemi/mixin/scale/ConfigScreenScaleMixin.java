package com.greggifiedemi.mixin.scale;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.greggifiedemi.GreggifiedEmi;
import com.greggifiedemi.scale.ScaleConfigEntries;

import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.widget.config.ConfigSearch;
import dev.emi.emi.screen.widget.config.ListWidget;

/**
 * Puts the detached GUI scale options into EMI's config screen. {@code init} is a Minecraft method (renamed in
 * production), so this hooks EMI's own {@code addJumpButtons}, which {@code init} calls once the list is built.
 */
@Mixin(value = ConfigScreen.class, remap = false)
public abstract class ConfigScreenScaleMixin {

    @Shadow
    private ConfigSearch search;

    @Shadow
    public ListWidget list;

    @Inject(method = "addJumpButtons", at = @At("HEAD"))
    private void greggifiedemi$addScaleOptions(CallbackInfo ci) {
        try {
            ScaleConfigEntries.add((ConfigScreen) (Object) this, this.list, () -> this.search.getSearch());
        } catch (RuntimeException e) {
            GreggifiedEmi.LOGGER.error("Failed to add the GUI scale options to EMI's config screen", e);
        }
    }

}
