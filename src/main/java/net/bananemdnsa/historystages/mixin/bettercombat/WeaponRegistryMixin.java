package net.bananemdnsa.historystages.mixin.bettercombat;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.LockGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only: makes Better Combat treat a stage-locked held item as a non-weapon, so it plays no
 * attack/upswing animation. Better Combat starts its upswing (in {@code pre_doAttack}) only when
 * {@code WeaponRegistry.getAttributes(mainHandItem)} is non-null; returning null for a locked item
 * skips the animation entirely — on air, entities and blocks — deterministically and without
 * depending on mixin ordering.
 *
 * <p>Uses {@code @Pseudo} + a string target + {@code require = 0} (same pattern as the JEI/EMI
 * mixins) so it is silently skipped when Better Combat is absent — no separate mixin config needed.
 * The check uses the local player, since the attack animation is always the local player's own.</p>
 */
@Pseudo
@Mixin(targets = "net.bettercombat.logic.WeaponRegistry", remap = false)
public class WeaponRegistryMixin {

    @Inject(method = "getAttributes(Lnet/minecraft/world/item/ItemStack;)Lnet/bettercombat/api/WeaponAttributes;",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void historystages$noWeaponForLocked(ItemStack stack, CallbackInfoReturnable<Object> cir) {
        if (stack.isEmpty()) return;
        if (!Config.COMMON.lockItemUsage.get() && !Config.COMMON.individualLockItemUsage.get()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (LockGate.isActionLocked(stack, player, "attack",
                        Config.COMMON.lockItemUsage, Config.COMMON.individualLockItemUsage)
                || LockGate.isActionLocked(stack, player, "use",
                        Config.COMMON.lockItemUsage, Config.COMMON.individualLockItemUsage)) {
            cir.setReturnValue(null); // treat as a non-weapon -> Better Combat plays no animation
        }
    }
}
