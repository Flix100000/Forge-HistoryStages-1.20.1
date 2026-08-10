package net.bananemdnsa.historystages.client.tooltip;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.client.cache.ClientDependencyCache;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;
import net.bananemdnsa.historystages.data.graph.GraphColors;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLayout;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLine;
import net.bananemdnsa.historystages.research.TierMatcher;
import net.bananemdnsa.historystages.research.TierMode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the research scroll's item name and hover tooltip from a {@link ScrollTooltipLayout}.
 *
 * <p>{@link #append} and {@link #preview} share one code path: both funnel through
 * {@link #build}, which takes an explicit list of {@link ScrollTooltipLine}s and a
 * {@link ScrollTooltipContext} rather than reaching for the live layout or the stack's tag
 * itself. {@code append} builds its context from the item stack's {@code CompoundTag} via
 * {@link #fromStack}; the editor's preview hands it a made-up context with every dependency
 * type filled in, so every configurable line can be seen at once.
 */
public final class ScrollTooltipRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ScrollTooltipRenderer() {}

    /** The item name line, or null to keep the vanilla name. */
    public static @Nullable Component name(CompoundTag tag) {
        return name(ScrollTooltipLayout.active(), fromStack(tag));
    }

    /**
     * Preview variant of {@link #name(CompoundTag)}: builds the name line from an explicit
     * layout and context instead of the live tag-derived one, the same way {@link #preview}
     * handles the rest of the tooltip. The editor hands this its own edited lines so the name
     * line stays in sync with whatever else is being edited.
     */
    public static @Nullable Component name(List<ScrollTooltipLine> layout, ScrollTooltipContext ctx) {
        String stageName = ctx.stageName();
        if (stageName.isEmpty()) return null;

        ScrollTooltipLine line = layout.stream()
                .filter(l -> l.id().equals(ScrollTooltipLayout.NAME_ID))
                .findFirst().orElse(null);
        if (line == null || !line.enabled()) return null;

        Map<String, String> vars = Map.of("stage", stageName);
        // Only the first line: an item's name is one line by definition, whatever the text holds.
        return withArg(line, "tooltip.historystages.research_scroll.named", vars,
                stageName, ChatFormatting.AQUA).get(0);
    }

    /** Appends every configured line, in configured order, into the tooltip list. */
    public static void append(CompoundTag tag, List<Component> tooltip) {
        try {
            tooltip.addAll(build(ScrollTooltipLayout.active(), fromStack(tag)));
        } catch (Exception e) {
            LOGGER.error("[HistoryStages] Failed to build the scroll tooltip", e);
        }
    }

    /**
     * Preview variant: renders from an explicit layout and context instead of the live,
     * tag-derived one. {@code layout} is expected to have the same shape as
     * {@link ScrollTooltipLayout#active()} — the name line, the movable sections and the
     * dependency templates/options all present. {@code ctx} is typically a made-up scroll with
     * every dependency type present, so the editor's preview can show every configurable line.
     */
    public static List<Component> preview(List<ScrollTooltipLine> layout, ScrollTooltipContext ctx) {
        return build(layout, ctx);
    }

    // --- context factory ---

    /**
     * Builds the context for a real scroll from its item stack tag. This is the only place that
     * still reaches into {@link StageManager} and {@link ClientDependencyCache} — everything
     * downstream of {@link #build} works from the resulting {@link ScrollTooltipContext} alone.
     */
    private static ScrollTooltipContext fromStack(CompoundTag tag) {
        String stageId = stageIdOf(tag);
        StageEntry stageEntry = stage(stageId);
        String stageName = stageEntry != null ? stageEntry.getDisplayName() : "";
        boolean individual = stageId != null && StageManager.isIndividualStage(stageId);
        String ownerName = tag != null && tag.contains("OwnerName") ? tag.getString("OwnerName") : null;

        int minTier = 0;
        TierMode tierMode = null;
        if (stageId != null) {
            // Deliberately resolved via isIndividualStage rather than the global-first stage()
            // helper above — this mirrors the original code's own (different) lookup for this
            // section.
            StageEntry tierEntry = StageManager.isIndividualStage(stageId)
                    ? StageManager.getIndividualStages().get(stageId)
                    : StageManager.getStages().get(stageId);
            if (tierEntry != null) {
                minTier = tierEntry.getMinPedestalTier();
                tierMode = tierEntry.getPedestalTierMode();
            }
        }

        List<DependencyGroup> groups = List.of();
        DependencyResult result = null;
        if (stageId != null && stageEntry != null && stageEntry.hasDependencies()) {
            // Deliberately resolved as "not in the global map" rather than isIndividualStage() —
            // this mirrors the original code's own (different) resolution for this section, and
            // ClientDependencyCache must receive the exact same flag it received before the
            // context split.
            boolean cacheIndividual = StageManager.getStages().get(stageId) == null;
            result = ClientDependencyCache.get(stageId, cacheIndividual);
            groups = stageEntry.getDependencies();
        }

        return new ScrollTooltipContext(stageName, individual, ownerName, minTier, tierMode, groups, result);
    }

    // --- shared builder ---

    private static List<Component> build(List<ScrollTooltipLine> layout, ScrollTooltipContext ctx) {
        List<Component> tooltip = new ArrayList<>();
        if (layout == null) return tooltip;

        Map<String, ScrollTooltipLine> byId = new HashMap<>();
        for (ScrollTooltipLine l : layout) byId.put(l.id(), l);

        List<ScrollTooltipLine> movable = layout.stream()
                .filter(l -> ScrollTooltipLayout.MOVABLE_IDS.contains(l.id()))
                .toList();

        for (ScrollTooltipLine line : movable) {
            if (!line.enabled()) continue;

            List<Component> section = switch (line.id()) {
                case "individual_badge" -> individualBadgeSection(line, ctx);
                case "owner" -> ownerSection(line, ctx);
                case "info1" -> simple(line, "tooltip.historystages.research_scroll.info1",
                        Map.of("stage", ctx.stageName()), ChatFormatting.GRAY, ChatFormatting.ITALIC);
                case "info2" -> simple(line, "tooltip.historystages.research_scroll.info2",
                        Map.of("stage", ctx.stageName()), ChatFormatting.GRAY, ChatFormatting.ITALIC);
                case "tier" -> tierSection(line, ctx);
                case "dependencies" -> dependencySection(byId, ctx);
                default -> List.<Component>of();
            };

            if (section.isEmpty()) continue;
            if (line.spacerBefore()) tooltip.add(Component.empty());
            tooltip.addAll(section);
        }

        return tooltip;
    }

    // --- sections ---

    private static List<Component> individualBadgeSection(ScrollTooltipLine line, ScrollTooltipContext ctx) {
        if (!ctx.individual()) return List.of();
        return simple(line, "tooltip.historystages.scroll.individual",
                Map.of("stage", ctx.stageName()), ChatFormatting.LIGHT_PURPLE);
    }

    private static List<Component> ownerSection(ScrollTooltipLine line, ScrollTooltipContext ctx) {
        String owner = ctx.ownerName();
        if (!ctx.individual() || owner == null || owner.isEmpty()) return List.of();
        Map<String, String> vars = Map.of("owner", owner, "stage", ctx.stageName());
        return withArg(line, "tooltip.historystages.scroll.owner", vars, owner, ChatFormatting.GRAY);
    }

    private static List<Component> tierSection(ScrollTooltipLine line, ScrollTooltipContext ctx) {
        if (!Config.CLIENT.showScrollTierTooltip.get() || ctx.tierMode() == null) return List.of();

        int minTier = ctx.minTier();
        TierMode mode = ctx.tierMode();
        if (!(minTier > 1 || mode == TierMode.EXACT)) return List.of();

        String roman = TierMatcher.roman(minTier);
        String key = mode == TierMode.EXACT
                ? "tooltip.historystages.research_scroll.tier.exact"
                : "tooltip.historystages.research_scroll.tier.min";
        Map<String, String> vars = Map.of("tier", roman, "tier_num", Integer.toString(minTier),
                "stage", ctx.stageName());
        return withArg(line, key, vars, roman, ChatFormatting.GRAY);
    }

    private static List<Component> dependencySection(Map<String, ScrollTooltipLine> byId, ScrollTooltipContext ctx) {
        if (ctx.groups().isEmpty()) return List.of();

        DependencyResult result = ctx.result();
        boolean hideFulfilled = Config.COMMON.hideFulfilledDependencies.get();

        ScrollTooltipLine headerLine = byId.get("dep.header");
        ScrollTooltipLine itemLine = byId.get("dep.item");
        ScrollTooltipLine stageLine = byId.get("dep.stage");
        ScrollTooltipLine individualLine = byId.get("dep.individual");
        ScrollTooltipLine xpLine = byId.get("dep.xp");
        ScrollTooltipLine separatorLine = byId.get("dep.separator");

        Icons icons = new Icons(
                option(byId, "dep.icon_fulfilled", "✔"),
                option(byId, "dep.icon_open", "✘"),
                option(byId, "dep.icon_unknown", "•"));
        Colors colors = new Colors(
                option(byId, "dep.color_fulfilled", "green"),
                option(byId, "dep.color_open", "gray"));

        List<List<Component>> renderedGroups = new ArrayList<>();
        List<String> renderedLogics = new ArrayList<>();
        for (DependencyGroup group : ctx.groups()) {
            if (group.isEmpty()) continue;
            List<Component> lines = renderGroup(group, result, hideFulfilled,
                    itemLine, stageLine, individualLine, xpLine, icons, colors);
            if (lines.isEmpty()) continue;
            renderedGroups.add(lines);
            renderedLogics.add(group.getLogic());
        }
        // Nothing survived (all groups empty or fully hidden) — no header either.
        if (renderedGroups.isEmpty()) return List.of();

        List<Component> out = new ArrayList<>();
        if (headerLine != null && headerLine.enabled()) {
            out.addAll(headerComponent(headerLine));
        }
        for (int i = 0; i < renderedGroups.size(); i++) {
            out.addAll(renderedGroups.get(i));
            // Only between groups that actually rendered — never a trailing separator.
            if (i < renderedGroups.size() - 1 && separatorLine != null && separatorLine.enabled()) {
                out.addAll(separatorComponent(separatorLine, renderedLogics.get(i)));
            }
        }
        return out;
    }

    private static List<Component> renderGroup(DependencyGroup group, DependencyResult result, boolean hideFulfilled,
                                                 ScrollTooltipLine itemLine, ScrollTooltipLine stageLine,
                                                 ScrollTooltipLine individualLine, ScrollTooltipLine xpLine,
                                                 Icons icons, Colors colors) {
        List<Component> lines = new ArrayList<>();

        if (itemLine != null && itemLine.enabled()) {
            for (DependencyItem item : group.getItems()) {
                DependencyResult.EntryResult er = findResult(result, "item", item.getId());
                if (hideFulfilled && er != null && er.isFulfilled()) continue;
                lines.addAll(itemComponent(itemLine, item, er, icons, colors));
            }
        }
        if (stageLine != null && stageLine.enabled()) {
            for (String sid : group.getStages()) {
                DependencyResult.EntryResult er = findResult(result, "stage", sid);
                if (hideFulfilled && er != null && er.isFulfilled()) continue;
                lines.addAll(stageComponent(stageLine, sid, er, icons, colors));
            }
        }
        if (individualLine != null && individualLine.enabled()) {
            for (IndividualStageDep dep : group.getIndividualStages()) {
                DependencyResult.EntryResult er = findResult(result, "individual_stage", dep.getStageId());
                if (hideFulfilled && er != null && er.isFulfilled()) continue;
                lines.addAll(individualComponent(individualLine, dep, er, icons, colors));
            }
        }
        if (xpLine != null && xpLine.enabled()) {
            XpLevelDep xp = group.getXpLevel();
            if (xp != null && xp.getLevel() > 0) {
                DependencyResult.EntryResult er = findResult(result, "xp_level", "xp");
                if (!(hideFulfilled && er != null && er.isFulfilled())) {
                    lines.addAll(xpComponent(xpLine, xp, er, icons, colors));
                }
            }
        }
        return lines;
    }

    // --- dependency entry components ---

    private static List<Component> itemComponent(ScrollTooltipLine line, DependencyItem item, DependencyResult.EntryResult er,
                                            Icons icons, Colors colors) {
        ResourceLocation rl = ResourceLocation.tryParse(item.getId());
        Item mcItem = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
        String name = mcItem != null ? mcItem.getDescription().getString() : item.getId();

        boolean fulfilled = er != null && er.isFulfilled();
        String icon = er != null ? (fulfilled ? icons.fulfilled() : icons.open()) : icons.unknown();

        Map<String, String> vars = new HashMap<>();
        vars.put("icon", icon);
        vars.put("name", name);
        vars.put("current", er != null ? String.valueOf(er.getCurrent()) : "");
        vars.put("required", er != null ? String.valueOf(er.getRequired()) : "");

        String template;
        if (line.text().isEmpty()) {
            // No cached result yet: the lang key would render an empty "()" progress suffix, so
            // fall back to the short form instead — matches the original's "  • Name" with no counts.
            template = er == null
                    ? "  %icon% %name%"
                    : Language.getInstance().getOrDefault("tooltip.historystages.dep.item");
        } else {
            template = line.text();
        }
        return styledLines(ScrollTooltipLayout.fill(template, vars),
                fulfilled ? colors.fulfilled() : colors.open(),
                fulfilled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private static List<Component> stageComponent(ScrollTooltipLine line, String sid, DependencyResult.EntryResult er,
                                             Icons icons, Colors colors) {
        boolean fulfilled = er != null && er.isFulfilled();
        String icon = er != null ? (fulfilled ? icons.fulfilled() : icons.open()) : icons.unknown();
        StageEntry se = StageManager.getStages().get(sid);
        String name = se != null ? se.getDisplayName() : sid;

        String colour = fulfilled ? colors.fulfilled() : colors.open();
        ChatFormatting fallback = fulfilled ? ChatFormatting.GREEN : ChatFormatting.GRAY;
        if (line.text().isEmpty()) {
            return List.of(applyStyle(
                    Component.translatable("tooltip.historystages.dep.stage", icon, name),
                    colour, fallback));
        }
        return styledLines(ScrollTooltipLayout.fill(line.text(), Map.of("icon", icon, "name", name)),
                colour, fallback);
    }

    private static List<Component> individualComponent(ScrollTooltipLine line, IndividualStageDep dep, DependencyResult.EntryResult er,
                                                   Icons icons, Colors colors) {
        boolean fulfilled = er != null && er.isFulfilled();
        String icon = er != null ? (fulfilled ? icons.fulfilled() : icons.open()) : icons.unknown();
        StageEntry se = StageManager.getIndividualStages().get(dep.getStageId());
        String name = se != null ? se.getDisplayName() : dep.getStageId();
        String mode = Component.translatable(dep.isAllEver()
                ? "tooltip.historystages.dep.all" : "tooltip.historystages.dep.online").getString();

        String template = line.text().isEmpty()
                ? Language.getInstance().getOrDefault("tooltip.historystages.dep.individual")
                : line.text();
        Map<String, String> vars = Map.of("icon", icon, "name", name, "mode", mode);
        return styledLines(ScrollTooltipLayout.fill(template, vars),
                fulfilled ? colors.fulfilled() : colors.open(),
                fulfilled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private static List<Component> xpComponent(ScrollTooltipLine line, XpLevelDep xp, DependencyResult.EntryResult er,
                                          Icons icons, Colors colors) {
        boolean fulfilled = er != null && er.isFulfilled();
        String icon = er != null ? (fulfilled ? icons.fulfilled() : icons.open()) : icons.unknown();

        String colour = fulfilled ? colors.fulfilled() : colors.open();
        ChatFormatting fallback = fulfilled ? ChatFormatting.GREEN : ChatFormatting.GRAY;
        if (line.text().isEmpty()) {
            return List.of(applyStyle(
                    Component.translatable("tooltip.historystages.dep.level", icon, xp.getLevel()),
                    colour, fallback));
        }
        return styledLines(ScrollTooltipLayout.fill(line.text(),
                Map.of("icon", icon, "level", String.valueOf(xp.getLevel()))), colour, fallback);
    }

    private static List<Component> headerComponent(ScrollTooltipLine line) {
        return simple(line, "tooltip.historystages.scroll.dependencies", Map.of(), ChatFormatting.GOLD);
    }

    private static List<Component> separatorComponent(ScrollTooltipLine line, String logic) {
        return withArg(line, "tooltip.historystages.dep.separator", Map.of("logic", logic), logic, ChatFormatting.DARK_GRAY);
    }

    // --- helpers ---

    /**
     * Turns a filled-in custom text into a component, converting {@code &} format codes to the
     * section sign the font renderer acts on.
     *
     * <p>{@code &} rather than {@code §} because that is what the rest of the mod's configurable
     * message strings already use — {@code unlockMessageFormat} and the lock messages all convert
     * the same way — and because {@code §} cannot be typed on most keyboards. Only custom text
     * goes through here; the built-in lang defaults are ordinary translatable components.
     */
    private static MutableComponent formatted(String text) {
        return Component.literal(text.replace('&', '§'));
    }

    private static @Nullable String stageIdOf(CompoundTag tag) {
        return tag != null && tag.contains("StageResearch") ? tag.getString("StageResearch") : null;
    }

    /** Shorthand for a reserved dependency-block option, sourced from the given layout (not the live one). */
    private static String option(Map<String, ScrollTooltipLine> byId, String id, String fallback) {
        ScrollTooltipLine line = byId.get(id);
        return line == null || line.text().isEmpty() ? fallback : line.text();
    }

    /** Custom text if set, otherwise the lang key; then the style, falling back to the defaults. */
    private static List<Component> simple(ScrollTooltipLine line, String langKey,
                                          Map<String, String> vars, ChatFormatting... fallback) {
        if (line.text().isEmpty()) {
            return List.of(applyStyle(Component.translatable(langKey), line.style(), fallback));
        }
        return styledLines(ScrollTooltipLayout.fill(line.text(), vars), line.style(), fallback);
    }

    /**
     * Splits a custom text on its line breaks and styles each resulting line separately.
     *
     * <p>One configured line can therefore render as several tooltip lines. That is the only way
     * to get a second line out of a section: the editor gives every id exactly one row, so a
     * break has to live inside the text rather than as another row.
     */
    private static List<Component> styledLines(String text, String style,
                                               ChatFormatting... fallback) {
        List<Component> out = new ArrayList<>();
        for (String part : text.split("\n", -1)) {
            out.add(applyStyle(formatted(part), style, fallback));
        }
        return out;
    }

    /** Like {@link #simple}, for lang keys that take one argument. */
    private static List<Component> withArg(ScrollTooltipLine line, String langKey,
                                           Map<String, String> vars, Object arg,
                                           ChatFormatting... fallback) {
        if (line.text().isEmpty()) {
            return List.of(applyStyle(Component.translatable(langKey, arg), line.style(), fallback));
        }
        return styledLines(ScrollTooltipLayout.fill(line.text(), vars), line.style(), fallback);
    }

    /**
     * Maps the style field onto a {@link Style}: each token is either a {@code #RRGGBB} hex
     * colour or a {@link ChatFormatting} name. An unknown token is logged and skipped rather
     * than swallowing the whole line's styling. If several colour tokens appear (e.g. a hex
     * colour after a named colour, or several hex tokens), the last one wins — both
     * {@link Style#applyFormat} and {@link Style#withColor} simply replace the previous colour.
     */
    private static MutableComponent applyStyle(MutableComponent text, String style,
                                                ChatFormatting... fallback) {
        List<String> tokens = ScrollTooltipLayout.styleTokens(style);
        if (tokens.isEmpty()) return text.withStyle(fallback);

        Style result = Style.EMPTY;
        boolean any = false;
        for (String token : tokens) {
            if (GraphColors.isValid(token)) {
                result = result.withColor(TextColor.fromRgb(GraphColors.parse(token, 0)));
                any = true;
                continue;
            }
            ChatFormatting format = ChatFormatting.getByName(token);
            if (format == null) {
                LOGGER.warn("[HistoryStages] Unknown scroll tooltip style '{}' — ignored.", token);
                continue;
            }
            result = result.applyFormat(format);
            any = true;
        }
        return any ? text.setStyle(result) : text.withStyle(fallback);
    }

    private static @Nullable StageEntry stage(String stageId) {
        if (stageId == null || stageId.isEmpty()) return null;
        StageEntry entry = StageManager.getStages().get(stageId);
        return entry != null ? entry : StageManager.getIndividualStages().get(stageId);
    }

    /** Finds a specific entry result in the cached dependency data. */
    private static @Nullable DependencyResult.EntryResult findResult(DependencyResult result, String type, String id) {
        if (result == null) return null;
        for (DependencyResult.GroupResult group : result.getGroups()) {
            for (DependencyResult.EntryResult entry : group.getEntries()) {
                if (entry.getType().equals(type) && entry.getId().equals(id)) {
                    return entry;
                }
            }
        }
        return null;
    }

    private record Icons(String fulfilled, String open, String unknown) {}

    private record Colors(String fulfilled, String open) {}
}
