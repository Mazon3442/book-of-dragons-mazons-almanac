package local.bodaddon.editor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import local.bodaddon.BodAddon;
import local.bodaddon.ModEffects;
import local.bodaddon.dragon.DragonFriendships;
import net.magister.liberanimalium.fauna.identity.Trait;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.component.InventoryComponent;
import net.magister.liberanimalium.body.effect.InjuryEffect;
import net.magister.liberanimalium.chassis.component.IncapacitationComponent;
import net.magister.liberanimalium.fauna.core.FaunaMind;
import net.magister.liberanimalium.fauna.identity.Temper;
import net.magister.liberanimalium.fauna.know.Courtship;
import net.magister.liberanimalium.fauna.know.SocialBook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Server-side logic behind the dragon editor. Everything is gated on creative mode and reach, since
 * the client packet can be forged. State the Fauna Engine derives (temper) or keeps transiently
 * (friendships) is stored in the dragon's persistent data and re-applied by {@link DragonEditEvents}.
 */
public final class DragonEdits {
    static final String TRAITS_KEY = "bodaddon_traits";
    static final double REACH_SQ = 16.0 * 16.0;
    static final double FRIEND_RADIUS = 32.0;

    public static void handle(ServerPlayer player, EditorPayloads.Act act) {
        if (!player.isCreative()) return;
        Entity e = player.level().getEntity(act.entityId());
        if (!(e instanceof DragonBase dragon) || dragon.distanceToSqr(player) > REACH_SQ) return;

        switch (act.action()) {
            case TAME -> {
                if (!dragon.isTame()) tameFully(player, dragon);
            }
            case SET_BOND -> {
                UUID who = dragon.ownerId() != null ? dragon.ownerId() : player.getUUID();
                setBond(dragon, who, Mth.clamp(Math.round(act.value()), 0, 1000));
            }
            case SET_HEALTH -> dragon.setHealth(Mth.clamp(act.value(), 1.0F, dragon.getMaxHealth()));
            case SET_MAX_HEALTH -> GeneEdits.setHealthStep(dragon, act.index());
            case SET_VARIANT -> VariantEdits.set(dragon, act.index(), Math.round(act.value()));
            case SET_TRAIT -> setTrait(dragon, act.index(), act.value());
            case CLEAR_INJURY -> clearInjury(dragon);
            case CLEAR_BREEDING -> clearBreeding(dragon);
            case BEFRIEND_NEARBY -> befriendNearby(player, dragon);
        }
        EditorPayloads.sendOpen(player, snapshot(player, dragon));
    }

    public static EditorState snapshot(ServerPlayer player, DragonBase d) {
        UUID who = d.ownerId() != null ? d.ownerId() : player.getUUID();
        GeneEdits.HealthSteps steps = GeneEdits.healthSteps(d);
        return new EditorState(d.getId(), d.getDisplayName().getString(), d.isTame(), d.getBond(who),
            d.getHealth(), d.getMaxHealth(), readTraits(d), isInjured(d), Math.max(0, d.getAge()),
            steps.health(), steps.current(), VariantEdits.rows(d));
    }

    // --- loyalty ------------------------------------------------------------------------------

    /**
     * The engine's own setBond only ever raises the bond (it keeps the larger of the old and new value), so lowering it
     * has to write the stored opinion directly: a new Opinion record handed to the package-private SocialBook.put.
     */
    static void setBond(DragonBase dragon, UUID who, int value) {
        if (value >= dragon.getBond(who)) {
            dragon.setBond(who, value);
            return;
        }
        try {
            SocialBook social = dragon.faunaMind().knowledge().social();
            SocialBook.Opinion opinion = social.of(who);
            // Opinion.withBond clamps to [current bond, 1000], so it cannot lower either: build the record directly.
            SocialBook.Opinion lowered = new SocialBook.Opinion(opinion.like(), opinion.fear(), opinion.anger(),
                opinion.familiarity(), (float) value, opinion.ritual());
            Method put = SocialBook.class.getDeclaredMethod("put", UUID.class, SocialBook.Opinion.class);
            put.setAccessible(true);
            put.invoke(social, who, lowered);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            BodAddon.LOGGER.warn("Could not lower the bond of {}: {}", dragon, ex.toString());
        }
    }

    // --- taming -------------------------------------------------------------------------------

    /**
     * Same sequence as Book of Dragons' own debug tame stick. The vanilla {@code tame()} only sets an owner;
     * {@link DragonBase#isTame()} also needs the taming ritual flagged complete, which {@code adopt} does and the
     * courtship ladder does when it finishes. Without that, the Rattlestaff, whistle and riding all say the
     * dragon is not yours. The dragon also needs a saddle in slot 0 before it can be ridden.
     */
    private static void tameFully(ServerPlayer player, DragonBase dragon) {
        UUID who = player.getUUID();
        FaunaMind mind = dragon.faunaMind();
        boolean laddered = false;
        if (mind != null) {
            mind.knowledge().social().stage(who, 1.0F);
            laddered = mind.forceStage(who, Courtship.length(dragon.rideable()));
        }
        if (!laddered) dragon.adopt(who);
        dragon.setBond(who, 1000);

        InventoryComponent inventory = dragon.componentRegistry.get(InventoryComponent.class);
        if (inventory != null && dragon.getInventory().getItem(0).isEmpty()) {
            dragon.getInventory().setItem(0, new ItemStack(Items.SADDLE));
            inventory.updateEquipment();
        }
    }

    // --- traits -------------------------------------------------------------------------------

    private static float[] readTraits(DragonBase d) {
        float[] out = new float[Traits.COUNT];
        CompoundTag data = d.getPersistentData();
        if (data.contains(TRAITS_KEY, Tag.TAG_LIST)) {
            ListTag list = data.getList(TRAITS_KEY, Tag.TAG_FLOAT);
            for (int i = 0; i < out.length && i < list.size(); i++) out[i] = list.getFloat(i);
            return out;
        }
        float[] live = Temper.of(d.faunaId(), d.species()).readings();
        System.arraycopy(live, 0, out, 0, Math.min(out.length, live.length));
        return out;
    }

    private static void setTrait(DragonBase d, int index, float value) {
        if (index < 0 || index >= Traits.COUNT) return;
        float[] traits = readTraits(d);
        traits[index] = Mth.clamp(value, 0.0F, 1.0F);
        ListTag list = new ListTag();
        for (float t : traits) list.add(FloatTag.valueOf(t));
        d.getPersistentData().put(TRAITS_KEY, list);
        applyTraits(d);
    }

    /** Composes saved or natural personality with temporary calming, without changing the saved traits. */
    public static void applyTraits(DragonBase d) {
        if (d.faunaMind() == null) return;
        float[] traits = readTraits(d);
        if (d.hasEffect(ModEffects.CALMED)) {
            traits[Trait.AGGRESSION.ordinal()] = Math.min(traits[Trait.AGGRESSION.ordinal()], 0.1F);
            traits[Trait.DOMINANCE.ordinal()] = Math.min(traits[Trait.DOMINANCE.ordinal()], 0.2F);
            traits[Trait.NERVOUSNESS.ordinal()] = Math.min(traits[Trait.NERVOUSNESS.ordinal()], 0.1F);
            traits[Trait.SYMPATHY.ordinal()] = Math.max(traits[Trait.SYMPATHY.ordinal()], 0.9F);
        }
        try {
            Temper temper = d.faunaMind().temper();
            Field f = Temper.class.getDeclaredField("values");
            f.setAccessible(true);
            float[] values = (float[]) f.get(temper);
            System.arraycopy(traits, 0, values, 0, Math.min(values.length, traits.length));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            BodAddon.LOGGER.warn("Could not apply personality to {}: {}", d, ex.toString());
        }
    }

    // --- injury and breeding ------------------------------------------------------------------

    public static boolean isInjured(DragonBase d) {
        IncapacitationComponent inc = d.componentRegistry.get(IncapacitationComponent.class);
        return d.isGroundedByWound() || (inc != null && (inc.isIncapacitated() || !inc.crippledFeatures().isEmpty()))
            || d.getActiveEffects().stream().anyMatch(i -> i.getEffect().value() instanceof InjuryEffect);
    }

    public static void clearInjury(DragonBase d) {
        float health = d.getHealth();
        IncapacitationComponent inc = d.componentRegistry.get(IncapacitationComponent.class);
        if (inc != null) {
            // The public limb list is immutable; native loading resets all recovery stages and syncs them.
            inc.load(new CompoundTag());
        }
        clearWoundGrounding(d);
        d.getActiveEffects().stream()
            .filter(i -> i.getEffect().value() instanceof InjuryEffect)
            .map(i -> i.getEffect()).toList()
            .forEach(d::removeEffect);
        d.recalculateStats();
        d.applyStats();
        // Applying recalculated stats can refill health; curing an injury must not heal hit points.
        d.setHealth(Math.min(health, d.getMaxHealth()));
    }

    /** Advances the engine's recovery stages without erasing the remaining injuries or injury history. */
    public static void reduceInjuryTime(DragonBase d, int ticks) {
        if (ticks <= 0 || d.level().isClientSide) return;
        float health = d.getHealth();
        try {
            IncapacitationComponent inc = d.componentRegistry.get(IncapacitationComponent.class);
            if (inc != null) {
                Field timer = IncapacitationComponent.class.getDeclaredField("stageTimerTicks");
                Method tickTimer = IncapacitationComponent.class.getDeclaredMethod("tickInjuryStageTimer");
                timer.setAccessible(true);
                tickTimer.setAccessible(true);
                if (inc.isIncapacitated()) inc.exitIncapacitation(d.getRandom());
                int budget = ticks;
                while (budget > 0 && inc.ticksToMend() > 0) {
                    CompoundTag state = new CompoundTag();
                    inc.save(state);
                    int stages = state.getInt("Incap_CurrentStage");
                    int elapsed = state.getInt("Incap_StageTimerTicks");
                    int stageLength = (inc.ticksToMend() + elapsed) / stages;
                    int untilNextStage = stageLength - elapsed;
                    if (budget < untilNextStage) {
                        timer.setInt(inc, elapsed + budget);
                        break;
                    }
                    // Let the native transition remove the healed limb, update debuffs, and sync clients.
                    timer.setInt(inc, stageLength - 1);
                    tickTimer.invoke(inc);
                    budget -= untilNextStage;
                }
            }
            if (d.isGroundedByWound()) {
                Field timer = DragonBase.class.getDeclaredField("woundGroundedTicks");
                timer.setAccessible(true);
                int remaining = timer.getInt(d);
                if (remaining <= ticks) clearWoundGrounding(d);
                else timer.setInt(d, remaining - ticks);
            }
        } catch (ReflectiveOperationException ex) {
            BodAddon.LOGGER.warn("Could not advance dragon injury recovery: {}", ex.toString());
        } finally {
            d.setHealth(Math.min(health, d.getMaxHealth()));
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearWoundGrounding(DragonBase d) {
        setField(DragonBase.class, d, "woundDamage", 0.0F);
        setField(DragonBase.class, d, "woundGroundedTicks", 0);
        try {
            Field grounded = DragonBase.class.getDeclaredField("DATA_WOUND_GROUNDED");
            grounded.setAccessible(true);
            d.getEntityData().set((EntityDataAccessor<Boolean>) grounded.get(null), false);
        } catch (ReflectiveOperationException ex) {
            BodAddon.LOGGER.warn("Could not reset dragon wound grounding: {}", ex.toString());
        }
    }

    private static void clearBreeding(DragonBase d) {
        if (d.getAge() > 0) d.setAge(0);
        d.resetLove();
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException ex) {
            BodAddon.LOGGER.warn("Could not reset {}.{}: {}", owner.getSimpleName(), name, ex.toString());
        }
    }

    // --- friendships --------------------------------------------------------------------------

    private static void befriendNearby(ServerPlayer player, DragonBase center) {
        AABB box = center.getBoundingBox().inflate(FRIEND_RADIUS);
        List<DragonBase> group = center.level().getEntitiesOfClass(DragonBase.class, box);
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) DragonFriendships.befriend(group.get(i), group.get(j));
        }
        player.displayClientMessage(Component.translatable("bodaddon.editor.befriended", group.size()), true);
    }

    private DragonEdits() {}
}
