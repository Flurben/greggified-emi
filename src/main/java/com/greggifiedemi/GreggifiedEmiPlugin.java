package com.greggifiedemi;

import java.util.List;

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
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;

/**
 * EMI plugin: adds NEI's "Use for recipe tree" button (TreeOverlayButton) to recipes while the crafting tree waits for
 * a replacement recipe. The widget is invisible and inert at all other times.
 */
@EmiEntrypoint
public class GreggifiedEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipeDecorator(GreggifiedEmiPlugin::decorate);
    }

    private static void decorate(EmiRecipe recipe, WidgetHolder widgets) {
        if (recipe.getId() != null && widgets.getWidth() >= 12) {
            widgets.add(new TreeButton(recipe, widgets.getWidth() - 9, widgets.getHeight() - 9));
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
