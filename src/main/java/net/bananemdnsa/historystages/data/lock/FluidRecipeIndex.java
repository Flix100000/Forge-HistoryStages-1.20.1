package net.bananemdnsa.historystages.data.lock;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Codec;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Which loaded recipes touch which fluids, worked out once per reload.
 *
 * <p>This is the piece that lets a fluid lock reach a modded machine. A recipe producing a
 * <em>filled bucket</em> is already gated, because its result is an item and the fluid
 * capability sees through it. A recipe producing a raw {@code FluidStack} — a Create mixer
 * making molten copper — is not: {@code getResultItem} comes back empty and the gate never sees
 * it. Nothing in NeoForge exposes a fluid result generically, so the answer has to come from the
 * recipe's serialised form.
 *
 * <p>Feeding the result into {@code RecipeHandler} rather than into a second lock set is
 * deliberate: {@code RecipeManagerMixin} already filters {@code getRecipeFor}, which every modded
 * station goes through, and the recipe viewers already hide what that reports as locked. So the
 * one thing missing was the knowledge, not the seam.
 *
 * <p><strong>Cost.</strong> Encoding every recipe is not free, so it only happens when some stage
 * actually gates a fluid. A pack that uses no fluid stage pays one map scan per reload and
 * nothing else.
 */
public final class FluidRecipeIndex {

    /** recipe id to the fluids it mentions, each with the sides it was found on. */
    private static volatile Map<String, Map<String, Set<FluidRecipeScanner.Position>>> byRecipe =
            Map.of();

    /** fluid id to how many recipes mention it — what the editor badge shows. */
    private static volatile Map<String, Integer> countByFluid = Map.of();

    private static volatile boolean recipesDirty = true;
    private static volatile boolean relevanceDirty = true;

    /**
     * Set while the editor is open. The editor needs the index for its recipe picker, where a
     * fluid-producing recipe is otherwise unreachable at all, and for the recipe count beside
     * each fluid row — which reads zero for everything until something is gated, i.e. exactly
     * when the number would inform the decision.
     */
    private static volatile boolean editorWantsIt = false;

    /**
     * What the last rebuild saw. Read by the log line and by the GameTests, which use them to
     * prove the re-encode actually works against a real recipe list rather than silently failing
     * on all of it — a scanner that is never fed anything reports a clean, empty, useless index.
     */
    private static volatile int lastScanned = 0;
    private static volatile int lastUnreadable = 0;

    private FluidRecipeIndex() {}

    /**
     * The recipe list changed, so what the index would contain changed with it. Cheap and safe to
     * call often — the work happens on the next rebuild, never here.
     */
    public static void markDirty() {
        recipesDirty = true;
    }

    /**
     * Something changed about whether an index is wanted — a stage was edited, or the editor
     * opened. Deliberately not {@link #markDirty()}: stages do not change a single recipe, and
     * treating them as if they did re-encodes the whole pack on every save.
     */
    public static void markRelevanceDirty() {
        relevanceDirty = true;
    }

    /**
     * The editor is open and wants an index whether or not any stage gates a fluid.
     *
     * <p>Never unset. The scan it keeps alive is worth its memory for the rest of the session,
     * and dropping it the moment a screen closes would only mean paying for it again.
     */
    public static void requestForEditor() {
        if (editorWantsIt) return;
        editorWantsIt = true;
        relevanceDirty = true;
    }

    /** The fluids this recipe mentions; empty for almost every recipe, and for an unbuilt index. */
    public static Map<String, Set<FluidRecipeScanner.Position>> fluidsIn(String recipeId) {
        Map<String, Set<FluidRecipeScanner.Position>> found = byRecipe.get(recipeId);
        return found != null ? found : Map.of();
    }

    /**
     * How many loaded recipes mention this fluid.
     *
     * <p>Shown beside the entry in the editor. Gating a fluid can take out far more of a pack
     * than the author expects — {@code minecraft:water} reaches into four figures — and a number
     * on the row is the difference between deciding that and discovering it.
     */
    public static int recipeCountFor(String fluidId) {
        return countByFluid.getOrDefault(fluidId, 0);
    }

    /** How many recipes the last rebuild looked at. */
    public static int lastScanned() {
        return lastScanned;
    }

    /** How many of those could not be turned back into JSON by their own serialiser. */
    public static int lastUnreadable() {
        return lastUnreadable;
    }

    /** Whether anything is indexed at all, for callers that want to skip a loop. */
    public static boolean isEmpty() {
        return byRecipe.isEmpty();
    }

    /**
     * Rebuilds when stale, and does nothing otherwise.
     *
     * <p>Driven from the server tick and from the client's stage sync rather than from the
     * recipe reload itself: KubeJS and CraftTweaker change recipes <em>after</em>
     * {@code RecipeManager.apply}, so an index built there would miss exactly the recipes a
     * script pack cares about.
     */
    public static void rebuildIfDirty(Iterable<RecipeHolder<?>> recipes,
                                      HolderLookup.Provider registries) {
        boolean hadRecipeChange = recipesDirty;
        boolean hadRelevanceChange = relevanceDirty;
        if (!hadRecipeChange && !hadRelevanceChange) return;

        boolean wanted = editorWantsIt || anyStageGatesAFluid();
        // "Built" is read as "holds something". A pack whose recipes genuinely mention no fluid
        // therefore re-scans once per relevance change; that scan finds nothing and costs less
        // than a third flag saying "scanned, and empty on purpose".
        FluidIndexStaleness.Action action = FluidIndexStaleness.decide(
                hadRecipeChange, hadRelevanceChange, !byRecipe.isEmpty(), wanted);

        recipesDirty = false;
        relevanceDirty = false;

        switch (action) {
            case REBUILD -> rebuild(recipes, registries);
            case DROP -> {
                byRecipe = Map.of();
                countByFluid = Map.of();
                lastScanned = 0;
                lastUnreadable = 0;
            }
            case NOTHING -> {
            }
        }
    }

    private static void rebuild(Iterable<RecipeHolder<?>> recipes,
                                HolderLookup.Provider registries) {
        Predicate<String> isFluid = id -> {
            ResourceLocation key = ResourceLocation.tryParse(id);
            return key != null && BuiltInRegistries.FLUID.containsKey(key);
        };
        Predicate<String> isItem = id -> {
            ResourceLocation key = ResourceLocation.tryParse(id);
            return key != null && BuiltInRegistries.ITEM.containsKey(key);
        };

        Map<String, Map<String, Set<FluidRecipeScanner.Position>>> built = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        int unreadable = 0;
        int scanned = 0;

        for (RecipeHolder<?> holder : recipes) {
            if (holder == null || holder.id() == null) continue;
            scanned++;

            JsonElement json = encode(holder, registries);
            if (json == null) {
                unreadable++;
                continue;
            }
            Map<String, Set<FluidRecipeScanner.Position>> found =
                    FluidRecipeScanner.scan(json, isFluid, isItem);
            if (found.isEmpty()) continue;

            built.put(holder.id().toString(), found);
            for (String fluidId : found.keySet()) {
                counts.merge(fluidId, 1, Integer::sum);
            }
        }

        byRecipe = Map.copyOf(built);
        countByFluid = Map.copyOf(counts);
        lastScanned = scanned;
        lastUnreadable = unreadable;

        DebugLogger.info("Fluid Recipes", "Indexed " + built.size() + " recipes mentioning "
                + counts.size() + " fluids"
                + (unreadable > 0 ? " (" + unreadable + " could not be re-encoded)" : ""));
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            DebugLogger.info("Fluid Recipes",
                    "  " + entry.getKey() + " is mentioned by " + entry.getValue() + " recipes");
        }
        warnAboutIndividualRecipeGating();
    }

    /**
     * Warns where a per-player fluid lock cannot reach the recipe it names.
     *
     * <p>The recipe half of a fluid lock rides on {@code RecipeCraftContext}: a station that
     * knows who is crafting gets the individual answer, one that does not gets the global answer
     * and nothing else. A furnace, a hopper, an autocrafter and most modded machines are in the
     * second group. So an individual stage gating a fluid's recipes sits in the file looking
     * correct while doing nothing at those stations — the same shape of silence the item side
     * already warns about in {@code RecipeManagerMixin.auditIndividualRecipeTypes}.
     *
     * <p>Warns and changes nothing. The entry stays as the author wrote it.
     */
    private static void warnAboutIndividualRecipeGating() {
        StageManager.getIndividualStages().forEach((stageId, entry) -> {
            if (entry == null) return;
            for (net.bananemdnsa.historystages.data.FluidEntry fluid : entry.getFluidEntries()) {
                java.util.List<String> actions = fluid.getLockActions();
                boolean gatesRecipes = actions == null
                        || actions.contains("recipe") || actions.contains("ingredient");
                if (!gatesRecipes) continue;

                DebugLogger.warn("Fluid Recipes",
                        "Individual stage '" + stageId + "' gates fluid '" + fluid.getId()
                                + "' including its recipes. Stations that resolve with no player "
                                + "present — furnaces, hoppers, autocrafters and most modded "
                                + "machines — can only be gated globally, so there the recipe half "
                                + "of this entry does nothing. Put it on a global stage if that "
                                + "matters. The entry is left as written.");
            }
        });
    }

    /**
     * A recipe back as JSON, or null when its serialiser will not produce one.
     *
     * <p>Every recipe has a codec — that is how it was read in the first place — but a codec is
     * only required to decode in practice, and a handful of mods write ones that throw on the way
     * out. A recipe that cannot be re-encoded is skipped and counted rather than allowed to take
     * the reload down with it.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JsonElement encode(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        try {
            RecipeSerializer serializer = holder.value().getSerializer();
            if (serializer == null) return null;

            Codec<Recipe<?>> codec = (Codec) serializer.codec().codec();
            return codec.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE),
                            (Recipe<?>) holder.value())
                    .result().orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Whether building the index would answer anything at all. */
    private static boolean anyStageGatesAFluid() {
        for (StageEntry entry : StageManager.getStages().values()) {
            if (entry != null && !entry.getAllFluidIds().isEmpty()) return true;
        }
        for (StageEntry entry : StageManager.getIndividualStages().values()) {
            if (entry != null && !entry.getAllFluidIds().isEmpty()) return true;
        }
        return false;
    }

    /** Drops everything, for a disconnect or a test. */
    public static void clear() {
        byRecipe = Map.of();
        countByFluid = Map.of();
        lastScanned = 0;
        lastUnreadable = 0;
        recipesDirty = true;
        relevanceDirty = true;
        editorWantsIt = false;
    }
}
