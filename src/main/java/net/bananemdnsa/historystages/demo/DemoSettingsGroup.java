package net.bananemdnsa.historystages.demo;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.settings.RegisterStageSettingsGroupsEvent;
import net.bananemdnsa.historystages.api.settings.Setting;
import net.bananemdnsa.historystages.api.settings.StageSettingsGroup;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * A stand-in addon's own per-stage settings, so the settings path can be exercised before a real
 * addon exists — and so there is a worked example to point an addon author at.
 *
 * <p>Off unless the game is started with {@code -Dhistorystages.demoCategory=true}, which means it
 * never exists for a player. It is written the way a real addon would write it: declare a field
 * per setting, group them under a namespaced id, and register the group. This covers all seven
 * field kinds, {@link net.bananemdnsa.historystages.api.settings.SettingKind#ITEM} included.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DemoSettingsGroup {

    /** Namespaced like any addon must be — {@code historystages} is reserved for the built-ins. */
    public static final String GROUP_ID = "hsdemo:settings";

    private static final Setting<Boolean> ENABLE_LOOTING = Setting.bool("enable_looting")
            .defaultValue(false)
            .langKey("settings.hsdemo.settings.field.enable_looting")
            .build();

    private static final Setting<Integer> RESPAWN_DELAY = Setting.integer("respawn_delay")
            .range(0, 100)
            .defaultValue(20)
            .langKey("settings.hsdemo.settings.field.respawn_delay")
            .build();

    private static final Setting<String> WELCOME_MESSAGE = Setting.text("welcome_message")
            .defaultValue("")
            .langKey("settings.hsdemo.settings.field.welcome_message")
            .build();

    private static final Setting<String> DIFFICULTY_MODE = Setting.choice("difficulty_mode")
            .defaultValue("easy")
            .langKey("settings.hsdemo.settings.field.difficulty_mode")
            .option("easy", "settings.hsdemo.settings.field.difficulty_mode.option.easy")
            .option("hard", "settings.hsdemo.settings.field.difficulty_mode.option.hard")
            .build();

    private static final Setting<String> LORE = Setting.longText("lore")
            .defaultValue("")
            .langKey("settings.hsdemo.settings.field.lore")
            .hintLangKey("settings.hsdemo.settings.field.lore.hint")
            .placeholder("{player}")
            .placeholder("{stage}")
            .build();

    private static final Setting<String> REWARD_ITEM = Setting.item("reward_item")
            .defaultValue("minecraft:diamond")
            .langKey("settings.hsdemo.settings.field.reward_item")
            .build();

    /**
     * The escape hatch: a value only the addon knows how to edit. Stored as a plain string, so
     * nothing about reading, writing, syncing or scoping it is new.
     */
    public static final Setting<String> RELIC_LAYOUT = Setting.customScreen("relic_layout")
            .defaultValue("")
            .langKey("settings.hsdemo.settings.field.relic_layout")
            .build();

    private DemoSettingsGroup() {}

    /** Builds the group fresh. Called both to register it and, without Minecraft, by tests. */
    public static StageSettingsGroup build() {
        return StageSettingsGroup.builder(GROUP_ID)
                .titleLangKey("settings.hsdemo.settings.title")
                .field(ENABLE_LOOTING)
                .field(RESPAWN_DELAY)
                .field(WELCOME_MESSAGE)
                .field(DIFFICULTY_MODE)
                .field(LORE)
                .field(REWARD_ITEM)
                .field(RELIC_LAYOUT)
                .build();
    }

    @SubscribeEvent
    public static void onRegisterGroups(RegisterStageSettingsGroupsEvent event) {
        if (!DemoAddonCategory.enabled()) return;
        event.register(build());
    }
}
