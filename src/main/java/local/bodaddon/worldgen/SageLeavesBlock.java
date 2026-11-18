package local.bodaddon.worldgen;

import local.bodaddon.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/** Leaves of the sage tree. Leaves attached to a trunk slowly ripen fruit that is picked with a right click. */
public final class SageLeavesBlock extends LeavesBlock {
    public static final BooleanProperty FRUITING = BooleanProperty.create("fruiting");
    /** Each random tick of an unripe, trunk-connected leaf has a 1 in this many chance to ripen. */
    private static final int RIPEN_ODDS = 60;

    public SageLeavesBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FRUITING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FRUITING);
    }

    private static boolean attached(BlockState state) {
        return state.getValue(DISTANCE) < 7;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return super.isRandomlyTicking(state) || (!state.getValue(FRUITING) && attached(state));
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        BlockState now = level.getBlockState(pos);
        if (!now.is(this) || now.getValue(FRUITING) || !attached(now) || random.nextInt(RIPEN_ODDS) != 0) return;
        // Keep fruit sparse: no ripening next to fruit that is already hanging.
        boolean crowded = BlockPos.betweenClosedStream(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))
            .anyMatch(p -> level.getBlockState(p).is(this) && level.getBlockState(p).getValue(FRUITING));
        if (!crowded) level.setBlock(pos, now.setValue(FRUITING, true), Block.UPDATE_CLIENTS);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(FRUITING)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            popResource(level, pos, new ItemStack(ModItems.SAGEFRUIT.get()));
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
            level.setBlock(pos, state.setValue(FRUITING, false), Block.UPDATE_CLIENTS);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
