package com.greggifiedemi.mixin.scale;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;

/** Calls {@code EmiScreenManager}'s private methods that {@link ScreenManagerScaleMixin} re-runs in another space. */
@Mixin(value = EmiScreenManager.class, remap = false)
public interface ScreenManagerInvoker {

    @Invoker("renderSlotOverlays")
    static void greggifiedemi$renderSlotOverlays(EmiDrawContext context, int mouseX, int mouseY, float delta,
            EmiScreenBase base) {
        throw new AssertionError();
    }

    @Invoker("deleteCursor")
    static boolean greggifiedemi$deleteCursor(int mouseX, int mouseY) {
        throw new AssertionError();
    }

}
