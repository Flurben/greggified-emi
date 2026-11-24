package com.greggifiedemi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.greggifiedemi.nei.HistoryPanel;

import dev.emi.emi.config.EmiConfig;

/** Keeps the in-memory history subpanel ({@link HistoryPanel}) out of {@code emi.css} when EMI saves its config. */
@Mixin(value = EmiConfig.class, remap = false)
public abstract class EmiConfigMixin {

    @Unique
    private static boolean greggifiedemi$detached;

    @Inject(method = "writeConfig", at = @At("HEAD"))
    private static void greggifiedemi$beforeWrite(CallbackInfo ci) {
        greggifiedemi$detached = HistoryPanel.detachForSave();
    }

    @Inject(method = "writeConfig", at = @At("RETURN"))
    private static void greggifiedemi$afterWrite(CallbackInfo ci) {
        HistoryPanel.reattachAfterSave(greggifiedemi$detached);
        greggifiedemi$detached = false;
    }

}
