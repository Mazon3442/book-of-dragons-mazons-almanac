package local.bodaddon.worldgen;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import local.bodaddon.BodAddon;
import local.bodaddon.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Places the hand-built tree saved in {@code data/bodaddon/structure/sage_tree.nbt}, with its trunk centred on the
 * feature origin and a random quarter-turn. Used for both wild trees and saplings. The trunk needs room; the canopy grows
 * around obstacles, and the tree is refused only if more than a quarter of its leaves would be blocked.
 */
public final class SageTreeFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "sage_tree");
    /** The trunk's column inside the saved structure (x, z). */
    private static final BlockPos TRUNK = new BlockPos(2, 0, 2);

    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, BodAddon.MOD_ID);
    public static final DeferredHolder<Feature<?>, SageTreeFeature> TYPE = FEATURES.register("sage_tree",
        () -> new SageTreeFeature(NoneFeatureConfiguration.CODEC));

    public static void register(IEventBus modBus) { FEATURES.register(modBus); }

    private SageTreeFeature(com.mojang.serialization.Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        var template = level.getLevel().getStructureManager().get(TEMPLATE).orElse(null);
        if (template == null || !level.getBlockState(origin.below()).is(BlockTags.DIRT)) return false;

        var random = context.random();
        var settings = new StructurePlaceSettings()
            .setRotation(Rotation.getRandom(random))
            .setRotationPivot(TRUNK)
            .setIgnoreEntities(true)
            .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR)
            .addProcessor(SKIP_OBSTRUCTED);
        BlockPos corner = origin.subtract(TRUNK);
        // The trunk must have room, but the canopy may grow around things in the way, as a natural tree does.
        boolean trunkClear = template.filterBlocks(corner, settings, Blocks.OAK_LOG).stream()
            .allMatch(info -> isClear(level.getBlockState(info.pos())));
        if (!trunkClear) return false;
        var leaves = template.filterBlocks(corner, settings, ModBlocks.SAGE_LEAVES.get());
        long blocked = leaves.stream().filter(info -> !isClear(level.getBlockState(info.pos()))).count();
        if (blocked * 4 > leaves.size()) return false;
        BoundingBox box = template.getBoundingBox(settings, corner);
        if (!template.placeInWorld(level, corner, corner, settings, random, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) return false;
        settleLeaves(level, box);
        return true;
    }

    /** Air, plants, snow and leaves can be grown through; anything solid cannot. */
    private static boolean isClear(BlockState state) {
        return state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.canBeReplaced();
    }

    /** Leaves that would land on a solid block are left out, so the tree never overwrites the terrain or a build. */
    private static final StructureProcessor SKIP_OBSTRUCTED = new StructureProcessor() {
        @Override
        public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos,
                StructureTemplate.StructureBlockInfo raw, StructureTemplate.StructureBlockInfo relative, StructurePlaceSettings settings) {
            return isClear(level.getBlockState(relative.pos())) ? relative : null;
        }

        @Override
        protected StructureProcessorType<?> getType() { return StructureProcessorType.NOP; }
    };

    /** The saved leaves are player-placed (persistent). Make them natural and give them real distances to the trunk. */
    private static void settleLeaves(WorldGenLevel level, BoundingBox box) {
        Map<BlockPos, Integer> distance = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos.betweenClosedStream(box).forEach(p -> {
            if (level.getBlockState(p).is(BlockTags.LOGS)) {
                distance.put(p.immutable(), 0);
                queue.add(p.immutable());
            }
        });
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            int next = distance.get(p) + 1;
            if (next > 6) continue;
            for (var dir : net.minecraft.core.Direction.values()) {
                BlockPos q = p.relative(dir);
                if (!distance.containsKey(q) && level.getBlockState(q).is(ModBlocks.SAGE_LEAVES.get())) {
                    distance.put(q, next);
                    queue.add(q);
                }
            }
        }
        BlockPos.betweenClosedStream(box).forEach(p -> {
            BlockState state = level.getBlockState(p);
            if (state.is(ModBlocks.SAGE_LEAVES.get())) {
                level.setBlock(p, state.setValue(LeavesBlock.PERSISTENT, false)
                    .setValue(LeavesBlock.DISTANCE, distance.getOrDefault(p, 7)), Block.UPDATE_CLIENTS);
            }
        });
    }
}
