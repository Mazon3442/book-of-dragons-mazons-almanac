package local.bodaddon.eel;

import java.util.List;
import local.bodaddon.BodAddonConfig;
import local.bodaddon.ModItems;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Wild dragons are scared of live eels, and every dragon, wild or tamed, is disgusted by eel items: a dragon that sees an eel,
 * an eel item on the ground or a player holding one sniffs and hisses at it, and one that is offered an eel turns it down. Only a
 * wild dragon is driven away. A tamed dragon is never sent running: a forced route outranks its owner's orders for over a minute,
 * so its owner holding a Cooked Eel, or an eel in a nearby river, would leave it ignoring calls, stuck at an unreachable spot and
 * kept from sleeping. A tamed dragon also ignores live eels, and no dragon runs while it is ridden or already stuck.
 */
public final class EelDread {
    private static final int PERIOD = 10;
    private static final int REACT_EVERY = 4;       // checks between disgusted puffs and hisses (2 seconds)
    private static final double SIGHT = 12.0;
    private static final double HOP = 14.0;

    /** Raw or cooked eel, or a bucket of eel. */
    public static boolean isEelItem(ItemStack stack) {
        return stack.is(ModItems.EEL.get()) || stack.is(ModItems.COOKED_EEL.get()) || stack.is(ModItems.EEL_BUCKET.get());
    }

    private static boolean frightens(DragonBase dragon, Entity e) {
        if (e instanceof EelEntity eel) return !dragon.isTame() && eel.isAlive();
        if (e instanceof ItemEntity item) return isEelItem(item.getItem());
        return e instanceof Player player && (isEelItem(player.getMainHandItem()) || isEelItem(player.getOffhandItem()));
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof DragonBase dragon) || !(dragon.level() instanceof ServerLevel level)) return;
        if (!BodAddonConfig.EEL_FEAR.get() || !dragon.isAlive() || (dragon.tickCount + dragon.getId()) % PERIOD != 0 || dragon.faunaMind() == null) return;
        List<Entity> threats = level.getEntities(dragon, AABB.ofSize(dragon.position(), SIGHT * 2, SIGHT * 2, SIGHT * 2),
            e -> frightens(dragon, e) && e.distanceToSqr(dragon) <= SIGHT * SIGHT);
        if (threats.isEmpty()) return;
        Entity nearest = threats.stream().min((a, b) -> Double.compare(a.distanceToSqr(dragon), b.distanceToSqr(dragon))).get();
        if (dragon.getTarget() != null && frightens(dragon, dragon.getTarget())) dragon.setTarget(null);
        if ((dragon.tickCount / PERIOD) % REACT_EVERY == 0) disgust(level, dragon);
        flee(level, dragon, nearest.position());
    }

    /** A dragon that is offered an eel wrinkles its snout and refuses it. */
    @SubscribeEvent
    public static void onOffered(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof DragonBase dragon) || !isEelItem(event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (event.getLevel() instanceof ServerLevel level) {
            disgust(level, dragon);
            event.getEntity().displayClientMessage(Component.translatable("bodaddon.eel.refused"), true);
            flee(level, dragon, event.getEntity().position());
        }
    }

    private static void disgust(ServerLevel level, DragonBase dragon) {
        level.sendParticles(ParticleTypes.SNEEZE, dragon.getX(), dragon.getEyeY(), dragon.getZ(), 6, 0.4, 0.2, 0.4, 0.02);
        level.playSound(null, dragon.blockPosition(), SoundEvents.CAT_HISS, SoundSource.NEUTRAL, 0.7F, 0.7F);
    }

    /** Whether eels may send this dragon running: only a wild one, never a tamed one, which must stay under its owner's orders. */
    static boolean drivenOff(DragonBase dragon) {
        return !dragon.isTame();
    }

    /** Sends a wild dragon a hop away from the source, unless it is already on a route. */
    private static void flee(ServerLevel level, DragonBase dragon, Vec3 from) {
        if (!drivenOff(dragon)) return;
        var mind = dragon.faunaMind();
        var route = mind.route();
        if (route.hasRoute() || route.solving() || mind.cornered() || dragon.isVehicle()) return;
        Vec3 away = new Vec3(dragon.getX() - from.x, 0, dragon.getZ() - from.z);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        BlockPos exit = BlockPos.containing(dragon.getX() + away.x * HOP, dragon.getY(), dragon.getZ() + away.z * HOP);
        if (!level.hasChunkAt(exit)) return;
        if (!dragon.isFlying()) exit = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, exit);
        mind.forceRoute(exit);
    }

    private EelDread() {}
}
