package net.bananemdnsa.historystages.data.auto.conditions;

import com.google.gson.JsonObject;

/**
 * A trigger whose type nothing in this build understands, kept exactly as it was read.
 *
 * <p>Before this existed, the loader dropped such a trigger on the floor — so a maintainer who
 * edited a stage while the addon that owns the trigger was not installed destroyed it without
 * ever being told. Carrying the raw object through means the trigger comes back the moment its
 * mod does.
 *
 * <p>It never fires, which is the only honest behaviour: nothing here knows what would satisfy
 * it. Its signature still has to be stable, because progress is stored against it.
 *
 * @param rawType the {@code type} discriminator as it appeared in the file
 * @param raw     the whole original object, written back untouched
 */
public record UnknownTrigger(String rawType, JsonObject raw) implements TriggerCondition {

    @Override
    public String type() {
        return rawType;
    }

    @Override
    public long signature() {
        // Derived from the raw JSON so it stays put across loads, and so it cannot collide with
        // the real trigger's signature once the owning mod is back and parses it properly.
        return defaultSignature(raw == null ? "" : raw.toString());
    }
}
