package com.greggifiedemi;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import com.greggifiedemi.tree.CraftingTreeScreen;
import com.greggifiedemi.tree.TreeCanvas;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.RecipeFillButtonWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.WidgetGroup;

/**
 * EMI plugin: decorates every recipe with NEI's "Use for recipe tree" button (TreeOverlayButton, shown only while the
 * crafting tree waits for a replacement recipe) and NEI's present-ingredient highlight on the fill button.
 */
@EmiEntrypoint
public class GreggifiedEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        // the dev-only baseline benchmark measures EMI without anything from this mod
        if (Boolean.getBoolean("greggifiedemi.baseline")) {
            return;
        }

        registry.addRecipeDecorator(GreggifiedEmiPlugin::decorate);
    }

    private static void decorate(EmiRecipe recipe, WidgetHolder widgets) {
        if (recipe.getId() != null && widgets.getWidth() >= 12) {
            widgets.add(new TreeButton(recipe, widgets.getWidth() - 9, widgets.getHeight() - 9));
        }

        if (widgets instanceof WidgetGroup group) {
            widgets.add(new PresenceHighlight(recipe, group));
        }
    }

    /**
     * NEI's "Highlight Present Item" (inventory.guirecipe.slotHighlightPresent): while the fill button of a recipe is
     * hovered, ingredients the player already has are tinted green. EMI tints the missing ones red but leaves its
     * green tint commented out.
     */
    private static final class PresenceHighlight extends Widget {

        private final EmiRecipe recipe;
        private final WidgetGroup group;

        PresenceHighlight(EmiRecipe recipe, WidgetGroup group) {
            this.recipe = recipe;
            this.group = group;
        }

        @Override
        public Bounds getBounds() {
            return Bounds.EMPTY;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (!Config.slotHighlightPresent() || EmiScreenManager.lastPlayerInventory == null) {
                return;
            }

            boolean hovered = false;

            for (Widget widget : this.group.widgets) {
                if (widget instanceof RecipeFillButtonWidget && widget.getBounds().contains(mouseX, mouseY)) {
                    hovered = true;
                    break;
                }
            }

            if (!hovered) {
                return;
            }

            final List<Boolean> available = EmiScreenManager.lastPlayerInventory.getCraftAvailability(this.recipe);
            final List<EmiIngredient> inputs = this.recipe.getInputs();

            if (available.size() != inputs.size()) {
                return;
            }

            final Map<EmiIngredient, Boolean> present = new IdentityHashMap<>();

            for (int i = 0; i < inputs.size(); i++) {
                present.put(inputs.get(i), available.get(i));
            }

            for (Widget widget : this.group.widgets) {
                if (widget instanceof SlotWidget slot && slot.getRecipe() == null
                        && Boolean.TRUE.equals(present.get(slot.getStack())) && !slot.getStack().isEmpty()) {
                    final Bounds bounds = slot.getBounds();
                    graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0x4400FF00);
                }
            }
        }
    }

    private static final class TreeButton extends Widget {

        private final EmiRecipe recipe;
        private final Bounds bounds;

        TreeButton(EmiRecipe recipe, int x, int y) {
            this.recipe = recipe;
            this.bounds = new Bounds(x, y, 9, 9);
        }

        @Override
        public Bounds getBounds() {
            return CraftingTreeScreen.isPicking() ? this.bounds : Bounds.EMPTY;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (!CraftingTreeScreen.isPicking()) {
                return;
            }

            final boolean hover = this.bounds.contains(mouseX, mouseY);
            graphics.fill(this.bounds.x(), this.bounds.y(), this.bounds.right(), this.bounds.bottom(),
                    hover ? 0xFF6A8F6A : 0xFF3A5F3A);
            graphics.renderOutline(this.bounds.x(), this.bounds.y(), 9, 9, 0xFF101010);
            graphics.pose().pushPose();
            graphics.pose().translate(this.bounds.x() + 1, this.bounds.y() + 1, 0);
            graphics.pose().scale(0.5f, 0.5f, 1);
            graphics.blit(TreeCanvas.TEXTURE, 0, 0, 67, 48, 16, 16);
            graphics.pose().popPose();
        }

        @Override
        public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
            return CraftingTreeScreen.isPicking() ? List.of(ClientTooltipComponent.create(
                    Component.translatable("greggifiedemi.tree.overlay").getVisualOrderText())) : List.of();
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0 || !CraftingTreeScreen.isPicking()) {
                return false;
            }

            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            CraftingTreeScreen.usePickedRecipe(this.recipe);
            return true;
        }
    }

}
