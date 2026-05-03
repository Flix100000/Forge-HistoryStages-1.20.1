package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.data.StageManager;

import java.util.Set;
import java.util.TreeSet;

public final class ClientStructureRegistry {
    private ClientStructureRegistry() {
    }

    public static Set<String> get() {
        Set<String> values = new TreeSet<>();
        StageManager.getStages().values().forEach(entry ->
                entry.getStructures().stream().filter(id -> !id.startsWith("#")).forEach(values::add));
        StageManager.getIndividualStages().values().forEach(entry ->
                entry.getStructures().stream().filter(id -> !id.startsWith("#")).forEach(values::add));
        return values;
    }

    public static Set<String> getTags() {
        Set<String> values = new TreeSet<>();
        StageManager.getStages().values().forEach(entry ->
                entry.getStructures().stream().filter(id -> id.startsWith("#")).map(id -> id.substring(1)).forEach(values::add));
        StageManager.getIndividualStages().values().forEach(entry ->
                entry.getStructures().stream().filter(id -> id.startsWith("#")).map(id -> id.substring(1)).forEach(values::add));
        return values;
    }
}
