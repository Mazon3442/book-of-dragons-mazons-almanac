package local.bodaddon;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.data.DragonType;
import net.magister.liberanimalium.body.stats.PrimaryAttribute;
import net.magister.liberanimalium.genetics.Locus;
import net.magister.liberanimalium.genetics.config.SpeciesGeneticsConfig;
import net.magister.liberanimalium.genetics.config.SpeciesGeneticsConfig.AttributeRange;
import net.magister.liberanimalium.genetics.config.SpeciesGeneticsConfigRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Common config (config/bodaddon-common.toml, also editable from the mod list in game).
 *
 * <p>Trough reach, and the stat letter (S to D) of every dragon species. The Book of Dragons grades each stat from the
 * middle of the species' floor..ceiling range (75+ is S, 60+ A, 45+ B, 30+ C, else D), and the same range sets the real stat
 * (the engine maps each dragon's 0-100 gene score onto it), so moving the range changes the letter and the stat together.
 * A chosen letter keeps the species' original spread and moves the whole range to the middle of that letter's band.
 */
public final class BodAddonConfig {
    public enum Rating {
        DEFAULT(-1), S(87), A(67), B(52), C(37), D(15);

        final int middle;

        Rating(int middle) { this.middle = middle; }
    }

    private static final String KEY = "bodaddon.configuration.";

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue TROUGH_RADIUS;
    public static final ModConfigSpec.DoubleValue STABLE_RADIUS;
    public static final ModConfigSpec.BooleanValue EEL_FEAR;
    public static final ModConfigSpec.IntValue CHEST_FEEDING_START;
    private static final ModConfigSpec.IntValue[] SKETCHBOOK_BOND = new ModConfigSpec.IntValue[3];
    /** Bond (0 to 100) the Sketchbook asks for a rarity: 0 COMMON, 1 UNCOMMON, 2 RARE, 3 VERY_RARE. */
    public static int sketchbookBond(int rarity) { return rarity <= 0 ? 0 : SKETCHBOOK_BOND[Math.min(rarity, 3) - 1].get(); }

    private static final Map<DragonType, Map<PrimaryAttribute, ModConfigSpec.EnumValue<Rating>>> RATINGS = new EnumMap<>(DragonType.class);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.translation(KEY + "trough").push("trough");
        TROUGH_RADIUS = b.comment("How far, in blocks, a feeding trough reaches for hungry dragons.")
            .translation(KEY + "trough.radius").defineInRange("radius", 16.0, 2.0, 64.0);
        b.pop();
        b.translation(KEY + "dragon_feeding").push("dragon_feeding");
        CHEST_FEEDING_START = b.comment("A tamed dragon with a chest starts eating from it when its food drops to this level (of 100), and keeps eating until it is full or the chest has nothing it will eat.")
            .translation(KEY + "dragon_feeding.start").defineInRange("start_eating_at", 70, 0, 99);
        b.pop();
        b.translation(KEY + "sketchbook").push("sketchbook");
        String[] tiers = {"uncommon", "rare", "very_rare"};
        int[] defaults = {25, 50, 90};
        for (int i = 0; i < tiers.length; i++) {
            SKETCHBOOK_BOND[i] = b.comment("Bond (0 to 100) a dragon needs before the Sketchbook offers its " + tiers[i].replace('_', ' ')
                    + " colors and markings. Common ones are always offered. 0 unlocks the tier for everyone.")
                .translation(KEY + "sketchbook." + tiers[i]).defineInRange(tiers[i], defaults[i], 0, 100);
        }
        b.pop();
        b.translation(KEY + "stable_plate").push("stable_plate");
        STABLE_RADIUS = b.comment("How far, in blocks, a Stable Plate on a Dragon Roost holds the dragons assigned to that roost in their stay order.")
            .translation(KEY + "stable_plate.radius").defineInRange("radius", 24.0, 4.0, 128.0);
        b.pop();
        b.translation(KEY + "eels").push("eels");
        EEL_FEAR = b.comment("Wild dragons run from live eels and eel items. Tamed dragons only sniff and hiss at eel items and never run. Turn off to stop it.")
            .translation(KEY + "eels.fear").define("dragons_fear_eels", true);
        b.pop();
        b.comment("Stat letter per dragon species. DEFAULT keeps Book of Dragons' own rating. S, A, B, C or D moves that stat's",
                "range to that letter, so the Book shows the new letter and the dragon's real stat changes with it.")
            .translation(KEY + "ratings").push("ratings");
        for (DragonType type : DragonType.values()) {
            String species = type.getSerializedName();
            b.translation(KEY + "ratings." + species).push(species);
            Map<PrimaryAttribute, ModConfigSpec.EnumValue<Rating>> row = new EnumMap<>(PrimaryAttribute.class);
            for (PrimaryAttribute attribute : PrimaryAttribute.values()) {
                row.put(attribute, b.comment(attribute.getDisplayName())
                    .translation(KEY + "ratings." + species + "." + attribute.name()).defineEnum(attribute.name(), Rating.DEFAULT));
            }
            RATINGS.put(type, row);
            b.pop();
        }
        b.pop();
        SPEC = b.build();
    }

    /** The species' own ranges, kept the first time we see each config object so edits always start from the original. */
    private static final Map<SpeciesGeneticsConfig, Map<Locus, AttributeRange>> ORIGINALS = new WeakHashMap<>();
    private static Field rangesField;

    private static AttributeRange target(Rating rating, AttributeRange original) {
        int width = original.ceiling() - original.floor();
        int floor = Math.max(0, Math.min(100 - width, rating.middle - width / 2));
        return new AttributeRange(floor, floor + width);
    }

    /** Writes the configured ranges into the engine's per-species genetics configs. Safe to call repeatedly. */
    @SuppressWarnings("unchecked")
    static boolean apply() {
        boolean fresh = false;
        if (!SPEC.isLoaded()) return false;
        for (DragonType type : DragonType.values()) {
            String id = type.getSerializedName();
            if (!SpeciesGeneticsConfigRegistry.has(id)) continue;
            SpeciesGeneticsConfig cfg = SpeciesGeneticsConfigRegistry.getConfig(id);
            try {
                if (rangesField == null) {
                    rangesField = SpeciesGeneticsConfig.class.getDeclaredField("attributeRanges");
                    rangesField.setAccessible(true);
                }
                Map<Locus, AttributeRange> live = (Map<Locus, AttributeRange>) rangesField.get(cfg);
                if (!ORIGINALS.containsKey(cfg)) fresh = true;
                Map<Locus, AttributeRange> original = ORIGINALS.computeIfAbsent(cfg, c -> new EnumMap<>(live));
                Map<Locus, AttributeRange> next = new EnumMap<>(Locus.class);
                next.putAll(original);
                RATINGS.get(type).forEach((attribute, value) -> {
                    Locus locus = Locus.valueOf(attribute.name());
                    AttributeRange base = original.get(locus);
                    if (base != null && value.get() != Rating.DEFAULT) next.put(locus, target(value.get(), base));
                });
                rangesField.set(cfg, next);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                BodAddon.LOGGER.warn("Could not apply ratings to {}: {}", id, ex.toString());
            }
        }
        return fresh;
    }

    /** Recomputes the stats of every loaded dragon after a rating changed, keeping its health fraction. */
    private static void refreshDragons(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (!(e instanceof DragonBase d)) continue;
                float fraction = d.getMaxHealth() > 0 ? d.getHealth() / d.getMaxHealth() : 1.0F;
                d.recalculateStats();
                d.applyStats();
                d.setHealth(Math.max(1.0F, fraction * d.getMaxHealth()));
            }
        }
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) { apply(); }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) { apply(); }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) { apply(); }

    /**
     * The engine rebuilds every species config from its raw JSON when a player joins (data sync), which throws our ranges away.
     * A rebuilt config is a new object, so checking once a second and re-applying (then recomputing dragons) puts them back.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        if (apply()) refreshDragons(event.getServer());
    }

    /** The file was edited while the game runs (or the in-game screen saved it). */
    public static void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        apply();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> refreshDragons(server));
    }

    private BodAddonConfig() {}
}
