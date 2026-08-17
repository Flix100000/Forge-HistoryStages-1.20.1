package net.bananemdnsa.historystages.data.lock;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class BiomeLocks {
    private List<String> biomes;

    @SerializedName("mod_linked")
    private List<String> modLinked;

    public BiomeLocks() {
        this.biomes = new ArrayList<>();
        this.modLinked = new ArrayList<>();
    }

    public List<String> getBiomes() {
        return biomes != null ? biomes : new ArrayList<>();
    }

    public List<String> getModLinked() {
        return modLinked != null ? modLinked : new ArrayList<>();
    }

    public void setBiomes(List<String> biomes) {
        this.biomes = biomes != null ? new ArrayList<>(biomes) : new ArrayList<>();
    }

    public void setModLinked(List<String> modLinked) {
        this.modLinked = modLinked != null ? new ArrayList<>(modLinked) : new ArrayList<>();
    }
}
