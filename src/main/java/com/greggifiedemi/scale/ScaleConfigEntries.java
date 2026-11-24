package com.greggifiedemi.scale;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import com.greggifiedemi.Config;

import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.widget.config.BooleanWidget;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.IntWidget;
import dev.emi.emi.screen.widget.config.ListWidget;
import dev.emi.emi.screen.widget.config.SubGroupNameWidget;

/**
 * Adds "Detach GUI Scale" and "EMI GUI Scale" at the top of the UI group of EMI's config screen. The values live in this
 * mod's config, never in {@code emi.css}, so EMI's own "changes" counter and reset button ignore them.
 */
public final class ScaleConfigEntries {

    private ScaleConfigEntries() {}

    public static void add(ConfigScreen screen, ListWidget list, Supplier<String> search) {
        final List<ListWidget.Entry> entries = list.children();
        GroupNameWidget group = null;
        int index = entries.size();

        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof GroupNameWidget name && !(name instanceof SubGroupNameWidget)
                    && name.id.equals("ui")) {
                group = name;
                index = i + 1;
                break;
            }
        }

        final ConfigEntryWidget detach = new BooleanWidget(Component.translatable("config.greggifiedemi.detach_gui_scale"),
                tooltip("config.greggifiedemi.tooltip.detach_gui_scale"), search, screen.new Mutator<Boolean>() {

                    @Override
                    protected Boolean getValue() {
                        return Config.detachGuiScale();
                    }

                    @Override
                    protected void setValue(Boolean value) {
                        Config.setGuiScale(value, Config.emiGuiScale());
                    }
                });

        final ConfigEntryWidget scale = new IntWidget(Component.translatable("config.greggifiedemi.emi_gui_scale"),
                tooltip("config.greggifiedemi.tooltip.emi_gui_scale"), search, screen.new Mutator<Integer>() {

                    @Override
                    protected Integer getValue() {
                        return Config.emiGuiScale();
                    }

                    @Override
                    protected void setValue(Integer value) {
                        Config.setGuiScale(Config.detachGuiScale(), value);
                    }
                });

        for (ConfigEntryWidget entry : List.of(detach, scale)) {
            entries.add(index++, entry);
            entry.parentList = list;

            if (group != null) {
                group.children.add(entry);
                entry.parentGroups.add(group);
            }
        }
    }

    private static List<ClientTooltipComponent> tooltip(String key) {
        return Arrays.stream(Component.translatable(key).getString().split("\n"))
                .map(line -> EmiTooltipComponents.of(Component.literal(line))).toList();
    }

}
