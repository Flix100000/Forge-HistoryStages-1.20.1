package net.bananemdnsa.historystages.data;

import com.google.gson.annotations.SerializedName;
import net.bananemdnsa.historystages.data.dependency.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DependencyGroup {
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

    public DependencyGroup() {
        this.logic = "AND";
        this.items = new ArrayList<>();
        this.stages = new ArrayList<>();
        this.individualStages = new ArrayList<>();
        this.advancements = new ArrayList<>();
        this.entityKills = new ArrayList<>();
        this.stats = new ArrayList<>();
    }

    // --- Getters ---

    public String getLogic() { return logic != null ? logic : "AND"; }
    public boolean isOr() { return "OR".equalsIgnoreCase(logic); }

    public List<DependencyItem> getItems() { if (items == null) items = new ArrayList<>(); return items; }
    public List<String> getStages() { if (stages == null) stages = new ArrayList<>(); return stages; }
    public List<IndividualStageDep> getIndividualStages() { if (individualStages == null) individualStages = new ArrayList<>(); return individualStages; }
    public List<String> getAdvancements() { if (advancements == null) advancements = new ArrayList<>(); return advancements; }
    public XpLevelDep getXpLevel() { return xpLevel; }
    public List<EntityKillDep> getEntityKills() { if (entityKills == null) entityKills = new ArrayList<>(); return entityKills; }
    public List<StatDep> getStats() { if (stats == null) stats = new ArrayList<>(); return stats; }

    // --- Setters ---

    public void setLogic(String logic) { this.logic = logic; }
    public void setItems(List<DependencyItem> items) { this.items = items != null ? items : new ArrayList<>(); }
    public void setStages(List<String> stages) { this.stages = stages != null ? stages : new ArrayList<>(); }
    public void setIndividualStages(List<IndividualStageDep> individualStages) { this.individualStages = individualStages != null ? individualStages : new ArrayList<>(); }
    public void setAdvancements(List<String> advancements) { this.advancements = advancements != null ? advancements : new ArrayList<>(); }
    public void setXpLevel(XpLevelDep xpLevel) { this.xpLevel = xpLevel; }
    public void setEntityKills(List<EntityKillDep> entityKills) { this.entityKills = entityKills != null ? entityKills : new ArrayList<>(); }
    public void setStats(List<StatDep> stats) { this.stats = stats != null ? stats : new ArrayList<>(); }

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
                && getStats().isEmpty();
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
        copy.setLogic(getLogic());
        copy.setItems(getItems().stream().map(DependencyItem::copy).collect(Collectors.toList()));
        copy.setStages(new ArrayList<>(getStages()));
        copy.setIndividualStages(getIndividualStages().stream().map(IndividualStageDep::copy).collect(Collectors.toList()));
        copy.setAdvancements(new ArrayList<>(getAdvancements()));
        copy.setXpLevel(xpLevel != null ? xpLevel.copy() : null);
        copy.setEntityKills(getEntityKills().stream().map(EntityKillDep::copy).collect(Collectors.toList()));
        copy.setStats(getStats().stream().map(StatDep::copy).collect(Collectors.toList()));
        return copy;
    }
}
