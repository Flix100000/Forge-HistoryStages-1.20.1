package net.astr0.historystages.api;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class HistoryStagesAPI {

    private static IStageManager _stageManager;

    /**
     * Retrieve a reference to the active StageManager.
     * @return The active {@link IStageManager} object
     */
    public static IStageManager getStageManager() {
        return _stageManager;
    }

    /**
     * Set the StageManager instance for API usage. For internal use only!
     * @param stageManager An initialised instance of a stage manager object
     */
    public static void _setStageManager(IStageManager stageManager) {
        _stageManager = stageManager; //TODO(Astr0): Move this function behind a package-private barrier
                                      //             so that other mods can't over-write our manager
    }


    // Built-in categories using the highly optimized FastUtil maps
    public static final LockCategory<Item> ITEMS = register(
            new LockCategory<>("item", new Reference2ObjectOpenHashMap<>(300))
    );
    public static final LockCategory<Block> BLOCKS = register(
            new LockCategory<>("block", new Reference2ObjectOpenHashMap<>(300))
    );
    public static final LockCategory<ResourceLocation> DIMENSIONS = register(
            new LockCategory<>("dimension", new Object2ObjectOpenHashMap<>(20))
    );
    public static final LockCategory<EnchantmentKey> ENCHANTMENTS = register(
            new LockCategory<>("enchantment", new Object2ObjectOpenHashMap<>(100))
    );





    // Internal method. For use in testing bridge between main mod and API
    @Deprecated
    public static void _APIHelloWorld() {
        LogUtils.getLogger().info("[HistoryStages API] Hello World! from the History Stages API");
    }

    // The central registry of all lock types
    private static final Map<String, LockCategory<?>> CATEGORIES = new HashMap<>();

    // API Method for Addons
    public static <T> LockCategory<T> register(@Nonnull LockCategory<T> category) {
        assert CATEGORIES != null;
        CATEGORIES.put(category.getId(), category);
        return category;
    }

    // Used by the manager during baking
    public static Iterable<LockCategory<?>> getAllCategories() {
        return CATEGORIES.values();
    }
}
