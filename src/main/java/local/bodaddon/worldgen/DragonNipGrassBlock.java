package local.bodaddon.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Tall grass that spreads to nearby open ground when Bone Meal is used on it. */
public class DragonNipGrassBlock extends DoublePlantBlock implements BonemealableBlock {
    private static final int SPREAD_RADIUS = 2;
    private static final int MAX_NEW_PLANTS = 3;

    public DragonNipGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return !openSpots(level, base(pos, state)).isEmpty();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        List<BlockPos> spots = openSpots(level, base(pos, state));
        for (int planted = 0; planted < MAX_NEW_PLANTS && !spots.isEmpty(); planted++) {
            BlockPos spot = spots.remove(random.nextInt(spots.size()));
            // Planting can change the ground state for neighbors, so recheck before each one.
            if (canPlantAt(level, spot)) {
                DoublePlantBlock.placeAt(level, defaultBlockState(), spot, UPDATE_ALL);
            }
        }
    }

    private static BlockPos base(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    private boolean canPlantAt(LevelReader level, BlockPos spot) {
        return level.isEmptyBlock(spot) && level.isEmptyBlock(spot.above())
            && defaultBlockState().canSurvive(level, spot);
    }

    private List<BlockPos> openSpots(LevelReader level, BlockPos origin) {
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-SPREAD_RADIUS, -1, -SPREAD_RADIUS),
                origin.offset(SPREAD_RADIUS, 1, SPREAD_RADIUS))) {
            if (canPlantAt(level, p)) {
                spots.add(p.immutable());
            }
        }
        return spots;
    }
}
