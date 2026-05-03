package net.minecraftforge.fml;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.List;

public final class ModList {
    private static final ModList INSTANCE = new ModList();

    private ModList() {
    }

    public static ModList get() {
        return INSTANCE;
    }

    public List<IModInfo> getMods() {
        return FabricLoader.getInstance().getAllMods().stream().<IModInfo>map(FabricModInfo::new).toList();
    }

    private record FabricModInfo(ModContainer container) implements IModInfo {
        @Override
        public String getModId() {
            return container.getMetadata().getId();
        }

        @Override
        public String getDisplayName() {
            return container.getMetadata().getName();
        }
    }
}
