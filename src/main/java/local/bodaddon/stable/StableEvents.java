package local.bodaddon.stable;

import java.util.ArrayList;
import java.util.List;

import local.bodaddon.BodAddonConfig;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.fauna.api.Order;
import net.magister.liberanimalium.fauna.core.FaunaMind;
import net.magister.liberanimalium.roost.RoostBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What the Stable Plate does. The engine lets a dragon refuse or drift away from a stay order according to its
 * compliance (a 0-1 value from bond and temper). While a tamed dragon is within range of a plated roost that value is pinned
 * to 1, so it obeys and keeps its order. That applies only to a dragon assigned to a roost (one of its occupants) that is under a stay
 * order. A roost counts once a Stable Sign linked to it stands within range. Once it is hurt the pin is lifted for a while, so it can fight or flee as it
 * normally would, then settles back.
 * <p>
 * Under a stay order the engine scales a dragon's urge to eat by (1 - compliance) and its urge to sleep by (1 - compliance / 2),
 * so a pinned dragon cannot feed and sleeps badly. A hungry dragon also starts to see people as prey once its hunger passes its
 * prey line (0.3 for the most aggressive). So the pin is only ever held on a fed, rested, awake dragon: it is let go completely,
 * back to the engine, well before either need matters, and only taken up again once the dragon has eaten and slept.
 */
public final class StableEvents {
    private static final String HURT_KEY = "bodaddon_stable_hurt";
    private static final String HELD_KEY = "bodaddon_stable_held";
    private static final String RESTING_KEY = "bodaddon_stable_resting";
    /** Hunger (0 to 1) at which a held dragon is let go to eat: below every species' prey line, so it never turns on people first. */
    public static final float HUNGER_LET_GO = 0.2F;
    /** Sleep need (0 to 1) at which a held dragon is let go to sleep; the Book shows "Exhausted" only at 0.75. */
    public static final float SLEEP_LET_GO = 0.3F;
    /** A dragon that was let go is held again only once both needs are down to this, and it is awake and not eating. */
    private static final float SETTLED = 0.1F;
    private static final int CHECK_PERIOD = 20;
    private static final int HURT_WINDOW = 30 * 20;

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof DragonBase dragon) || !(dragon.level() instanceof ServerLevel level)) return;
        if ((dragon.tickCount + dragon.getId()) % CHECK_PERIOD != 0) return;
        FaunaMind mind = dragon.faunaMind();
        if (mind == null) return;
        CompoundTag data = dragon.getPersistentData();
        boolean recentlyHurt = level.getGameTime() - data.getLong(HURT_KEY) <= HURT_WINDOW && data.contains(HURT_KEY);
        // Only a tamed dragon that lives at the roost and is under a stay order is held: one that has been called or told to
        // follow or roam is the owner's to command, whatever is nearby.
        boolean hold = dragon.isTame() && !recentlyHurt && dragon.order() == Order.STAY
            && PlateData.get(level).holds(level, dragon, BodAddonConfig.STABLE_RADIUS.get());
        if (hold) {
            float hunger = dragon.hungerLevel();
            float sleep = mind.reservoirs().fatigue();
            boolean resting = data.getBoolean(RESTING_KEY)
                ? hunger > SETTLED || sleep > SETTLED || dragon.asleep() || dragon.isEating()
                : hunger >= HUNGER_LET_GO || sleep >= SLEEP_LET_GO || dragon.asleep();
            if (resting) data.putBoolean(RESTING_KEY, true);
            else data.remove(RESTING_KEY);
            pin(mind, data, !resting);
        } else {
            data.remove(RESTING_KEY);
            pin(mind, data, false);
        }
    }

    /** Pins compliance at 1, or hands it back to the engine (NaN) if this class pinned it. */
    private static void pin(FaunaMind mind, CompoundTag data, boolean on) {
        if (on) {
            mind.forceComply(1.0F);
            data.putBoolean(HELD_KEY, true);
        } else if (data.getBoolean(HELD_KEY)) {
            mind.forceComply(Float.NaN);
            data.remove(HELD_KEY);
        }
    }

    /** What sneak-right-click on a Stable Sign shows: the link, and how the tamed dragons around the roost are being held. */
    public static List<Component> status(ServerLevel level, BlockPos signPos, String stableName) {
        List<Component> lines = new ArrayList<>();
        lines.add(stableName.isEmpty() ? Component.translatable("bodaddon.stable.status.unnamed")
            : Component.translatable("bodaddon.stable.status.name", stableName));
        BlockPos roost = PlateData.get(level).roostOf(signPos);
        if (roost == null) {
            lines.add(Component.translatable("bodaddon.stable.status.unlinked"));
            return lines;
        }
        double radius = BodAddonConfig.STABLE_RADIUS.get();
        if (level.isLoaded(roost) && !(level.getBlockState(roost).getBlock() instanceof RoostBlock)) {
            lines.add(Component.translatable("bodaddon.stable.status.missing"));
            return lines;
        }
        lines.add(Component.translatable("bodaddon.stable.status.roost", roost.getX(), roost.getY(), roost.getZ(),
            Math.round(Math.sqrt(signPos.distSqr(roost))), Math.round(radius)));
        if (signPos.getCenter().distanceToSqr(roost.getCenter()) > radius * radius) {
            lines.add(Component.translatable("bodaddon.stable.status.far"));
            return lines;
        }
        PlateData plates = PlateData.get(level);
        int listed = 0;
        for (DragonBase dragon : level.getEntitiesOfClass(DragonBase.class, new AABB(roost).inflate(radius))) {
            if (!dragon.isTame() || !plates.holds(level, dragon, radius)) continue;
            CompoundTag data = dragon.getPersistentData();
            boolean hurt = data.contains(HURT_KEY) && level.getGameTime() - data.getLong(HURT_KEY) <= HURT_WINDOW;
            String key = hurt ? "hurt" : data.getBoolean(RESTING_KEY) ? "resting" : "held";
            lines.add(Component.translatable("bodaddon.stable.status.dragon." + key, dragon.getDisplayName()));
            listed++;
        }
        if (listed == 0) lines.add(Component.translatable("bodaddon.stable.status.none", Math.round(radius)));
        return lines;
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof DragonBase dragon && !dragon.level().isClientSide && event.getNewDamage() > 0) {
            dragon.getPersistentData().putLong(HURT_KEY, dragon.level().getGameTime());
        }
    }

    /** Forget signs that have been broken, and signs whose roost has been broken. */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 200 != 0) return;
        PlateData data = PlateData.get(level);
        for (BlockPos[] pair : data.pairs()) {
            boolean signGone = level.isLoaded(pair[0]) && !(level.getBlockState(pair[0]).getBlock() instanceof StableSignBlock);
            boolean roostGone = level.isLoaded(pair[1]) && !(level.getBlockState(pair[1]).getBlock() instanceof RoostBlock);
            if (signGone || roostGone) data.removeSign(pair[0]);
        }
    }

    private StableEvents() {}
}
