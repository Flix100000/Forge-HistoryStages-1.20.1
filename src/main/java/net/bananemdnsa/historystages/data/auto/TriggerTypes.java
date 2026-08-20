package net.bananemdnsa.historystages.data.auto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.bananemdnsa.historystages.data.auto.conditions.AdvancementTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockBreakTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockPlaceTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DimensionTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.ItemTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.PlaytimeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StructureTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
import org.jetbrains.annotations.Nullable;

/**
 * Which auto-trigger types exist, by the discriminator they use in a stage file.
 *
 * <p>The loader used to know them from a hardcoded switch, which meant two things: another mod
 * could not add one, and a type the switch did not list was thrown away on read and gone on the
 * next save. Both are fixed by asking a registry instead — and by keeping what the registry does
 * not recognise, rather than dropping it.
 *
 * <p>Registration closes once the window is over, for the same reason the lock categories do:
 * everything downstream may then treat the set as fixed.
 */
public final class TriggerTypes {

    private static final Object LOCK = new Object();
    private static final Map<String, Class<? extends TriggerCondition>> BY_TYPE = new LinkedHashMap<>();
    private static final Map<String, Class<? extends TriggerCondition>> BUILT_IN = new LinkedHashMap<>();

    private static boolean frozen;

    static {
        BUILT_IN.put("biome", BiomeTrigger.class);
        BUILT_IN.put("structure", StructureTrigger.class);
        BUILT_IN.put("dimension", DimensionTrigger.class);
        BUILT_IN.put("item", ItemTrigger.class);
        BUILT_IN.put("entity", EntityTrigger.class);
        BUILT_IN.put("block_place", BlockPlaceTrigger.class);
        BUILT_IN.put("block_break", BlockBreakTrigger.class);
        BUILT_IN.put("advancement", AdvancementTrigger.class);
        BUILT_IN.put("playtime", PlaytimeTrigger.class);
        BY_TYPE.putAll(BUILT_IN);
    }

    private TriggerTypes() {}

    /**
     * Adds a trigger type. Legal only while {@link RegisterTriggerTypesEvent} is being dispatched.
     *
     * <p>The type string ends up in stage files and in the identity hash progress is stored
     * against, so it has to be namespaced and it can never change afterwards.
     *
     * @param type            the discriminator, e.g. {@code "mymod:relic_found"}
     * @param conditionClass  a Gson-deserialisable class implementing {@link TriggerCondition}
     */
    public static void register(String type, Class<? extends TriggerCondition> conditionClass) {
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException("Trigger type '" + type
                        + "' registered after the window closed.");
            }
            if (type == null || type.indexOf(':') <= 0) {
                throw new IllegalArgumentException("Trigger type '" + type
                        + "' needs a namespace; the unnamespaced ones are the built-ins.");
            }
            if (BY_TYPE.containsKey(type)) {
                throw new IllegalArgumentException("Trigger type '" + type + "' is already taken.");
            }
            BY_TYPE.put(type, conditionClass);
        }
    }

    public static void freeze() {
        synchronized (LOCK) {
            frozen = true;
        }
    }

    public static boolean isFrozen() {
        synchronized (LOCK) {
            return frozen;
        }
    }

    /** Null when nothing understands this type — the caller keeps the raw object instead. */
    @Nullable
    public static Class<? extends TriggerCondition> classFor(String type) {
        synchronized (LOCK) {
            return BY_TYPE.get(type);
        }
    }

    /** Every known type string, built-ins first. */
    public static List<String> allTypes() {
        synchronized (LOCK) {
            return List.copyOf(BY_TYPE.keySet());
        }
    }

    /** Test-only: drops addon registrations and reopens the window. */
    public static void resetForTesting() {
        synchronized (LOCK) {
            BY_TYPE.clear();
            BY_TYPE.putAll(BUILT_IN);
            frozen = false;
        }
    }
}
