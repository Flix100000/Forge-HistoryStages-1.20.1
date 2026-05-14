package net.bananemdnsa.historystages.data;

import com.google.gson.annotations.JsonAdapter;

import java.util.ArrayList;
import java.util.List;

public class EntityLocks {
    @JsonAdapter(EntitySpawnLockEntryListAdapter.class)
    private List<EntitySpawnLockEntry> spawnlock;
    private List<String> attacklock;
    private List<String> modLinked;

    public EntityLocks() {
        this.spawnlock = new ArrayList<>();
        this.attacklock = new ArrayList<>();
        this.modLinked = new ArrayList<>();
    }

    public List<EntitySpawnLockEntry> getSpawnlock() {
        return spawnlock != null ? spawnlock : new ArrayList<>();
    }

    public List<String> getAttacklock() {
        return attacklock != null ? attacklock : new ArrayList<>();
    }

    public List<String> getModLinked() {
        return modLinked != null ? modLinked : new ArrayList<>();
    }

    public void setSpawnlock(List<EntitySpawnLockEntry> spawnlock) {
        if (spawnlock == null) {
            this.spawnlock = new ArrayList<>();
            return;
        }
        List<EntitySpawnLockEntry> copy = new ArrayList<>(spawnlock.size());
        for (EntitySpawnLockEntry e : spawnlock) copy.add(e.copy());
        this.spawnlock = copy;
    }

    public void setAttacklock(List<String> attacklock) {
        this.attacklock = attacklock != null ? new ArrayList<>(attacklock) : new ArrayList<>();
    }

    public void setModLinked(List<String> modLinked) {
        this.modLinked = modLinked != null ? new ArrayList<>(modLinked) : new ArrayList<>();
    }

    /** Convenience: returns just the entity IDs in spawnlock (without source detail). */
    public List<String> getSpawnlockIds() {
        List<String> ids = new ArrayList<>(getSpawnlock().size());
        for (EntitySpawnLockEntry e : getSpawnlock()) ids.add(e.getId());
        return ids;
    }
}
