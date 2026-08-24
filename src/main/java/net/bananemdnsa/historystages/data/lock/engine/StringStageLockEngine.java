package net.bananemdnsa.historystages.data.lock.engine;

import net.bananemdnsa.historystages.api.stage.StageStateView;

import net.bananemdnsa.historystages.api.stage.StageScope;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.category.CategoryLockResolver;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The engine the mod runs on: every lock question is answered by asking a
 * {@link net.bananemdnsa.historystages.api.lock.LockCategory} through
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
        CategoryLockIndexes.ItemGating remembered =
                CategoryLockIndexes.rememberedItemGating(itemId, scope);
        if (remembered != null) return remembered.stages();
        return computeItemGating(itemId, modId, stack, scope).stages();
    }

    /**
     * Whether this item is locked for this viewer, answered in bits where it can be.
     *
     * <p>The list-returning {@link #gatingStagesForItem} exists for callers that print the
     * missing stages. This one only has to say yes or no, which is what nearly every call
     * actually wants — and once the answer for an item is remembered, both sides of the question
     * are already masks and the comparison is a handful of AND operations rather than a lookup
     * per gating stage.
     */
    @Override
    public boolean isItemLocked(String itemId, String modId, @Nullable ItemStack stack,
                                StageScope scope, StageStateView state, @Nullable StageMask unlocked) {
        CategoryLockIndexes.ItemGating gating = CategoryLockIndexes.rememberedItemGating(itemId, scope);
        if (gating == null) gating = computeItemGating(itemId, modId, stack, scope);

        if (unlocked != null && gating.mask() != StageMask.EMPTY) {
            return unlocked.missesAnyOf(gating.mask());
        }
        return LockResolution.isLocked(gating.stages(), state);
    }

    /**
     * Works the answer out and remembers it when it is safe to.
     *
     * <p>"Safe" means the answer cannot differ between two stacks of the same item — see
     * {@link #dependsOnTheStack}. Remembering a stack-dependent answer would serve one enchanted
     * sword's verdict for every plain one, which is the kind of fault that looks like a config
     * mistake rather than a cache.
     */
    private CategoryLockIndexes.ItemGating computeItemGating(String itemId, String modId,
                                                             @Nullable ItemStack stack, StageScope scope) {
        Item item = stack != null ? stack.getItem() : BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        Collection<String> candidates = scope == StageScope.GLOBAL
                ? CategoryLockIndexes.globalCandidates(itemId, modId, item)
                : CategoryLockIndexes.individualCandidates(itemId, modId, item);

        Map<String, StageEntry> stages = stagesOf(scope);
        List<String> gating = candidates.isEmpty() ? List.of()
                : CategoryLockResolver.gatingStages(itemCategories(),
                        new LockSubjects.ItemSubject(itemId, modId, stack, item), candidates, stages);

        CategoryLockIndexes.ItemGating answer = new CategoryLockIndexes.ItemGating(gating,
                gating.isEmpty() ? StageMask.EMPTY
                        : StageMask.of(CategoryLockIndexes.stageIndex(), gating));

        if (!dependsOnTheStack(candidates, stages, itemId)) {
            CategoryLockIndexes.rememberItemGating(itemId, scope, answer);
        }
        return answer;
    }

    /**
     * Whether any candidate stage decides this item by something only a stack carries.
     *
     * <p>Scanned over the candidates, which the relevance index has already narrowed to a
     * handful, and only on a miss. Erring towards "yes" costs a recomputation; erring towards
     * "no" caches a wrong answer, so every NBT-bearing shape counts — an item entry for this id,
     * any tag entry at all, and a mod exception for this id.
     */
    private static boolean dependsOnTheStack(Collection<String> candidates,
                                             Map<String, StageEntry> stages, String itemId) {
        for (String stageId : candidates) {
            StageEntry stage = stages.get(stageId);
            if (stage == null) continue;
            for (net.bananemdnsa.historystages.data.ItemEntry entry : stage.getItemEntries()) {
                if (entry.hasNbt() && entry.getId().equals(itemId)) return true;
            }
            for (net.bananemdnsa.historystages.data.lock.NamedLockEntry tag : stage.getTagEntries()) {
                if (tag.hasNbt()) return true;
            }
            for (net.bananemdnsa.historystages.data.ItemEntry exception : stage.getModExceptionEntries()) {
                if (exception.hasNbt() && exception.getId().equals(itemId)) return true;
            }
        }
        return false;
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
        return narrowed("historystages:recipes", recipeId, scope);
    }

    @Override
    public List<String> gatingStagesForDimension(String dimensionId, StageScope scope) {
        return narrowed("historystages:dimensions", dimensionId, scope);
    }

    @Override
    public List<String> gatingStagesForStructure(String structureId, StageScope scope) {
        return narrowed("historystages:structures", structureId, scope);
    }

    @Override
    public List<String> gatingStagesForEntityAttack(String entityId, StageScope scope) {
        return narrowed("historystages:attacklock", entityId, scope);
    }

    @Override
    public List<String> gatingStagesForEntityInteraction(String entityId, String action,
                                                         ItemStack held, StageScope scope) {
        return narrowed("historystages:interactionlock",
                new LockSubjects.InteractionSubject(entityId, action, held), scope);
    }

    @Override
    public List<String> gatingStagesForEntitySpawn(String entityId, String source,
                                                   String dimension, StageScope scope) {
        // Individual spawn locks do not exist in the data model. Returning empty here is what
        // the pre-seam code effectively did; closing that gap is a separate change.
        if (scope == StageScope.INDIVIDUAL) return List.of();
        return narrowed("historystages:spawnlock",
                new LockSubjects.SpawnSubject(entityId, source, dimension), scope);
    }

    @Override
    public List<String> gatingStagesWithSpawnEntry(String entityId, String dimension, StageScope scope) {
        if (scope == StageScope.INDIVIDUAL) return List.of();
        // A null source is the "any source" question — see LockSubjects.SpawnSubject.
        return narrowed("historystages:spawnlock",
                new LockSubjects.SpawnSubject(entityId, null, dimension), scope);
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
        return CategoryLockIndexes.anyStageUses("historystages:structures");
    }

    @Override
    public boolean anyBiomeLocks() {
        return CategoryLockIndexes.anyStageUses("historystages:biomes");
    }

    @Override
    public void stagesChanged() {
        CategoryLockIndexes.markRelevanceDirty();
    }


    /**
     * One category, narrowed through its own index where it has one.
     *
     * <p>The index answers "which stages could possibly match this key", and only those are then
     * asked properly. A category that does not index itself falls through to the full scan, which
     * is what every category did before Phase 10 — correct, just linear in the number of stages,
     * and that is four microseconds at the three hundred a real pack ships.
     */
    private static List<String> narrowed(String categoryId, Object subject, StageScope scope) {
        LockCategory<?> category = category(categoryId);
        List<String> candidates =
                CategoryLockIndexes.candidates(categoryId, scope, category.lookupKey(subject));
        if (candidates == null) {
            return CategoryLockResolver.gatingStages(category, subject, stagesOf(scope));
        }
        if (candidates.isEmpty()) return List.of();
        return CategoryLockResolver.gatingStages(List.of(category), subject, candidates, stagesOf(scope));
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
