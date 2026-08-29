package net.bananemdnsa.historystages.mixin.bettercombat;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.lock.LockGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only: suppresses the vanilla attack arm-swing when the held weapon is stage-locked, so a
 * locked weapon produces no swing animation on attack. Better Combat's own upswing animation is
 * handled by {@link WeaponRegistryMixin}; this covers the plain vanilla swing that remains once
 * Better Combat treats the item as a non-weapon (or when Better Combat is not installed).
 *
 * <p>Only cancels when the player is NOT targeting a block, so block breaking — governed separately
 * by the {@code break} lock — is unaffected.</p>
 */
@Mixin(Minecraft.class)
public class AttackStartLockMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void historystages$cancelLockedAttackSwing(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        LocalPlayer player = mc.player;
        if (player == null) return;
        // Let mining proceed — block breaking is governed by the "break" lock, not here.
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return;
        if (!Config.GAMEPLAY.lockItemUsage.get() && !Config.GAMEPLAY.individualLockItemUsage.get()) return;

        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty()) return;

        if (LockGate.isActionLocked(weapon, player, "attack",
                        Config.GAMEPLAY.lockItemUsage, Config.GAMEPLAY.individualLockItemUsage)
                || LockGate.isActionLocked(weapon, player, "use",
                        Config.GAMEPLAY.lockItemUsage, Config.GAMEPLAY.individualLockItemUsage)) {
            cir.setReturnValue(false);
        }
    }
}
