package local.bodaddon;

import java.util.Optional;
import local.bodaddon.root.DragonRootBlock;
import local.bodaddon.repel.DragonRepellentBlock;
import local.bodaddon.repel.RepelBlockEntity;
import local.bodaddon.stable.StableSignBlock;
import local.bodaddon.stable.StableSignBlockEntity;
import local.bodaddon.trough.FeedingTroughBlock;
import local.bodaddon.trough.FeedingTroughBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import local.bodaddon.worldgen.SageLeavesBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, BodAddon.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BodAddon.MOD_ID);

    public static final ResourceKey<ConfiguredFeature<?, ?>> SAGE_TREE = ResourceKey.create(Registries.CONFIGURED_FEATURE,
        ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "sage_tree"));
    private static final TreeGrower SAGE_GROWER = new TreeGrower("bodaddon:sage", Optional.empty(), Optional.of(SAGE_TREE), Optional.empty());

    public static final DeferredHolder<Block, SageLeavesBlock> SAGE_LEAVES = BLOCKS.register("sage_leaves",
        () -> new SageLeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    public static final DeferredHolder<Block, SaplingBlock> SAGE_SAPLING = BLOCKS.register("sage_sapling",
        () -> new SaplingBlock(SAGE_GROWER, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING)));
    public static final DeferredHolder<Item, BlockItem> SAGE_LEAVES_ITEM = ModItems.ITEMS.register("sage_leaves",
        () -> new BlockItem(SAGE_LEAVES.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> SAGE_SAPLING_ITEM = ModItems.ITEMS.register("sage_sapling",
        () -> new BlockItem(SAGE_SAPLING.get(), new Item.Properties()));

    // Same block class as vanilla tall grass; harvesting rules live in its loot table.
    public static final DeferredHolder<Block, DoublePlantBlock> DRAGON_NIP_GRASS = BLOCKS.register("dragon_nip_grass",
        () -> new DoublePlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS)));
    public static final DeferredHolder<Item, DoubleHighBlockItem> DRAGON_NIP_GRASS_ITEM = ModItems.ITEMS.register("dragon_nip_grass",
        () -> new DoubleHighBlockItem(DRAGON_NIP_GRASS.get(), new Item.Properties()));

    public static final DeferredHolder<Block, DragonRootBlock> DRAGON_ROOT = BLOCKS.register("dragon_root",
        () -> new DragonRootBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DEAD_BUSH).noOcclusion()));
    public static final DeferredHolder<Item, BlockItem> DRAGON_ROOT_ITEM = ModItems.ITEMS.register("dragon_root",
        () -> new BlockItem(DRAGON_ROOT.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
                    java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
                tooltip.add(net.minecraft.network.chat.Component.translatable("item.bodaddon.dragon_root.desc1").withStyle(net.minecraft.ChatFormatting.GRAY));
                tooltip.add(net.minecraft.network.chat.Component.translatable("item.bodaddon.dragon_root.desc2").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        });
    public static final DeferredHolder<Block, DragonRepellentBlock> DRAGON_REPELLENT_BLOCK = BLOCKS.register("dragon_repellent_block",
        () -> new DragonRepellentBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.MOSS)));
    public static final DeferredHolder<Item, BlockItem> DRAGON_REPELLENT_BLOCK_ITEM = ModItems.ITEMS.register("dragon_repellent_block",
        () -> new BlockItem(DRAGON_REPELLENT_BLOCK.get(), new Item.Properties()));
    /** Both repelling blocks share one block entity type: the radius comes from the block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RepelBlockEntity>> REPEL_ENTITY = BLOCK_ENTITIES.register("repel",
        () -> BlockEntityType.Builder.of(RepelBlockEntity::new, DRAGON_ROOT.get(), DRAGON_REPELLENT_BLOCK.get()).build(null));

    public static final DeferredHolder<Block, FeedingTroughBlock> FEEDING_TROUGH = BLOCKS.register("feeding_trough",
        () -> new FeedingTroughBlock(BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

    public static final DeferredHolder<Block, StableSignBlock> STABLE_SIGN = BLOCKS.register("stable_sign",
        () -> new StableSignBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().noCollission()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StableSignBlockEntity>> STABLE_SIGN_ENTITY = BLOCK_ENTITIES.register("stable_sign",
        () -> BlockEntityType.Builder.of(StableSignBlockEntity::new, STABLE_SIGN.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FeedingTroughBlockEntity>> TROUGH_ENTITY = BLOCK_ENTITIES.register("feeding_trough",
        () -> BlockEntityType.Builder.of(FeedingTroughBlockEntity::new, FEEDING_TROUGH.get()).build(null));

    public static final DeferredHolder<Item, BlockItem> FEEDING_TROUGH_ITEM = ModItems.ITEMS.register("feeding_trough",
        () -> new BlockItem(FEEDING_TROUGH.get(), new Item.Properties()));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    private ModBlocks() {}
}
