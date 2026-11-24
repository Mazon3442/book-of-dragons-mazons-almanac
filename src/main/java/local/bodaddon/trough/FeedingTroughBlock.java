package local.bodaddon.trough;

import com.mojang.serialization.MapCodec;
import local.bodaddon.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A two-block-long wooden trough, built the way a bed is: a FOOT half (which owns the inventory and does the feeding) and
 * a HEAD half one block clockwise of it. Either half opens the same 27 slots; breaking either removes both, and the item
 * drops from the head half only (see the loot table), so it drops once however it is broken.
 */
public class FeedingTroughBlock extends BaseEntityBlock {
    public static final MapCodec<FeedingTroughBlock> CODEC = simpleCodec(FeedingTroughBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    private static final VoxelShape ALONG_X = Block.box(0, 0, 1, 16, 14, 15);
    private static final VoxelShape ALONG_Z = Block.box(1, 0, 0, 15, 14, 16);

    public FeedingTroughBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, PART);
    }

    /** Direction from a half to its partner. */
    private static Direction toOther(BlockState state) {
        Direction along = state.getValue(HorizontalDirectionalBlock.FACING).getClockWise();
        return state.getValue(PART) == BedPart.FOOT ? along : along.getOpposite();
    }

    private static BlockPos footPos(BlockState state, BlockPos pos) {
        return state.getValue(PART) == BedPart.FOOT ? pos : pos.relative(toOther(state));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos head = context.getClickedPos().relative(facing.getClockWise());
        if (!context.getLevel().getBlockState(head).canBeReplaced(context)) return null;
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        level.setBlock(pos.relative(toOther(state)), state.setValue(PART, BedPart.HEAD), 3);
        level.blockUpdated(pos, Blocks.AIR);
        state.updateNeighbourShapes(level, pos, 3);
    }

    /** A half whose partner is gone removes itself (with drops, which is how the item still drops from the head). */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == toOther(state)) {
            boolean partner = neighbor.is(this) && neighbor.getValue(PART) != state.getValue(PART)
                && neighbor.getValue(HorizontalDirectionalBlock.FACING) == state.getValue(HorizontalDirectionalBlock.FACING);
            return partner ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** In creative nothing should drop, so take the head out quietly before the foot goes. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) == BedPart.FOOT) {
            BlockPos head = pos.relative(toOther(state));
            if (level.getBlockState(head).is(this)) level.setBlock(head, Blocks.AIR.defaultBlockState(), 35);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HorizontalDirectionalBlock.FACING).getAxis() == Direction.Axis.X ? ALONG_Z : ALONG_X;
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    /** Only the foot half has a block entity. */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == BedPart.FOOT ? new FeedingTroughBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlocks.TROUGH_ENTITY.get(), FeedingTroughBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(footPos(state, pos)) instanceof FeedingTroughBlockEntity trough) {
            if (player.isShiftKeyDown() && level instanceof ServerLevel server) {
                trough.status(server).forEach(line -> player.displayClientMessage(line, false));
            } else {
                player.openMenu(trough);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FeedingTroughBlockEntity trough) {
            Containers.dropContents(level, pos, trough);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
