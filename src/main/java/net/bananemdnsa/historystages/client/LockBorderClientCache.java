package net.bananemdnsa.historystages.client;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * Client-side cache of locked-structure bounding boxes pushed by the server.
 * Read each frame by the lock-border renderer.
 */
public final class LockBorderClientCache {

    private static volatile List<BoundingBox> boxes = List.of();

    private LockBorderClientCache() {}

    public static List<BoundingBox> get() {
        return boxes;
    }

    public static void set(List<BoundingBox> next) {
        boxes = next == null ? List.of() : List.copyOf(next);
    }

    public static void clear() {
        boxes = List.of();
    }
}
