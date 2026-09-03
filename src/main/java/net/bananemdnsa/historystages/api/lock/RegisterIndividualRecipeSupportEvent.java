package net.bananemdnsa.historystages.api.lock;

import net.bananemdnsa.historystages.data.lock.IndividualRecipeSupport;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once so other mods can declare that one of their recipe types can be gated per player.
 *
 * <p><strong>Only register a type whose station has its own menu.</strong> An individual stage
 * gates a recipe by asking who is crafting, and that question only has an answer when a player is
 * standing at the station with its screen open. A furnace, a hopper or an autocrafter resolves
 * recipes with nobody there. Registering such a type makes the editor offer a per-player lock
 * that cannot work: the entry is written to the stage file and silently does nothing.
 *
 * <p>Fired during common setup — this one gates and stores, so a dedicated server needs it too.
 *
 * <p>This is the only moment registration is legal: when dispatch ends the registry freezes, and
 * both readers — the editor's picker and the load-time audit — may then treat it as constant.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterIndividualRecipeSupportEvent.class,
 *         event -> event.register("mymod:assembler"));
 * }</pre>
 */
public class RegisterIndividualRecipeSupportEvent extends Event implements IModBusEvent {

    /** @param recipeTypeId registry id of the recipe type, e.g. {@code mymod:assembler} */
    public void register(String recipeTypeId) {
        IndividualRecipeSupport.register(recipeTypeId);
    }
}
