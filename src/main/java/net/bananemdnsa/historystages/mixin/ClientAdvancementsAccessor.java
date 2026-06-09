package net.bananemdnsa.historystages.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Read access to {@link ClientAdvancements}'s private progress map so editor widgets can
 * enumerate ALL advancement holders received from the server, not just the display-having
 * ones in the {@code AdvancementTree} (recipe advancements and other display-less ones
 * never enter the tree, so they would be invisible in the trigger picker).
 */
@Mixin(ClientAdvancements.class)
public interface ClientAdvancementsAccessor {
    @Accessor("progress")
    Map<AdvancementHolder, AdvancementProgress> historystages$getProgress();
}
