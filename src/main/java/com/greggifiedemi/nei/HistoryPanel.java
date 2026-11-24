package com.greggifiedemi.nei;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.greggifiedemi.Config;

import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.config.SidebarSubpanels;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;

/**
 * NEI's history panel (ItemHistoryPanel): the items whose recipes or uses were looked at, in rows under the item list.
 * EMI keeps exactly that list ("lookup history") and can show it as a subpanel under a sidebar, so the panel is EMI's
 * subpanel added under the sidebar holding the index. It is added in memory only and taken out while EMI writes its
 * config, so {@code emi.css} is never changed; a pack or player that already shows the lookup history somewhere keeps
 * their layout.
 */
public final class HistoryPanel {

    private static @Nullable SidebarSubpanels.Subpanel added;
    private static @Nullable SidebarSubpanels addedTo;

    private HistoryPanel() {}

    /** Adds or removes the subpanel to match the config. Cheap when nothing changes; called every client tick. */
    public static void sync() {
        final boolean wanted = Config.historyPanel();

        if (added != null) {
            final boolean stale = !wanted || addedTo == null || !addedTo.subpanels.contains(added)
                    || added.rows != Config.historyRows() || addedTo != indexSubpanels();

            if (!stale) {
                return;
            }

            remove();
        }

        if (!wanted || shownElsewhere()) {
            return;
        }

        final SidebarSubpanels subpanels = indexSubpanels();

        if (subpanels != null) {
            added = new SidebarSubpanels.Subpanel(SidebarType.LOOKUP_HISTORY, Config.historyRows());
            addedTo = subpanels;
            subpanels.subpanels.add(added);
            EmiScreenManager.forceRecalculate();
        }
    }

    private static void remove() {
        if (added != null && addedTo != null && addedTo.subpanels.remove(added)) {
            EmiScreenManager.forceRecalculate();
        }

        added = null;
        addedTo = null;
    }

    /** Called before EMI writes its config: the file must not get our subpanel. Returns whether it was removed. */
    public static boolean detachForSave() {
        if (added != null && addedTo != null) {
            return addedTo.subpanels.remove(added);
        }
        return false;
    }

    /** Called after EMI wrote its config. */
    public static void reattachAfterSave(boolean wasAttached) {
        if (wasAttached && added != null && addedTo != null && !addedTo.subpanels.contains(added)) {
            addedTo.subpanels.add(added);
        }
    }

    private static @Nullable SidebarSubpanels indexSubpanels() {
        if (hasPage(EmiConfig.rightSidebarPages, SidebarType.INDEX)) {
            return EmiConfig.rightSidebarSubpanels;
        }
        if (hasPage(EmiConfig.leftSidebarPages, SidebarType.INDEX)) {
            return EmiConfig.leftSidebarSubpanels;
        }
        if (hasPage(EmiConfig.bottomSidebarPages, SidebarType.INDEX)) {
            return EmiConfig.bottomSidebarSubpanels;
        }
        if (hasPage(EmiConfig.topSidebarPages, SidebarType.INDEX)) {
            return EmiConfig.topSidebarSubpanels;
        }
        return null;
    }

    private static boolean shownElsewhere() {
        for (SidebarPages pages : List.of(EmiConfig.leftSidebarPages, EmiConfig.rightSidebarPages,
                EmiConfig.topSidebarPages, EmiConfig.bottomSidebarPages)) {
            if (hasPage(pages, SidebarType.LOOKUP_HISTORY)) {
                return true;
            }
        }

        for (SidebarSubpanels subpanels : List.of(EmiConfig.leftSidebarSubpanels, EmiConfig.rightSidebarSubpanels,
                EmiConfig.topSidebarSubpanels, EmiConfig.bottomSidebarSubpanels)) {
            for (SidebarSubpanels.Subpanel subpanel : subpanels.subpanels) {
                if (subpanel != added && subpanel.type == SidebarType.LOOKUP_HISTORY) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean hasPage(SidebarPages pages, SidebarType type) {
        for (SidebarPages.SidebarPage page : pages.pages) {
            if (page.type == type) {
                return true;
            }
        }
        return false;
    }

}
