package net.bananemdnsa.historystages.demo;

import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.config.AddonConfigField;
import net.bananemdnsa.historystages.api.config.AddonConfigSection;
import net.bananemdnsa.historystages.api.config.ConfigSide;
import net.bananemdnsa.historystages.api.config.RegisterConfigSectionsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * A stand-in addon's own config sections, so the config-screen path can be exercised before a
 * real addon exists — and so there is a worked example to point an addon author at. Registers one
 * CLIENT section and one COMMON section, together covering all eleven {@link
 * AddonConfigField.AddonConfigKind} kinds an addon may declare, exactly once each.
 *
 * <p>Off unless the game is started with {@code -Dhistorystages.demoCategory=true}, which means it
 * never exists for a player.
 *
 * <p>This stand-in has no config file of its own, so its values live in plain static fields here
 * instead — that is a shortcut for a demo with nothing to persist to, not something to copy. A
 * real addon has its own config spec (NeoForge's {@code ModConfigSpec} or equivalent) and its
 * field's {@code read()}/{@code write()} callbacks talk to that spec directly, the same way {@link
 * net.bananemdnsa.historystages.network.CommonConfigSync} talks to this mod's own {@code Config}.
 *
 * <p>{@code tradeMode} deliberately has no {@code descLangKey}. A field's description is optional,
 * and without one example of that here, {@code AddonConfigLangParityTest} would never exercise the
 * branch that must not report a field with no description as a missing translation. Do not "fix"
 * this by giving it one — give a new field the missing-description slot instead if you need this
 * one filled in.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DemoConfigSections {

    /** Namespaced like any addon must be — {@code historystages} is reserved for the built-ins. */
    public static final String CLIENT_SECTION_ID = "hsdemo:display";

    /** Namespaced like any addon must be — {@code historystages} is reserved for the built-ins. */
    public static final String COMMON_SECTION_ID = "hsdemo:trades";

    // Stand-in state — see class javadoc. A real addon would not have fields like these; it would
    // read and write its own config spec inside the read()/write() callbacks below.
    private static boolean showRelicGlow = true;
    private static String nickname = "";
    private static String relicGlowColor = "#FFD700";
    private static String relicIconTexture = "minecraft:textures/block/gold_block.png";
    private static String relicFoundMessage = "&6You found a &e{relic}&6!";
    private static int restockHours = 24;
    private static String tradeMode = "normal";
    private static double priceMultiplier = 1.0;
    private static String featuredItem = "minecraft:emerald";
    private static String bannedItems = "minecraft:diamond,minecraft:netherite_ingot";
    private static String acceptedTags = "minecraft:logs";

    private DemoConfigSections() {}

    /** Builds both sections fresh. Called both to register them and, without Minecraft, by tests. */
    public static List<AddonConfigSection> build() {
        return List.of(buildClientSection(), buildCommonSection());
    }

    private static AddonConfigSection buildClientSection() {
        return AddonConfigSection.builder(CLIENT_SECTION_ID)
                .titleLangKey("config.hsdemo.display.title")
                .side(ConfigSide.CLIENT)
                .field(AddonConfigField.bool("showRelicGlow")
                        .labelLangKey("config.hsdemo.display.field.showRelicGlow")
                        .descLangKey("config.hsdemo.display.field.showRelicGlow.desc")
                        .defaultValue("true")
                        .read(() -> Boolean.toString(showRelicGlow))
                        .write(v -> showRelicGlow = Boolean.parseBoolean(v))
                        .build())
                .field(AddonConfigField.text("nickname")
                        .labelLangKey("config.hsdemo.display.field.nickname")
                        .descLangKey("config.hsdemo.display.field.nickname.desc")
                        .defaultValue("")
                        .read(() -> nickname)
                        .write(v -> nickname = v)
                        .build())
                .field(AddonConfigField.color("relicGlowColor")
                        .labelLangKey("config.hsdemo.display.field.relicGlowColor")
                        .descLangKey("config.hsdemo.display.field.relicGlowColor.desc")
                        .defaultValue("#FFD700")
                        .read(() -> relicGlowColor)
                        .write(v -> relicGlowColor = v)
                        .build())
                .field(AddonConfigField.texture("relicIconTexture")
                        .labelLangKey("config.hsdemo.display.field.relicIconTexture")
                        .descLangKey("config.hsdemo.display.field.relicIconTexture.desc")
                        .defaultValue("minecraft:textures/block/gold_block.png")
                        .read(() -> relicIconTexture)
                        .write(v -> relicIconTexture = v)
                        .build())
                .field(AddonConfigField.richText("relicFoundMessage")
                        .labelLangKey("config.hsdemo.display.field.relicFoundMessage")
                        .descLangKey("config.hsdemo.display.field.relicFoundMessage.desc")
                        .defaultValue("&6You found a &e{relic}&6!")
                        .placeholder("{relic}")
                        .read(() -> relicFoundMessage)
                        .write(v -> relicFoundMessage = v)
                        .build())
                .build();
    }

    private static AddonConfigSection buildCommonSection() {
        return AddonConfigSection.builder(COMMON_SECTION_ID)
                .titleLangKey("config.hsdemo.trades.title")
                .side(ConfigSide.COMMON)
                .field(AddonConfigField.integer("restockHours")
                        .labelLangKey("config.hsdemo.trades.field.restockHours")
                        .descLangKey("config.hsdemo.trades.field.restockHours.desc")
                        .range(1, 72)
                        .defaultValue("24")
                        .read(() -> Integer.toString(restockHours))
                        .
                        write(v -> restockHours = Integer.parseInt(v)) .build())
                .field(AddonConfigField.choice("tradeMode")
                        .labelLangKey("config.hsdemo.trades.field.tradeMode")
                        .defaultValue("normal")
                        .option("normal", "config.hsdemo.trades.field.tradeMode.option.normal")
                        .option("strict", "config.hsdemo.trades.field.tradeMode.option.strict")
                        .read(() -> tradeMode)
                        .write(v -> tradeMode = v)
                        .build())
                .field(AddonConfigField.decimal("priceMultiplier")
                        .labelLangKey("config.hsdemo.trades.field.priceMultiplier")
                        .descLangKey("config.hsdemo.trades.field.priceMultiplier.desc")
                        .range(0.1, 5.0)
                        .defaultValue("1.0")
                        .read(() -> Double.toString(priceMultiplier))
                        .write(v -> priceMultiplier = Double.parseDouble(v))
                        .build())
                .field(AddonConfigField.item("featuredItem")
                        .labelLangKey("config.hsdemo.trades.field.featuredItem")
                        .descLangKey("config.hsdemo.trades.field.featuredItem.desc")
                        .defaultValue("minecraft:emerald")
                        .read(() -> featuredItem)
                        .write(v -> featuredItem = v)
                        .build())
                .field(AddonConfigField.itemList("bannedItems")
                        .labelLangKey("config.hsdemo.trades.field.bannedItems")
                        .descLangKey("config.hsdemo.trades.field.bannedItems.desc")
                        .defaultValue("minecraft:diamond,minecraft:netherite_ingot")
                        .read(() -> bannedItems)
                        .write(v -> bannedItems = v)
                        .build())
                .field(AddonConfigField.tagList("acceptedTags")
                        .labelLangKey("config.hsdemo.trades.field.acceptedTags")
                        .descLangKey("config.hsdemo.trades.field.acceptedTags.desc")
                        .defaultValue("minecraft:logs")
                        .read(() -> acceptedTags)
                        .write(v -> acceptedTags = v)
                        .build())
                .build();
    }

    @SubscribeEvent
    public static void onRegisterConfigSections(RegisterConfigSectionsEvent event) {
        if (!DemoAddonCategory.enabled()) return;
        for (AddonConfigSection section : build()) {
            event.register(section);
        }
    }
}
