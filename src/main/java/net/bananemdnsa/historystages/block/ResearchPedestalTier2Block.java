package net.bananemdnsa.historystages.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ResearchPedestalTier2Block extends ResearchPedestalBlock {
    public static final MapCodec<ResearchPedestalTier2Block> CODEC = simpleCodec(ResearchPedestalTier2Block::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Collision shapes for all 4 horizontal facings, base shape treated as FACING=EAST. */
    private static final VoxelShape[] SHAPES = MultiBlockResearchPedestalBlock.precomputeFacingShapes(makeShape());

    public ResearchPedestalTier2Block(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(WORKING, false)
                .setValue(LIT, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    public int getTier() {
        return 2;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    private static VoxelShape makeShape() {
        VoxelShape s = Shapes.empty();
        s = Shapes.or(s, MultiBlockResearchPedestalBlock.userBox(0, 0.625, 0, 1, 0.9375, 1));
        s = Shapes.or(s, MultiBlockResearchPedestalBlock.userBox(0.1875, 0.1875, 0.1875, 0.8125, 0.625, 0.8125));
        s = Shapes.or(s, MultiBlockResearchPedestalBlock.userBox(0, 0, 0, 1, 0.1875, 1));
        s = Shapes.or(s, MultiBlockResearchPedestalBlock.userBox(0.6875, 0.9375, 0.75, 0.875, 1, 0.9375));
        s = Shapes.or(s, MultiBlockResearchPedestalBlock.userBox(0.65625, 1.125, 0.71875, 0.90625, 1.1875, 0.96875));
        s = Shapes.or(s, MultiBlockResearchPedestalBlock.userBox(0.71875, 1, 0.78125, 0.84375, 1.125, 0.90625));
        s = Shapes.or(s, MultiBlockResearchPedestalBlock.userBox(0.71875, 1.1875, 0.78125, 0.84375, 1.375, 0.90625));
        return s;
    }
}
