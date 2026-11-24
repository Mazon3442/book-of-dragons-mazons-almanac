package local.bodaddon.stable;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Stable Sign, a sign in four placements, all built from the vanilla sign boxes: a hanging sign on a bracket off the side of a
 * block, a hanging sign under a block, a standing sign on top of one, or (sneak while placing) a flat wall sign. The roost it works for is recorded when it is placed (see {@link StablePlateItem}); what it does is in
 * {@link StableEvents}. It carries one line of text, the stable's name, edited by right-clicking it.
 *
 * <p>FACING is the direction the sign looks: for a bracket, the way it points out of the wall (its board faces sideways to it);
 * for the others, the way the front of the board looks, toward the player who placed it.
 */
public final class StableSignBlock extends BaseEntityBlock {
    public static final MapCodec<StableSignBlock> CODEC = simpleCodec(StableSignBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);

    public enum Mount implements StringRepresentable {
        WALL, CEILING, FLOOR, FLAT;

        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    /**
     * Boxes as modelled, in 16ths: {x0, y0, z0, x1, y1, z1}. The wall bracket is modelled facing east (its wall at x = 0); the
     * others face south (front on the +z side, wall or support behind or above or below).
     */
    private static final double[][] WALL_BOXES = {{0, -2, 7.33, 16, 6, 8.67}, {0, 14, 7.33, 16, 16, 8.67}, {0, 9, 7.33, 1.5, 14, 8.67}, {14.5, 9, 7.33, 16, 14, 8.67}};
    private static final double[][] CEILING_BOXES = {{0, -4, 7.33, 16, 4, 8.67}, {2, 4, 7.5, 14, 16, 8.5}};
    private static final double[][] FLOOR_BOXES = {{7, 0, 7.33, 9, 9.33, 8.67}, {0, 9.33, 7.33, 16, 17.33, 8.67}};
    private static final double[][] FLAT_BOXES = {{0, 4.33, 0, 16, 12.33, 1.33}};
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][4];

    static {
        double[][][] boxes = {WALL_BOXES, CEILING_BOXES, FLOOR_BOXES, FLAT_BOXES};
        for (int m = 0; m < 4; m++) {
            for (int turns = 0; turns < 4; turns++) {
                VoxelShape shape = Shapes.empty();
                for (double[] b : boxes[m]) shape = Shapes.or(shape, turned(b, turns));
                SHAPES[m][turns] = shape;
            }
        }
    }

    /** Clockwise quarter turns, seen from above, from how the model is authored to the way this state faces. */
    public static int turns(BlockState state) {
        int d = state.getValue(FACING).get2DDataValue();   // south 0, west 1, north 2, east 3
        return state.getValue(MOUNT) == Mount.WALL ? (d + 1) % 4 : d;
    }

    /** One box turned clockwise by quarter turns, seen from above (north to east to south to west). */
    private static VoxelShape turned(double[] b, int turns) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        for (int i = 0; i < turns; i++) {
            double nx0 = 16 - z1, nx1 = 16 - z0, nz0 = x0, nz1 = x1;
            x0 = nx0; x1 = nx1; z0 = nz0; z1 = nz1;
        }
        return net.minecraft.world.level.block.Block.box(x0, b[1], z0, x1, b[4], z1);
    }

    public StableSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNT, Mount.WALL));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(MOUNT).ordinal()][turns(state)];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState state;
        if (face.getAxis().isHorizontal()) {
            boolean flat = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();   // sneak: flat on the wall
            state = defaultBlockState().setValue(FACING, face).setValue(MOUNT, flat ? Mount.FLAT : Mount.WALL);
        } else {
            Direction toPlayer = context.getHorizontalDirection().getOpposite();                  // the front looks at the player
            state = defaultBlockState().setValue(FACING, toPlayer).setValue(MOUNT, face == Direction.UP ? Mount.FLOOR : Mount.CEILING);
        }
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction support = switch (state.getValue(MOUNT)) {
            case WALL, FLAT -> state.getValue(FACING).getOpposite();
            case CEILING -> Direction.UP;
            case FLOOR -> Direction.DOWN;
        };
        BlockPos behind = pos.relative(support);
        return level.getBlockState(behind).isFaceSturdy(level, behind, support.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof StableSignBlockEntity sign) {
            if (player.isShiftKeyDown()) {
                StableEvents.status(server, pos, sign.name()).forEach(line -> player.displayClientMessage(line, false));
            } else {
                StablePayloads.sendOpen(sp, pos, sign.name());
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof StableSignBlockEntity sign) {
            sign.setOwner(player.getGameProfile().getName());
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StableSignBlockEntity(pos, state); }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) PlateData.get(server).removeSign(pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}
