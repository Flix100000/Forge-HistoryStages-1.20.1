package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A recipe lock answering for one player rather than for the world.
 *
 * <p>Asked through {@code RecipeHandler}, which is the single place that judges — the same path
 * {@code RecipeManagerMixin} takes when a station resolves. What the tests supply by hand is only
 * the {@link RecipeCraftContext} that a menu mixin would otherwise have set.
 *
 * <p>The test that matters is {@link #twoPlayersAtTheSameRecipeGetDifferentAnswers}. The rest
 * guard the edges around it: no crafter must still mean global-only, and a crafter must never
 * outlive its resolution.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeLockTests {

    private static final String LOCKED_RECIPE = "minecraft:torch";
    private static final ResourceLocation LOCKED_RECIPE_ID = ResourceLocation.parse(LOCKED_RECIPE);

    /**
     * A second recipe, for the tests that ask whether a change was worth a reload.
     *
     * <p>Not the one above. The suite ticks its tests side by side, so another test's stage gating
     * {@code minecraft:torch} can already be standing when these run — and adding a second stage
     * gating the same recipe changes nothing, which is the answer they are trying to tell apart.
     */
    private static final String RELOAD_PROBE_RECIPE = "minecraft:stick";

    /**
     * Every class the recipe hooks inject into. A {@code @Redirect} whose signature does not match,
     * or a {@code @Shadow} of a field that moved, fails when the target class is transformed — and
     * that happens the first time the class is loaded, which on a headless server is never,
     * because nobody opens a crafting table. Loading them here is what turns a crash on a player's
     * screen into a failing test.
     */
    private static final List<String> HOOKED_CLASSES = List.of(
            "net.minecraft.world.inventory.CraftingMenu",
            "net.minecraft.world.inventory.StonecutterMenu",
            "net.minecraft.world.inventory.SmithingMenu",
            "net.minecraft.server.network.ServerGamePacketListenerImpl");

    private RecipeLockTests() {}

    private static void individualStageGating(String name) {
        GameTestStages.individual(name, stage ->
                stage.setRecipes(new ArrayList<>(List.of(LOCKED_RECIPE))));
    }

    private static void globalStageGating(String name) {
        GameTestStages.global(name, stage ->
                stage.setRecipes(new ArrayList<>(List.of(LOCKED_RECIPE))));
    }

    /** What a station asks after setting the context around its resolution. */
    private static boolean lockedFor(ServerPlayer player) {
        return RecipeCraftContext.with(player.getUUID(),
                () -> RecipeHandler.isRecipeIdLocked(LOCKED_RECIPE_ID, false));
    }

    /** What a furnace, a hopper or an autocrafter asks: nobody is standing there. */
    private static boolean lockedWithNobodyCrafting() {
        return RecipeHandler.isRecipeIdLocked(LOCKED_RECIPE_ID, false);
    }

    @GameTest(template = "empty")
    public static void theRecipeHooksApplyToTheirTargets(GameTestHelper helper) {
        for (String target : HOOKED_CLASSES) {
            try {
                Class.forName(target, false, RecipeLockTests.class.getClassLoader());
            } catch (Throwable failure) {
                helper.fail("loading " + target + " failed, which means a recipe hook did not "
                        + "apply: " + failure);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aReloadStillSendsClientsEveryRecipe(GameTestHelper helper) {
        // A datapack reload pushes the whole recipe list back to every client. That list becomes
        // the client's recipe manager and therefore the vanilla recipe book, so anything the
        // reload drops disappears from the player's book until they rejoin.
        //
        // Reloads here rather than going through reloadForLockChange, which decides for itself
        // whether a reload is warranted: this test is about what a reload does, and the deciding
        // is held by the two tests below.
        //
        // getOrderedRecipes rather than getRecipes throughout: getOrderedRecipes is the list that
        // actually goes into the packet, and it is the one the gate leaves alone.
        MinecraftServer server = helper.getLevel().getServer();
        int before = server.getRecipeManager().getOrderedRecipes().size();
        Object holderBefore = server.getRecipeManager().byKey(LOCKED_RECIPE_ID).orElse(null);

        server.reloadResources(server.getPackRepository().getSelectedIds());

        helper.runAfterDelay(100, () -> {
            int after = server.getRecipeManager().getOrderedRecipes().size();
            Object holderAfter = server.getRecipeManager().byKey(LOCKED_RECIPE_ID).orElse(null);
            // Without this the test would pass by never having reloaded at all: the reload is
            // asynchronous, and an unchanged count proves nothing on its own. A reload rebuilds
            // every holder, so a holder that is still the same object means it did not happen.
            if (holderBefore != null && holderBefore == holderAfter) {
                helper.fail("the reload had not run after 100 ticks, so this test was about to "
                        + "report success without having tested anything");
                return;
            }
            if (after < before) {
                helper.fail("the reload cut the recipe list from " + before + " to " + after
                        + " — every client is then sent the short list, and their recipe book "
                        + "loses whatever went missing");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void gatingARecipeIsSeenAsAChangeToTheGatedSet(GameTestHelper helper) {
        // What decides whether a stage change costs a datapack reload. This half says it notices
        // when it has to.
        MinecraftServer server = helper.getLevel().getServer();
        try {
            settleGatedSet(server);

            GameTestStages.global("gated_set_recipe", stage ->
                    stage.setRecipes(new ArrayList<>(List.of(RELOAD_PROBE_RECIPE))));

            if (!gatedSetChanged(server)) {
                helper.fail("a stage that gates " + RELOAD_PROBE_RECIPE + " was judged not to "
                        + "change which recipes are hidden, so no datapack reload would run — and "
                        + "a machine holding its own copy of the recipe list would keep making it");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aStageGatingOnlyABiomeIsNotWorthAReload(GameTestHelper helper) {
        // The other half, and the one that keeps large modpacks playable. Reloading every datapack
        // freezes the server for as long as it takes, and this can be reached from an auto-trigger
        // while people are playing — commit ca3988f took the full reload out for exactly that
        // reason. A stage gating a biome and nothing else must not pay for one.
        //
        // Synchronous from end to end on purpose: the suite ticks its tests side by side, so a
        // test that waited would have other tests' stages appearing underneath it.
        MinecraftServer server = helper.getLevel().getServer();
        try {
            settleGatedSet(server);

            GameTestStages.global("gated_set_biome", stage ->
                    stage.setBiomes(new ArrayList<>(List.of("minecraft:plains"))));

            if (gatedSetChanged(server)) {
                helper.fail("a stage gating only a biome was judged to change which recipes are "
                        + "hidden, which reloads every datapack. On a large modpack that freezes "
                        + "the server, and an auto-trigger can set it off mid-game");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** Brings the remembered set in line with what is gated right now. */
    private static void settleGatedSet(MinecraftServer server) {
        gatedSetChanged(server);
    }

    private static boolean gatedSetChanged(MinecraftServer server) {
        return net.bananemdnsa.historystages.data.lock.VisibleRecipes.gatedSetChanged(
                server.getRecipeManager().getOrderedRecipes());
    }

    @GameTest(template = "empty")
    public static void aGlobalUnlockTakesEffectWithoutAnyRecipeReload(GameTestHelper helper) {
        // Global lock and unlock fire PacketHandler.reloadForLockChange, on the grounds that the
        // recipes would otherwise reach nobody until something else reloaded them. For the gate
        // itself that is no longer true — filtering moved from load time to query time — and this
        // test says so: the verdict flips on its own, with no reload in sight.
        //
        // That is not licence to remove the reload. It was removed once on exactly this evidence
        // and JEI broke: items hidden by a stage never reappeared after an unlock. Whatever the
        // resend is really doing, it is not this.
        StageData data = StageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "global_recipe_no_reload";
        try {
            globalStageGating("global_recipe_no_reload");

            if (!lockedWithNobodyCrafting()) {
                helper.fail(LOCKED_RECIPE + " should start out locked by the global stage");
                return;
            }

            data.addStage(stageId);
            StageData.refreshCache(data.getUnlockedStages());

            if (lockedWithNobodyCrafting()) {
                helper.fail("the global stage is unlocked, but " + LOCKED_RECIPE + " is still "
                        + "reported as locked without a recipe reload — the reload is load-bearing "
                        + "after all");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(stageId);
        }
    }

    @GameTest(template = "empty")
    public static void aGlobalLockAlsoTakesTheRecipeOutOfTheWholeList(GameTestHelper helper) {
        // The bug this was written for: a Create basin does not ask which recipe fits its
        // contents, it takes the entire recipe list and searches it itself. Mixing and compacting
        // were therefore craftable inside a locked stage while everything that asks was blocked.
        // Both ways of reading a list are gated now; looking one up by its id deliberately is not.
        MinecraftServer server = helper.getLevel().getServer();
        StageData data = StageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "global_recipe_listings";
        try {
            globalStageGating("global_recipe_listings");

            if (listed(server.getRecipeManager().getRecipes())) {
                helper.fail(LOCKED_RECIPE + " is gated by a locked global stage but is still in "
                        + "the full recipe list — a machine that reads the list instead of asking "
                        + "for one recipe can make it");
                return;
            }
            if (listed(server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING))) {
                helper.fail(LOCKED_RECIPE + " is gated but is still in the list of every crafting "
                        + "recipe — the same hole one recipe type wide");
                return;
            }
            if (server.getRecipeManager().byKey(LOCKED_RECIPE_ID).isEmpty()) {
                helper.fail("looking " + LOCKED_RECIPE + " up by its id was gated. That is not an "
                        + "improvement: a player's recipe book is a list of ids resolved this way "
                        + "on every login, and an id that does not resolve is deleted from the "
                        + "book and written back short. See the note on RecipeManagerMixin");
                return;
            }

            data.addStage(stageId);
            StageData.refreshCache(data.getUnlockedStages());

            if (!listed(server.getRecipeManager().getRecipes())
                    || !listed(server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING))) {
                helper.fail("the stage is unlocked and " + LOCKED_RECIPE + " has not come back to "
                        + "the recipe lists — the gate is answering from a cache it never dropped");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(stageId);
        }
    }

    @GameTest(template = "empty")
    public static void theListEveryClientIsSentKeepsTheLockedRecipes(GameTestHelper helper) {
        // getOrderedRecipes fills the packet sent on join and after every reload. It has to keep
        // carrying the locked recipes: the client draws them with a lock on them, the editor's
        // recipe picker lists them, and the fluid index is built from them. Cut them out here and
        // a recipe could be locked once and then never found again to unlock it.
        MinecraftServer server = helper.getLevel().getServer();
        try {
            globalStageGating("global_recipe_wire");

            if (!listed(server.getRecipeManager().getOrderedRecipes())) {
                helper.fail(LOCKED_RECIPE + " was cut out of the list that goes to every client. "
                        + "Locked recipes stop being drawn with a lock, the editor's picker cannot "
                        + "find them any more, and the recipe book loses them until a rejoin");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** Whether the locked recipe is in this listing. */
    private static boolean listed(java.util.Collection<? extends RecipeHolder<?>> recipes) {
        for (RecipeHolder<?> holder : recipes) {
            if (LOCKED_RECIPE_ID.equals(holder.id())) return true;
        }
        return false;
    }

    @GameTest(template = "empty")
    public static void aLockedItemStillBlocksItsRecipesWithoutAnyReload(GameTestHelper helper) {
        // The other half of recipe gating, and a different code path: not a recipe id on the
        // stage, but an item whose lock_actions include "recipe", which takes down every recipe
        // producing it. Asked through isOutputLocked rather than isRecipeIdLocked.
        MinecraftServer server = helper.getLevel().getServer();
        StageData data = StageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "global_item_recipe";
        RecipeHolder<?> torch = server.getRecipeManager().byKey(LOCKED_RECIPE_ID).orElse(null);
        if (torch == null) {
            helper.fail("the test needs the recipe " + LOCKED_RECIPE + " to exist");
            return;
        }
        try {
            GameTestStages.global("global_item_recipe", stage ->
                    stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry("minecraft:torch")))));

            if (!RecipeHandler.isOutputLocked(torch, false)) {
                helper.fail("minecraft:torch sits in a locked global stage, so every recipe "
                        + "producing it should be blocked");
                return;
            }

            data.addStage(stageId);
            StageData.refreshCache(data.getUnlockedStages());

            if (RecipeHandler.isOutputLocked(torch, false)) {
                helper.fail("the global stage holding minecraft:torch is unlocked, but its recipe "
                        + "is still blocked without a recipe reload — the reload is load-bearing "
                        + "for the item path");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(stageId);
        }
    }

    @GameTest(template = "empty")
    public static void aRecipeOnALockedIndividualStageIsLockedForThatPlayer(GameTestHelper helper) {
        try {
            individualStageGating("individual_recipe");
            ServerPlayer player = GameTestPlayers.create(helper);

            if (!lockedFor(player)) {
                helper.fail(LOCKED_RECIPE + " sits in an individual stage this player has not "
                        + "unlocked, but the recipe check reports it as craftable");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void theSameRecipeIsFreeOnceThePlayerHasTheStage(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "individual_recipe_unlocked";
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            individualStageGating("individual_recipe_unlocked");
            data.addStage(player.getUUID(), stageId);

            if (lockedFor(player)) {
                helper.fail("the individual stage holding " + LOCKED_RECIPE + " is unlocked for "
                        + "this player, but the recipe check still reports it as locked");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(player.getUUID(), stageId);
        }
    }

    @GameTest(template = "empty")
    public static void twoPlayersAtTheSameRecipeGetDifferentAnswers(GameTestHelper helper) {
        // The whole feature in one test. If this passes, the context carries; if it does not,
        // every station hook in this change is decoration.
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "individual_recipe_two_players";
        ServerPlayer withStage = GameTestPlayers.create(helper);
        ServerPlayer withoutStage = GameTestPlayers.create(helper);
        try {
            individualStageGating("individual_recipe_two_players");
            data.addStage(withStage.getUUID(), stageId);

            if (lockedFor(withStage)) {
                helper.fail("the player who has the stage cannot craft " + LOCKED_RECIPE);
                return;
            }
            if (!lockedFor(withoutStage)) {
                helper.fail("the player who does not have the stage can craft " + LOCKED_RECIPE
                        + " — the same answer went to both players, so the crafter is being "
                        + "ignored");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(withStage.getUUID(), stageId);
        }
    }

    @GameTest(template = "empty")
    public static void withoutACrafterTheAnswerStaysGlobalOnly(GameTestHelper helper) {
        // The assurance behind the documented limits: a furnace, a hopper and an autocrafter
        // resolve with nobody there and must not start seeing individual stages.
        try {
            individualStageGating("individual_recipe_no_crafter");

            if (lockedWithNobodyCrafting()) {
                helper.fail(LOCKED_RECIPE + " is gated by an individual stage and was refused to "
                        + "a resolution with no crafter — automation just changed behaviour");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aGlobalRecipeLockStaysLockedWithAndWithoutACrafter(GameTestHelper helper) {
        try {
            globalStageGating("global_recipe");
            ServerPlayer player = GameTestPlayers.create(helper);

            if (!lockedWithNobodyCrafting()) {
                helper.fail(LOCKED_RECIPE + " sits in a locked global stage but was allowed to a "
                        + "resolution with no crafter");
                return;
            }
            if (!lockedFor(player)) {
                helper.fail(LOCKED_RECIPE + " sits in a locked global stage but was allowed once "
                        + "a crafter was known — the global half got lost on the player path");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aThrownResolutionDoesNotGateTheNextOne(GameTestHelper helper) {
        // A leaked crafter would gate the next, unrelated resolution — one that may well belong
        // to a hopper standing next to the crafting table.
        try {
            individualStageGating("individual_recipe_leak");
            ServerPlayer player = GameTestPlayers.create(helper);

            try {
                RecipeCraftContext.with(player.getUUID(), () -> {
                    throw new IllegalStateException("resolution blew up");
                });
                helper.fail("the thrown resolution was swallowed, so this test proves nothing");
                return;
            } catch (IllegalStateException expected) {
                // exactly what a broken recipe or a misbehaving mod would do
            }

            if (lockedWithNobodyCrafting()) {
                helper.fail("a resolution with no crafter was refused right after one that threw "
                        + "— the crafter outlived its window");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }
}
