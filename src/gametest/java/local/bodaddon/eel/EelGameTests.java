package local.bodaddon.eel;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import java.util.UUID;
import local.bodaddon.ModBlocks;
import local.bodaddon.ModEffects;
import local.bodaddon.ModEntities;
import local.bodaddon.ModItems;
import local.bodaddon.repel.DragonRepel;
import local.bodaddon.repel.DragonRepellentBlock;
import local.bodaddon.repel.DragonScentEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bodaddon")
@PrefixGameTestTemplate(false)
public final class EelGameTests {
    @GameTest(template = "empty")
    public static void eelSwimsBucketsAndLivesInRiversAndSwamps(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.WATER);
        helper.setBlock(pos.above(), Blocks.WATER);
        EelEntity eel = helper.spawn(ModEntities.EEL.get(), pos);
        helper.assertTrue(eel.isAlive() && eel.getBucketItemStack().is(ModItems.EEL_BUCKET.get()), "Eel must spawn and bucket");
        helper.assertTrue(eel.getMaxHealth() > 3, "Eel is a little tougher than a cod");
        var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        for (var key : java.util.List.of(Biomes.RIVER, Biomes.SWAMP, Biomes.MANGROVE_SWAMP)) {
            helper.assertTrue(biomes.getOrThrow(key).getMobSettings().getMobs(MobCategory.WATER_AMBIENT).unwrap().stream()
                .anyMatch(data -> data.type == ModEntities.EEL.get()), "Eels must spawn in " + key.location());
        }
        helper.assertTrue(biomes.getOrThrow(Biomes.DESERT).getMobSettings().getMobs(MobCategory.WATER_AMBIENT).unwrap().stream()
            .noneMatch(data -> data.type == ModEntities.EEL.get()), "Eels must not spawn in deserts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void killedEelDropsAnEel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.WATER);
        helper.setBlock(pos.above(), Blocks.WATER);
        EelEntity eel = helper.spawn(ModEntities.EEL.get(), pos);
        eel.kill();
        helper.runAfterDelay(5, () -> {
            helper.assertItemEntityPresent(ModItems.EEL.get(), pos, 8.0);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void fishingModifierSwapsOnlyFishForEels(GameTestHelper helper) {
        var level = helper.getLevel();
        LootContext context = new LootContext.Builder(new LootParams.Builder(level).create(LootContextParamSets.EMPTY))
            .create(Optional.empty());
        var always = new EelFishingModifier(new LootItemCondition[0], 1.0F);
        var never = new EelFishingModifier(new LootItemCondition[0], 0.0F);
        var fish = ObjectArrayList.of(new ItemStack(Items.COD));
        helper.assertTrue(always.apply(fish, context).size() == 1 && fish.getFirst().is(ModItems.EEL.get()), "A catch must become an eel");
        var treasure = ObjectArrayList.of(new ItemStack(Items.NAME_TAG));
        helper.assertTrue(always.apply(treasure, context).getFirst().is(Items.NAME_TAG), "Treasure and junk must never be swapped");
        var kept = ObjectArrayList.of(new ItemStack(Items.SALMON));
        helper.assertTrue(never.apply(kept, context).getFirst().is(Items.SALMON), "Zero chance must keep the fish");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void repellentIsCraftedFromEelRootAndBottleAndGivesDragonScent(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getRecipeManager();
        helper.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath("bodaddon", "dragon_repellent")).isPresent(),
            "The repellent recipe must exist");
        helper.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath("bodaddon", "dragon_repellent_block")).isPresent(),
            "The repellent block recipe must exist");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DRAGON_REPELLENT.get(), 2));
        var stack = player.getMainHandItem();
        var result = ModItems.DRAGON_REPELLENT.get().finishUsingItem(stack, level, player);
        helper.assertTrue(player.hasEffect(ModEffects.DRAGON_SCENT), "Drinking must grant Dragon Scent");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.GLASS_BOTTLE)), "The empty bottle must come back");
        helper.assertTrue(result.getCount() == 1, "One repellent is used per drink");
        helper.succeed();
    }

    @GameTest(template = "sage_empty")
    public static void scentAndBlockRepelOnlyWildDragonsInTheirRadius(GameTestHelper helper) {
        var level = helper.getLevel();
        var centre = helper.absoluteVec(new BlockPos(40, 2, 40).getCenter());
        var wildNear = helper.spawn(net.magister.bookofdragons.entity.ModEntities.TERRIBLE_TERROR.get(), 40 + 20, 2, 40);
        var wildMid = helper.spawn(net.magister.bookofdragons.entity.ModEntities.TERRIBLE_TERROR.get(), 40 + 27, 2, 40);
        var wildFar = helper.spawn(net.magister.bookofdragons.entity.ModEntities.TERRIBLE_TERROR.get(), 40, 2, 40 + 35);
        var tame = helper.spawn(net.magister.bookofdragons.entity.ModEntities.TERRIBLE_TERROR.get(), 40, 2, 45);
        tame.adopt(UUID.randomUUID());
        var scent = DragonRepel.wildDragonsNear(level, centre, DragonScentEffect.RADIUS);
        helper.assertTrue(scent.contains(wildNear) && !scent.contains(wildMid) && !scent.contains(tame),
            "Scent must reach wild dragons within " + DragonScentEffect.RADIUS + " blocks only");
        var block = DragonRepel.wildDragonsNear(level, centre, DragonRepellentBlock.RADIUS);
        helper.assertTrue(block.contains(wildNear) && block.contains(wildMid) && !block.contains(wildFar) && !block.contains(tame),
            "The block must reach wild dragons within " + DragonRepellentBlock.RADIUS + " blocks only");
        wildNear.setTarget(tame);
        helper.setBlock(new BlockPos(40, 2, 40).below(), Blocks.STONE);
        helper.setBlock(new BlockPos(40, 2, 40), ModBlocks.DRAGON_REPELLENT_BLOCK.get());
        helper.assertTrue(helper.getBlockEntity(new BlockPos(40, 2, 40)) instanceof local.bodaddon.repel.RepelBlockEntity,
            "The block must have its block entity");
        helper.succeedWhen(() -> helper.assertTrue(wildNear.getTarget() == null, "The block must make a wild dragon drop its target"));
    }
}
