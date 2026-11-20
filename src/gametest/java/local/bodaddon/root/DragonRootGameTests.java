package local.bodaddon.root;

import java.util.List;
import local.bodaddon.ModBlocks;
import net.magister.bookofdragons.entity.ModEntities;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
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

    @GameTest(template = "empty")
    public static void rootSeedsCraftPlantGrowAndHarvest(GameTestHelper helper) {
        var level = helper.getLevel();
        var root = ModBlocks.DRAGON_ROOT.get();
        var seeds = ModBlocks.DRAGON_ROOT_SEEDS.get();
        var input = CraftingInput.of(2, 2, List.of(new ItemStack(ModBlocks.DRAGON_ROOT_ITEM.get()),
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        var seedStack = recipe.value().assemble(input, level.registryAccess());
        helper.assertTrue(seedStack.is(seeds) && seedStack.getCount() == 2, "One root must craft into two seeds");
        helper.assertTrue(root.asItem() == ModBlocks.DRAGON_ROOT_ITEM.get(), "Seeds must not replace the mature block item");

        BlockPos pos = new BlockPos(2, 2, 2);
        BlockPos absolutePos = helper.absolutePos(pos);
        BlockPos soil = absolutePos.below();
        helper.setBlock(pos.below(), Blocks.FARMLAND);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, seedStack);
        var hit = new BlockHitResult(soil.getCenter().relative(Direction.UP, 0.5), Direction.UP, soil, false);
        var useSeeds = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(seeds.useOn(useSeeds).consumesAction() && seedStack.getCount() == 1,
            "Planting must use one seed");

        var boneMeal = new ItemStack(Items.BONE_MEAL, DragonRootBlock.MATURE_AGE);
        player.setItemInHand(InteractionHand.MAIN_HAND, boneMeal);
        var cropHit = new BlockHitResult(absolutePos.getCenter(), Direction.UP, absolutePos, false);
        for (int age = 0; age < DragonRootBlock.MATURE_AGE; age++) {
            var state = level.getBlockState(absolutePos);
            helper.assertTrue(state.is(root) && state.getValue(DragonRootBlock.AGE) == age, "Unexpected growth stage " + age);
            helper.assertTrue(state.isRandomlyTicking(), "Immature roots must grow naturally");
            helper.assertTrue(state.getTicker(level, ModBlocks.REPEL_ENTITY.get()) == null, "Immature roots must not repel dragons");
            var drops = Block.getDrops(state, level, absolutePos, helper.getBlockEntity(pos));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(seeds) && drops.getFirst().getCount() == 1,
                "An immature plant must return one seed");
            Items.BONE_MEAL.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, cropHit));
        }

        var mature = level.getBlockState(absolutePos);
        helper.assertTrue(mature.equals(root.defaultBlockState()) && boneMeal.isEmpty(), "Bone meal must reach the mature stage");
        helper.assertTrue(mature.getTicker(level, ModBlocks.REPEL_ENTITY.get()) != null, "The grown root must repel dragons");
        var drops = Block.getDrops(mature, level, absolutePos, helper.getBlockEntity(pos));
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModBlocks.DRAGON_ROOT_ITEM.get()),
            "A fully grown plant must yield Dragon Root");
        helper.succeed();
    }
}
