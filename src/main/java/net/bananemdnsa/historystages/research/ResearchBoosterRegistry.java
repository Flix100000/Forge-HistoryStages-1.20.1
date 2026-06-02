package net.bananemdnsa.historystages.research;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Maps block IDs to their {@link ResearchBooster} effect, built from the
 * common config's {@code researchBoosters} list.
 */
public final class ResearchBoosterRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<ResourceLocation, ResearchBooster> BOOSTERS = Collections.emptyMap();

    private ResearchBoosterRegistry() {}

    /** Replace the registry from a list of "block_id, speed%, cost%" entries. */
    public static void rebuildFromConfig(List<? extends String> entries) {
        Map<ResourceLocation, ResearchBooster> map = new HashMap<>();
        if (entries == null) {
            BOOSTERS = Map.copyOf(map);
            return;
        }

        for (String raw : entries) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split(",");
            // Accepted formats:
            //   3 cols (legacy): block_id, speed%, cost%
            //   5 cols (current): block_id, speed%, cost%, tier, mode
            if (parts.length != 3 && parts.length != 5) {
                LOGGER.warn(
                        "[ResearchBooster] Skipping malformed entry '{}': expected 'block_id, speed%, cost%' or 'block_id, speed%, cost%, tier, mode'",
                        line);
                continue;
            }

            ResourceLocation blockId = ResourceLocation.tryParse(parts[0].trim());
            if (blockId == null || !ForgeRegistries.BLOCKS.containsKey(blockId)) {
                LOGGER.warn(
                        "[ResearchBooster] Skipping entry '{}': unknown block id", line);
                continue;
            }

            int speed = clampPercent(parts[1].trim(), line, "speed");
            int cost = clampPercent(parts[2].trim(), line, "cost");

            if (speed == 0 && cost == 0) {
                LOGGER.warn(
                        "[ResearchBooster] Skipping entry '{}': both percentages are 0 (no effect)",
                        line);
                continue;
            }

            int tier = 1;
            TierMode mode = TierMode.MIN;
            if (parts.length == 5) {
                tier = clampTier(parts[3].trim(), line);
                mode = TierMode.parse(parts[4].trim(), TierMode.MIN);
            }

            if (map.containsKey(blockId)) {
                LOGGER.warn(
                        "[ResearchBooster] Duplicate block id '{}' — using last definition", blockId);
            }

            map.put(blockId, new ResearchBooster(speed / 100.0, cost / 100.0, tier, mode));
        }

        BOOSTERS = Map.copyOf(map);
        LOGGER.info("[ResearchBooster] Loaded {} booster entries", BOOSTERS.size());
    }

    private static int clampTier(String token, String line) {
        int value;
        try {
            value = Integer.parseInt(token);
        } catch (NumberFormatException e) {
            LOGGER.warn(
                    "[ResearchBooster] Entry '{}': tier '{}' is not a number, treating as 1",
                    line, token);
            return 1;
        }
        if (value < 1) {
            LOGGER.warn("[ResearchBooster] Entry '{}': tier {} < 1, clamping to 1", line, value);
            return 1;
        }
        if (value > 4) {
            LOGGER.warn("[ResearchBooster] Entry '{}': tier {} > 4, clamping to 4", line, value);
            return 4;
        }
        return value;
    }

    private static int clampPercent(String token, String line, String which) {
        int value;
        try {
            value = Integer.parseInt(token);
        } catch (NumberFormatException e) {
            LOGGER.warn(
                    "[ResearchBooster] Entry '{}': {} '{}' is not a number, treating as 0",
                    line, which, token);
            return 0;
        }
        int max = (int) (ResearchBooster.MAX_REDUCTION * 100);
        if (value < 0) {
            LOGGER.warn(
                    "[ResearchBooster] Entry '{}': {} {} is negative, clamping to 0", line, which, value);
            return 0;
        }
        if (value > max) {
            LOGGER.warn(
                    "[ResearchBooster] Entry '{}': {} {} exceeds cap, clamping to {}",
                    line, which, value, max);
            return max;
        }
        return value;
    }

    public static Map<ResourceLocation, ResearchBooster> all() {
        return BOOSTERS;
    }

    public static Optional<ResearchBooster> get(ResourceLocation blockId) {
        if (blockId == null) return Optional.empty();
        return Optional.ofNullable(BOOSTERS.get(blockId));
    }

    /** Lookup by block instance — convenience for the pedestal tick + tooltip event. */
    public static Optional<ResearchBooster> lookup(Block block) {
        if (block == null) return Optional.empty();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        return get(id);
    }

    public static Optional<ResearchBooster> forBlockState(BlockState state) {
        if (state == null || state.isAir()) return Optional.empty();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return get(id);
    }

    /**
     * Walks the registry, resolves each block id to a non-empty {@link ItemStack}
     * and invokes the consumer. Shared by JEI / EMI registration so the
     * lookup loop doesn't have to be re-written everywhere.
     */
    public static void forEachStack(BiConsumer<ItemStack, ResearchBooster> consumer) {
        BOOSTERS.forEach((blockId, booster) -> {
            Block block = ForgeRegistries.BLOCKS.getValue(blockId);
            if (block == null) return;
            ItemStack stack = new ItemStack(block);
            if (stack.isEmpty()) return;
            consumer.accept(stack, booster);
        });
    }
}
