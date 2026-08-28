package net.bananemdnsa.historystages.data;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.bananemdnsa.historystages.data.dependency.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class DependencyGroup {

    /**
     * Maximum number of dependency groups recognised per stage. Enforced by the editor
     * and when loading stage JSON — surplus groups from hand-written files are dropped
     * with a warning rather than silently evaluated.
     */
    public static final int MAX_GROUPS = 5;

    /**
     * This group's identity, stable across everything the editor can do to the group list.
     *
     * <p>Deposited progress on a research scroll is filed per group. It used to be filed by the
     * group's position, which meant deleting or reordering a group moved every item a player had
     * already thrown in onto a different requirement. The auto-trigger axis solved the same
     * problem long ago — {@code TriggerCondition.signature()} says outright that the index must
     * not be the identity — and the dependency side never got the treatment.
     *
     * <p>Null on a group that has just been built in memory, never on one that came off disk.
     * That case is not a defect and needs no migration: {@code DependencyProgress.groupKey} falls back to the position, which
     * is precisely what such a group's existing progress is already filed under. Ids are handed
     * out when a stage is loaded, so the fallback only ever applies to a group built in memory.
     */
    private String id;

    private String logic; // "AND" or "OR"

    private List<DependencyItem> items;
    private List<String> stages;

    @SerializedName("individual_stages")
    private List<IndividualStageDep> individualStages;

    private List<String> advancements;

    @SerializedName("xp_level")
    private XpLevelDep xpLevel;

    @SerializedName("entity_kills")
    private List<EntityKillDep> entityKills;

    private List<StatDep> stats;

    private List<ScoreboardDep> scoreboard;

    /**
     * Requirements owned by other mods, keyed by requirement id.
     *
     * <p>Raw {@link JsonElement} for the same reason {@code StageEntry.addons} is raw: a stage
     * file saved by an instance that does not have the owning addon installed has to round-trip
     * this untouched, and a blank Gson binding would drop what it cannot name. Built-in
     * requirements do not live here — they keep the typed fields above.
     */
    @SerializedName("addons")
    private Map<String, JsonElement> addons;

    public DependencyGroup() {
        this.logic = "AND";
        this.items = new ArrayList<>();
        this.stages = new ArrayList<>();
        this.individualStages = new ArrayList<>();
        this.advancements = new ArrayList<>();
        this.entityKills = new ArrayList<>();
        this.stats = new ArrayList<>();
        this.scoreboard = new ArrayList<>();
    }

    /**
     * An id no group in {@code existing} uses.
     *
     * <p>Random rather than "one past the highest", because ids are not only compared against
     * the groups that are here now: a group added where a deleted one used to be would inherit
     * the deposits players made into the group that is gone.
     */
    public static String freshId(List<DependencyGroup> existing) {
        Set<String> taken = new HashSet<>();
        for (DependencyGroup group : existing) {
            if (group.id != null) taken.add(group.id);
        }
        String candidate;
        do {
            candidate = "g" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (taken.contains(candidate));
        return candidate;
    }

    // --- Getters ---

    /** Null only on a group built in memory — a loaded stage has an id on every group. */
    public String getId() { return id; }

    public String getLogic() { return logic != null ? logic : "AND"; }
    public boolean isOr() { return "OR".equalsIgnoreCase(logic); }

    public List<DependencyItem> getItems() { if (items == null) items = new ArrayList<>(); return items; }
    public List<String> getStages() { if (stages == null) stages = new ArrayList<>(); return stages; }
    public List<IndividualStageDep> getIndividualStages() { if (individualStages == null) individualStages = new ArrayList<>(); return individualStages; }
    public List<String> getAdvancements() { if (advancements == null) advancements = new ArrayList<>(); return advancements; }
    public XpLevelDep getXpLevel() { return xpLevel; }
    public List<EntityKillDep> getEntityKills() { if (entityKills == null) entityKills = new ArrayList<>(); return entityKills; }
    public List<StatDep> getStats() { if (stats == null) stats = new ArrayList<>(); return stats; }
    public List<ScoreboardDep> getScoreboard() { if (scoreboard == null) scoreboard = new ArrayList<>(); return scoreboard; }

    /** This requirement's stored entries, or null when the group declares none. */
    public JsonElement addonEntries(String requirementId) {
        return addons == null ? null : addons.get(requirementId);
    }

    /** Passing null removes the slot rather than storing an empty stub. */
    public void setAddonEntries(String requirementId, JsonElement entries) {
        if (entries == null) {
            if (addons != null) {
                addons.remove(requirementId);
                if (addons.isEmpty()) addons = null;
            }
            return;
        }
        if (addons == null) addons = new LinkedHashMap<>();
        addons.put(requirementId, entries);
    }

    /** The requirement ids this group has stored data for, in insertion order. */
    public Set<String> addonRequirementIds() {
        return addons == null ? Set.of() : Collections.unmodifiableSet(addons.keySet());
    }

    // --- Setters ---

    public void setId(String id) { this.id = id; }
    public void setLogic(String logic) { this.logic = logic; }
    public void setItems(List<DependencyItem> items) { this.items = items != null ? items : new ArrayList<>(); }
    public void setStages(List<String> stages) { this.stages = stages != null ? stages : new ArrayList<>(); }
    public void setIndividualStages(List<IndividualStageDep> individualStages) { this.individualStages = individualStages != null ? individualStages : new ArrayList<>(); }
    public void setAdvancements(List<String> advancements) { this.advancements = advancements != null ? advancements : new ArrayList<>(); }
    public void setXpLevel(XpLevelDep xpLevel) { this.xpLevel = xpLevel; }
    public void setEntityKills(List<EntityKillDep> entityKills) { this.entityKills = entityKills != null ? entityKills : new ArrayList<>(); }
    public void setStats(List<StatDep> stats) { this.stats = stats != null ? stats : new ArrayList<>(); }
    public void setScoreboard(List<ScoreboardDep> scoreboard) { this.scoreboard = scoreboard != null ? scoreboard : new ArrayList<>(); }

    /**
     * Returns true if this group has no dependencies defined at all.
     */
    public boolean isEmpty() {
        return getItems().isEmpty()
                && getStages().isEmpty()
                && getIndividualStages().isEmpty()
                && getAdvancements().isEmpty()
                && xpLevel == null
                && getEntityKills().isEmpty()
                && getStats().isEmpty()
                && getScoreboard().isEmpty()
                && (addons == null || addons.isEmpty());
    }

    /**
     * Returns true if this group demands anything other than stages — items, XP, advancements,
     * kills, stats or scoreboard values.
     *
     * <p>The graph cares about this because it cannot evaluate those requirements client-side.
     * In an OR group they act as an escape hatch: the player may well be able to satisfy the
     * group without any of its stage references, so a locked stage ref must not make the whole
     * group read as blocked.</p>
     */
    public boolean hasNonStageRequirements() {
        return !getItems().isEmpty()
                || !getAdvancements().isEmpty()
                || xpLevel != null
                || !getEntityKills().isEmpty()
                || !getStats().isEmpty()
                || !getScoreboard().isEmpty()
                || (addons != null && !addons.isEmpty());
    }

    /**
     * Returns all stage IDs referenced by this group (for cycle detection).
     */
    public List<String> getReferencedStageIds() {
        List<String> refs = new ArrayList<>(getStages());
        for (IndividualStageDep dep : getIndividualStages()) {
            if (dep.getStageId() != null) {
                refs.add(dep.getStageId());
            }
        }
        return refs;
    }

    public DependencyGroup copy() {
        DependencyGroup copy = new DependencyGroup();
        // The id comes along: this is how the editor's own working copies and the copy taken on
        // save reach the file. Duplicating a group in the editor is the one case that must not
        // keep it, and that path assigns a fresh one itself.
        copy.setId(id);
        copy.setLogic(getLogic());
        copy.setItems(getItems().stream().map(DependencyItem::copy).collect(Collectors.toList()));
        copy.setStages(new ArrayList<>(getStages()));
        copy.setIndividualStages(getIndividualStages().stream().map(IndividualStageDep::copy).collect(Collectors.toList()));
        copy.setAdvancements(new ArrayList<>(getAdvancements()));
        copy.setXpLevel(xpLevel != null ? xpLevel.copy() : null);
        copy.setEntityKills(getEntityKills().stream().map(EntityKillDep::copy).collect(Collectors.toList()));
        copy.setStats(getStats().stream().map(StatDep::copy).collect(Collectors.toList()));
        copy.setScoreboard(getScoreboard().stream().map(ScoreboardDep::copy).collect(Collectors.toList()));
        // Deep-copied, not shared: this is where StageEntry's addons block was silently lost once
        // already, by building a new object and copying it field by field.
        if (this.addons != null) {
            Map<String, JsonElement> addonsCopy = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : this.addons.entrySet()) {
                addonsCopy.put(e.getKey(), e.getValue().deepCopy());
            }
            copy.addons = addonsCopy;
        }
        return copy;
    }
}
