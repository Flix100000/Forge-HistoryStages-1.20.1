package net.bananemdnsa.historystages.mixin;

import java.util.function.BiConsumer;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.lock.LockGate;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses a stage-locked item's attribute modifiers for the holding player, so a locked
 * weapon/wand grants none of its bonuses (attack damage, Spell Power, etc.) while it stays
 * inert in hand. Player-aware, so individual (per-player) stage locks are respected — unlike
 * NeoForge's ItemAttributeModifierEvent, which carries no entity context.
 *
 * <p>Redirects the first {@code ItemStack.forEachModifier} call in
 * {@code collectEquipmentChanges} (the new-item apply path). The second call (old-item removal)
 * is left untouched so modifiers are still cleaned up on slot changes.</p>
 */
@Mixin(LivingEntity.class)
public class AttributeLockMixin {

    @Redirect(
            method = "collectEquipmentChanges",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/world/item/ItemStack;forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V"))
    private void historystages$skipLockedAttributeModifiers(
            ItemStack stack, EquipmentSlot slot,
            BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player
                && (Config.GAMEPLAY.lockItemUsage.get() || Config.GAMEPLAY.individualLockItemUsage.get())
                && LockGate.isActionLocked(stack, player, "use",
                        Config.GAMEPLAY.lockItemUsage, Config.GAMEPLAY.individualLockItemUsage)) {
            return; // locked: do not apply this item's attribute modifiers
        }
        stack.forEachModifier(slot, consumer);
    }
}
