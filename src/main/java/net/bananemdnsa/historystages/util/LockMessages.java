package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Resolves a lock-message Component from a config string override with translation-key fallback.
 * Empty config value → Component.translatable(key); otherwise → Component.literal(value) with & → § conversion.
 */
public final class LockMessages {

    private LockMessages() {}

    public static MutableComponent resolve(ForgeConfigSpec.ConfigValue<String> override, String translationKey) {
        String raw = override.get();
        if (raw == null || raw.isEmpty()) {
            return Component.translatable(translationKey);
        }
        return Component.literal(raw.replace('&', '§'));
    }

    public static MutableComponent dimensionUnknown() {
        return resolve(Config.COMMON.msgDimensionUnknown, "message.historystages.dimension_unknown");
    }

    public static MutableComponent mobUnknown() {
        return resolve(Config.COMMON.msgMobUnknown, "message.historystages.mob_unknown");
    }

    public static MutableComponent itemLocked() {
        return resolve(Config.COMMON.msgItemLocked, "message.historystages.item_locked");
    }

    public static MutableComponent blockLocked() {
        return resolve(Config.COMMON.msgBlockLocked, "message.historystages.block_locked");
    }

    public static MutableComponent entityItemLocked() {
        return resolve(Config.COMMON.msgEntityItemLocked, "message.historystages.entity_item_locked");
    }

    public static MutableComponent enchantmentLocked() {
        return resolve(Config.COMMON.msgEnchantmentLocked, "message.historystages.enchantment_locked");
    }
}
