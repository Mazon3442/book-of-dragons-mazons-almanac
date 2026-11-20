package local.bodaddon.dragon;

import local.bodaddon.BodAddonConfig;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * A tamed dragon with a chest eats from it when its food runs low (see {@link BodAddonConfig#CHEST_FEEDING_START}) and keeps
 * eating, one meal after another, until it is full or the chest holds nothing it will eat. It picks the food it likes best
 * among what it will eat, so a well-stocked saddlebag keeps a dragon fed on long trips.
 */
public final class DragonSaddlebagFeeding {
    private static final int FULL = 100;
    private static final int CHECK_PERIOD = 20;
    /** Marks a dragon that is part-way through filling up, so it carries on past the level it started at. */
    private static final String FEEDING_KEY = "bodaddon_chest_feeding";

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof DragonBase dragon) || !(dragon.level() instanceof ServerLevel level)) return;
        if ((dragon.tickCount + dragon.getId()) % CHECK_PERIOD != 0) return;
        var needs = dragon.getNeedsSystem();
        if (needs == null || !dragon.isTame() || !dragon.hasChest() || !dragon.isAlive() || dragon.isEating()) return;
        var data = dragon.getPersistentData();
        if (needs.getFoodLevel() >= FULL) {
            data.remove(FEEDING_KEY);
            return;
        }
        if (!data.getBoolean(FEEDING_KEY) && needs.getFoodLevel() > BodAddonConfig.CHEST_FEEDING_START.get()) return;
        ItemStack food = bestFood(dragon);
        if (food.isEmpty() || !needs.eat(food)) {
            data.remove(FEEDING_KEY);   // nothing left it will eat: wait for the next time it is hungry
            return;
        }
        data.putBoolean(FEEDING_KEY, true);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food), dragon.getX(), dragon.getEyeY(), dragon.getZ(), 8, 0.3, 0.2, 0.3, 0.05);
        level.playSound(null, dragon.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, 1.0F);
        food.shrink(1);   // eat() applies the meal but leaves the stack to the caller
        dragon.getInventory().setChanged();
    }

    private static ItemStack bestFood(DragonBase dragon) {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = 0;
        var inventory = dragon.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !dragon.isFood(stack) || dragon.isDislikedFood(stack) || local.bodaddon.eel.EelDread.isEelItem(stack)) continue;
            int score = dragon.isFavoriteFood(stack) ? 3 : dragon.isSpecialFood(stack) ? 2 : 1;
            if (score > bestScore) {
                best = stack;
                bestScore = score;
            }
        }
        return best;
    }

    private DragonSaddlebagFeeding() {}
}
