package net.bananemdnsa.historystages.data.lock;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class StructureLocks {
    private List<String> structures;

    @SerializedName("mod_linked")
    private List<String> modLinked;

    /**
     * Subset of {@link #structures} that must not generate at all while the stage is locked.
     * Worldgen is global and permanent per chunk, so this only applies to global stages.
     */
    @SerializedName("block_generation")
    private List<String> blockGeneration;

    public StructureLocks() {
        this.structures = new ArrayList<>();
        this.modLinked = new ArrayList<>();
        this.blockGeneration = new ArrayList<>();
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

    public List<String> getBlockGeneration() {
        return blockGeneration != null ? blockGeneration : new ArrayList<>();
    }

    public void setBlockGeneration(List<String> blockGeneration) {
        this.blockGeneration = blockGeneration != null ? new ArrayList<>(blockGeneration) : new ArrayList<>();
    }
}
