package com.greggifiedemi.mixin.scale;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;

import com.greggifiedemi.scale.EmiScale;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.runtime.EmiDrawContext;

/**
 * Detached EMI GUI scale ({@link EmiScale}): tooltips keep the game's scale, like the inventory's own, so their text is
 * as readable as everywhere else. All of EMI's tooltip helpers (and this mod's) end in this overload.
 */
@Mixin(value = EmiRenderHelper.class, remap = false)
public abstract class TooltipScaleMixin {

    @Inject(method = "drawTooltip(Lnet/minecraft/client/gui/screens/Screen;Ldev/emi/emi/runtime/EmiDrawContext;"
            + "Ljava/util/List;IIILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V",
            at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$drawTooltip(Screen screen, EmiDrawContext context,
            List<ClientTooltipComponent> components, int x, int y, int maxWidth, ClientTooltipPositioner positioner,
            CallbackInfo ci) {
        if (!EmiScale.inEmi() || components.isEmpty()) {
            return;
        }

        final int gameX = EmiScale.toGameX(x);
        final int gameY = EmiScale.toGameY(y);
        final int gameMaxWidth = (int) EmiScale.toGame(maxWidth);
        EmiScale.drawInGame(context.raw(), () -> EmiRenderHelper.drawTooltip(screen, context, components, gameX,
                gameY, gameMaxWidth, positioner));
        ci.cancel();
    }

}
