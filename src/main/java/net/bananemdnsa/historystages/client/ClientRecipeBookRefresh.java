package net.bananemdnsa.historystages.client;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;

/**
 * Rebuilds the vanilla recipe book after the player's stages changed.
 *
 * <p>{@code ClientRecipeBookMixin} filters the book as it is assembled, and the book is only
 * assembled when the server sends a recipe update — on join, and on the recipe resync that a global
 * lock or unlock triggers. An <em>individual</em> unlock sends no such packet, so without this the
 * newly unlocked recipes would not show up until the player rejoined.
 *
 * <p>Cheap enough to call on every stage sync: it walks the recipe list once, which is the same
 * work the client already does whenever recipes arrive.
 */
public final class ClientRecipeBookRefresh {

    private ClientRecipeBookRefresh() {}

    /** No-op before the player is in a world. */
    public static void rebuild() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        try {
            ClientRecipeBook book = mc.player.getRecipeBook();
            book.setupCollections(
                    mc.level.getRecipeManager().getOrderedRecipes(),
                    mc.level.registryAccess());

            // Not optional. setupCollections builds new RecipeCollection objects and every one of
            // them starts out knowing none of the player's recipes, and RecipeBookComponent drops
            // a collection with no known recipes from the display entirely — so rebuilding without
            // this empties the book. Vanilla does the same two lines in
            // ClientPacketListener.handleAddOrRemoveRecipes; there is no other path that restores
            // it, which is also why the server must never send a recipe list without the book.
            book.getCollections().forEach(collection -> collection.updateKnownRecipes(book));
        } catch (Exception e) {
            // A book that failed to rebuild shows a stale list; it must not take the packet
            // handler down with it.
            net.bananemdnsa.historystages.util.DebugLogger.warn("Recipe Book",
                    "could not rebuild the recipe book after a stage change: " + e);
        }
    }
}
