package local.bodaddon;

import java.util.List;
import java.util.UUID;

import local.bodaddon.stable.PlateData;
import local.bodaddon.stable.StableEvents;
import local.bodaddon.trough.FeedingTroughBlock;
import local.bodaddon.trough.FeedingTroughBlockEntity;
import net.magister.bookofdragons.entity.ModEntities;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.chassis.component.CommandComponent;
import net.magister.liberanimalium.chassis.component.command.Stance;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
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
            UUID owner = UUID.randomUUID();
            dragon.adopt(owner);
            helper.assertTrue(keys(StableEvents.status(level, sign, "Home")).contains("bodaddon.stable.status.none"),
                "A tamed dragon that is not assigned to the roost is not held");
            var roostEntity = (net.magister.liberanimalium.roost.RoostBlockEntity) level.getBlockEntity(roost);
            roostEntity.setPlacedBy(owner);
            String refusal = roostEntity.assign(dragon);
            helper.assertTrue(refusal == null, "The dragon is assigned to the roost: " + refusal);
            helper.assertTrue(keys(StableEvents.status(level, sign, "Home")).contains("bodaddon.stable.status.dragon.held"),
                "A tamed dragon assigned to the roost is held");
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

    /**
     * Regression: the Stable Plate used to pin every tamed dragon near the roost to full obedience, whatever its order, so they
     * ignored calls and never slept or ate. It must hold only a dragon assigned to the roost under a stay order, let go of it
     * completely when it is hungry or tired, and hand control back as soon as it is told to follow.
     */
    @GameTest(template = "empty")
    public static void stablePlateHoldsOnlyStayingRoostDragonsAndLetsThemRest(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos sign = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos roost = sign.offset(3, 0, 0);
        var plates = PlateData.get(level);
        try {
            level.setBlock(roost, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("bookofdragons", "roost_controller"))
                .defaultBlockState(), 3);
            plates.put(sign, roost);
            DragonBase dragon = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 4, 2, 4);
            UUID owner = UUID.randomUUID();
            dragon.adopt(owner);
            var command = dragon.componentRegistry.get(CommandComponent.class);
            var obedience = dragon.faunaMind().knowledge().obedience();
            var needs = dragon.getNeedsSystem();
            needs.setFoodLevel(100);

            command.setCommandForced(Stance.STAY, Stance.STAY.defaultModifier());
            holdCheck(dragon);
            helper.assertTrue(!dragon.getPersistentData().getBoolean("bodaddon_stable_held"),
                "A staying dragon that is not assigned to the roost is not held");

            var roostEntity = (net.magister.liberanimalium.roost.RoostBlockEntity) level.getBlockEntity(roost);
            roostEntity.setPlacedBy(owner);
            helper.assertTrue(roostEntity.assign(dragon) == null, "The dragon is assigned to the roost");
            holdCheck(dragon);
            helper.assertTrue(obedience.compliance() == 1.0F, "An assigned dragon under a stay order is held");

            // Regression: the pin blocks eating and halves sleep under a stay order, and a hungry dragon sees people as prey from
            // 0.3 hunger. The hold must let go completely, back to the engine, before that.
            needs.setFoodLevel((int) (100 * (1 - StableEvents.HUNGER_LET_GO)) - 2);
            holdCheck(dragon);
            helper.assertTrue(released(obedience) && dragon.getPersistentData().getBoolean("bodaddon_stable_resting"),
                "A dragon getting hungry is let go completely, so it can eat");
            helper.assertTrue(dragon.hungerLevel() < 0.3F, "It is let go before even the most aggressive dragon's prey line");
            needs.setFoodLevel(85);
            holdCheck(dragon);
            helper.assertTrue(released(obedience), "It stays free until it has eaten its fill, not the moment it dips under the line");
            needs.setFoodLevel(100);
            holdCheck(dragon);
            helper.assertTrue(obedience.compliance() == 1.0F && !dragon.getPersistentData().contains("bodaddon_stable_resting"),
                "A fed, rested dragon is held again");

            dragon.faunaMind().reservoirs().fatigueTo(StableEvents.SLEEP_LET_GO);
            holdCheck(dragon);
            helper.assertTrue(released(obedience), "A tired dragon is let go completely, so it can sleep");
            helper.assertTrue(StableEvents.SLEEP_LET_GO < 0.75F, "It is let go long before the Book would call it exhausted");

            command.setCommandForced(Stance.FOLLOW, Stance.FOLLOW.defaultModifier());
            holdCheck(dragon);
            helper.assertTrue(!dragon.getPersistentData().contains("bodaddon_stable_held") && obedience.compliance() != 1.0F,
                "A dragon told to follow is let go at once");
        } finally {
            plates.removeSign(sign);
        }
        helper.succeed();
    }

    /** Whether compliance has been handed back to the engine rather than pinned. */
    private static boolean released(net.magister.liberanimalium.fauna.know.Obedience obedience) {
        try {
            var forced = net.magister.liberanimalium.fauna.know.Obedience.class.getDeclaredField("forced");
            forced.setAccessible(true);
            return Float.isNaN(forced.getFloat(obedience));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Runs the Stable Plate's periodic check on this dragon now. */
    private static void holdCheck(DragonBase dragon) {
        dragon.tickCount += Math.floorMod(-(dragon.tickCount + dragon.getId()), 20);
        StableEvents.onTick(new EntityTickEvent.Post(dragon));
        dragon.tickCount += 20;
    }
}
