package net.bananemdnsa.historystages.mixin;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps locked recipes out of the vanilla recipe book.
 *
 * <p>The book is built from {@code RecipeManager.getOrderedRecipes()}, which the recipe filter in
 * {@code RecipeManagerMixin} deliberately does not touch — that filter sits on {@code getRecipeFor}
 * and {@code getRecipesFor}, the paths a station uses to produce a result. So a locked recipe was
 * never craftable, but it sat in the book looking as if it were.
 *
 * <p><strong>Client-side, and that is the whole design.</strong> The server sends one recipe packet
 * to every player; filtering it there would take an individually gated recipe away from everyone,
 * and take it away for good — the client would have no record of it to bring back on unlock.
 * Filtering the list as the book is assembled affects the book and nothing else.
 *
 * <p>The verdict comes from {@link RecipeHandler}, the single place that judges, wrapped in a
 * {@link RecipeCraftContext} for the local player. On the client that player is the only one there
 * is, which is what makes the individual half meaningful here: the book belongs to one person.
 *
 * <p>Rebuilt whenever stages change — see {@code ClientRecipeBookRefresh}.
 */
@Mixin(ClientRecipeBook.class)
public class ClientRecipeBookMixin {

    @ModifyVariable(method = "setupCollections", at = @At("HEAD"), argsOnly = true, index = 1)
    private Iterable<RecipeHolder<?>> historystages$dropLockedRecipes(Iterable<RecipeHolder<?>> recipes) {
        // This runs inside the client's recipe packet handler. Anything thrown here takes the
        // whole recipe update with it, so every failure hands the untouched list back instead —
        // a book showing too much beats a client that cannot process recipes. The config read is
        // inside the guard on purpose: it is the part that runs before the file is loaded.
        try {
            if (recipes == null || !Config.VISUAL.hideLockedRecipesInBook.get()) return recipes;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return recipes;

            return RecipeCraftContext.with(mc.player.getUUID(), () -> {
                List<RecipeHolder<?>> visible = new ArrayList<>();
                for (RecipeHolder<?> holder : recipes) {
                    if (holder == null) continue;
                    // Both halves of recipe gating: the id on a stage, and an output item whose
                    // lock_actions include "recipe".
                    if (RecipeHandler.isRecipeIdLocked(holder.id(), true)) continue;
                    if (RecipeHandler.isOutputLocked(holder, true)) continue;
                    visible.add(holder);
                }
                return visible;
            });
        } catch (Throwable failure) {
            net.bananemdnsa.historystages.util.DebugLogger.warn("Recipe Book",
                    "could not filter the recipe book, showing it unfiltered: " + failure);
            return recipes;
        }
    }
}
