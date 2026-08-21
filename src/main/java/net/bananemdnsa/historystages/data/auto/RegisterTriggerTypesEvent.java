package net.bananemdnsa.historystages.data.auto;

import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once so another mod can add its own auto-trigger type.
 *
 * <p>A trigger says when a stage unlocks by itself. The built-in ones cover entering a biome,
 * crafting an item, killing something and so on; an addon that owns some other notion of progress
 * adds it here and fires it through the same path.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterTriggerTypesEvent.class, event ->
 *         event.register("mymod:relic_found", RelicFoundTrigger.class, StageScope.GLOBAL));
 * }</pre>
 */
public class RegisterTriggerTypesEvent extends Event implements IModBusEvent {

    public void register(String type, Class<? extends TriggerCondition> conditionClass) {
        TriggerTypes.register(type, conditionClass);
    }

    /**
     * Registers a trigger type restricted to the given scopes. See
     * {@link TriggerTypes#register(String, Class, StageScope...)} for what that means and why
     * both scopes apply by default.
     */
    public void register(String type, Class<? extends TriggerCondition> conditionClass,
                         StageScope... scopes) {
        TriggerTypes.register(type, conditionClass, scopes);
    }
}
