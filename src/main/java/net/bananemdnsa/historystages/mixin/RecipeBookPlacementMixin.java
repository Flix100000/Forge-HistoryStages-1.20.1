package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Says why nothing happened when a player clicks a locked recipe in the recipe book.
 *
 * <p>The click fills the grid, and the grid then produces no result — not exploitable, only
 * silent. Clicking is a deliberate act, unlike dragging items around, so it earns a line; the
 * crafting preview deliberately gets none, because the grid changes on every item moved and a
 * message per change would be constant noise.
 *
 * <p>Does not cancel the placement. The grid filling up is vanilla's business, and the gate that
 * matters already sits at the resolution.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class RecipeBookPlacementMixin {

    @Shadow @Final public ServerPlayer player;

    private static final String FEEDBACK_CATEGORY = "recipe_book";

    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"))
    private void historystages$tellThemWhyNothingHappened(ServerboundPlaceRecipePacket packet,
                                                          CallbackInfo ci) {
        // The same narrow window a station opens: this question is about this player and this
        // click, and nothing else.
        boolean locked = RecipeCraftContext.with(this.player.getUUID(),
                () -> RecipeHandler.isRecipeIdLocked(packet.getRecipe(), false));
        if (!locked) return;

        LockFeedback.sendActionbar(this.player, FEEDBACK_CATEGORY, LockMessages.recipeLocked());
    }
}
