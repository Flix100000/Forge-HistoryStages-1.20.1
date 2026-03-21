package net.bananemdnsa.historystages.data;

import java.util.ArrayList;
import java.util.List;

public class EntityLocks {
    private List<String> spawnlock;
    private List<String> attacklock;
    private List<String> modLinked;

    public EntityLocks() {
        this.spawnlock = new ArrayList<>();
        this.attacklock = new ArrayList<>();
        this.modLinked = new ArrayList<>();
    }

    public List<String> getSpawnlock() {
        return spawnlock != null ? spawnlock : new ArrayList<>();
    }

    public List<String> getAttacklock() {
        return attacklock != null ? attacklock : new ArrayList<>();
    }

    public List<String> getModLinked() {
        return modLinked != null ? modLinked : new ArrayList<>();
    }

    public void setSpawnlock(List<String> spawnlock) {
        this.spawnlock = spawnlock != null ? new ArrayList<>(spawnlock) : new ArrayList<>();
    }

    public void setAttacklock(List<String> attacklock) {
        this.attacklock = attacklock != null ? new ArrayList<>(attacklock) : new ArrayList<>();
    }

    public void setModLinked(List<String> modLinked) {
        this.modLinked = modLinked != null ? new ArrayList<>(modLinked) : new ArrayList<>();
    }
}
