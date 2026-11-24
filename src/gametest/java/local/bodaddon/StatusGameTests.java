package local.bodaddon;

import java.util.List;
import java.util.UUID;

import local.bodaddon.stable.PlateData;
import local.bodaddon.stable.StableEvents;
import local.bodaddon.trough.FeedingTroughBlock;
import local.bodaddon.trough.FeedingTroughBlockEntity;
import net.magister.bookofdragons.entity.ModEntities;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bodaddon")
@PrefixGameTestTemplate(false)
public final class StatusGameTests {
    private static List<String> keys(List<Component> lines) {
        return lines.stream().map(line -> ((TranslatableContents) line.getContents()).getKey()).toList();
    }

    @GameTest(template = "empty")
    public static void troughStatusReportsFullnessAndEachDragonsHunger(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos foot = helper.absolutePos(new BlockPos(4, 2, 4));
        var facing = net.minecraft.core.Direction.NORTH;
        var base = ModBlocks.FEEDING_TROUGH.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
        level.setBlock(foot, base, 3);
        level.setBlock(foot.relative(facing.getClockWise()), base.setValue(FeedingTroughBlock.PART, BedPart.HEAD), 3);
        var trough = (FeedingTroughBlockEntity) level.getBlockEntity(foot);

        var alone = keys(trough.status(level));
        helper.assertTrue(alone.equals(List.of("bodaddon.trough.status.store", "bodaddon.trough.status.none")),
            "An empty trough with nobody around reports only its store: " + alone);

        DragonBase dragon = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 2, 2, 2);
        dragon.adopt(UUID.randomUUID());
        ItemStack food = java.util.stream.Stream.of(Items.COD, Items.SALMON, Items.BEEF, Items.COOKED_BEEF, Items.COOKED_COD)
            .map(item -> new ItemStack(item, 5)).filter(dragon::isFood).findFirst().orElseThrow();
        dragon.getNeedsSystem().setFoodLevel(10);

        helper.assertTrue(keys(trough.status(level)).contains("bodaddon.trough.status.dragon.hungry_nofood"),
            "A hungry dragon with nothing it will eat in the trough must be reported as such");
        trough.setItem(0, food);
        var stocked = trough.status(level);
        helper.assertTrue(keys(stocked).contains("bodaddon.trough.status.dragon.hungry_food"), "A hungry dragon with food available must be reported as such");
        var store = (TranslatableContents) stocked.getFirst().getContents();
        helper.assertTrue(store.getArgs()[0].equals(1) && store.getArgs()[2].equals(5), "The store line counts used slots and items");
        dragon.getNeedsSystem().setFoodLevel(100);
        helper.assertTrue(keys(trough.status(level)).contains("bodaddon.trough.status.dragon.full"), "A full dragon must be reported as full");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stableSignStatusShowsLinkAndHowDragonsAreHeld(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos sign = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos roost = sign.offset(3, 0, 0);
        var plates = PlateData.get(level);
        try {
            var unlinked = keys(StableEvents.status(level, sign, "Home"));
            helper.assertTrue(unlinked.equals(List.of("bodaddon.stable.status.name", "bodaddon.stable.status.unlinked")),
                "A sign that is not linked says so: " + unlinked);

            level.setBlock(roost, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("bookofdragons", "roost_controller"))
                .defaultBlockState(), 3);
            plates.put(sign, roost);
            helper.assertTrue(keys(StableEvents.status(level, sign, "")).contains("bodaddon.stable.status.unnamed"), "An unnamed stable says so");
            helper.assertTrue(keys(StableEvents.status(level, sign, "Home")).contains("bodaddon.stable.status.none"),
                "A linked stable with no dragons around says so");

            DragonBase dragon = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 4, 2, 4);
            dragon.adopt(UUID.randomUUID());
            helper.assertTrue(keys(StableEvents.status(level, sign, "Home")).contains("bodaddon.stable.status.dragon.held"),
                "A tamed dragon near the roost is held");
            dragon.getPersistentData().putLong("bodaddon_stable_hurt", level.getGameTime());
            helper.assertTrue(keys(StableEvents.status(level, sign, "Home")).contains("bodaddon.stable.status.dragon.hurt"),
                "A recently hurt dragon is reported as free to move");

            level.setBlock(roost, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(keys(StableEvents.status(level, sign, "Home")).contains("bodaddon.stable.status.missing"),
                "A sign whose roost is gone says so");
        } finally {
            plates.removeSign(sign);
        }
        helper.succeed();
    }
}
