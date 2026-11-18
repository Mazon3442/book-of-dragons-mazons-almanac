package local.bodaddon.root;

import com.mojang.serialization.MapCodec;
import local.bodaddon.ModBlocks;
import local.bodaddon.repel.DragonRepeller;
import local.bodaddon.repel.RepelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A bitter little plant. Wherever it grows, wild dragons keep their distance (see {@link RepelBlockEntity}). */
public class DragonRootBlock extends BushBlock implements EntityBlock, DragonRepeller {
    public static final double RADIUS = 6.0;
    public static final MapCodec<DragonRootBlock> CODEC = simpleCodec(DragonRootBlock::new);
    private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(3, 0, 3, 13, 12, 13);

    public DragonRootBlock(Properties properties) { super(properties); }

    @Override public double repelRadius() { return RADIUS; }

    @Override protected MapCodec<? extends BushBlock> codec() { return CODEC; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RepelBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlocks.REPEL_ENTITY.get() ? null
            : (lvl, pos, st, be) -> RepelBlockEntity.serverTick(lvl, pos, st);
    }
}
