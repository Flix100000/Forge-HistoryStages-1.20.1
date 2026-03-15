package net.bananemdnsa.historystages.data;

import java.util.ArrayList;
import java.util.List;

public class EntityLocks {
    private List<String> spawnlock;
    private List<String> attacklock;

    public List<String> getSpawnlock() {
        return spawnlock != null ? spawnlock : new ArrayList<>();
    }

    public List<String> getAttacklock() {
        return attacklock != null ? attacklock : new ArrayList<>();
    }
}
