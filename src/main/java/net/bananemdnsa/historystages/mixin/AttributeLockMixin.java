package net.bananemdnsa.historystages.mixin;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.LockGate;
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
 * Forge's ItemAttributeModifierEvent, which carries no entity context.
 *
 * <p>Redirects the new-item apply call in {@code LivingEntity.collectEquipmentChanges} — the
 * second {@code ItemStack.getAttributeModifiers} invocation (ordinal 1); the first (ordinal 0)
 * is the old-item removal and is left untouched so modifiers are still cleaned up on slot
 * changes. Returning an empty multimap for a locked item skips its bonuses entirely.</p>
 */
@Mixin(LivingEntity.class)
public class AttributeLockMixin {

    @Redirect(
            method = "collectEquipmentChanges",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/world/item/ItemStack;getAttributeModifiers(Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;"))
    private Multimap<Attribute, AttributeModifier> historystages$skipLockedAttributeModifiers(
            ItemStack stack, EquipmentSlot slot) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player
                && (Config.COMMON.lockItemUsage.get() || Config.COMMON.individualLockItemUsage.get())
                && LockGate.isActionLocked(stack, player, "use",
                        Config.COMMON.lockItemUsage, Config.COMMON.individualLockItemUsage)) {
            return ImmutableMultimap.of(); // locked: do not apply this item's attribute modifiers
        }
        return stack.getAttributeModifiers(slot);
    }
}
