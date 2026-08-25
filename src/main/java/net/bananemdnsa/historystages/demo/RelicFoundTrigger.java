package net.bananemdnsa.historystages.demo;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

/**
 * The stand-in addon's own auto-trigger: "this relic was found".
 *
 * <p>Shows what a trigger type from another mod has to provide — a stable type string and a
 * signature derived only from its values, because player progress is stored against that hash
 * and must survive the stage being edited.
 *
 * <p>Registered global-only ({@link DemoAddonCategory#onRegisterTriggerTypes}) purely to exercise
 * the scope-narrowing rule at least once — not because finding a relic could not sensibly unlock
 * an individual stage too. Do not copy the narrowing itself as a modelling example.
 *
 * @param relic which relic, by the same ids the demo category offers
 */
public record RelicFoundTrigger(String relic) implements TriggerCondition {

    @Override
    public String type() {
        return DemoAddonCategory.TRIGGER_TYPE;
    }

    @Override
    public long signature() {
        return defaultSignature(relic);
    }
}
