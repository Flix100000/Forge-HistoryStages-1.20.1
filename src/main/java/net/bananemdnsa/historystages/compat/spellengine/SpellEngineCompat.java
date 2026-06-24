package net.bananemdnsa.historystages.compat.spellengine;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockGate;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.internals.casting.SpellCast;

/**
 * Blocks Spell Engine spell casting with a stage-locked item. Spell Engine's caster items
 * (wands/staves) drive casting through their own input + network packet, bypassing every vanilla
 * interaction event, so the generic event handlers cannot see them. This adapter hooks Spell
 * Engine's own casting-attempt event instead. Loaded only when {@code spell_engine} is present.
 */
public final class SpellEngineCompat {

    private SpellEngineCompat() {}

    public static void register() {
        SpellEvents.CASTING_ATTEMPT.PRE.register(args -> {
            if (!Config.COMMON.lockItemUsage.get() && !Config.COMMON.individualLockItemUsage.get()) {
                return null; // returning null lets the cast proceed
            }
            Player caster = args.caster();
            ItemStack stack = args.itemStack();
            if (LockGate.isActionLocked(stack, caster, "use",
                    Config.COMMON.lockItemUsage, Config.COMMON.individualLockItemUsage)) {
                if (caster instanceof ServerPlayer sp) {
                    ResourceLocation itemRL = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    DebugLogger.runtimeThrottled("Item Use Lock", "cast_" + sp.getUUID() + "_" + itemRL,
                            "<" + sp.getName().getString() + "> Spell cast with '" + itemRL + "' blocked [action: use]");
                    LockFeedback.sendActionbar(sp, "item", LockMessages.itemLocked());
                }
                return SpellCast.Attempt.none(); // non-null result aborts the cast
            }
            return null;
        });
    }
}
