package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Resolves a lock-message Component from a config string override with translation-key fallback.
 * Empty config value → Component.translatable(key); otherwise → Component.literal(value) with & → § conversion.
 */
public final class LockMessages {

    private LockMessages() {}

    public static MutableComponent resolve(ModConfigSpec.ConfigValue<String> override, String translationKey) {
        String raw = override.get();
        if (raw == null || raw.isEmpty()) {
            return Component.translatable(translationKey);
        }
        return Component.literal(raw.replace('&', '§'));
    }

    public static MutableComponent dimensionUnknown() {
        return resolve(Config.VISUAL.msgDimensionUnknown, "message.historystages.dimension_unknown");
    }

    public static MutableComponent mobUnknown() {
        return resolve(Config.VISUAL.msgMobUnknown, "message.historystages.mob_unknown");
    }

    public static MutableComponent itemLocked() {
        return resolve(Config.VISUAL.msgItemLocked, "message.historystages.item_locked");
    }

    /**
     * Its own message rather than the item one, because the two say different things. Holding a
     * gated bucket is "you do not know this item"; scooping from a gated pool is "you do not know
     * what is in there" — the bucket may be perfectly familiar.
     */
    public static MutableComponent fluidLocked() {
        return resolve(Config.VISUAL.msgFluidLocked, "message.historystages.fluid_locked");
    }

    /**
     * Shown only when a merchant's whole list was filtered away.
     *
     * <p>A merchant showing two offers instead of three says nothing, on purpose — not noticing
     * is the point of the category. A merchant showing <em>nothing</em> looks broken, and that is
     * the one case worth explaining.
     */
    public static MutableComponent tradeLocked() {
        return resolve(Config.VISUAL.msgTradeLocked, "message.historystages.trade_locked");
    }

    public static MutableComponent blockLocked() {
        return resolve(Config.VISUAL.msgBlockLocked, "message.historystages.block_locked");
    }

    public static MutableComponent entityItemLocked() {
        return resolve(Config.VISUAL.msgEntityItemLocked, "message.historystages.entity_item_locked");
    }

    public static MutableComponent enchantmentLocked() {
        return resolve(Config.VISUAL.msgEnchantmentLocked, "message.historystages.enchantment_locked");
    }

    public static MutableComponent recipeLocked() {
        return resolve(Config.VISUAL.msgRecipeLocked, "message.historystages.recipe_locked");
    }
}
