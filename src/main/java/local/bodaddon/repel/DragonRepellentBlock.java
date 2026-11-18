package local.bodaddon.repel;

import com.mojang.serialization.MapCodec;
import local.bodaddon.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A crafted block of dragon repellent: keeps wild dragons well away, over a wider area than a root. */
public class DragonRepellentBlock extends BaseEntityBlock implements DragonRepeller {
    public static final MapCodec<DragonRepellentBlock> CODEC = simpleCodec(DragonRepellentBlock::new);
    public static final double RADIUS = 30.0;

    public DragonRepellentBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public double repelRadius() { return RADIUS; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RepelBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlocks.REPEL_ENTITY.get() ? null
            : (lvl, pos, st, be) -> RepelBlockEntity.serverTick(lvl, pos, st);
    }
}
