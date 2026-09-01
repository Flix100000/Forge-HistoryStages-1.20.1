package net.bananemdnsa.historystages.demo;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

/**
 * The stand-in addon's third auto-trigger: "a relic of this rarity turned up".
 *
 * <p>Exists to exercise the third way of authoring one. {@link RelicFoundTrigger} is picked from a
 * list of sixty ids and {@link RelicHoardTrigger} is a number typed into a dialog; this one is a
 * choice between three named things, which is neither — a searchable list over three rows is
 * ceremony, and there is nothing to count.
 *
 * @param rarity one of {@link #RARITIES}
 */
public record RelicRarityTrigger(String rarity) implements TriggerCondition {

    public static final String TYPE = "hsdemo:relic_rarity";

    /** The choices the editor offers. Kept here so the trigger and its editor cannot disagree. */
    public static final java.util.List<String> RARITIES = java.util.List.of("common", "rare", "legendary");

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public long signature() {
        return defaultSignature(rarity);
    }
}
