package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.events.RecipeHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The recipe list with everything a locked global stage gates taken out.
 *
 * <p>A station that asks "which of your recipes fits what is inside me" is answered one recipe at
 * a time, and the gate has always sat on that answer. A station that instead takes the whole list
 * and searches it itself never met the gate at all — which is why Create's mixing and compacting
 * stayed craftable inside a locked stage while pressing on the belt, on the very same machine, was
 * blocked. One asks, the other reads. Most modded machines read, because a custom recipe type
 * leaves them no other option.
 *
 * <p>Global stages only, on purpose. A list has no player attached, so there is nobody to resolve
 * an individual stage against — the same limit {@link IndividualRecipeSupport} describes from the
 * other side, and the one the editor warns about when a recipe is put on an individual stage.
 *
 * <p>Cached because the list runs to thousands of entries and the machines reading it read it
 * again every few ticks. The answer is rebuilt when the unlocked set changes, when a stage's
 * contents change, and when recipes reload — never in between. All three are read from counters
 * rather than pushed in, so a fourth place that changes one of them cannot forget to report.
 */
public final class VisibleRecipes {

    /** The three things that can change the answer, as they stood when it was worked out. */
    private record Stamp(long unlocked, long definitions, long recipes) {}

    /** Moved by {@link #invalidate()}; the other two counters live next to their own data. */
    private static long recipeGeneration;

    /**
     * The recipe manager the cached answers were worked out from.
     *
     * <p>A datapack reload builds a whole new recipe manager and only swaps it in once every
     * reload listener has finished. Between those two moments the old one is still the server's,
     * still answering queries, and already past the point where the new one said the recipes had
     * changed — so an answer cached in that window belongs to the outgoing recipes and would
     * otherwise be handed to the incoming manager.
     */
    private static Object owner;

    private static Stamp allStamp;
    private static Collection<RecipeHolder<?>> all;

    private static Stamp typeStamp;
    private static final Map<RecipeType<?>, List<? extends RecipeHolder<?>>> byType = new HashMap<>();

    private VisibleRecipes() {}

    /** Called when the loaded recipes themselves change — a datapack reload, or a script pack. */
    public static synchronized void invalidate() {
        recipeGeneration++;
        owner = null;
        all = null;
        byType.clear();
    }

    /** The whole list, gated. Returns {@code loaded} itself while nothing is gated. */
    public static synchronized Collection<RecipeHolder<?>> all(Object recipeManager,
                                                               Collection<RecipeHolder<?>> loaded) {
        changeOwner(recipeManager);
        Stamp stamp = stamp();
        if (all != null && stamp.equals(allStamp)) return all;

        Collection<RecipeHolder<?>> gated = filter(loaded);
        all = gated == loaded ? loaded : Collections.unmodifiableCollection(gated);
        allStamp = stamp;
        return all;
    }

    /** One recipe type's list, gated. Returns {@code loaded} itself while nothing is gated. */
    @SuppressWarnings("unchecked")
    public static synchronized <H extends RecipeHolder<?>> List<H> ofType(Object recipeManager,
                                                                         RecipeType<?> type,
                                                                         List<H> loaded) {
        changeOwner(recipeManager);
        Stamp stamp = stamp();
        if (!stamp.equals(typeStamp)) {
            byType.clear();
            typeStamp = stamp;
        }

        List<? extends RecipeHolder<?>> cached = byType.get(type);
        if (cached != null) return (List<H>) cached;

        List<H> gated = filter(loaded);
        // Vanilla hands out an immutable copy here and callers rely on that. Immutable also means
        // the list this cache hands out again cannot be edited by whoever received it last.
        List<H> result = gated == loaded ? loaded : List.copyOf(gated);
        byType.put(type, result);
        return result;
    }

    /**
     * Whether the set of recipes this gate hides is different from the last time it was asked.
     *
     * <p>This is what decides whether a stage change costs a datapack reload. Only a machine that
     * kept its own copy of the recipe list needs one, and it only needs one when that list would
     * now come out different — so the honest question is not "did a stage change" but "did the set
     * of hidden recipes change", and that can simply be worked out and compared. It answers for
     * every route at once: a recipe named on the stage, an item whose lock covers {@code recipe},
     * an item reached through a tag or a mod id, a fluid the recipe touches.
     *
     * <p>A stage that gates only blocks, biomes or mobs therefore costs nothing here, and neither
     * does saving a stage in the editor without touching what it gates.
     *
     * <p>Walks every recipe once, which is the same walk the filter above does when it rebuilds —
     * a few milliseconds against the seconds a reload costs on a large pack.
     *
     * <p>Must be given the unfiltered list, or it would be comparing the answer with itself.
     */
    public static synchronized boolean gatedSetChanged(Collection<RecipeHolder<?>> loaded) {
        Set<ResourceLocation> now = new HashSet<>();
        for (RecipeHolder<?> holder : loaded) {
            if (RecipeHandler.isLockedForEveryone(holder)) now.add(holder.id());
        }

        boolean changed = !now.equals(gatedIds);
        gatedIds = now;
        return changed;
    }

    /** What {@link #gatedSetChanged} last reported. Not a cache — do not clear it on invalidate. */
    private static Set<ResourceLocation> gatedIds = Set.of();

    private static void changeOwner(Object recipeManager) {
        if (owner == recipeManager) return;
        owner = recipeManager;
        all = null;
        byType.clear();
    }

    private static Stamp stamp() {
        return new Stamp(StageData.cacheVersion(), StageLocks.definitionsVersion(), recipeGeneration);
    }

    /**
     * Returns {@code loaded} itself when nothing in it is gated — the answer on a world where no
     * stage locks a recipe at all. No copy is made and none is kept.
     */
    @SuppressWarnings("unchecked")
    private static <C extends Collection<? extends RecipeHolder<?>>> C filter(C loaded) {
        boolean anyGated = false;
        for (RecipeHolder<?> holder : loaded) {
            if (RecipeHandler.isLockedForEveryone(holder)) {
                anyGated = true;
                break;
            }
        }
        if (!anyGated) return loaded;

        List<RecipeHolder<?>> kept = new ArrayList<>(loaded.size());
        for (RecipeHolder<?> holder : loaded) {
            if (!RecipeHandler.isLockedForEveryone(holder)) kept.add(holder);
        }
        return (C) kept;
    }
}
