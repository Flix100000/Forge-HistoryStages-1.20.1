package net.bananemdnsa.historystages.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;

/**
 * A connected lock zone for one structure. The actual lock area is the union of
 * {@code lockShapes} — inflated piece bounding boxes plus connector boxes that
 * bridge nearby pieces. The {@code bounds} field is the axis-aligned envelope of
 * those shapes and is used as a cheap broad-phase test before iterating shapes.
 *
 * <p>{@code pieceBoxes} retains the original, un-inflated piece BBs purely for
 * debug rendering (white outlines of the actual structure geometry).
 */
public record StructureCluster(
        BoundingBox bounds,
        Holder.Reference<Structure> structure,
        List<BoundingBox> pieceBoxes,
        List<BoundingBox> lockShapes
) {
    public boolean contains(BlockPos pos) {
        if (!bounds.isInside(pos)) return false;
        for (BoundingBox shape : lockShapes) {
            if (shape.isInside(pos)) return true;
        }
        return false;
    }
}
