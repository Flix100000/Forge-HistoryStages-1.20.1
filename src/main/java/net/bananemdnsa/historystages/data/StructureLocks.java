package net.bananemdnsa.historystages.data;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class StructureLocks {
    private List<String> structures;

    @SerializedName("mod_linked")
    private List<String> modLinked;

    public StructureLocks() {
        this.structures = new ArrayList<>();
        this.modLinked = new ArrayList<>();
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
}
