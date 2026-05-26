package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.MobLootLockHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Consumer;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDropMixin {
    /**
     * {@code LivingEntity.dropFromLootTable} passes {@code this::spawnAtLocation} as the
     * {@link Consumer} to {@code LootTable.getRandomItems}. Because that's an
     * {@code invokedynamic} lambda the spawnAtLocation call site is not visible as an
     * {@code INVOKE} target. We wrap the consumer argument instead so we can filter
     * each generated stack before it is spawned.
     */
    @ModifyArg(
            method = "dropFromLootTable",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"
            )
    )
    private Consumer<ItemStack> historystages$wrapLootConsumer(Consumer<ItemStack> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        return stack -> {
            ItemStack filtered = MobLootLockHandler.filterDrop(self, stack);
            if (!filtered.isEmpty()) {
                original.accept(filtered);
            }
        };
    }
}
