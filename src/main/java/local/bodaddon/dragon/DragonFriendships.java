package local.bodaddon.dragon;

import java.util.Objects;
import java.util.UUID;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.fauna.api.RelationType;
import net.magister.liberanimalium.fauna.api.Relation;
import net.magister.liberanimalium.fauna.core.PairSession;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Sagefruit friendships: two dragons become permanent pack mates and stop fighting.
 *
 * <p>Friends must share a household: both wild, or both tamed by the same player. The engine makes a dragon turn hostile to
 * anyone it sees attacking a pack mate, and it does not exempt the owner. A tamed dragon held as pack with a wild one (or with
 * another player's dragon) therefore turned on its owner the moment the owner fought that dragon. A pair that stops sharing a
 * household (one of them is tamed later, or by someone else) is released: the forced pack band and liking are taken back.
 */
public final class DragonFriendships {
    private static final String FRIENDS_KEY = "bodaddon_friends";
    public static final double NEARBY_RADIUS = 32.0;

    /** Whether {@code other} is stored as this dragon's friend. Only counts while {@link #sameHousehold} holds. */
    public static boolean isFriend(DragonBase dragon, UUID other) {
        return dragon.getPersistentData().getList(FRIENDS_KEY, Tag.TAG_INT_ARRAY).contains(NbtUtils.createUUID(other));
    }

    /** Both wild, or both owned by the same player. */
    public static boolean sameHousehold(DragonBase a, DragonBase b) {
        return Objects.equals(a.ownerId(), b.ownerId());
    }

    private static boolean friends(DragonBase a, DragonBase b) {
        return sameHousehold(a, b) && (isFriend(a, b.getUUID()) || isFriend(b, a.getUUID()));
    }

    public static boolean befriend(DragonBase a, DragonBase b) {
        if (a == b || !a.isAlive() || !b.isAlive() || a.faunaMind() == null || b.faunaMind() == null || !sameHousehold(a, b)) return false;
        boolean addedA = remember(a, b.getUUID());
        boolean addedB = remember(b, a.getUUID());
        applyFriend(a, b);
        applyFriend(b, a);
        return addedA || addedB;
    }

    private static boolean remember(DragonBase dragon, UUID other) {
        ListTag friends = dragon.getPersistentData().getList(FRIENDS_KEY, Tag.TAG_INT_ARRAY);
        Tag id = NbtUtils.createUUID(other);
        if (friends.contains(id)) return false;
        friends.add(id);
        dragon.getPersistentData().put(FRIENDS_KEY, friends);
        return true;
    }

    public static int befriendNearby(DragonBase center) {
        int added = 0;
        for (DragonBase other : center.level().getEntitiesOfClass(DragonBase.class,
            center.getBoundingBox().inflate(NEARBY_RADIUS), d -> d != center && d.isAlive()
                && d.distanceToSqr(center) <= NEARBY_RADIUS * NEARBY_RADIUS)) {
            if (befriend(center, other)) added++;
        }
        return added;
    }

    private static void applyFriend(DragonBase dragon, DragonBase other) {
        var mind = dragon.faunaMind();
        if (mind == null || !other.isAlive()) return;
        UUID id = other.getUUID();
        // Keep the perceived creature: forceCompany and forceRelation need its existing representation.
        var social = mind.knowledge().social();
        social.angerTo(id, 0.0F, false);
        social.fearTo(id, 0.0F);
        social.stage(id, 1.0F);
        social.holdBand(id, new Relation(RelationType.PACK, 1.0F));
        mind.knowledge().forceRelation(id, RelationType.PACK, 1.0F);
        mind.forceCompany(id, 1.0F);
        PairSession.close(dragon.getUUID(), id);
        PairSession.noteSettled(dragon.getUUID(), id, PairSession.DISPUTE, dragon.level().getGameTime());
        if (dragon.getTarget() == other) dragon.setTarget(null);
        if (dragon.getLastHurtByMob() == other) dragon.setLastHurtByMob(null);
        if (id.equals(mind.engagedId()) && mind.engagement() != null) mind.engagement().reset(mind.handle());
    }

    /** Takes back what {@link #applyFriend} forced, so the engine judges the other dragon on its own again. */
    private static void release(DragonBase dragon, DragonBase other) {
        var mind = dragon.faunaMind();
        if (mind == null) return;
        var social = mind.knowledge().social();
        // holdBand keeps only PACK, IGNORES and UNCOMFORTABLE bands; any other type removes the held one.
        social.holdBand(other.getUUID(), new Relation(RelationType.PLAYS_WITH, 0.0F));
        // A liking above the engine's permanent band also makes it defend the other dragon, so drop the forced liking too.
        if (social.knows(other.getUUID())) social.stage(other.getUUID(), 0.0F);
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof DragonBase dragon) || !(dragon.level() instanceof ServerLevel level)) return;
        if ((dragon.tickCount + dragon.getId()) % 20 != 0 || !dragon.isAlive()) return;
        ListTag stored = dragon.getPersistentData().getList(FRIENDS_KEY, Tag.TAG_INT_ARRAY);
        if (stored.isEmpty()) return;
        ListTag kept = new ListTag();
        for (Tag tag : stored) {
            if (level.getEntity(NbtUtils.loadUUID(tag)) instanceof DragonBase other) {
                if (!sameHousehold(dragon, other)) {
                    release(dragon, other);
                    continue;
                }
                applyFriend(dragon, other);
            }
            kept.add(tag);
        }
        if (kept.size() != stored.size()) dragon.getPersistentData().put(FRIENDS_KEY, kept);
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof DragonBase dragon && event.getNewAboutToBeSetTarget() instanceof DragonBase other
            && friends(dragon, other)) event.setNewAboutToBeSetTarget(null);
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof DragonBase dragon && event.getSource().getEntity() instanceof DragonBase other
            && friends(dragon, other)) event.setCanceled(true);
    }

    private DragonFriendships() {}
}
