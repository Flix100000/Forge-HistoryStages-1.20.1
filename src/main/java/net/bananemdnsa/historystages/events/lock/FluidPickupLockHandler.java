package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.CauldronFluidContent;
import org.jetbrains.annotations.Nullable;

/**
 * Denies taking a gated fluid out of the world.
 *
 * <p>The one fluid case {@link net.bananemdnsa.historystages.data.lock.engine.FluidContent}
 * cannot answer: an <em>empty</em> bucket carries no fluid, so the subject built from the held
 * stack says nothing about what the player is reaching for. That has to come from the world.
 *
 * <p><strong>Why its own raytrace.</strong> The crosshair clips with {@code Fluid.NONE}, so
 * looking at a lava lake reports the floor underneath, not the lava — {@code getPos()} on the
 * event is the wrong block for this question. A bucket does not use it either: it runs its own
 * {@code SOURCE_ONLY} trace, and this repeats that trace so both agree on which fluid is meant.
 *
 * <p><strong>Why two events.</strong> Filling from a pool never reaches
 * {@code RightClickBlock} — with the fluid clipped away the crosshair may hit nothing in range,
 * and the fill happens on the item-use path. A cauldron, on the other hand, <em>is</em> a solid
 * block and is answered on the block path. Neither event alone covers both.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class FluidPickupLockHandler {

    private static final String FEEDBACK_CATEGORY = "fluid";

    /** Filling from a cauldron, and any other container that is a real block. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Another handler already refused this interaction; nothing to add.
        if (event.getUseBlock() == TriState.FALSE) return;
        if (!refuses(event.getLevel(), event.getEntity(), event.getItemStack(), event.getPos())) {
            return;
        }
        // Never a hard cancel: that would also block placing an item against the surface, the
        // exact regression BlockLockHandler documents.
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
    }

    /** Filling from a pool, which is where a bucket's own raytrace does the work. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (refuses(event.getLevel(), event.getEntity(), event.getItemStack(), null)) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether this interaction is an attempt to take a gated fluid out of the world.
     *
     * <p>Answers identically on both sides on purpose. {@code PlayerInteractEvent} fires on the
     * client too, and a server-only refusal would let the client predict the fill and then snap
     * back — the fluid would visibly flicker.
     */
    private static boolean refuses(Level level, Player player, ItemStack held,
                                   @Nullable BlockPos clickedPos) {
        if (held.isEmpty()) return false;

        // Only a fluid container can take a fluid out of the world. Everything else is a plain
        // right-click and none of our business.
        if (held.getCapability(Capabilities.FluidHandler.ITEM) == null) return false;

        String fluidId = fluidWithinReach(level, player, clickedPos);
        if (fluidId == null) return false;

        boolean locked = level.isClientSide()
                ? StageLockHelper.isFluidActionLockedForClient(fluidId, "pickup")
                        || StageLockHelper.isFluidActionLockedByIndividualStageClient(fluidId, "pickup")
                : StageLockHelper.isFluidActionLockedForServer(fluidId, "pickup")
                        || StageLockHelper.isFluidActionLockedByIndividualStage(
                                fluidId, player.getUUID(), "pickup");
        if (!locked) return false;

        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            DebugLogger.runtimeThrottled("Fluid Lock", "pickup_" + sp.getUUID() + "_" + fluidId,
                    "<" + sp.getName().getString() + "> pickup of '" + fluidId
                            + "' blocked [action: pickup]");
            LockFeedback.sendActionbar(sp, FEEDBACK_CATEGORY, LockMessages.fluidLocked());
        }
        return true;
    }

    /**
     * The fluid the player is reaching for, or null.
     *
     * <p>Two sources, in the order they can occur: the block that was clicked, when there was one
     * — that is the cauldron case, and a cauldron is a solid block whose own fluid state is empty
     * — and otherwise the fluid the bucket's raytrace lands on.
     */
    @Nullable
    private static String fluidWithinReach(Level level, Player player,
                                           @Nullable BlockPos clickedPos) {
        if (clickedPos != null) {
            String cauldron = cauldronFluid(level, clickedPos);
            if (cauldron != null) return cauldron;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(
                eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;

        FluidState fluidState = level.getBlockState(hit.getBlockPos()).getFluidState();
        if (fluidState.isEmpty()) return cauldronFluid(level, hit.getBlockPos());

        return idOf(fluidState);
    }

    /** A filled cauldron holds a fluid without being one; NeoForge keeps the mapping. */
    @Nullable
    private static String cauldronFluid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        CauldronFluidContent content = CauldronFluidContent.getForBlock(state.getBlock());
        if (content == null || content.currentLevel(state) <= 0) return null;

        ResourceLocation id = BuiltInRegistries.FLUID.getKey(content.fluid);
        return id != null ? id.toString() : null;
    }

    @Nullable
    private static String idOf(FluidState fluidState) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluidState.getType());
        return id != null ? id.toString() : null;
    }
}
