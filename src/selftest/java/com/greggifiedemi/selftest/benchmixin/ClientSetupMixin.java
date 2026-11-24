package com.greggifiedemi.selftest.benchmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.greggifiedemi.ClientSetup;

/**
 * Dev-only: for the baseline benchmark (-Pbaseline) the mod registers nothing on the client, so together with its
 * mixins left out EMI runs exactly as without the mod.
 */
@Mixin(value = ClientSetup.class, remap = false)
public abstract class ClientSetupMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$baseline(CallbackInfo ci) {
        if (Boolean.getBoolean("greggifiedemi.baseline")) {
            ci.cancel();
        }
    }

}
