package net.bananemdnsa.historystages.data.auto;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;

import java.util.ArrayList;
import java.util.List;

@JsonAdapter(AutoTriggerAdapter.class)
public class AutoTrigger {

    @SerializedName("mode")
    private String mode;            // "any" | "all", null → "any"

    @SerializedName("triggers")
    private List<TriggerCondition> triggers;

    public AutoTrigger() {
        this.triggers = new ArrayList<>();
    }

    public AutoTrigger(String mode, List<TriggerCondition> triggers) {
        this.mode = mode;
        this.triggers = triggers != null ? new ArrayList<>(triggers) : new ArrayList<>();
    }

    /** Returns "any" or "all". Defaults to "any". */
    public String resolvedMode() {
        if (mode == null) return "any";
        String s = mode.toLowerCase();
        return s.equals("all") ? "all" : "any";
    }

    public String getRawMode() { return mode; }

    public void setMode(String mode) { this.mode = mode; }

    public List<TriggerCondition> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<TriggerCondition> triggers) {
        this.triggers = triggers != null ? new ArrayList<>(triggers) : new ArrayList<>();
    }

    public boolean isEmpty() {
        return triggers == null || triggers.isEmpty();
    }

    public AutoTrigger copy() {
        // TriggerCondition impls are records — immutable, safe to share refs.
        return new AutoTrigger(mode, new ArrayList<>(getTriggers()));
    }
}
