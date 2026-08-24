package net.bananemdnsa.historystages.demo;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

/**
 * The stand-in addon's second auto-trigger: "this many relics have been found".
 *
 * <p>Exists to exercise the authoring escape hatch. It carries a number and no id at all, so the
 * free tier — a searchable list of ids — cannot author it: there is nothing to pick. Its editor
 * supplies a screen instead.
 *
 * @param count how many relics
 */
public record RelicHoardTrigger(int count) implements TriggerCondition {

    public static final String TYPE = "hsdemo:relic_hoard";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public long signature() {
        return defaultSignature(String.valueOf(count));
    }
}
