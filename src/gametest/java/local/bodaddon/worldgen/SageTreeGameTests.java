package local.bodaddon.worldgen;

import local.bodaddon.ModBlocks;
import local.bodaddon.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bodaddon")
@PrefixGameTestTemplate(false)
public final class SageTreeGameTests {
    @GameTest(template = "empty")
    public static void sageFeaturePlacesFruitLeavesAndSaplingGrowsSameTree(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        clearAndSoil(helper, pos);
        ConfiguredFeature<?, ?> feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(ModBlocks.SAGE_TREE);
        helper.assertTrue(feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(17), pos),
            "Configured tree must place successfully on grass");
        helper.assertTrue(level.getBlockState(pos).is(Blocks.OAK_LOG), "Sage tree must have its oak trunk");
        helper.assertTrue(BlockPos.betweenClosedStream(pos.offset(-3, 1, -3), pos.offset(3, 10, 3))
            .anyMatch(p -> level.getBlockState(p).is(ModBlocks.SAGE_LEAVES.get())), "Tree must generate sage leaves");
        helper.assertTrue(level.getBlockState(pos.above(4)).is(Blocks.OAK_LOG) && !level.getBlockState(pos.above(5)).is(Blocks.OAK_LOG),
            "The hand-built tree has a five-log trunk");
        var leafPositions = BlockPos.betweenClosedStream(pos.offset(-3, 0, -3), pos.offset(3, 8, 3))
            .filter(p -> level.getBlockState(p).is(ModBlocks.SAGE_LEAVES.get())).map(BlockPos::immutable).toList();
        helper.assertTrue(leafPositions.size() > 50, "Tree must keep its full canopy");
        for (BlockPos p : leafPositions) {
            var state = level.getBlockState(p);
            helper.assertTrue(!state.getValue(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.DISTANCE) < 7,
                "Placed leaves must be natural and connected to the trunk");
            state.randomTick(level, p, RandomSource.create(3));
            helper.assertTrue(level.getBlockState(p).is(ModBlocks.SAGE_LEAVES.get()), "Connected leaves must not decay");
        }
        clearAndSoil(helper, pos);
        level.setBlockAndUpdate(pos, ModBlocks.SAGE_SAPLING.get().defaultBlockState());
        for (int i = 0; i < 2; i++) {
            ModBlocks.SAGE_SAPLING.get().performBonemeal(level, RandomSource.create(19), pos, level.getBlockState(pos));
        }
        helper.assertTrue(level.getBlockState(pos).is(Blocks.OAK_LOG), "Sage sapling must grow a sage tree with bonemeal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void forestBiomesReceiveRareTreeFeature(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        ResourceKey<PlacedFeature> placedKey = ResourceKey.create(Registries.PLACED_FEATURE,
            ResourceLocation.fromNamespaceAndPath("bodaddon", "sage_tree"));
        var placed = access.registryOrThrow(Registries.PLACED_FEATURE).getHolderOrThrow(placedKey);
        for (var biomeKey : java.util.List.of(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST,
            Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST)) {
            var biome = access.registryOrThrow(Registries.BIOME).getOrThrow(biomeKey);
            helper.assertTrue(biome.getGenerationSettings().features().get(GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()).contains(placed),
                "Forest biome must receive the sage tree: " + biomeKey.location());
        }
        var desert = access.registryOrThrow(Registries.BIOME).getOrThrow(Biomes.DESERT);
        helper.assertTrue(desert.getGenerationSettings().features().stream().noneMatch(set -> set.contains(placed)),
            "Sage trees must not generate in deserts");
        var json = PlacedFeature.DIRECT_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, placed.value()).getOrThrow();
        helper.assertTrue(json.toString().contains("chance:300"), "Wild trees must use one placement attempt per 300 eligible chunks on average");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void leafLootDropsFruitOnlyWhenRipeAndRespectsShears(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var leaves = ModBlocks.SAGE_LEAVES.get().defaultBlockState();
        var ripe = leaves.setValue(SageLeavesBlock.FRUITING, true);
        int saplings = 0;
        for (int i = 0; i < 512; i++) {
            for (ItemStack stack : Block.getDrops(leaves, level, pos, null)) {
                helper.assertTrue(!stack.is(ModItems.SAGEFRUIT.get()) && !stack.is(Items.APPLE), "Unripe leaves must not drop fruit");
                if (stack.is(ModBlocks.SAGE_SAPLING_ITEM.get())) saplings += stack.getCount();
            }
            int fruit = Block.getDrops(ripe, level, pos, null).stream()
                .filter(s -> s.is(ModItems.SAGEFRUIT.get())).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(fruit == 1, "Ripe leaves must always drop one sagefruit");
        }
        helper.assertTrue(saplings > 0, "Leaves must yield renewable saplings");
        var sheared = Block.getDrops(ripe, level, pos, null, null, new ItemStack(Items.SHEARS));
        helper.assertTrue(sheared.size() == 1 && sheared.getFirst().is(ModBlocks.SAGE_LEAVES_ITEM.get()),
            "Shears must collect leaves without extra fruit or saplings");
        helper.assertTrue(leaves.is(BlockTags.LEAVES) && ModBlocks.SAGE_SAPLING.get().defaultBlockState().is(BlockTags.SAPLINGS),
            "New tree blocks must participate in vanilla tree tags");
        level.setBlockAndUpdate(pos, leaves);
        leaves.randomTick(level, pos, RandomSource.create(7));
        helper.assertTrue(level.isEmptyBlock(pos), "Unsupported natural leaves must decay");
        var saplingDrops = Block.getDrops(ModBlocks.SAGE_SAPLING.get().defaultBlockState(), level, pos, null);
        helper.assertTrue(saplingDrops.size() == 1 && saplingDrops.getFirst().is(ModBlocks.SAGE_SAPLING_ITEM.get()),
            "Broken saplings must be recoverable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void connectedLeavesRipenAndRightClickPicksFruit(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        clearAndSoil(helper, pos);
        level.setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState());
        var attached = ModBlocks.SAGE_LEAVES.get().defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        BlockPos leaf = pos.above();
        level.setBlockAndUpdate(leaf, attached);
        for (int i = 0; i < 2000 && !level.getBlockState(leaf).getValue(SageLeavesBlock.FRUITING); i++) {
            level.getBlockState(leaf).randomTick(level, leaf, level.random);
        }
        helper.assertTrue(level.getBlockState(leaf).getValue(SageLeavesBlock.FRUITING), "Leaves connected to a trunk must ripen");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var hit = new BlockHitResult(Vec3.atCenterOf(leaf), Direction.UP, leaf, false);
        var result = level.getBlockState(leaf).useWithoutItem(level, player, hit);
        helper.assertTrue(result.consumesAction(), "Right click must pick ripe fruit");
        helper.assertTrue(!level.getBlockState(leaf).getValue(SageLeavesBlock.FRUITING), "Picked leaves must be bare again");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(leaf).inflate(2)).stream()
            .anyMatch(e -> e.getItem().is(ModItems.SAGEFRUIT.get())), "Picking must drop sagefruit");
        helper.assertTrue(!level.getBlockState(leaf).useWithoutItem(level, player, hit).consumesAction(), "Bare leaves must not be pickable");

        BlockPos neighbour = leaf.east();
        level.setBlockAndUpdate(leaf, attached.setValue(SageLeavesBlock.FRUITING, true));
        level.setBlockAndUpdate(neighbour, attached);
        for (int i = 0; i < 2000; i++) level.getBlockState(neighbour).randomTick(level, neighbour, level.random);
        helper.assertTrue(!level.getBlockState(neighbour).getValue(SageLeavesBlock.FRUITING), "Fruit must not ripen beside other fruit");

        BlockPos loose = pos.offset(0, 6, 0);
        level.setBlockAndUpdate(loose, ModBlocks.SAGE_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        for (int i = 0; i < 2000; i++) level.getBlockState(loose).randomTick(level, loose, level.random);
        helper.assertTrue(!level.getBlockState(loose).getValue(SageLeavesBlock.FRUITING), "Leaves with no trunk must not ripen");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void saplingGrowsAmongPlantsAndBuildsButNotIntoSolidTrunkSpace(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        clearAndSoil(helper, pos);
        // Tall grass, a flower and snow in the canopy space, and a couple of solid blocks the canopy has to grow around.
        level.setBlockAndUpdate(pos.offset(2, 0, 0), Blocks.SHORT_GRASS.defaultBlockState());
        level.setBlockAndUpdate(pos.offset(-1, 0, 2), Blocks.DANDELION.defaultBlockState());
        level.setBlockAndUpdate(pos.offset(1, 5, 1), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos.offset(-2, 4, 0), Blocks.OAK_PLANKS.defaultBlockState());
        for (int attempt = 0; attempt < 12 && !level.getBlockState(pos).is(Blocks.OAK_LOG); attempt++) {
            level.setBlockAndUpdate(pos, ModBlocks.SAGE_SAPLING.get().defaultBlockState());
            for (int i = 0; i < 2; i++) {
                ModBlocks.SAGE_SAPLING.get().performBonemeal(level, RandomSource.create(attempt), pos, level.getBlockState(pos));
            }
        }
        helper.assertTrue(level.getBlockState(pos).is(Blocks.OAK_LOG), "A sapling must grow into a tree among plants and a few obstacles");
        helper.assertTrue(level.getBlockState(pos.offset(1, 5, 1)).is(Blocks.STONE) && level.getBlockState(pos.offset(-2, 4, 0)).is(Blocks.OAK_PLANKS),
            "The tree must not replace solid blocks");

        clearAndSoil(helper, pos);
        level.setBlockAndUpdate(pos.above(3), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, ModBlocks.SAGE_SAPLING.get().defaultBlockState());
        for (int i = 0; i < 6; i++) {
            ModBlocks.SAGE_SAPLING.get().performBonemeal(level, RandomSource.create(i), pos, level.getBlockState(pos));
        }
        helper.assertTrue(level.getBlockState(pos).is(ModBlocks.SAGE_SAPLING.get()), "A blocked trunk must leave the sapling alone");

        clearAndSoil(helper, pos);
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, 2, -2), pos.offset(2, 7, 2))) {
            if (p.getX() != pos.getX() || p.getZ() != pos.getZ()) level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(pos, ModBlocks.SAGE_SAPLING.get().defaultBlockState());
        for (int i = 0; i < 6; i++) {
            ModBlocks.SAGE_SAPLING.get().performBonemeal(level, RandomSource.create(i), pos, level.getBlockState(pos));
        }
        helper.assertTrue(level.getBlockState(pos).is(ModBlocks.SAGE_SAPLING.get()), "A canopy walled in by stone must not grow a mangled tree");
        helper.succeed();
    }

    private static void clearAndSoil(GameTestHelper helper, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 11, 4))) {
            helper.getLevel().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, -1, 4))) {
            helper.getLevel().setBlockAndUpdate(p, Blocks.GRASS_BLOCK.defaultBlockState());
        }
    }
}
