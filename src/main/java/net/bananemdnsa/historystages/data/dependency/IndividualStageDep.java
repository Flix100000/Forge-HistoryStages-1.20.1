package net.bananemdnsa.historystages.data.dependency;

import com.google.gson.annotations.SerializedName;

public class IndividualStageDep {
    @SerializedName("stage_id")
    private String stageId;

    private String mode; // "all_online" or "all_ever"

    public IndividualStageDep() {
        this.mode = "all_online";
    }

    public IndividualStageDep(String stageId, String mode) {
        this.stageId = stageId;
        this.mode = mode != null ? mode : "all_online";
    }

    public String getStageId() { return stageId; }
    public String getMode() { return mode != null ? mode : "all_online"; }

    public void setStageId(String stageId) { this.stageId = stageId; }
    public void setMode(String mode) { this.mode = mode; }

    public boolean isAllEver() { return "all_ever".equals(mode); }

    public IndividualStageDep copy() {
        return new IndividualStageDep(stageId, mode);
    }
}
