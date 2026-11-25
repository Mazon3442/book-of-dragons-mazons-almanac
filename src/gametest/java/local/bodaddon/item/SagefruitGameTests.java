package local.bodaddon.item;

import java.util.UUID;
import java.util.Map;
import local.bodaddon.ModItems;
import local.bodaddon.dragon.DragonFriendships;
import net.magister.bookofdragons.entity.ModEntities;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.fauna.api.Relation;
import net.magister.liberanimalium.fauna.api.RelationType;
import net.magister.liberanimalium.fauna.core.PairSession;
import net.magister.liberanimalium.fauna.know.Knowledge;
import net.magister.liberanimalium.fauna.know.Representation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bodaddon")
@PrefixGameTestTemplate(false)
public final class SagefruitGameTests {
    @GameTest(template = "sage_empty")
    public static void feedingCreatesMutualPermanentFriendships(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase a = dragon(helper, 2);
        DragonBase b = dragon(helper, 4);
        a.setTarget(b);
        b.setTarget(a);
        a.setLastHurtByMob(b);
        b.setLastHurtByMob(a);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SAGEFRUIT.get(), 2));
        helper.assertTrue(player.interactOn(a, InteractionHand.MAIN_HAND).consumesAction(), "Normal right click must feed sagefruit");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "Successful use must consume one fruit");
        helper.assertTrue(DragonFriendships.isFriend(a, b.getUUID()) && DragonFriendships.isFriend(b, a.getUUID()),
            "Friendship must be mutual");
        helper.assertTrue(a.getTarget() == null && b.getTarget() == null
            && a.getLastHurtByMob() == null && b.getLastHurtByMob() == null, "Existing fighting must stop on both dragons");
        a.setTarget(b);
        b.setTarget(a);
        helper.assertTrue(a.getTarget() == null && b.getTarget() == null, "Friends must not reacquire each other as attack targets");
        helper.assertTrue(!a.isTame() && !b.isTame() && a.ownerId() == null && b.ownerId() == null, "Friendship must not tame dragons");
        DragonBase loadedA = ModEntities.TERRIBLE_TERROR.get().create(helper.getLevel());
        DragonBase loadedB = ModEntities.TERRIBLE_TERROR.get().create(helper.getLevel());
        loadedA.load(a.saveWithoutId(new CompoundTag()));
        loadedB.load(b.saveWithoutId(new CompoundTag()));
        helper.assertTrue(DragonFriendships.isFriend(loadedA, loadedB.getUUID())
            && DragonFriendships.isFriend(loadedB, loadedA.getUUID()), "Both friendships must survive saving and loading");
        helper.assertTrue(player.interactOn(a, InteractionHand.MAIN_HAND) == InteractionResult.FAIL
            && player.getMainHandItem().getCount() == 1, "Already befriended dragons must not waste fruit");
        helper.succeed();
    }

    @GameTest(template = "sage_empty")
    public static void friendshipChangesNativeRivalRelation(GameTestHelper helper) throws ReflectiveOperationException {
        DragonBase a = dragon(helper, 2);
        DragonBase b = dragon(helper, 4);
        Knowledge knowledge = a.faunaMind().knowledge();
        var meet = Knowledge.class.getDeclaredMethod("meet", UUID.class, String.class, boolean.class, Relation.class, Vec3.class, long.class);
        meet.setAccessible(true);
        Representation perceived = (Representation) meet.invoke(knowledge, b.getUUID(), "bookofdragons:terrible_terror", false,
            new Relation(RelationType.RIVAL, 1.0F), b.position(), a.faunaMind().age());
        var known = Knowledge.class.getDeclaredField("known");
        known.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Representation> representations = (Map<UUID, Representation>) known.get(knowledge);
        representations.put(b.getUUID(), perceived);
        knowledge.social().angerTo(b.getUUID(), 0.9F, false);
        knowledge.social().fearTo(b.getUUID(), 0.8F);
        PairSession.open(a.getUUID(), b.getUUID(), PairSession.DISPUTE, helper.getLevel().getGameTime());
        DragonFriendships.befriend(a, b);
        helper.assertTrue(knowledge.about(b.getUUID()) == perceived, "Friendship must retain the perceived dragon");
        helper.assertTrue(perceived.relation().type() == RelationType.PACK, "A rival must become a native pack companion");
        helper.assertTrue(knowledge.social().of(b.getUUID()).anger() == 0 && knowledge.social().of(b.getUUID()).fear() == 0,
            "Stored fear and anger toward the friend must clear");
        helper.assertTrue(PairSession.find(a.getUUID(), b.getUUID()) == null, "Territory disputes must close");
        helper.succeed();
    }

    @GameTest(template = "sage_empty")
    public static void friendlyDragonDamageIsBlockedButOtherDamageStillWorks(GameTestHelper helper) {
        DragonBase a = dragon(helper, 2);
        DragonBase b = dragon(helper, 4);
        DragonFriendships.befriend(a, b);
        float health = b.getHealth();
        b.hurt(helper.getLevel().damageSources().mobAttack(a), 2.0F);
        helper.assertTrue(b.getHealth() == health, "Friends must not hurt each other");
        b.hurt(helper.getLevel().damageSources().generic(), 1.0F);
        helper.assertTrue(b.getHealth() < health, "Friendship must not grant general invulnerability");
        helper.succeed();
    }

    @GameTest(template = "sage_empty")
    public static void fruitRequiresNewFriendsAndRespectsOwnership(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase a = dragon(helper, 2);
        ItemStack fruit = new ItemStack(ModItems.SAGEFRUIT.get(), 2);
        helper.assertTrue(use(fruit, player, a) == InteractionResult.FAIL && fruit.getCount() == 2, "A lone dragon must not waste fruit");
        DragonBase b = dragon(helper, 4);
        UUID owner = UUID.randomUUID();
        a.adopt(owner);
        helper.assertTrue(use(fruit, player, a) == InteractionResult.FAIL && !DragonFriendships.isFriend(a, b.getUUID()),
            "Other owners' dragons must not be fed");
        a.adopt(player.getUUID());
        helper.assertTrue(use(fruit, player, a) == InteractionResult.FAIL, "A tamed dragon must not befriend a wild one");
        b.adopt(player.getUUID());
        player.getAbilities().instabuild = true;
        helper.assertTrue(use(fruit, player, a).consumesAction() && fruit.getCount() == 2, "Creative owners must keep the fruit");
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, 5, 2, 2);
        helper.assertTrue(fruit.interactLivingEntity(player, cow, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
            "Non-dragons must pass through");
        helper.succeed();
    }

    @GameTest(template = "sage_empty")
    public static void friendshipsAccumulateWithoutDuplicatesAndRespectRadius(GameTestHelper helper) {
        DragonBase a = dragon(helper, 2);
        DragonBase b = dragon(helper, 4);
        DragonBase distant = dragon(helper, 5);
        distant.setPos(a.getX() + 40, a.getY(), a.getZ());
        helper.assertTrue(DragonFriendships.befriendNearby(a) == 1, "Only nearby dragons must be befriended");
        helper.assertTrue(!DragonFriendships.isFriend(a, distant.getUUID()), "Distant dragons must not become friends");
        DragonBase c = dragon(helper, 5);
        helper.assertTrue(DragonFriendships.befriendNearby(a) == 1, "Later uses must add only new friends");
        helper.assertTrue(DragonFriendships.isFriend(a, b.getUUID()) && DragonFriendships.isFriend(a, c.getUUID()),
            "New friendships must retain existing ones");
        helper.assertTrue(a.getPersistentData().getList("bodaddon_friends", 11).size() == 2, "Repeated use must not duplicate UUIDs");
        helper.succeed();
    }

    /**
     * Regression: a tamed dragon held as pack with a wild one turned hostile to its owner when the owner fought that wild dragon,
     * because the engine defends pack mates against anyone, owner included.
     */
    @GameTest(template = "sage_empty")
    public static void friendshipsNeverCrossOwnersAndOldOnesAreReleased(GameTestHelper helper) {
        DragonBase tame = dragon(helper, 2);
        DragonBase wild = dragon(helper, 4);
        DragonBase otherOwners = dragon(helper, 6);
        UUID owner = UUID.randomUUID();
        tame.adopt(owner);
        otherOwners.adopt(UUID.randomUUID());
        helper.assertTrue(!DragonFriendships.befriend(tame, wild) && !DragonFriendships.befriend(tame, otherOwners),
            "A tamed dragon must not befriend a wild one or another player's dragon");
        helper.assertTrue(!DragonFriendships.isFriend(tame, wild.getUUID()) && !DragonFriendships.isFriend(wild, tame.getUUID()),
            "A refused friendship must not be stored");

        // A friendship saved by an older version, between two dragons where one has since been tamed.
        DragonBase laterTamed = dragon(helper, 8);
        helper.assertTrue(DragonFriendships.befriend(laterTamed, wild), "Two wild dragons can be friends");
        var social = laterTamed.faunaMind().knowledge().social();
        helper.assertTrue(social.band(wild.getUUID()) != null, "The friendship holds a pack band");
        laterTamed.adopt(owner);
        helper.succeedWhen(() -> {
            helper.assertTrue(!DragonFriendships.isFriend(laterTamed, wild.getUUID()) && !DragonFriendships.isFriend(wild, laterTamed.getUUID()),
                "A friendship that now crosses owners must be dropped on both sides");
            helper.assertTrue(social.band(wild.getUUID()) == null, "The forced pack band must be released");
            helper.assertTrue(social.of(wild.getUUID()).like() <= 0.0F, "The forced liking must be taken back");
            float health = wild.getHealth();
            wild.hurt(helper.getLevel().damageSources().mobAttack(laterTamed), 1.0F);
            helper.assertTrue(wild.getHealth() < health, "Released dragons are no longer shielded from each other");
        });
    }

    private static DragonBase dragon(GameTestHelper helper, int x) {
        // Keep the 32-block friendship radius inside this test's larger structure.
        return helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), x + 38, 2, 40);
    }

    private static InteractionResult use(ItemStack fruit, Player player, DragonBase dragon) {
        return fruit.interactLivingEntity(player, dragon, InteractionHand.MAIN_HAND);
    }
}
