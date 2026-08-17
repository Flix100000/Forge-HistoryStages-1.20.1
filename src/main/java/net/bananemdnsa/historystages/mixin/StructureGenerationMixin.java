package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.util.lock.StructureGenerationGate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps structures belonging to a locked stage out of the world entirely, for stages that opted
 * into it via {@code block_generation}.
 *
 * <p>Forge has no hook for this: structure modifiers only reach a structure's spawn overrides,
 * and there is no structure-placement event. Hence the mixin.
 *
 * <p>Because worldgen bakes its result into the chunk, this only affects chunks created while the
 * stage is locked — unlocking never fills in what was skipped.
 */
@Mixin(ChunkGenerator.class)
public class StructureGenerationMixin {

    /**
     * Placement itself. Returning false is exactly what vanilla does when a structure fails to
     * find a valid spot, so nothing downstream has to know the difference.
     */
    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void historystages$blockLockedStructure(
            StructureSet.StructureSelectionEntry entry,
            net.minecraft.world.level.StructureManager structureManager,
            net.minecraft.core.RegistryAccess registryAccess,
            net.minecraft.world.level.levelgen.RandomState random,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager,
            long seed,
            net.minecraft.world.level.chunk.ChunkAccess chunk,
            net.minecraft.world.level.ChunkPos chunkPos,
            net.minecraft.core.SectionPos sectionPos,
            CallbackInfoReturnable<Boolean> cir) {
        if (!StructureGenerationGate.isActive()) return;
        if (StructureGenerationGate.isBlocked(entry.structure())) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Locating. One method covers {@code /locate structure}, explorer maps from loot tables,
     * cartographer trades and the Eye of Ender — they all route through here. Filtering the
     * requested set means a search for only-blocked structures ends up with an empty set, which
     * vanilla already answers with null.
     */
    @ModifyVariable(method = "findNearestMapStructure", at = @At("HEAD"), argsOnly = true)
    private HolderSet<Structure> historystages$hideLockedFromLocate(HolderSet<Structure> requested) {
        if (requested == null || !StructureGenerationGate.isActive()) return requested;

        List<Holder<Structure>> allowed = new ArrayList<>();
        boolean removedAny = false;
        for (Holder<Structure> holder : requested) {
            if (StructureGenerationGate.isBlocked(holder)) {
                removedAny = true;
            } else {
                allowed.add(holder);
            }
        }
        return removedAny ? HolderSet.direct(allowed) : requested;
    }
}
