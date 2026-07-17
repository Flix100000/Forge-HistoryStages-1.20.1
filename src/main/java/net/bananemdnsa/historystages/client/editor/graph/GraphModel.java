package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.AdvancementTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockBreakTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockPlaceTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DimensionTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.ItemTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.PlaytimeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StructureTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.EntityKillDep;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.bananemdnsa.historystages.data.dependency.ScoreboardDep;
import net.bananemdnsa.historystages.data.dependency.StatDep;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the focus/neighborhood graph for a single stage: the focus stage in the
 * center, its prerequisite stages (left) and dependents (right), plus one
 * satellite node per distinct dependency element and (for AUTO/TEMPORARY stages)
 * per auto-trigger condition. Pure logic — no rendering.
 */
public class GraphModel {

    /** The focused stage this model is built around. */
    public final GraphNode focus;
    public final List<GraphNode> nodes = new ArrayList<>();
    public final List<GraphEdge> edges = new ArrayList<>();

    /** De-dupes satellite detail/trigger nodes per owning stage (by "<stageId>|<type>:<value>"). */
    private final Set<String> seenSatellites = new HashSet<>();

    private GraphModel(GraphNode focus) {
        this.focus = focus;
    }

    /** Localized string for a {@code editor.historystages.depgraph.<key>} entry. */
    private static String tr(String key) {
        return Component.translatable("editor.historystages.depgraph." + key).getString();
    }

    /**
     * Builds the model for {@code focusStageId}, or null if the id is unknown in both maps
     * or the {@code filter} hides the focus stage.
     */
    public static GraphModel build(String focusStageId, GraphViewFilter filter) {
        Map<String, StageEntry> global = StageManager.getStages();
        Map<String, StageEntry> individual = StageManager.getIndividualStages();

        boolean focusIsIndividual = individual.containsKey(focusStageId);
        StageEntry focusEntry = focusIsIndividual ? individual.get(focusStageId) : global.get(focusStageId);
        if (focusEntry == null) return null;
        if (!filter.showsStage(focusStageId, focusIsIndividual)) return null;

        GraphNode focusNode = stageNode(focusStageId, focusEntry, focusIsIndividual, GraphNode.Zone.CENTER, filter);
        GraphModel model = new GraphModel(focusNode);
        model.nodes.add(focusNode);

        // --- Left column: prerequisite stages + detail satellites ---
        Set<String> seenPrereqs = new HashSet<>();
        for (DependencyGroup group : focusEntry.getDependencies()) {
            if (group.isEmpty()) continue;
            boolean or = group.isOr();

            for (String depId : group.getStages()) {
                StageEntry depEntry = global.get(depId);
                if (depEntry == null) continue;
                if (!filter.showsStage(depId, false)) continue;
                if (!seenPrereqs.add(depId)) continue;
                GraphNode n = stageNode(depId, depEntry, false, GraphNode.Zone.LEFT, filter);
                model.nodes.add(n);
                model.edges.add(new GraphEdge(n, focusNode, or)); // arrow prerequisite -> focus
            }
            for (IndividualStageDep dep : group.getIndividualStages()) {
                String depId = dep.getStageId();
                if (depId == null) continue;
                StageEntry depEntry = individual.get(depId);
                if (depEntry == null) continue;
                if (!filter.showsStage(depId, true)) continue;
                if (!seenPrereqs.add(depId)) continue;
                GraphNode n = stageNode(depId, depEntry, true, GraphNode.Zone.LEFT, filter);
                model.nodes.add(n);
                model.edges.add(new GraphEdge(n, focusNode, or)); // arrow prerequisite -> focus
            }
            if (filter.showsDetails()) model.addDetailNodes(focusNode, group, or);
        }

        // --- Trigger satellites (AUTO / TEMPORARY only) ---
        StageMode mode = focusEntry.getMode();
        if (filter.showsTriggers() && (mode == StageMode.AUTO || mode == StageMode.TEMPORARY)) {
            AutoTrigger at = focusEntry.getAutoTrigger();
            if (at != null && !at.isEmpty()) {
                for (TriggerCondition c : at.getTriggers()) {
                    model.addTriggerNode(focusNode, c);
                }
            }
        }

        // --- Right column: dependents ---
        addDependents(model, focusNode, focusStageId, global, false, filter);
        addDependents(model, focusNode, focusStageId, individual, true, filter);

        return model;
    }


    private static void addDependents(GraphModel model, GraphNode focusNode, String focusStageId,
                                      Map<String, StageEntry> map, boolean individual,
                                      GraphViewFilter filter) {
        for (Map.Entry<String, StageEntry> e : map.entrySet()) {
            String id = e.getKey();
            if (id.equals(focusStageId)) continue;
            if (!filter.showsStage(id, individual)) continue;
            StageEntry entry = e.getValue();
            boolean dependsOnFocus = false;
            boolean viaOr = false;
            for (DependencyGroup group : entry.getDependencies()) {
                if (group.getReferencedStageIds().contains(focusStageId)) {
                    dependsOnFocus = true;
                    if (group.isOr()) viaOr = true;
                }
            }
            if (!dependsOnFocus) continue;
            GraphNode n = stageNode(id, entry, individual, GraphNode.Zone.RIGHT, filter);
            model.nodes.add(n);
            model.edges.add(new GraphEdge(focusNode, n, viaOr)); // arrow focus -> dependent
        }
    }

    private void addDetailNodes(GraphNode focusNode, DependencyGroup group, boolean or) {
        for (DependencyItem it : group.getItems()) {
            if (it.getId() == null) continue;
            if (!seenSatellites.add(focusNode.id + "|item:" + it.getId())) continue;
            List<String> tip = List.of("§6" + tr("node.item"), it.getCount() + "x " + it.getId());
            addDetail(focusNode, GraphNode.Type.DETAIL_ITEM, "", it.getId(), null, tip, or);
        }
        XpLevelDep xp = group.getXpLevel();
        if (xp != null && seenSatellites.add(focusNode.id + "|xp:" + xp.getLevel())) {
            List<String> tip = List.of("§6" + tr("node.xp_level"),
                    xp.getLevel() + (xp.isConsume() ? " (" + tr("node.consumed") + ")" : ""));
            addDetail(focusNode, GraphNode.Type.DETAIL_XP, "", "minecraft:experience_bottle", null, tip, or);
        }
        for (String a : group.getAdvancements()) {
            if (!seenSatellites.add(focusNode.id + "|adv:" + a)) continue;
            addDetail(focusNode, GraphNode.Type.DETAIL_ADVANCEMENT, "Ad", null, null,
                    List.of("§6" + tr("node.advancement"), a), or);
        }
        for (EntityKillDep k : group.getEntityKills()) {
            if (k.getEntityId() == null) continue;
            if (!seenSatellites.add(focusNode.id + "|kill:" + k.getEntityId())) continue;
            List<String> tip = List.of("§6" + tr("node.entity_kill"), k.getCount() + "x " + k.getEntityId());
            addDetail(focusNode, GraphNode.Type.DETAIL_KILL, "", null, k.getEntityId(), tip, or);
        }
        for (StatDep s : group.getStats()) {
            if (s.getStatId() == null) continue;
            if (!seenSatellites.add(focusNode.id + "|stat:" + s.getStatId())) continue;
            addDetail(focusNode, GraphNode.Type.DETAIL_STAT, "St", null, null,
                    List.of("§6" + tr("node.stat"), s.getStatId() + " >= " + s.getMinValue()), or);
        }
        for (ScoreboardDep s : group.getScoreboard()) {
            String holder = s.isPlayerSelf() ? "<player>" : s.getScoreHolder();
            String line = holder + " " + s.getObjective() + " " + s.getOp() + " " + s.getValue();
            if (!seenSatellites.add(focusNode.id + "|score:" + line)) continue;
            addDetail(focusNode, GraphNode.Type.DETAIL_SCOREBOARD, "SB", null, null,
                    List.of("§6" + tr("node.scoreboard"), line), or);
        }
    }

    private void addTriggerNode(GraphNode focusNode, TriggerCondition c) {
        String itemIcon = null;
        String entityId = null;
        String label;
        String detail;
        switch (c) {
            case ItemTrigger t -> { itemIcon = t.id(); label = ""; detail = tr("trigger.item") + ": " + t.id(); }
            case BlockPlaceTrigger t -> { itemIcon = t.id(); label = ""; detail = tr("trigger.place") + ": " + t.id(); }
            case BlockBreakTrigger t -> { itemIcon = t.id(); label = ""; detail = tr("trigger.break") + ": " + t.id(); }
            case EntityTrigger t -> { entityId = t.id(); label = "";
                detail = tr("trigger.entity") + ": " + t.id() + " (" + t.resolvedSubMode().serialize() + ")"; }
            case BiomeTrigger t -> { label = "Bi"; detail = tr("trigger.biome") + ": " + t.id(); }
            case StructureTrigger t -> { label = "Str"; detail = tr("trigger.structure") + ": " + t.id(); }
            case DimensionTrigger t -> { label = "Dim"; detail = tr("trigger.dimension") + ": " + t.id(); }
            case AdvancementTrigger t -> { label = "Ad"; detail = tr("trigger.advancement") + ": " + t.id(); }
            case PlaytimeTrigger t -> { label = "PT"; detail = tr("trigger.playtime") + ": " + t.days() + "d"; }
        }
        if (!seenSatellites.add(focusNode.id + "|trig:" + detail)) return;
        String id = "trigger:" + nodes.size();
        GraphNode n = new GraphNode(id, GraphNode.Type.TRIGGER, GraphNode.Category.TRIGGER,
                GraphNode.Zone.SATELLITE, label, false, itemIcon, entityId,
                List.of("§6" + tr("node.trigger"), detail));
        nodes.add(n);
        edges.add(new GraphEdge(n, focusNode, false)); // arrow trigger -> focus
    }

    private void addDetail(GraphNode focusNode, GraphNode.Type type, String label,
                           String itemIcon, String entityId, List<String> tooltip, boolean or) {
        String id = "detail:" + type.name() + ":" + nodes.size();
        GraphNode n = new GraphNode(id, type, GraphNode.Category.DETAIL, GraphNode.Zone.SATELLITE,
                label, false, itemIcon, entityId, new ArrayList<>(tooltip));
        nodes.add(n);
        edges.add(new GraphEdge(n, focusNode, or)); // arrow detail -> focus
    }

    /**
     * Builds a stage node. When the filter anonymises the stage, the node reveals only its
     * lock state: name, id and custom icon are all replaced — the id is usually as
     * descriptive as the name, and a custom icon typically shows the stage's reward item.
     */
    private static GraphNode stageNode(String id, StageEntry entry, boolean individual, GraphNode.Zone zone,
                                       GraphViewFilter filter) {
        GraphNode.Type type = individual ? GraphNode.Type.STAGE_INDIVIDUAL : GraphNode.Type.STAGE_GLOBAL;
        boolean unlocked = !individual && ClientStageCache.isStageUnlocked(id);
        boolean anon = filter.anonymizes(id, entry);

        String display = anon
                ? Component.translatable("editor.historystages.depgraph.hidden_stage").getString()
                : entry.getDisplayName();

        List<String> tip = new ArrayList<>();
        tip.add(display);
        if (!anon) tip.add("§7" + id);
        tip.add(individual ? "§8" + tr("node.individual") : (unlocked ? "§a" + tr("node.unlocked") : "§7" + tr("node.locked")));

        // Fall back to the configured default stage icon when no custom icon is set,
        // matching the pedestal / FTB / command rendering elsewhere.
        String icon = anon
                ? net.bananemdnsa.historystages.Config.COMMON.defaultStageIcon.get()
                : (entry.getIcon().isEmpty()
                        ? net.bananemdnsa.historystages.Config.COMMON.defaultStageIcon.get()
                        : entry.getIcon());

        return new GraphNode(id, type, GraphNode.Category.STAGE, zone, display, unlocked, icon, null, tip);
    }
}
