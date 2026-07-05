package net.bananemdnsa.historystages.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Read access to {@link ClientAdvancements}'s private progress map so editor widgets can
 * enumerate ALL advancements received from the server, not just the display-having ones in
 * the {@code AdvancementList} tree (recipe advancements and other display-less ones never
 * enter the tree, so they would be invisible in the trigger picker).
 *
 * <p>On 1.20.1 the map is keyed by {@link Advancement} (not {@code AdvancementHolder} as on
 * 1.21). The field name {@code progress} matches the mapped MC source, which ForgeGradle's
 * refmap resolves to the correct SRG name at load time — same convention the other mixins in
 * this package use for {@code @Shadow} fields.
 */
@Mixin(ClientAdvancements.class)
public interface ClientAdvancementsAccessor {
    @Accessor("progress")
    Map<Advancement, AdvancementProgress> historystages$getProgress();
}
