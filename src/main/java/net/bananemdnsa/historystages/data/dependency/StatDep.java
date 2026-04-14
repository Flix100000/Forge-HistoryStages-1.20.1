package net.bananemdnsa.historystages.data.dependency;

import com.google.gson.annotations.SerializedName;

public class StatDep {
    @SerializedName("stat_id")
    private String statId;

    @SerializedName("min_value")
    private int minValue;

    public StatDep() {}

    public StatDep(String statId, int minValue) {
        this.statId = statId;
        this.minValue = Math.max(0, minValue);
    }

    public String getStatId() { return statId; }
    public int getMinValue() { return minValue; }

    public void setStatId(String statId) { this.statId = statId; }
    public void setMinValue(int minValue) { this.minValue = Math.max(0, minValue); }

    public StatDep copy() {
        return new StatDep(statId, minValue);
    }
}
