package net.bananemdnsa.historystages.data.dependency;

import com.google.gson.annotations.SerializedName;

public class ScoreboardDep {
    public static final String[] OPERATORS = { ">=", "<=", "==", ">", "<", "!=" };

    private String objective;

    @SerializedName("score_holder")
    private String scoreHolder;

    private String op;

    private int value;

    public ScoreboardDep() {}

    public ScoreboardDep(String objective, String scoreHolder, String op, int value) {
        this.objective = objective;
        this.scoreHolder = scoreHolder;
        this.op = normalizeOp(op);
        this.value = value;
    }

    public String getObjective() { return objective; }
    public String getScoreHolder() { return scoreHolder; }
    public String getOp() { return normalizeOp(op); }
    public int getValue() { return value; }

    public void setObjective(String objective) { this.objective = objective; }
    public void setScoreHolder(String scoreHolder) { this.scoreHolder = scoreHolder; }
    public void setOp(String op) { this.op = normalizeOp(op); }
    public void setValue(int value) { this.value = value; }

    /** True if score_holder is empty/null — i.e. evaluate against the acting player. */
    public boolean isPlayerSelf() {
        return scoreHolder == null || scoreHolder.isEmpty();
    }

    public boolean compare(int current) {
        return switch (getOp()) {
            case "<=" -> current <= value;
            case "==" -> current == value;
            case ">"  -> current >  value;
            case "<"  -> current <  value;
            case "!=" -> current != value;
            default   -> current >= value; // ">=" and any unknown fallback
        };
    }

    public ScoreboardDep copy() {
        return new ScoreboardDep(objective, scoreHolder, op, value);
    }

    private static String normalizeOp(String op) {
        if (op == null) return ">=";
        for (String valid : OPERATORS) {
            if (valid.equals(op)) return op;
        }
        return ">=";
    }
}
