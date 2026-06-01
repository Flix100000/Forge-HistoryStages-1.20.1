package net.bananemdnsa.historystages.block;

import com.mojang.serialization.MapCodec;
import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MultiBlockResearchPedestalBlock extends BaseEntityBlock implements TieredPedestal {
    public static final MapCodec<MultiBlockResearchPedestalBlock> CODEC = simpleCodec(MultiBlockResearchPedestalBlock::new);

    /** Pedestal tier (1-4). Subclasses (Tier3/4) override. */
    @Override
    public int getTier() {
        return 1;
    }

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty WORKING = ResearchPedestalBlock.WORKING;
    public static final BooleanProperty LIT = ResearchPedestalBlock.LIT;

    public MultiBlockResearchPedestalBlock(Properties pProperties) {
        super(pProperties.noOcclusion().lightLevel(state -> state.getValue(LIT) ? 13 : 0));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.FOOT)
                .setValue(WORKING, false)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) {
        return 1.0F;
    }

    @Override
    public boolean isOcclusionShapeFullBlock(BlockState s, BlockGetter w, BlockPos p) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, WORKING, LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction footToHead = ctx.getHorizontalDirection().getClockWise();
        BlockPos headPos = ctx.getClickedPos().relative(footToHead);
        if (ctx.getLevel().getBlockState(headPos).canBeReplaced(ctx)) {
            return this.defaultBlockState().setValue(FACING, footToHead).setValue(PART, Part.FOOT);
        }
        return null;
    }

    /** Build a box, bumping any zero-thickness dimension to 1/64 wide so the outline renders. */
    public static VoxelShape userBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        double eps = 1.0 / 64.0;
        if (x1 >= x2) { double m = (x1 + x2) / 2; x1 = m - eps; x2 = m + eps; }
        if (y1 >= y2) { double m = (y1 + y2) / 2; y1 = m - eps; y2 = m + eps; }
        if (z1 >= z2) { double m = (z1 + z2) / 2; z1 = m - eps; z2 = m + eps; }
        return Shapes.box(x1, y1, z1, x2, y2, z2);
    }

    /** Clamp a box to the foot block's x in [0,1]. Returns empty shape if no overlap. */
    public static VoxelShape footBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        double cx1 = Math.max(0, x1);
        double cx2 = Math.min(1, x2);
        if (cx1 >= cx2) return Shapes.empty();
        return userBox(cx1, y1, z1, cx2, y2, z2);
    }

    /** Clamp a box to the head block's x in [1,2], shifted to head local [0,1]. */
    public static VoxelShape headBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        double cx1 = Math.max(0, x1 - 1);
        double cx2 = Math.min(1, x2 - 1);
        if (cx1 >= cx2) return Shapes.empty();
        return userBox(cx1, y1, z1, cx2, y2, z2);
    }

    /** Rotate a shape 90° clockwise about the block's Y axis (looking down). */
    public static VoxelShape rotateY90(VoxelShape shape) {
        VoxelShape[] result = {Shapes.empty()};
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                result[0] = Shapes.or(result[0], Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
        return result[0];
    }

    /** Precompute the shape for all 4 horizontal facings, indexed by Direction.get2DDataValue().
     *  Input shape is in default orientation (FACING=EAST, no rotation). */
    public static VoxelShape[] precomputeFacingShapes(VoxelShape baseEast) {
        VoxelShape[] shapes = new VoxelShape[4];
        VoxelShape east = baseEast;
        VoxelShape south = rotateY90(east);
        VoxelShape west = rotateY90(south);
        VoxelShape north = rotateY90(west);
        shapes[Direction.SOUTH.get2DDataValue()] = south;
        shapes[Direction.WEST.get2DDataValue()] = west;
        shapes[Direction.NORTH.get2DDataValue()] = north;
        shapes[Direction.EAST.get2DDataValue()] = east;
        return shapes;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            BlockPos headPos = pos.relative(facing);
            level.setBlock(headPos, state.setValue(PART, Part.HEAD), 3);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction towardPartner = getPartnerDirection(state);
        if (direction == towardPartner) {
            return isPartnerValid(neighborState, state) ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            Direction partnerDir = getPartnerDirection(state);
            BlockPos partnerPos = pos.relative(partnerDir);
            BlockState partnerState = level.getBlockState(partnerPos);
            if (isPartnerValid(partnerState, state)) {
                level.setBlock(partnerPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, partnerPos, Block.getId(partnerState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!oldState.is(newState.getBlock())) {
            // Foot has the BE — drop its inventory (scroll + deposit slot).
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ResearchPedestalBlockEntity rpbe) {
                rpbe.dropContents(level, pos);
            }
            Direction partnerDir = getPartnerDirection(oldState);
            BlockPos partnerPos = pos.relative(partnerDir);
            BlockState partnerState = level.getBlockState(partnerPos);
            if (isPartnerValid(partnerState, oldState)) {
                level.removeBlock(partnerPos, false);
            }
        }
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos footPos = state.getValue(PART) == Part.HEAD
                    ? pos.relative(state.getValue(FACING).getOpposite())
                    : pos;
            BlockEntity be = level.getBlockEntity(footPos);
            if (be instanceof ResearchPedestalBlockEntity) {
                ((ServerPlayer) player).openMenu((MenuProvider) be, footPos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.FOOT ? new ResearchPedestalBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(PART) != Part.FOOT) return null;
        return createTickerHelper(type, ModBlockEntities.RESEARCH_PEDESTAL_BE.get(),
                (l, p, s, e) -> ResearchPedestalBlockEntity.tick(l, p, s, e));
    }

    private static Direction getPartnerDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(PART) == Part.FOOT ? facing : facing.getOpposite();
    }

    private boolean isPartnerValid(BlockState neighbor, BlockState self) {
        return neighbor.is(this)
                && neighbor.getValue(FACING) == self.getValue(FACING)
                && neighbor.getValue(PART) != self.getValue(PART);
    }

    public enum Part implements StringRepresentable {
        FOOT("foot"),
        HEAD("head");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
