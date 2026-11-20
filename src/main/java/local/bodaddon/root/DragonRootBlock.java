package local.bodaddon.root;

import com.mojang.serialization.MapCodec;
import local.bodaddon.ModBlocks;
import local.bodaddon.repel.DragonRepeller;
import local.bodaddon.repel.RepelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;

/** A bitter little plant that repels wild dragons once mature. */
public class DragonRootBlock extends BushBlock implements BonemealableBlock, EntityBlock, DragonRepeller {
    public static final double RADIUS = 6.0;
    public static final MapCodec<DragonRootBlock> CODEC = simpleCodec(DragonRootBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final int MATURE_AGE = 3;
    private static final VoxelShape[] SHAPES = {
        Block.box(5, 0, 5, 11, 5, 11),
        Block.box(4, 0, 4, 12, 8, 12),
        Block.box(3, 0, 3, 13, 11, 13),
        Block.box(3, 0, 3, 13, 12, 13)
    };

    public DragonRootBlock(Properties properties) {
        super(properties);
        // Existing saves and wild features without an explicit age should still contain mature roots.
        registerDefaultState(stateDefinition.any().setValue(AGE, MATURE_AGE));
    }

    @Override public double repelRadius() { return RADIUS; }

    @Override protected MapCodec<? extends BushBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(AGE)];
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < MATURE_AGE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isRandomlyTicking(state) || !level.isAreaLoaded(pos, 1) || level.getRawBrightness(pos, 0) < 9) {
            return;
        }

        int growthRollBound = level.getBlockState(pos.below()).getBlock() instanceof FarmBlock ? 2 : 5;
        if (CommonHooks.canCropGrow(level, pos, state, random.nextInt(growthRollBound) == 0)) {
            grow(level, pos, state);
            CommonHooks.fireCropGrowPost(level, pos, state);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(AGE) < MATURE_AGE;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        grow(level, pos, state);
    }

    private void grow(ServerLevel level, BlockPos pos, BlockState state) {
        int age = state.getValue(AGE);
        if (age < MATURE_AGE) {
            level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RepelBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlocks.REPEL_ENTITY.get() || state.getValue(AGE) < MATURE_AGE ? null
            : (lvl, pos, st, be) -> RepelBlockEntity.serverTick(lvl, pos, st);
    }
}
