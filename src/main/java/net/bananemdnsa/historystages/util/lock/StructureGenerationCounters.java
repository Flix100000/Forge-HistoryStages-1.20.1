package net.bananemdnsa.historystages.util.lock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How often each limited structure has already been placed, keyed by
 * {@code stageId|ruleId}.
 *
 * <p>World generation runs on worker threads and asks per structure per chunk, so every counter is
 * an {@link AtomicInteger} and a reservation is a compare-and-set loop. Reservations are
 * all-or-nothing: a structure covered by two stages must not charge one of them when the other is
 * already full, otherwise budgets drain without anything being built.
 */
public final class StructureGenerationCounters {

    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    public int get(String key) {
        AtomicInteger v = counts.get(key);
        return v == null ? 0 : v.get();
    }

    /**
     * Claims one slot for every key, or none at all.
     *
     * @param limits counter key -> limit
     * @return true if every key had budget left
     */
    public boolean tryReserve(Map<String, Integer> limits) {
        if (limits.isEmpty()) return true;

        List<String> taken = new ArrayList<>(limits.size());
        for (Map.Entry<String, Integer> e : limits.entrySet()) {
            if (claim(e.getKey(), e.getValue())) {
                taken.add(e.getKey());
            } else {
                release(taken);
                return false;
            }
        }
        return true;
    }

    private boolean claim(String key, int limit) {
        AtomicInteger counter = counts.computeIfAbsent(key, k -> new AtomicInteger());
        while (true) {
            int current = counter.get();
            if (current >= limit) return false;
            if (counter.compareAndSet(current, current + 1)) return true;
        }
    }

    /** Gives reserved slots back — used when world generation ended up not placing anything. */
    public void release(Collection<String> keys) {
        for (String key : keys) {
            AtomicInteger counter = counts.get(key);
            if (counter != null) counter.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    /** Clears the named counters, for rules that restart their budget with the phase. */
    public void reset(Collection<String> keys) {
        for (String key : keys) counts.remove(key);
    }

    public Map<String, Integer> snapshot() {
        Map<String, Integer> out = new LinkedHashMap<>();
        counts.forEach((k, v) -> {
            int value = v.get();
            if (value > 0) out.put(k, value);
        });
        return out;
    }

    public void restore(Map<String, Integer> values) {
        counts.clear();
        values.forEach((k, v) -> counts.put(k, new AtomicInteger(Math.max(0, v))));
    }
}
