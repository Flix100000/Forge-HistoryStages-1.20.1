package net.bananemdnsa.historystages.data.lock.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.category.CategoryLockResolver;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.bananemdnsa.historystages.data.lock.category.LockCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The engine the mod runs on: every lock question is answered by asking a
 * {@link net.bananemdnsa.historystages.data.lock.category.LockCategory} through
 * {@link CategoryLockResolver}, over stages read from {@link StageManager}.
 *
 * <p>Still string-based — a stage is an id and a lock check is a walk over entries. What Phase 8
 * changed is <em>where</em> that walk lives: in the categories, one implementation per kind of
 * thing, instead of a dozen near-identical global/individual method pairs on the store. The
 * representation is what Phase 10 replaces, and it replaces this class rather than reaching past
 * it.
 *
 * <p>This class holds no match logic of its own. Its job is to pick the category, build the
 * subject, and hand both to the resolver. Two exceptions, both argued where they stand:
 * {@link #isItemActionLocked}, which is a precedence question rather than a gating one, and
 * {@link #gatingStagesForEnchantment}, whose locks are a sub-view of item entries and not a
 * category.
 */
public class StringStageLockEngine implements StageLockEngine {

    /**
     * Items, mods and tags are three categories but one question: does this stage gate this item.
     * They are asked together, in one pass over the candidate stages, because asking them
     * separately would name a stage that gates by id <em>and</em> by mod twice, and in a
     * different order — and that order is what the "you still need" tooltip prints.
     */
    private static final List<String> ITEM_CATEGORY_IDS =
            List.of("historystages:items", "historystages:mods", "historystages:tags");

    @Override
    public List<String> gatingStagesForItem(String itemId, String modId,
                                            @Nullable ItemStack stack, StageScope scope) {
        Item item = stack != null ? stack.getItem() : BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        Collection<String> candidates = scope == StageScope.GLOBAL
                ? CategoryLockIndexes.globalCandidates(itemId, modId, item)
                : CategoryLockIndexes.individualCandidates(itemId, modId, item);
        if (candidates.isEmpty()) return List.of();

        return CategoryLockResolver.gatingStages(itemCategories(),
                new LockSubjects.ItemSubject(itemId, modId, stack, item), candidates, stagesOf(scope));
    }

    private static List<LockCategory<?>> itemCategories() {
        List<LockCategory<?>> categories = new ArrayList<>(ITEM_CATEGORY_IDS.size());
        for (String id : ITEM_CATEGORY_IDS) categories.add(category(id));
        return categories;
    }

    @Override
    public List<String> globalDualPhaseStagesForItem(String itemId, String modId, @Nullable Item item) {
        List<String> stages = new ArrayList<>();

        Set<String> itemStages = CategoryLockIndexes.dualPhaseGlobal("historystages:items").get(itemId);
        if (itemStages != null) stages.addAll(itemStages);

        Set<String> modStages = CategoryLockIndexes.dualPhaseGlobal("historystages:mods").get(modId);
        if (modStages != null) stages.addAll(modStages);

        if (item != null) {
            for (Map.Entry<String, Set<String>> tagEntry : CategoryLockIndexes.dualPhaseGlobal("historystages:tags").entrySet()) {
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
                ? CategoryLockIndexes.globalCandidates(itemId, modId, stack.getItem())
                : CategoryLockIndexes.individualCandidates(itemId, modId, stack.getItem());
        Map<String, StageEntry> stages = stagesOf(scope);
        LockSubjects.ItemSubject subject =
                new LockSubjects.ItemSubject(itemId, modId, stack, stack.getItem());

        for (String stageId : candidates) {
            if (state.isUnlocked(stageId)) continue;
            StageEntry entry = stages.get(stageId);
            if (entry == null) continue;
            if (ItemActionLocks.isBlockedBy(entry, subject, action)) return true;
        }
        return false;
    }

    @Override
    public List<String> gatingStagesForRecipe(String recipeId, StageScope scope) {
        return CategoryLockResolver.gatingStages(
                category("historystages:recipes"), recipeId, stagesOf(scope));
    }

    @Override
    public List<String> gatingStagesForDimension(String dimensionId, StageScope scope) {
        return CategoryLockResolver.gatingStages(
                category("historystages:dimensions"), dimensionId, stagesOf(scope));
    }

    @Override
    public List<String> gatingStagesForStructure(String structureId, StageScope scope) {
        return CategoryLockResolver.gatingStages(
                category("historystages:structures"), structureId, stagesOf(scope));
    }

    @Override
    public List<String> gatingStagesForEntityAttack(String entityId, StageScope scope) {
        return CategoryLockResolver.gatingStages(
                category("historystages:attacklock"), entityId, stagesOf(scope));
    }

    @Override
    public List<String> gatingStagesForEntityInteraction(String entityId, String action,
                                                         ItemStack held, StageScope scope) {
        return CategoryLockResolver.gatingStages(category("historystages:interactionlock"),
                new LockSubjects.InteractionSubject(entityId, action, held), stagesOf(scope));
    }

    @Override
    public List<String> gatingStagesForEntitySpawn(String entityId, String source,
                                                   String dimension, StageScope scope) {
        // Individual spawn locks do not exist in the data model. Returning empty here is what
        // the pre-seam code effectively did; closing that gap is a separate change.
        if (scope == StageScope.INDIVIDUAL) return List.of();
        return CategoryLockResolver.gatingStages(category("historystages:spawnlock"),
                new LockSubjects.SpawnSubject(entityId, source, dimension), stagesOf(scope));
    }

    @Override
    public List<String> gatingStagesWithSpawnEntry(String entityId, String dimension, StageScope scope) {
        if (scope == StageScope.INDIVIDUAL) return List.of();
        // A null source is the "any source" question — see LockSubjects.SpawnSubject.
        return CategoryLockResolver.gatingStages(category("historystages:spawnlock"),
                new LockSubjects.SpawnSubject(entityId, null, dimension), stagesOf(scope));
    }

    @Override
    public List<String> gatingStagesForEnchantment(String enchantmentId, int level, StageScope scope) {
        Map<String, StageEntry> stages = scope == StageScope.GLOBAL
                ? StageManager.getStages() : StageManager.getIndividualStages();
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : stages.entrySet()) {
            if (EnchantmentLockMatcher.locksEnchantment(entry.getValue(), enchantmentId, level)) {
                found.add(entry.getKey());
            }
        }
        return found;
    }

    @Override
    public boolean anyStructureLocks() {
        return anyStageHasEntries("historystages:structures");
    }

    @Override
    public boolean anyBiomeLocks() {
        return anyStageHasEntries("historystages:biomes");
    }

    /**
     * Whether any stage in either scope has an entry in this category. Drives the per-tick
     * fast-outs in the structure and biome handlers, so it stops at the first hit.
     */
    private static boolean anyStageHasEntries(String categoryId) {
        LockCategory<?> category = category(categoryId);
        for (StageEntry stage : StageManager.getStages().values()) {
            if (!category.read(stage).isEmpty()) return true;
        }
        for (StageEntry stage : StageManager.getIndividualStages().values()) {
            if (!category.read(stage).isEmpty()) return true;
        }
        return false;
    }

    @Override
    public void stagesChanged() {
        CategoryLockIndexes.markRelevanceDirty();
    }

    /** The stage map for a scope — the one thing this class still asks the store for. */
    private static Map<String, StageEntry> stagesOf(StageScope scope) {
        return scope == StageScope.GLOBAL ? StageManager.getStages() : StageManager.getIndividualStages();
    }

    /**
     * Fails loudly rather than quietly unlocking everything, which is what a missing category
     * would otherwise do — a null here would turn into "nothing gates this".
     */
    private static LockCategory<?> category(String id) {
        LockCategory<?> found = LockCategories.byId(id);
        if (found == null) throw new IllegalStateException("built-in lock category missing: " + id);
        return found;
    }
}
