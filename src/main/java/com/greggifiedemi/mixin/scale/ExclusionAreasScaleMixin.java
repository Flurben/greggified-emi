package com.greggifiedemi.mixin.scale;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.greggifiedemi.scale.EmiScale;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.registry.EmiExclusionAreas;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;

/**
 * Detached EMI GUI scale ({@link EmiScale}): EMI's exclusion list mixes EMI's own widgets (EMI space) with areas that
 * mods report for the screen (game space). In EMI space the first are built here, as EMI does, and the others are asked
 * for in game space and converted.
 */
@Mixin(value = EmiExclusionAreas.class, remap = false)
public abstract class ExclusionAreasScaleMixin {

    @Inject(method = "getExclusion", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$getExclusion(EmiScreenBase base, CallbackInfoReturnable<List<Bounds>> cir) {
        if (!EmiScale.inEmi()) {
            return;
        }

        final List<Bounds> list = new ArrayList<>();
        final Bounds bounds = base.bounds();
        list.add(bounds);
        // EMI's buttons in the lower left corner
        list.add(new Bounds(0, base.screen().height - 22, bounds.left(), 22));
        int own = 2;

        if (EmiScreenManager.search.isVisible()) {
            list.add(new Bounds(EmiScreenManager.search.getX() - 1, EmiScreenManager.search.getY() - 1,
                    EmiScreenManager.search.getWidth() + 2, EmiScreenManager.search.getHeight() + 2));
            own++;
        }

        final List<Bounds> game;
        final boolean left = EmiScale.leave();

        try {
            game = EmiExclusionAreas.getExclusion(base);
        } finally {
            EmiScale.reenter(left);
        }

        for (int i = own; i < game.size(); i++) {
            list.add(EmiScale.toEmi(game.get(i)));
        }

        cir.setReturnValue(list);
    }

}
