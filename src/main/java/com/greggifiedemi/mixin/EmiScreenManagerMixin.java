package com.greggifiedemi.mixin;

import java.util.Collection;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import com.greggifiedemi.GreggifiedEmi;
import com.greggifiedemi.bookmark.BookmarkPanel;
import com.greggifiedemi.bookmark.GridGeometry;
import com.greggifiedemi.bookmark.ItemShortcuts;
import com.greggifiedemi.bookmark.NamespaceFooter;
import com.greggifiedemi.nei.NeiInput;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarSettings;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.registry.EmiDragDropHandlers;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.EmiScreenManager.SidebarPanel;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;

/**
 * Gives the bookmark panel the first look at input over EMI's sidebars, and makes room for NEI's group gutter left of
 * a favorites sidebar.
 */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiScreenManagerMixin {

    @Unique
    private static SidebarPanel greggifiedemi$creatingPanel;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$mouseClicked(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (BookmarkPanel.mouseClicked(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
            }
        } catch (RuntimeException e) {
            GreggifiedEmi.LOGGER.error("Bookmark panel failed to handle a click", e);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$mouseReleased(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (BookmarkPanel.mouseReleased(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
            }
        } catch (RuntimeException e) {
            GreggifiedEmi.LOGGER.error("Bookmark panel failed to handle a mouse release", e);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$mouseDragged(double mouseX, double mouseY, int button, double deltaX,
            double deltaY, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (BookmarkPanel.mouseDragged(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
            }
        } catch (RuntimeException e) {
            GreggifiedEmi.LOGGER.error("Bookmark panel failed to handle a drag", e);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$mouseScrolled(double mouseX, double mouseY, double amount,
            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (BookmarkPanel.mouseScrolled(mouseX, mouseY, amount)) {
                cir.setReturnValue(true);
            }
        } catch (RuntimeException e) {
            GreggifiedEmi.LOGGER.error("Bookmark panel failed to handle a scroll", e);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$keyPressed(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (NeiInput.keyPressed(keyCode, scanCode, modifiers)) {
                cir.setReturnValue(true);
            }
        } catch (RuntimeException e) {
            GreggifiedEmi.LOGGER.error("Failed to handle a key", e);
        }
    }

    @Inject(method = "createScreenSpace", at = @At("HEAD"))
    private static void greggifiedemi$createScreenSpace(SidebarPanel panel, Screen screen, List<Bounds> exclusion,
            boolean rtl, Bounds bounds, SidebarSettings settings, CallbackInfo ci) {
        greggifiedemi$creatingPanel = panel;
    }

    /** A favorites sidebar starts {@link GridGeometry#GROUP_PANEL_WIDTH} pixels further in, for the group gutter. */
    @ModifyVariable(method = "createScreenSpace", at = @At("STORE"), name = "xMin", require = 0)
    private static int greggifiedemi$reserveGutter(int xMin) {
        final SidebarPanel panel = greggifiedemi$creatingPanel;
        return panel != null && panel.getType() == SidebarType.FAVORITES ? xMin + GridGeometry.GROUP_PANEL_WIDTH
                : xMin;
    }

    /** Room for NEI's namespace footer below a favorites sidebar. */
    @ModifyVariable(method = "createScreenSpace", at = @At("STORE"), name = "yMax", require = 0)
    private static int greggifiedemi$reserveFooter(int yMax) {
        final SidebarPanel panel = greggifiedemi$creatingPanel;
        return panel != null && panel.getType() == SidebarType.FAVORITES ? yMax - NamespaceFooter.HEIGHT : yMax;
    }

    /** NEI's hotkey hints on stacks in EMI's sidebars (bookmarks add their own). */
    @Redirect(method = "renderCurrentTooltip", at = @At(value = "INVOKE",
            target = "Ljava/util/List;addAll(Ljava/util/Collection;)Z", ordinal = 0), require = 0)
    private static boolean greggifiedemi$hotkeys(List<ClientTooltipComponent> list,
            Collection<? extends ClientTooltipComponent> tooltip) {
        final boolean changed = list.addAll(tooltip);

        try {
            final EmiStackInteraction hovered = EmiScreenManager.getHoveredStack(EmiScreenManager.lastMouseX,
                    EmiScreenManager.lastMouseY, false);

            if (!hovered.isEmpty() && !list.isEmpty()) {
                list.addAll(1, ItemShortcuts.itemHotkeyTips(hovered.getStack(), EmiScreenManager.lastMouseX,
                        EmiScreenManager.lastMouseY, hovered.getRecipeContext()));
            }
        } catch (RuntimeException e) {
            GreggifiedEmi.LOGGER.debug("Failed to add hotkey hints", e);
        }

        return changed;
    }

    /** EMI's drop marker for dragging into favorites assumes its own favorites list; the panel draws its own. */
    @Inject(method = "renderDraggedStack", at = @At("HEAD"), cancellable = true)
    private static void greggifiedemi$renderDraggedStack(EmiDrawContext context, int mouseX, int mouseY,
            float delta, EmiScreenBase base, CallbackInfo ci) {
        final EmiIngredient dragged = EmiScreenManager.draggedStack;

        if (dragged.isEmpty()) {
            return;
        }

        final EmiScreenManager.ScreenSpace space = EmiScreenManager.getHoveredSpace(mouseX, mouseY);

        if (space != null && space.getType() == SidebarType.FAVORITES) {
            context.push();
            context.matrices().translate(0, 0, 400);
            EmiDragDropHandlers.render(base.screen(), dragged, context.raw(), mouseX, mouseY, delta);
            dragged.render(context.raw(), mouseX - 8, mouseY - 8, delta, EmiIngredient.RENDER_ICON);
            context.pop();
            ci.cancel();
        }
    }

}
