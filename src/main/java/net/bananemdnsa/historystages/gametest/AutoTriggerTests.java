package net.bananemdnsa.historystages.gametest;

import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.AutoTriggerManager;
import net.bananemdnsa.historystages.data.auto.conditions.EffectTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.XpLevelTrigger;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.events.AutoTriggerEventBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The two ways a trigger can reach the manager: polled from the server tick, and pushed from an
 * event.
 *
 * <p>Not one test per new trigger type. The wiring is the same for all of them and the differences
 * sit in each record's {@code matches}, which the unit suite already covers without needing a
 * server. What is under examination here is that the bridge asks at all, and asks the right thing.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AutoTriggerTests {

    private AutoTriggerTests() {}

    private static void autoStage(String name, TriggerCondition trigger) {
        GameTestStages.global(name, entry -> {
            entry.setMode(StageMode.AUTO);
            entry.setAutoTrigger(new AutoTrigger("any", List.of(trigger)));
        });
    }

    @GameTest(template = "empty")
    public static void aPolledTriggerOpensTheStage(GameTestHelper helper) {
        String id = GameTestStages.PREFIX + "xp_polled";
        StageData data = StageData.get(helper.getLevel());
        try {
            autoStage("xp_polled", new XpLevelTrigger(5));
            AutoTriggerManager.rebuildIndex();

            ServerPlayer player = GameTestPlayers.create(helper);
            player.experienceLevel = 7;

            AutoTriggerEventBridge.pollPlayer(player, 0);

            if (!data.hasStage(id)) {
                helper.fail("the player was above the required XP level and the poll did not open "
                        + "the stage");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(id);
            GameTestStages.removeAll();
            AutoTriggerManager.rebuildIndex();
        }
    }

    @GameTest(template = "empty")
    public static void aPolledTriggerLeavesTheStageShutWhileTheValueIsTooLow(GameTestHelper helper) {
        String id = GameTestStages.PREFIX + "xp_too_low";
        StageData data = StageData.get(helper.getLevel());
        try {
            autoStage("xp_too_low", new XpLevelTrigger(5));
            AutoTriggerManager.rebuildIndex();

            ServerPlayer player = GameTestPlayers.create(helper);
            player.experienceLevel = 4;

            AutoTriggerEventBridge.pollPlayer(player, 0);

            if (data.hasStage(id)) {
                helper.fail("the player was below the required XP level and the stage opened anyway");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(id);
            GameTestStages.removeAll();
            AutoTriggerManager.rebuildIndex();
        }
    }

    @GameTest(template = "empty")
    public static void anEventTriggerOpensTheStage(GameTestHelper helper) {
        String id = GameTestStages.PREFIX + "effect_event";
        StageData data = StageData.get(helper.getLevel());
        ResourceLocation blindness = BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.BLINDNESS.value());
        try {
            autoStage("effect_event", new EffectTrigger(String.valueOf(blindness)));
            AutoTriggerManager.rebuildIndex();

            ServerPlayer player = GameTestPlayers.create(helper);
            // Posted rather than applied through addEffect: vanilla's ServerPlayer.onEffectAdded
            // sends the client a sync packet, and the test player has no connection to send it
            // over. What is under examination is the listener, not vanilla's effect plumbing.
            MobEffectInstance blindnessEffect = new MobEffectInstance(MobEffects.BLINDNESS, 100);
            NeoForge.EVENT_BUS.post(new MobEffectEvent.Added(player, null, blindnessEffect, null));

            if (!data.hasStage(id)) {
                helper.fail("the effect was applied and the stage did not open — the "
                        + "MobEffectEvent.Added listener is not reaching the manager");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(id);
            GameTestStages.removeAll();
            AutoTriggerManager.rebuildIndex();
        }
    }
}
