package local.bodaddon.item;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import local.bodaddon.ModEffects;
import local.bodaddon.ModItems;
import local.bodaddon.editor.DragonEdits;
import net.magister.bookofdragons.entity.ModEntities;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.fauna.identity.Trait;
import net.magister.liberanimalium.fauna.know.Courtship;
import local.bodaddon.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bodaddon")
@PrefixGameTestTemplate(false)
public final class DragonNipGameTests {
    @GameTest(template = "empty")
    public static void rightClickCalmsWildDragonWithoutTaming(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase dragon = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 2, 2, 2);
        float before = Courtship.familiarityLine(dragon.faunaMind().temper());
        int bond = dragon.getBond(player.getUUID());
        dragon.setTarget(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DRAGON_NIP.get(), 2));
        helper.assertTrue(player.interactOn(dragon, InteractionHand.MAIN_HAND).consumesAction(), "Normal right-click must apply nip");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "Survival must consume exactly one");
        helper.assertTrue(dragon.getEffect(ModEffects.CALMED).getDuration() == 6000, "Effect must last five minutes");
        helper.assertTrue(dragon.faunaMind().temper().get(Trait.AGGRESSION) <= 0.1F, "Aggression must be reduced");
        helper.assertTrue(dragon.faunaMind().temper().get(Trait.SYMPATHY) >= 0.9F, "Sympathy must increase");
        helper.assertTrue(Courtship.familiarityLine(dragon.faunaMind().temper()) <= before,
            "Native taming familiarity threshold must become easier");
        helper.assertTrue(dragon.getTarget() == null, "Current attack target must clear");
        helper.assertTrue(!dragon.isTame() && dragon.ownerId() == null && dragon.getBond(player.getUUID()) == bond,
            "Calming must not grant ownership, instant taming or free bond");
        helper.assertTrue(player.interactOn(dragon, InteractionHand.MAIN_HAND) == InteractionResult.FAIL
            && player.getMainHandItem().getCount() == 1, "Reapplying must cost nothing and not refresh duration");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ownershipCreativeAndUnrelatedEntities(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        DragonBase dragon = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 2, 2, 2);
        dragon.adopt(UUID.randomUUID());
        ItemStack nip = new ItemStack(ModItems.DRAGON_NIP.get());
        helper.assertTrue(use(nip, player, dragon) == InteractionResult.FAIL && !dragon.hasEffect(ModEffects.CALMED),
            "Other players' dragons must be protected");
        dragon.adopt(player.getUUID());
        helper.assertTrue(use(nip, player, dragon).consumesAction() && nip.getCount() == 1,
            "Creative owners can calm their dragon without consuming nip");
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, 5, 2, 2);
        helper.assertTrue(nip.interactLivingEntity(player, cow, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
            "Unrelated entities must pass through");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void saveLoadAndRemovalRestoreNaturalTraits(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase dragon = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 2, 2, 2);
        float[] natural = dragon.faunaMind().temper().readings();
        use(new ItemStack(ModItems.DRAGON_NIP.get()), player, dragon);
        dragon.getEffect(ModEffects.CALMED).tick(dragon, () -> {});
        CompoundTag saved = dragon.saveWithoutId(new CompoundTag());
        DragonBase restored = ModEntities.TERRIBLE_TERROR.get().create(helper.getLevel());
        restored.load(saved);
        helper.assertTrue(restored.getEffect(ModEffects.CALMED).getDuration() == 5999, "Remaining duration must survive save/load");
        DragonNipItem.beforeTick(new EntityTickEvent.Pre(restored));
        helper.assertTrue(restored.faunaMind().temper().get(Trait.SYMPATHY) >= 0.9F, "Loaded dragon must regain calming");
        restored.removeEffect(ModEffects.CALMED);
        DragonNipItem.afterTick(new EntityTickEvent.Post(restored));
        helper.assertTrue(Arrays.equals(natural, restored.faunaMind().temper().readings()), "Removal must restore exact natural traits");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void expiryRestoresLatestEditorTraits(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase dragon = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 2, 2, 2);
        use(new ItemStack(ModItems.DRAGON_NIP.get()), player, dragon);
        ListTag edited = new ListTag();
        for (Trait trait : Trait.values()) edited.add(FloatTag.valueOf(0.7F));
        dragon.getPersistentData().put("bodaddon_traits", edited);
        DragonEdits.applyTraits(dragon);
        helper.assertTrue(dragon.faunaMind().temper().get(Trait.AGGRESSION) <= 0.1F, "Editor reapplication must preserve calming");
        dragon.removeEffect(ModEffects.CALMED);
        dragon.addEffect(new MobEffectInstance(ModEffects.CALMED, 2));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!dragon.hasEffect(ModEffects.CALMED), "Native status timer must expire");
            for (float value : dragon.faunaMind().temper().readings()) {
                helper.assertTrue(value == 0.7F, "Expiry must preserve edits made while calmed");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void anyToolCutsNipGrassAndShearsTakeThePlant(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        DoublePlantBlock.placeAt(level, ModBlocks.DRAGON_NIP_GRASS.get().defaultBlockState(), pos, Block.UPDATE_ALL);
        var lower = level.getBlockState(pos);
        helper.assertTrue(level.getBlockState(pos.above()).is(ModBlocks.DRAGON_NIP_GRASS.get()), "Nip grass must be two blocks tall");
        helper.assertTrue(lower.is(BlockTags.SWORD_EFFICIENT), "Swords must cut nip grass quickly");
        int nip = 0;
        for (int i = 0; i < 64; i++) {
            var drops = Block.getDrops(lower, level, pos, null, null, new ItemStack(Items.IRON_SWORD));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModItems.DRAGON_NIP.get()), "A sword must yield dragon nip");
            helper.assertTrue(drops.getFirst().getCount() <= 2, "A sword cut yields one or two nip");
            nip += drops.getFirst().getCount();
        }
        helper.assertTrue(nip > 64, "Cutting should sometimes yield two nip");
        for (ItemStack tool : new ItemStack[]{ItemStack.EMPTY, new ItemStack(Items.IRON_AXE), new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.STICK)}) {
            var drops = Block.getDrops(lower, level, pos, null, null, tool);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModItems.DRAGON_NIP.get()), "Any tool or a bare hand must yield dragon nip");
        }
        var sheared = Block.getDrops(lower, level, pos, null, null, new ItemStack(Items.SHEARS));
        helper.assertTrue(sheared.size() == 1 && sheared.getFirst().is(ModBlocks.DRAGON_NIP_GRASS_ITEM.get()), "Shears must collect the plant");
        helper.assertTrue(Block.getDrops(level.getBlockState(pos.above()), level, pos.above(), null, null, new ItemStack(Items.IRON_SWORD)).size() == 1,
            "Cutting the upper half must also yield nip");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void grassPatchesGenerateInGrasslandsAndNipIsNotCraftable(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-6, -1, -6), pos.offset(6, 3, 6))) {
            level.setBlockAndUpdate(p, p.getY() < pos.getY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        var access = level.registryAccess();
        var patch = access.registryOrThrow(Registries.CONFIGURED_FEATURE)
            .getOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath("bodaddon", "dragon_nip_patch")));
        helper.assertTrue(patch.place(level, level.getChunkSource().getGenerator(), RandomSource.create(5), pos), "Patch must place on grass");
        helper.assertTrue(BlockPos.betweenClosedStream(pos.offset(-6, 0, -6), pos.offset(6, 2, 6))
            .anyMatch(p -> level.getBlockState(p).is(ModBlocks.DRAGON_NIP_GRASS.get())), "Patch must contain nip grass");
        var placed = access.registryOrThrow(Registries.PLACED_FEATURE).getHolderOrThrow(
            ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath("bodaddon", "dragon_nip_patch")));
        var biomes = access.registryOrThrow(Registries.BIOME);
        helper.assertTrue(biomes.getOrThrow(Biomes.PLAINS).getGenerationSettings().features()
            .get(GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()).contains(placed), "Plains must receive nip grass");
        helper.assertTrue(biomes.getOrThrow(Biomes.DESERT).getGenerationSettings().features().stream().noneMatch(set -> set.contains(placed)),
            "Deserts must not receive nip grass");
        helper.assertTrue(level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("bodaddon", "dragon_nip")).isEmpty(),
            "Nip must no longer be craftable");
        helper.succeed();
    }

    private static InteractionResult use(ItemStack stack, Player player, DragonBase dragon) {
        return stack.interactLivingEntity(player, dragon, InteractionHand.MAIN_HAND);
    }
}
