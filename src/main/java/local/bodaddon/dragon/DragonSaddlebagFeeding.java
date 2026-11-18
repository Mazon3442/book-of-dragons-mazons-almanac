package local.bodaddon.dragon;

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
 * A tamed dragon with a chest eats from it when it gets hungry: it picks the food it likes best among what it will eat,
 * so a well-stocked saddlebag keeps a dragon fed on long trips.
 */
public final class DragonSaddlebagFeeding {
    /** Food level (of 100) below which a dragon starts helping itself. */
    private static final int HUNGRY_BELOW = 60;
    private static final int CHECK_PERIOD = 40;

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof DragonBase dragon) || !(dragon.level() instanceof ServerLevel level)) return;
        if ((dragon.tickCount + dragon.getId()) % CHECK_PERIOD != 0) return;
        var needs = dragon.getNeedsSystem();
        if (needs == null || !dragon.isTame() || !dragon.hasChest() || !dragon.isAlive() || dragon.isEating()
            || needs.getFoodLevel() >= HUNGRY_BELOW) return;
        ItemStack food = bestFood(dragon);
        if (food.isEmpty() || !needs.eat(food)) return;
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
            if (stack.isEmpty() || !dragon.isFood(stack) || dragon.isDislikedFood(stack)) continue;
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
