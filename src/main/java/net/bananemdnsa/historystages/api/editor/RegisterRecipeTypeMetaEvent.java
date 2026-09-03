package net.bananemdnsa.historystages.api.editor;

import net.bananemdnsa.historystages.client.editor.recipe.RecipeTypeMetas;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once so other mods can say what their recipe types look like in the editor.
 *
 * <p>Without this a mod's recipes still appear in the picker and still lock — they just draw
 * with no workstation icon, a neutral accent, and their registry id as a name. Registering here
 * is cosmetic, not functional.
 *
 * <p>This is the only moment registration is legal: when dispatch ends the registry freezes, and
 * everything that draws from it may then assume the table never changes again.
 *
 * <p><strong>Fired during client setup</strong>, unlike the registration events that gate and
 * store — those run in common setup. Everything here is UI, so nothing off the client has any use
 * for it, and a listener registered on a dedicated server will never be called.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterRecipeTypeMetaEvent.class, event -> event.register(
 *         new RecipeTypeMeta("create:mixing", "create:basin", 0xFF3399FF,
 *                 "recipe_type.create.mixing")));
 * }</pre>
 */
public class RegisterRecipeTypeMetaEvent extends Event implements IModBusEvent {

    public void register(RecipeTypeMeta meta) {
        RecipeTypeMetas.register(meta);
    }
}
