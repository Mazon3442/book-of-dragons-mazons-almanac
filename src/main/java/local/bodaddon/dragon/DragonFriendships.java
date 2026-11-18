package local.bodaddon.dragon;

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

public final class DragonFriendships {
    private static final String FRIENDS_KEY = "bodaddon_friends";
    public static final double NEARBY_RADIUS = 32.0;

    public static boolean isFriend(DragonBase dragon, UUID other) {
        return dragon.getPersistentData().getList(FRIENDS_KEY, Tag.TAG_INT_ARRAY).contains(NbtUtils.createUUID(other));
    }

    public static boolean befriend(DragonBase a, DragonBase b) {
        if (a == b || !a.isAlive() || !b.isAlive() || a.faunaMind() == null || b.faunaMind() == null) return false;
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

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof DragonBase dragon) || !(dragon.level() instanceof ServerLevel level)) return;
        if ((dragon.tickCount + dragon.getId()) % 20 != 0 || !dragon.isAlive()) return;
        for (Tag tag : dragon.getPersistentData().getList(FRIENDS_KEY, Tag.TAG_INT_ARRAY)) {
            if (level.getEntity(NbtUtils.loadUUID(tag)) instanceof DragonBase other) applyFriend(dragon, other);
        }
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof DragonBase dragon && event.getNewAboutToBeSetTarget() instanceof DragonBase other
            && isFriend(dragon, other.getUUID())) event.setNewAboutToBeSetTarget(null);
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof DragonBase dragon && event.getSource().getEntity() instanceof DragonBase other
            && (isFriend(dragon, other.getUUID()) || isFriend(other, dragon.getUUID()))) event.setCanceled(true);
    }

    private DragonFriendships() {}
}
