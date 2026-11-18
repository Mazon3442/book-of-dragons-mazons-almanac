package local.bodaddon.dragon;

import java.util.UUID;
import net.magister.bookofdragons.entity.ModEntities;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bodaddon")
@PrefixGameTestTemplate(false)
public final class DragonSaddlebagGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void hungryTamedDragonEatsFoodFromItsChest(GameTestHelper helper) {
        DragonBase dragon = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 2, 2, 2);
        dragon.adopt(UUID.randomUUID());
        dragon.setHasChest(true);
        ItemStack food = java.util.stream.Stream.of(Items.COD, Items.SALMON, Items.BEEF, Items.COOKED_BEEF, Items.COOKED_COD)
            .map(item -> new ItemStack(item, 3)).filter(dragon::isFood).findFirst().orElseThrow();
        dragon.getInventory().setItem(0, food.copy());
        dragon.getInventory().setItem(1, new ItemStack(Items.STICK, 5));
        dragon.getNeedsSystem().setFoodLevel(10);
        helper.succeedWhen(() -> {
            helper.assertTrue(dragon.getNeedsSystem().getFoodLevel() > 10, "A hungry dragon must eat from its chest");
            helper.assertTrue(dragon.getInventory().getItem(0).getCount() == 2, "It must use exactly one food item");
            helper.assertTrue(dragon.getInventory().getItem(1).getCount() == 5, "It must leave non-food alone");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void dragonsWithoutAChestOrHungerDoNotEat(GameTestHelper helper) {
        DragonBase hungryNoChest = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 2, 2, 2);
        hungryNoChest.adopt(UUID.randomUUID());
        hungryNoChest.getNeedsSystem().setFoodLevel(10);
        hungryNoChest.getInventory().setItem(0, new ItemStack(Items.COD, 3));
        DragonBase full = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 4, 2, 2);
        full.adopt(UUID.randomUUID());
        full.setHasChest(true);
        full.getNeedsSystem().setFoodLevel(100);
        full.getInventory().setItem(0, new ItemStack(Items.COD, 3));
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(hungryNoChest.getInventory().getItem(0).getCount() == 3, "No chest, no feeding");
            helper.assertTrue(full.getInventory().getItem(0).getCount() == 3, "A full dragon must not eat");
            helper.succeed();
        });
    }
}
