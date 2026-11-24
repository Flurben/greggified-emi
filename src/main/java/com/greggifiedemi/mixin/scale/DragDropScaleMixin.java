package com.greggifiedemi.mixin.scale;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import com.greggifiedemi.scale.EmiScale;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.registry.EmiDragDropHandlers;

/**
 * Detached EMI GUI scale ({@link EmiScale}): drop targets belong to the screen, so they are drawn and hit-tested at the
 * game's scale.
 */
@Mixin(value = EmiDragDropHandlers.class, remap = false)
public abstract class DragDropScaleMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$render(Screen screen, EmiIngredient stack, GuiGraphics draw, int mouseX,
            int mouseY, float delta, CallbackInfo ci) {
        if (!EmiScale.inEmi()) {
            return;
        }

        final int x = EmiScale.toGameX(mouseX);
        final int y = EmiScale.toGameY(mouseY);
        EmiScale.drawInGame(draw, () -> EmiDragDropHandlers.render(screen, stack, draw, x, y, delta));
        ci.cancel();
    }

    @Inject(method = "dropStack", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$dropStack(Screen screen, EmiIngredient stack, int x, int y,
            CallbackInfoReturnable<Boolean> cir) {
        if (!EmiScale.inEmi()) {
            return;
        }

        final int gameX = EmiScale.toGameX(x);
        final int gameY = EmiScale.toGameY(y);
        final boolean left = EmiScale.leave();

        try {
            cir.setReturnValue(EmiDragDropHandlers.dropStack(screen, stack, gameX, gameY));
        } finally {
            EmiScale.reenter(left);
        }
    }

}
