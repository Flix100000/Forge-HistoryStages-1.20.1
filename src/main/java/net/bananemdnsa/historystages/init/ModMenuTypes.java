package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.screen.ResearchPedestalMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

public final class ModMenuTypes {
    public static MenuType<ResearchPedestalMenu> RESEARCH_MENU;

    private ModMenuTypes() {
    }

    public static void register() {
        RESEARCH_MENU = Registry.register(BuiltInRegistries.MENU, HistoryStages.id("research_pedestal"),
                new ExtendedScreenHandlerType<>(ResearchPedestalMenu::new, BlockPos.STREAM_CODEC));
    }
}
