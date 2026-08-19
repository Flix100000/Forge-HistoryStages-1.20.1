package net.bananemdnsa.historystages.data.lock.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The engine the mod runs on for now: every answer comes from the existing string-based
 * {@link StageManager}. This class exists to hold the delegation in one place, not to add
 * logic — behavior must stay bit-for-bit what it was before the seam was introduced.
 */
public class StringStageLockEngine implements StageLockEngine {

    @Override
    public List<String> gatingStagesForItem(String itemId, String modId,
                                            @Nullable ItemStack stack, StageScope scope) {
        return scope == StageScope.GLOBAL
                ? StageManager.getAllStagesForItemOrMod(itemId, modId, stack)
                : StageManager.getAllIndividualStagesForItemOrMod(itemId, modId, stack);
    }

    @Override
    public List<String> globalDualPhaseStagesForItem(String itemId, String modId, @Nullable Item item) {
        List<String> stages = new ArrayList<>();

        Set<String> itemStages = StageManager.getDualPhaseItems().get(itemId);
        if (itemStages != null) stages.addAll(itemStages);

        Set<String> modStages = StageManager.getDualPhaseMods().get(modId);
        if (modStages != null) stages.addAll(modStages);

        if (item != null) {
            for (Map.Entry<String, Set<String>> tagEntry : StageManager.getDualPhaseTags().entrySet()) {
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagEntry.getKey()));
                if (item.builtInRegistryHolder().is(tagKey)) stages.addAll(tagEntry.getValue());
            }
        }

        return stages;
    }

    @Override
    public boolean isItemActionLocked(ItemStack stack, String action, StageScope scope, StageStateView state) {
        ResourceLocation res = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (res == null) return false;
        String itemId = res.toString();
        String modId = res.getNamespace();

        boolean global = scope == StageScope.GLOBAL;
        Iterable<String> candidates = global
                ? StageManager.globalStageCandidates(itemId, modId, stack.getItem())
                : StageManager.individualStageCandidates(itemId, modId, stack.getItem());
        Map<String, StageEntry> stages = global ? StageManager.getStages() : StageManager.getIndividualStages();

        for (String stageId : candidates) {
            if (state.isUnlocked(stageId)) continue;
            StageEntry entry = stages.get(stageId);
            if (entry == null) continue;
            if (StageManager.isItemActionLockedForStage(itemId, modId, stack, action, entry)) return true;
        }
        return false;
    }

    @Override
    public List<String> gatingStagesForRecipe(String recipeId, StageScope scope) {
        Map<String, StageEntry> stages = scope == StageScope.GLOBAL
                ? StageManager.getStages() : StageManager.getIndividualStages();
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : stages.entrySet()) {
            if (entry.getValue().getRecipes().contains(recipeId)) found.add(entry.getKey());
        }
        return found;
    }

    @Override
    public List<String> gatingStagesForDimension(String dimensionId, StageScope scope) {
        return scope == StageScope.GLOBAL
                ? StageManager.getAllStagesForDimension(dimensionId)
                : StageManager.getAllIndividualStagesForDimension(dimensionId);
    }

    @Override
    public List<String> gatingStagesForStructure(String structureId, StageScope scope) {
        return scope == StageScope.GLOBAL
                ? StageManager.getAllStagesForStructure(structureId)
                : StageManager.getAllIndividualStagesForStructure(structureId);
    }

    @Override
    public List<String> gatingStagesForEntityAttack(String entityId, StageScope scope) {
        return scope == StageScope.GLOBAL
                ? StageManager.getAllStagesForAttackLockedEntity(entityId)
                : StageManager.getAllIndividualStagesForAttackLockedEntity(entityId);
    }

    @Override
    public List<String> gatingStagesForEntityInteraction(String entityId, String action,
                                                         ItemStack held, StageScope scope) {
        return scope == StageScope.GLOBAL
                ? StageManager.getAllStagesForInteractionLockedEntity(entityId, action, held)
                : StageManager.getAllIndividualStagesForInteractionLockedEntity(entityId, action, held);
    }

    @Override
    public List<String> gatingStagesForEntitySpawn(String entityId, String source,
                                                   String dimension, StageScope scope) {
        // Individual spawn locks do not exist in the data model. Returning empty here is what
        // the pre-seam code effectively did; closing that gap is a separate change.
        if (scope == StageScope.INDIVIDUAL) return List.of();
        return StageManager.getAllStagesForSpawnLockedEntity(entityId, source, dimension);
    }

    @Override
    public List<String> gatingStagesWithSpawnEntry(String entityId, String dimension, StageScope scope) {
        if (scope == StageScope.INDIVIDUAL) return List.of();
        return StageManager.getAllStagesWithSpawnlockEntry(entityId, dimension);
    }

    @Override
    public List<String> gatingStagesForEnchantment(String enchantmentId, int level, StageScope scope) {
        // Filled in by a later task, when the enchantment matcher is extracted out of
        // StageLockHelper into its own testable class. Until then no caller uses this method.
        return List.of();
    }

    @Override
    public boolean anyStructureLocks() {
        return StageManager.anyStageHasStructures();
    }

    @Override
    public boolean anyBiomeLocks() {
        return StageManager.anyStageHasBiomes();
    }
}
