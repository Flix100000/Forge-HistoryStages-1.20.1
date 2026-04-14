package net.bananemdnsa.historystages.data.dependency;

public class XpLevelDep {
    private int level;
    private boolean consume;

    public XpLevelDep() {}

    public XpLevelDep(int level, boolean consume) {
        this.level = Math.max(0, level);
        this.consume = consume;
    }

    public int getLevel() { return level; }
    public boolean isConsume() { return consume; }

    public void setLevel(int level) { this.level = Math.max(0, level); }
    public void setConsume(boolean consume) { this.consume = consume; }

    public XpLevelDep copy() {
        return new XpLevelDep(level, consume);
    }
}
