package net.bananemdnsa.historystages.block;

import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ResearchPedestalBlock extends BaseEntityBlock {
    public static final MapCodec<ResearchPedestalBlock> CODEC = simpleCodec(ResearchPedestalBlock::new);
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    private static final VoxelShape SHAPE = makeShape();

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public ResearchPedestalBlock(Properties pProperties) {
        super(pProperties.noOcclusion().lightLevel(state -> state.getValue(LIT) ? 13 : 0));
        this.registerDefaultState(this.stateDefinition.any().setValue(WORKING, false).setValue(LIT, false));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        return 1.0F;
    }

    @Override
    public boolean isOcclusionShapeFullBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return false;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new ResearchPedestalBlockEntity(pPos, pState);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHit) {
        if (!pLevel.isClientSide()) {
            BlockEntity entity = pLevel.getBlockEntity(pPos);
            if (entity instanceof ResearchPedestalBlockEntity) {
                ((ServerPlayer) pPlayer).openMenu((MenuProvider) entity, pPos);
            }
        }
        return InteractionResult.sidedSuccess(pLevel.isClientSide());
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        if (pLevel.isClientSide()) return null;
        return createTickerHelper(pBlockEntityType, ModBlockEntities.RESEARCH_PEDESTAL_BE.get(),
                (level, pos, state, entity) -> ResearchPedestalBlockEntity.tick(level, pos, state, entity));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WORKING, LIT);
    }

    private static VoxelShape makeShape() {
        VoxelShape shape = Shapes.empty();
        shape = Shapes.join(shape, Shapes.box(0, 0.625, 0, 1, 0.9375, 1), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.1875, 0.1875, 0.1875, 0.8125, 0.625, 0.8125), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0, 0, 0, 1, 0.1875, 1), BooleanOp.OR);
        return shape;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
