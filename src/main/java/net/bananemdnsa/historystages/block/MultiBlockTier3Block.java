package net.bananemdnsa.historystages.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MultiBlockTier3Block extends MultiBlockResearchPedestalBlock {

    private static final VoxelShape[] FOOT = precomputeFacingShapes(makeFoot());
    private static final VoxelShape[] HEAD = precomputeFacingShapes(makeHead());

    public MultiBlockTier3Block(Properties props) {
        super(props);
    }

    @Override
    public int getTier() {
        return 3;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        VoxelShape[] shapes = state.getValue(PART) == Part.FOOT ? FOOT : HEAD;
        return shapes[state.getValue(FACING).get2DDataValue()];
    }

    /** Foot in default orientation (FACING=EAST, head extends +X = x in [1,2]). */
    private static VoxelShape makeFoot() {
        VoxelShape s = Shapes.empty();
        s = Shapes.or(s, footBox(0.1875, 0.1875, 0.1875, 1.8125, 0.625, 0.8125)); // plinth
        s = Shapes.or(s, footBox(0, 0, 0, 2, 0.1875, 1)); // base
        s = Shapes.or(s, footBox(0, 0.625, 0, 2, 0.9375, 1)); // cap
        // Pedestal 1
        s = Shapes.or(s, footBox(0.1875, 0.9375, 0.625, 0.375, 1, 0.8125));
        s = Shapes.or(s, footBox(0.21875, 1, 0.65625, 0.34375, 1.125, 0.78125));
        s = Shapes.or(s, footBox(0.15625, 1.125, 0.59375, 0.40625, 1.1875, 0.84375));
        s = Shapes.or(s, footBox(0.21875, 1.1875, 0.65625, 0.34375, 1.375, 0.78125));
        // Pedestal 2
        s = Shapes.or(s, footBox(0.5625, 0.9375, 0.6875, 0.75, 1, 0.875));
        s = Shapes.or(s, footBox(0.59375, 1, 0.71875, 0.71875, 1.1875, 0.84375));
        s = Shapes.or(s, footBox(0.53125, 1.1875, 0.65625, 0.78125, 1.25, 0.90625));
        s = Shapes.or(s, footBox(0.59375, 1.25, 0.71875, 0.71875, 1.4375, 0.84375));
        return s;
    }

    private static VoxelShape makeHead() {
        VoxelShape s = Shapes.empty();
        s = Shapes.or(s, headBox(0.1875, 0.1875, 0.1875, 1.8125, 0.625, 0.8125));
        s = Shapes.or(s, headBox(0, 0, 0, 2, 0.1875, 1));
        s = Shapes.or(s, headBox(0, 0.625, 0, 2, 0.9375, 1));
        return s;
    }
}
