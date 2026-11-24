package com.greggifiedemi.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.WidgetGroup;

/**
 * The recipes on the current page of EMI's recipe view, to find the recipe under the mouse (NEI's focused recipe), and
 * its tab/page position for NEI's recipe keys.
 */
@Mixin(value = RecipeScreen.class, remap = false)
public interface RecipeScreenAccessor {

    @Accessor("currentPage")
    List<WidgetGroup> greggifiedemi$getCurrentPage();

    @Accessor("tabPage")
    int greggifiedemi$getTabPage();

    @Accessor("tab")
    int greggifiedemi$getTab();

    @Accessor("page")
    int greggifiedemi$getPage();

}
