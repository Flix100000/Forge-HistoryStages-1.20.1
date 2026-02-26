package net.felix.historystages.data;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class StageEntry {
    @SerializedName("display_name")
    private String displayName;

    private List<String> items;
    private List<String> tags;
    private List<String> mods;
    private List<String> dimensions; // NEU

    public String getDisplayName() {
        return displayName != null ? displayName : "Unknown Stage";
    }

    public List<String> getItems() { return items != null ? items : new ArrayList<>(); }
    public List<String> getTags() { return tags != null ? tags : new ArrayList<>(); }
    public List<String> getMods() { return mods != null ? mods : new ArrayList<>(); }

    // NEU: Getter für Dimensionen
    public List<String> getDimensions() {
        return dimensions != null ? dimensions : new ArrayList<>();
    }
}