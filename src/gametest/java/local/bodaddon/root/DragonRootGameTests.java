package local.bodaddon.root;

import local.bodaddon.ModBlocks;
import net.magister.bookofdragons.entity.ModEntities;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bodaddon")
@PrefixGameTestTemplate(false)
public final class DragonRootGameTests {
    /**
     * The game-test server has no player near the dragons, so the engine never walks them; this checks the root's decisions
     * (who gets sent away and who is left alone), not the walking itself.
     */
    @GameTest(template = "sage_empty", timeoutTicks = 200)
    public static void rootSendsOnlyNearbyWildDragonsAwayAndDropsTheirTarget(GameTestHelper helper) {
        BlockPos root = new BlockPos(40, 2, 40);
        helper.setBlock(root.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(root, ModBlocks.DRAGON_ROOT.get());
        helper.assertTrue(helper.getBlockEntity(root) instanceof local.bodaddon.repel.RepelBlockEntity, "Root must have its block entity");
        DragonBase wild = helper.spawn(ModEntities.TERRIBLE_TERROR.get(), 44, 2, 40);
        DragonBase far = helper.spawn(ModEntities.TERRIBLE_TERROR.get(), 40, 2, 52);
        DragonBase tame = helper.spawn(ModEntities.TERRIBLE_TERROR.get(), 40, 2, 45);
        DragonBase victim = helper.spawn(ModEntities.TERRIBLE_TERROR.get(), 40, 2, 43);
        tame.adopt(java.util.UUID.randomUUID());
        wild.setTarget(victim);
        helper.succeedWhen(() -> {
            helper.assertTrue(wild.getTarget() == null, "A wild dragon in range must lose its target");
            var route = wild.faunaMind().route();
            helper.assertTrue(route.hasRoute() || route.solving(), "A wild dragon in range must be sent away");
            var affected = local.bodaddon.repel.DragonRepel.wildDragonsNear(helper.getLevel(),
                helper.absoluteVec(root.getCenter()), DragonRootBlock.RADIUS);
            helper.assertTrue(affected.contains(wild) && affected.contains(victim), "Wild dragons in range must be affected");
            helper.assertTrue(!affected.contains(tame), "Tamed dragons must be left alone");
            helper.assertTrue(!affected.contains(far), "Dragons beyond 6 blocks must be left alone");
        });
    }

    @GameTest(template = "empty")
    public static void rootNeedsSoilAndDropsItself(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.assertTrue(!ModBlocks.DRAGON_ROOT.get().defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(pos)),
            "Dragon root must not grow on bare stone");
        helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
        helper.assertTrue(ModBlocks.DRAGON_ROOT.get().defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(pos)),
            "Dragon root must grow on grass");
        var drops = net.minecraft.world.level.block.Block.getDrops(ModBlocks.DRAGON_ROOT.get().defaultBlockState(), helper.getLevel(), helper.absolutePos(pos), null);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModBlocks.DRAGON_ROOT_ITEM.get()), "Dragon root must drop itself");
        helper.succeed();
    }
}
