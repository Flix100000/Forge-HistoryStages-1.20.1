package net.bananemdnsa.historystages.data;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class StageEntry {
    @SerializedName("display_name")
    private String displayName;

    @SerializedName("research_time")
    private int researchTime; // 0 = use global config default

    private List<String> items;
    private List<String> tags;
    private List<String> mods;
    private List<String> dimensions; // NEU
    private List<String> entities;

    public String getDisplayName() {
        return displayName != null ? displayName : "Unknown Stage";
    }

    public int getResearchTime() {
        return researchTime; // 0 means "use global default from config"
    }

    public List<String> getItems() { return items != null ? items : new ArrayList<>(); }
    public List<String> getTags() { return tags != null ? tags : new ArrayList<>(); }
    public List<String> getMods() { return mods != null ? mods : new ArrayList<>(); }

    // NEU: Getter für Dimensionen
    public List<String> getDimensions() {
        return dimensions != null ? dimensions : new ArrayList<>();
    }

    public List<String> getEntities() {
        return entities != null ? entities : new ArrayList<>();
    }
}