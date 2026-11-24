package com.greggifiedemi.mixin.scale;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.Screen;

import com.greggifiedemi.scale.EmiScale;

import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.registry.EmiStackProviders;

/**
 * Detached EMI GUI scale ({@link EmiScale}): stacks under the mouse in the screen (inventory slots, recipe view
 * widgets) are looked up in game space.
 */
@Mixin(value = EmiStackProviders.class, remap = false)
public abstract class StackProvidersScaleMixin {

    @Inject(method = "getStackAt", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$getStackAt(Screen screen, int x, int y, boolean notClick,
            CallbackInfoReturnable<EmiStackInteraction> cir) {
        if (!EmiScale.inEmi()) {
            return;
        }

        final int gameX = EmiScale.toGameX(x);
        final int gameY = EmiScale.toGameY(y);
        final boolean left = EmiScale.leave();

        try {
            cir.setReturnValue(EmiStackProviders.getStackAt(screen, gameX, gameY, notClick));
        } finally {
            EmiScale.reenter(left);
        }
    }

}
