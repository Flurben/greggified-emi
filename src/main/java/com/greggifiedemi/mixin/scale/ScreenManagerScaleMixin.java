package com.greggifiedemi.mixin.scale;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.Screen;

import com.greggifiedemi.scale.EmiScale;

import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.EmiScreenManager.ScreenSpace;
import dev.emi.emi.screen.EmiScreenManager.SidebarPanel;

/**
 * Moves {@code EmiScreenManager}'s positional entry points into EMI space when EMI's GUI scale is detached (see
 * {@link EmiScale}): each one re-runs itself inside EMI space with converted coordinates. The mixin has a low priority
 * so these run before every other injection at the head of the same methods, which therefore see EMI coordinates.
 */
@Mixin(value = EmiScreenManager.class, remap = false, priority = 100)
public abstract class ScreenManagerScaleMixin {

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$drawBackground(EmiDrawContext context, int mouseX, int mouseY, float delta,
            CallbackInfo ci) {
        if (EmiScale.shouldEnter()) {
            EmiScale.draw(context.raw(), mouseX, mouseY, (x, y) -> EmiScreenManager.drawBackground(context, x, y, delta));
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$render(EmiDrawContext context, int mouseX, int mouseY, float delta,
            CallbackInfo ci) {
        if (EmiScale.shouldEnter()) {
            EmiScale.draw(context.raw(), mouseX, mouseY, (x, y) -> EmiScreenManager.render(context, x, y, delta));
            ci.cancel();
        }
    }

    @Inject(method = "drawForeground", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$drawForeground(EmiDrawContext context, int mouseX, int mouseY, float delta,
            CallbackInfo ci) {
        if (EmiScale.shouldEnter()) {
            EmiScale.draw(context.raw(), mouseX, mouseY, (x, y) -> EmiScreenManager.drawForeground(context, x, y, delta));
            ci.cancel();
        }
    }

    /** EMI's highlight over the screen's slots stays on the screen, at the game's scale. */
    @Inject(method = "renderSlotOverlays", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$renderSlotOverlays(EmiDrawContext context, int mouseX, int mouseY, float delta,
            EmiScreenBase base, CallbackInfo ci) {
        if (EmiScale.inEmi()) {
            final int x = EmiScale.toGameX(mouseX);
            final int y = EmiScale.toGameY(mouseY);
            EmiScale.drawInGame(context.raw(),
                    () -> ScreenManagerInvoker.greggifiedemi$renderSlotOverlays(context, x, y, delta, base));
            ci.cancel();
        }
    }

    /** The mouse EMI remembers stays in game coordinates, which EMI's recipe view and other mods compare it with. */
    @Inject(method = "updateMouse", at = @At("TAIL"))
    private static void greggifiedemi$updateMouse(int mouseX, int mouseY, CallbackInfo ci) {
        if (EmiScale.inEmi()) {
            EmiScreenManager.lastMouseX = EmiScale.toGameX(mouseX);
            EmiScreenManager.lastMouseY = EmiScale.toGameY(mouseY);
        }
    }

    @Inject(method = "recalculate", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$recalculate(CallbackInfo ci) {
        if (EmiScale.shouldEnter()) {
            EmiScale.enter();
            try {
                EmiScreenManager.recalculate();
            } finally {
                EmiScale.exit();
            }
            ci.cancel();
        }
    }

    @Inject(method = "addWidgets", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$addWidgets(Screen screen, CallbackInfo ci) {
        if (EmiScale.shouldEnter()) {
            EmiScale.enter();
            try {
                EmiScreenManager.addWidgets(screen);
            } finally {
                EmiScale.exit();
            }
            ci.cancel();
        }
    }

    @Inject(method = "getHoveredStack(IIZZ)Ldev/emi/emi/api/stack/EmiStackInteraction;", at = @At("HEAD"),
            cancellable = true)
    private static void greggifiedemi$getHoveredStack(int mouseX, int mouseY, boolean notClick,
            boolean ignoreLastHoveredCraftable, CallbackInfoReturnable<EmiStackInteraction> cir) {
        if (EmiScale.shouldEnter()) {
            EmiScale.enter();
            try {
                cir.setReturnValue(EmiScreenManager.getHoveredStack(EmiScale.toEmiX(mouseX), EmiScale.toEmiY(mouseY),
                        notClick, ignoreLastHoveredCraftable));
            } finally {
                EmiScale.exit();
            }
        }
    }

    @Inject(method = "getHoveredPanel", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$getHoveredPanel(int mouseX, int mouseY, CallbackInfoReturnable<SidebarPanel> cir) {
        if (EmiScale.shouldEnter()) {
            EmiScale.enter();
            try {
                cir.setReturnValue(EmiScreenManager.getHoveredPanel(EmiScale.toEmiX(mouseX), EmiScale.toEmiY(mouseY)));
            } finally {
                EmiScale.exit();
            }
        }
    }

    @Inject(method = "getHoveredSpace", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$getHoveredSpace(int mouseX, int mouseY, CallbackInfoReturnable<ScreenSpace> cir) {
        if (EmiScale.shouldEnter()) {
            EmiScale.enter();
            try {
                cir.setReturnValue(EmiScreenManager.getHoveredSpace(EmiScale.toEmiX(mouseX), EmiScale.toEmiY(mouseY)));
            } finally {
                EmiScale.exit();
            }
        }
    }

    @Inject(method = "deleteCursor", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$deleteCursor(int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (EmiScale.shouldEnter()) {
            EmiScale.enter();
            try {
                cir.setReturnValue(ScreenManagerInvoker.greggifiedemi$deleteCursor(EmiScale.toEmiX(mouseX),
                        EmiScale.toEmiY(mouseY)));
            } finally {
                EmiScale.exit();
            }
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$mouseScrolled(double mouseX, double mouseY, double amount,
            CallbackInfoReturnable<Boolean> cir) {
        if (EmiScale.shouldEnter()) {
            EmiScale.enter();
            try {
                cir.setReturnValue(EmiScreenManager.mouseScrolled(EmiScale.toEmi(mouseX), EmiScale.toEmi(mouseY),
                        amount));
            } finally {
                EmiScale.exit();
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$mouseClicked(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (EmiScale.shouldEnter()) {
            EmiScale.enter();
            try {
                cir.setReturnValue(EmiScreenManager.mouseClicked(EmiScale.toEmi(mouseX), EmiScale.toEmi(mouseY),
                        button));
            } finally {
                EmiScale.exit();
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$mouseReleased(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (EmiScale.shouldEnter()) {
            EmiScale.enter();
            try {
                cir.setReturnValue(EmiScreenManager.mouseReleased(EmiScale.toEmi(mouseX), EmiScale.toEmi(mouseY),
                        button));
            } finally {
                EmiScale.exit();
            }
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$mouseDragged(double mouseX, double mouseY, int button, double deltaX,
            double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (EmiScale.shouldEnter()) {
            EmiScale.enter();
            try {
                cir.setReturnValue(EmiScreenManager.mouseDragged(EmiScale.toEmi(mouseX), EmiScale.toEmi(mouseY),
                        button, EmiScale.toEmi(deltaX), EmiScale.toEmi(deltaY)));
            } finally {
                EmiScale.exit();
            }
        }
    }

}
