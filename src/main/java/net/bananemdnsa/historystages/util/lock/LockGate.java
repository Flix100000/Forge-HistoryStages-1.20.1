package net.bananemdnsa.historystages.util.lock;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Wraps the recurring "check global stage lock, then individual stage lock" pattern that
 * appeared inline in every event handler and lock mixin. Each method takes the two config
 * toggles directly so callers don't repeat the boilerplate guards.
 *
 * Side handling: {@link #isActionLocked} picks the client- or server-side StageLockHelper
 * variant based on the player's level. The server-only entry points are explicit.
 */
public final class LockGate {

    private LockGate() {}

    /**
     * Action-keyed lock check that picks the correct side automatically.
     * Use this from events that fire on both sides (PlayerInteract*, etc.).
     */
    public static boolean isActionLocked(ItemStack stack,
                                         Player player,
                                         String action,
                                         ForgeConfigSpec.BooleanValue globalEnabled,
                                         ForgeConfigSpec.BooleanValue individualEnabled) {
        if (stack.isEmpty()) return false;
        boolean isClient = player.level().isClientSide();
        if (globalEnabled.get()) {
            boolean locked = isClient
                    ? StageLockHelper.isActionLockedForClient(stack, action)
                    : StageLockHelper.isActionLockedForPlayer(stack, player.getUUID(), action);
            if (locked) return true;
        }
        if (individualEnabled.get()) {
            boolean locked = isClient
                    ? StageLockHelper.isActionLockedByIndividualStageClient(stack, action)
                    : StageLockHelper.isActionLockedByIndividualStage(stack, player.getUUID(), action);
            if (locked) return true;
        }
        return false;
    }

    /**
     * Server-only action-keyed check. Use from server-bus events
     * (BlockEvent.BreakEvent, LivingEquipmentChangeEvent, …).
     */
    public static boolean isActionLockedServer(ItemStack stack,
                                               ServerPlayer player,
                                               String action,
                                               ForgeConfigSpec.BooleanValue globalEnabled,
                                               ForgeConfigSpec.BooleanValue individualEnabled) {
        if (stack.isEmpty()) return false;
        if (globalEnabled.get()
                && StageLockHelper.isActionLockedForPlayer(stack, player.getUUID(), action)) return true;
        if (individualEnabled.get()
                && StageLockHelper.isActionLockedByIndividualStage(stack, player.getUUID(), action)) return true;
        return false;
    }

    /**
     * Server-only item-level (no action key) check. Used by handlers that lock the item
     * wholesale regardless of action — e.g. the anvil/enchantment path.
     */
    public static boolean isItemLockedServer(ItemStack stack,
                                             ServerPlayer player,
                                             ForgeConfigSpec.BooleanValue globalEnabled,
                                             ForgeConfigSpec.BooleanValue individualEnabled) {
        if (stack.isEmpty()) return false;
        if (globalEnabled.get()
                && StageLockHelper.isItemLockedForPlayer(stack, player)) return true;
        if (individualEnabled.get()
                && StageLockHelper.isItemLockedByIndividualStage(stack, player.getUUID())) return true;
        return false;
    }
}
