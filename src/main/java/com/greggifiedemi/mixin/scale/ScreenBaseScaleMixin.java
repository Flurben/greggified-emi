package com.greggifiedemi.mixin.scale;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.greggifiedemi.scale.EmiScale;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.EmiScreenBase;

/**
 * Detached EMI GUI scale ({@link EmiScale}): the screen's own area (inventory, recipe view) as EMI's layout sees it,
 * converted to EMI space while EMI lays out or draws its panels.
 */
@Mixin(value = EmiScreenBase.class, remap = false)
public abstract class ScreenBaseScaleMixin {

    @Inject(method = "bounds", at = @At("RETURN"), cancellable = true)
    private void greggifiedemi$bounds(CallbackInfoReturnable<Bounds> cir) {
        if (EmiScale.inEmi()) {
            cir.setReturnValue(EmiScale.toEmi(cir.getReturnValue()));
        }
    }

}
