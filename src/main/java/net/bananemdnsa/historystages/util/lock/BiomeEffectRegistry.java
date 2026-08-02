package net.bananemdnsa.historystages.util.lock;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses the common config's {@code biome_lock.effects} list into ready-to-apply
 * effect specs, built from "effect_id, seconds, amplifier" entries.
 */
public final class BiomeEffectRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAX_SECONDS = 3600;
    private static final int MAX_AMPLIFIER = 255;

    /** One configured effect. {@code durationTicks} is what gets handed to MobEffectInstance. */
    public record BiomeEffect(Holder<MobEffect> effect, int durationTicks, int amplifier) {}

    private static List<BiomeEffect> EFFECTS = Collections.emptyList();

    private BiomeEffectRegistry() {}

    public static void rebuildFromConfig(List<? extends String> entries) {
        if (entries == null) {
            EFFECTS = Collections.emptyList();
            return;
        }

        List<BiomeEffect> parsed = new ArrayList<>();
        for (String raw : entries) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split(",");
            if (parts.length != 3) {
                LOGGER.warn("[BiomeLock] Skipping malformed effect entry '{}': expected 'effect_id, seconds, amplifier'", line);
                continue;
            }

            ResourceLocation effectId = ResourceLocation.tryParse(parts[0].trim());
            if (effectId == null) {
                LOGGER.warn("[BiomeLock] Skipping effect entry '{}': '{}' is not a valid id", line, parts[0].trim());
                continue;
            }

            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT
                    .getHolder(effectId)
                    .map(h -> (Holder<MobEffect>) h)
                    .orElse(null);
            if (holder == null) {
                LOGGER.warn("[BiomeLock] Skipping effect entry '{}': unknown effect id", line);
                continue;
            }

            int seconds = clamp(parts[1].trim(), line, "seconds", 1, MAX_SECONDS, 30);
            int amplifier = clamp(parts[2].trim(), line, "amplifier", 0, MAX_AMPLIFIER, 0);

            parsed.add(new BiomeEffect(holder, seconds * 20, amplifier));
        }

        EFFECTS = List.copyOf(parsed);
        LOGGER.info("[BiomeLock] Loaded {} biome effect entries", EFFECTS.size());
    }

    private static int clamp(String token, String line, String which, int min, int max, int fallback) {
        int value;
        try {
            value = Integer.parseInt(token);
        } catch (NumberFormatException e) {
            LOGGER.warn("[BiomeLock] Entry '{}': {} '{}' is not a number, using {}", line, which, token, fallback);
            return fallback;
        }
        if (value < min) {
            LOGGER.warn("[BiomeLock] Entry '{}': {} {} below {}, clamping", line, which, value, min);
            return min;
        }
        if (value > max) {
            LOGGER.warn("[BiomeLock] Entry '{}': {} {} above {}, clamping", line, which, value, max);
            return max;
        }
        return value;
    }

    public static List<BiomeEffect> all() {
        return EFFECTS;
    }
}
