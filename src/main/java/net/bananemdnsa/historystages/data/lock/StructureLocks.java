package net.bananemdnsa.historystages.data.lock;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class StructureLocks {
    private List<String> structures;

    @SerializedName("mod_linked")
    private List<String> modLinked;

    /**
     * Generation restrictions for entries of {@link #structures}. Worldgen is global and permanent
     * per chunk, so this only applies to global stages.
     */
    @SerializedName("block_generation")
    private List<StructureGenerationRule> generationRules;

    public StructureLocks() {
        this.structures = new ArrayList<>();
        this.modLinked = new ArrayList<>();
        this.generationRules = new ArrayList<>();
    }

    public List<String> getStructures() {
        return structures != null ? structures : new ArrayList<>();
    }

    public List<String> getModLinked() {
        return modLinked != null ? modLinked : new ArrayList<>();
    }

    public void setStructures(List<String> structures) {
        this.structures = structures != null ? new ArrayList<>(structures) : new ArrayList<>();
    }

    public void setModLinked(List<String> modLinked) {
        this.modLinked = modLinked != null ? new ArrayList<>(modLinked) : new ArrayList<>();
    }

    public List<StructureGenerationRule> getGenerationRules() {
        return generationRules != null ? generationRules : new ArrayList<>();
    }

    /**
     * Rules without an id are dropped here, not just on load: the adapter writes a legacy rule as
     * a bare string, so a null id would put a JSON {@code null} into the array and produce a file
     * the same adapter cannot read back.
     */
    public void setGenerationRules(List<StructureGenerationRule> rules) {
        this.generationRules = new ArrayList<>();
        if (rules == null) return;
        for (StructureGenerationRule rule : rules) {
            if (rule != null && rule.id() != null && !rule.id().isEmpty()) this.generationRules.add(rule);
        }
    }
}
