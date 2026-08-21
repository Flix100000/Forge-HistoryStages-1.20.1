package net.bananemdnsa.historystages.util.lock;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StructureGenerationCountersTest {

    @Test
    void reservationsStopAtTheLimit() {
        StructureGenerationCounters c = new StructureGenerationCounters();
        Map<String, Integer> limits = Map.of("bronze|a:hut", 2);

        assertTrue(c.tryReserve(limits));
        assertTrue(c.tryReserve(limits));
        assertFalse(c.tryReserve(limits));
        assertEquals(2, c.get("bronze|a:hut"));
    }

    @Test
    void releasingGivesTheSlotBack() {
        StructureGenerationCounters c = new StructureGenerationCounters();
        Map<String, Integer> limits = Map.of("bronze|a:hut", 1);

        assertTrue(c.tryReserve(limits));
        c.release(limits.keySet());
        assertEquals(0, c.get("bronze|a:hut"));
        assertTrue(c.tryReserve(limits), "the freed slot must be usable again");
    }

    @Test
    void aFullKeyRollsBackTheOnesAlreadyTaken() {
        StructureGenerationCounters c = new StructureGenerationCounters();
        c.tryReserve(Map.of("iron|a:hut", 1));       // iron is now full

        assertFalse(c.tryReserve(Map.of("bronze|a:hut", 5, "iron|a:hut", 1)));
        assertEquals(0, c.get("bronze|a:hut"), "bronze must not be charged for a failed placement");
        assertEquals(1, c.get("iron|a:hut"));
    }

    @Test
    void emptyLimitsAlwaysReserve() {
        assertTrue(new StructureGenerationCounters().tryReserve(Map.of()));
    }

    @Test
    void resetClearsOnlyTheNamedKeys() {
        StructureGenerationCounters c = new StructureGenerationCounters();
        c.tryReserve(Map.of("bronze|a:hut", 5, "bronze|a:tent", 5));

        c.reset(java.util.Set.of("bronze|a:hut"));

        assertEquals(0, c.get("bronze|a:hut"));
        assertEquals(1, c.get("bronze|a:tent"));
    }

    @Test
    void snapshotAndRestoreRoundTrip() {
        StructureGenerationCounters c = new StructureGenerationCounters();
        c.tryReserve(Map.of("bronze|a:hut", 5));
        c.tryReserve(Map.of("bronze|a:hut", 5));

        StructureGenerationCounters restored = new StructureGenerationCounters();
        restored.restore(c.snapshot());

        assertEquals(2, restored.get("bronze|a:hut"));
    }

    @Test
    void parallelReservationsNeverExceedTheLimit() throws Exception {
        StructureGenerationCounters c = new StructureGenerationCounters();
        Map<String, Integer> limits = Map.of("bronze|a:hut", 10);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int n = 0; n < 100; n++) {
                    if (c.tryReserve(limits)) granted.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(10, granted.get());
        assertEquals(10, c.get("bronze|a:hut"));
    }

    @Test
    void keysAreReportedForPersistence() {
        StructureGenerationCounters c = new StructureGenerationCounters();
        c.tryReserve(Map.of("bronze|a:hut", 5));
        assertEquals(List.of("bronze|a:hut"), List.copyOf(c.snapshot().keySet()));
    }
}
