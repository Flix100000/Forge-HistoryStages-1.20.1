package net.bananemdnsa.historystages.spellengine;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.mojang.logging.LogUtils;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.LockFeedback;
import net.bananemdnsa.historystages.util.LockGate;
import net.bananemdnsa.historystages.util.LockMessages;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Blocks Spell Engine spell casting with a stage-locked item. Spell Engine's caster items
 * (wands/staves) drive casting through their own input + network packet, bypassing every vanilla
 * interaction event, so the generic event handlers cannot see them. This adapter hooks Spell
 * Engine's own casting-attempt event ({@code SpellEvents.CASTING_ATTEMPT.PRE}) instead. Loaded
 * only when {@code spell_engine} is present.
 *
 * <p>Spell Engine is a Fabric-first mod with no stable Forge 1.20.1 API artifact to compile
 * against, so the whole integration is done via reflection — no compile-time dependency on any
 * Spell Engine type. Every reflective hop is guarded; on any mismatch the adapter fails open
 * (lets the cast proceed) and logs, so a Spell Engine API change can never crash a cast.</p>
 */
public final class SpellEngineCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SpellEngineCompat() {}

    /**
     * Reflectively registers a listener on {@code SpellEvents.CASTING_ATTEMPT.PRE}. Throws on any
     * failure so the caller ({@link LockInterceptors}) can log and disable the adapter.
     */
    public static void register() throws Exception {
        // SpellEvents.CASTING_ATTEMPT.PRE.register(listener)
        Class<?> spellEventsClass = Class.forName("net.spell_engine.api.spell.event.SpellEvents");
        Object castingAttempt = spellEventsClass.getField("CASTING_ATTEMPT").get(null);
        Object preEvent = castingAttempt.getClass().getField("PRE").get(castingAttempt);

        Method registerMethod = null;
        for (Method m : preEvent.getClass().getMethods()) {
            if (m.getName().equals("register") && m.getParameterCount() == 1) {
                registerMethod = m;
                break;
            }
        }
        if (registerMethod == null) {
            throw new NoSuchMethodException("SpellEvents.CASTING_ATTEMPT.PRE#register(listener)");
        }
        Class<?> listenerType = registerMethod.getParameterTypes()[0];

        // SpellCast.Attempt.none() — a non-null result aborts the cast.
        Class<?> attemptClass = Class.forName("net.spell_engine.internals.casting.SpellCast$Attempt");
        Method noneMethod = attemptClass.getMethod("none");

        Object listener = Proxy.newProxyInstance(
                listenerType.getClassLoader(),
                new Class<?>[] { listenerType },
                new CastingAttemptHandler(noneMethod));
        registerMethod.invoke(preEvent, listener);
    }

    /**
     * Handles the single functional method of Spell Engine's casting-attempt listener.
     * Returning {@code null} lets the cast proceed; returning {@code SpellCast.Attempt.none()}
     * aborts it. Any reflection error fails open (returns {@code null}).
     */
    private static final class CastingAttemptHandler implements InvocationHandler {

        private final Method noneMethod;
        private boolean loggedError = false;

        CastingAttemptHandler(Method noneMethod) {
            this.noneMethod = noneMethod;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] methodArgs) {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "equals":
                        return methodArgs != null && proxy == methodArgs[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "HistoryStages$SpellEngineCastingAttemptListener";
                    default:
                        return null;
                }
            }

            if (methodArgs == null || methodArgs.length != 1 || methodArgs[0] == null) {
                return null; // unexpected shape: let the cast proceed
            }
            if (!Config.COMMON.lockItemUsage.get() && !Config.COMMON.individualLockItemUsage.get()) {
                return null;
            }

            try {
                Object args = methodArgs[0];
                Object casterObj = args.getClass().getMethod("caster").invoke(args);
                Object stackObj = args.getClass().getMethod("itemStack").invoke(args);
                if (!(casterObj instanceof Player caster) || !(stackObj instanceof ItemStack stack)) {
                    return null;
                }
                if (LockGate.isActionLocked(stack, caster, "use",
                        Config.COMMON.lockItemUsage, Config.COMMON.individualLockItemUsage)) {
                    if (caster instanceof ServerPlayer sp) {
                        ResourceLocation itemRL = ForgeRegistries.ITEMS.getKey(stack.getItem());
                        DebugLogger.runtimeThrottled("Item Use Lock", "cast_" + sp.getUUID() + "_" + itemRL,
                                "<" + sp.getName().getString() + "> Spell cast with '" + itemRL + "' blocked [action: use]");
                        LockFeedback.sendActionbar(sp, "item", LockMessages.itemLocked());
                    }
                    return noneMethod.invoke(null); // non-null result aborts the cast
                }
                return null;
            } catch (Throwable t) {
                if (!loggedError) {
                    loggedError = true;
                    LOGGER.error("[HistoryStages] Spell Engine casting-attempt hook failed; "
                            + "letting casts proceed. Spell Engine API may have changed.", t);
                }
                return null; // fail open
            }
        }
    }
}
