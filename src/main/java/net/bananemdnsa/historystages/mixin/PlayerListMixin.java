package net.bananemdnsa.historystages.mixin;

import java.util.Collection;

import net.bananemdnsa.historystages.data.lock.UngatedRecipes;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps the locked recipes in the packet every client is sent.
 *
 * <p>{@code RecipeManagerMixin} takes them out of {@code getRecipes()} so that a machine reading
 * the whole list cannot make them, and on this version that same method is what vanilla puts in
 * {@code ClientboundUpdateRecipesPacket} — on join and again after every datapack reload. That
 * list becomes the client's recipe manager, so cutting it short there would do three things we do
 * not want: JEI and EMI would stop drawing locked recipes with a lock on them, the editor's recipe
 * picker could no longer find a recipe that is already locked — meaning nobody could ever unlock
 * it again — and the vanilla recipe book would lose whatever went missing until a rejoin.
 *
 * <p>So both send sites are pointed back at the full list. The gate is a server-side rule about
 * what may be crafted, not about what the player may look at.
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {

    /** The packet a joining player is sent. */
    @Redirect(
            method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipes()Ljava/util/Collection;"),
            remap = true)
    private Collection<Recipe<?>> sendEveryRecipeOnJoin(RecipeManager manager) {
        return UngatedRecipes.of(manager);
    }

    /** The packet every player is sent after a datapack reload. */
    @Redirect(
            method = "reloadResources()V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipes()Ljava/util/Collection;"),
            remap = true)
    private Collection<Recipe<?>> sendEveryRecipeOnReload(RecipeManager manager) {
        return UngatedRecipes.of(manager);
    }
}
