package local.bodaddon.repel;

import local.bodaddon.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Once a second, drives wild dragons out of the radius of its {@link DragonRepeller} block. Tamed dragons are left alone. */
public class RepelBlockEntity extends BlockEntity {
    private static final int PERIOD = 20;

    public RepelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.REPEL_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % PERIOD != 0
            || !(state.getBlock() instanceof DragonRepeller repeller)) return;
        DragonRepel.repel(server, pos.getCenter(), repeller.repelRadius());
    }
}
