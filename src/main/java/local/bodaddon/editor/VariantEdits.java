package local.bodaddon.editor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import local.bodaddon.BodAddonConfig;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.chassis.component.GenotypeComponent;
import net.magister.liberanimalium.genetics.Locus;
import net.magister.liberanimalium.genetics.PaletteDraws;
import net.magister.liberanimalium.genetics.PalettePoolRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * Colour and pattern edits. A dragon's look is its {@link PaletteDraws}: for every colour or structure locus, and for
 * every expression slot of it, the palette or pattern drawn from the species' pool. A layer with two pattern slots
 * (a wing with two overlapping patterns, say) therefore has two pattern draws and two colour draws, and each one is its
 * own row. A row's options are that locus' pool, so only looks the species can naturally have are offered. Rows are
 * rebuilt from the dragon on both sides of the packet, so a row index plus an option index is all the client sends.
 */
public final class VariantEdits {
    private static final String[] RARITY_ORDER = {"COMMON", "UNCOMMON", "RARE", "VERY_RARE"};
    private static final String BLANK = "blank";
    private static final String[] PARENTS = {"c1", "c2"};
    /** Upper bound when scanning a locus for the slots the dragon actually has draws for. */
    private static final int MAX_SLOTS = 8;

    private record Row(Locus locus, int slot, int slots, List<ResourceLocation> options, int current) {}

    private VariantEdits() {}

    /** Every row, all options free: the creative editor. */
    public static VariantRow[] rows(DragonBase d) { return rows(d, -1); }

    /**
     * Rows for the sketchbook. {@code bond} is the owner's bond with the dragon (0 to 1000); options of a rarity that needs more
     * are marked with the bond they need. A negative bond marks nothing.
     */
    public static VariantRow[] rows(DragonBase d, int bond) {
        return rowsOf(d).stream().map(r -> new VariantRow(
            r.locus().getDisplayName(),
            r.locus().name(),
            r.slot(),
            r.locus().getCategory() == Locus.LocusCategory.COLOR,
            r.options().stream().map(VariantEdits::prettify).toArray(String[]::new),
            r.options().stream().map(ResourceLocation::toString).toArray(String[]::new),
            r.current(),
            bondNeeded(d.speciesId(), r, bond))).toArray(VariantRow[]::new);
    }

    /** For each option of a row, the bond (0 to 100) still missing before it may be chosen, else 0. The shown option is always free. */
    private static int[] bondNeeded(String species, Row row, int bond) {
        int[] needed = new int[row.options().size()];
        if (bond < 0) return needed;
        Map<String, List<ResourceLocation>> pool = PalettePoolRegistry.getPoolMap(species, row.locus().name());
        for (int i = 0; i < needed.length; i++) {
            if (i == row.current()) continue;
            int need = BodAddonConfig.sketchbookBond(rarityOf(pool, row.options().get(i)));
            if (need * 10 > bond) needed[i] = need;
        }
        return needed;
    }

    /** Index into {@link #RARITY_ORDER} of the lowest tier that lists the option; anything outside the known tiers counts as common. */
    private static int rarityOf(Map<String, List<ResourceLocation>> pool, ResourceLocation option) {
        if (pool == null) return 0;
        for (int i = 0; i < RARITY_ORDER.length; i++) {
            for (Map.Entry<String, List<ResourceLocation>> tier : pool.entrySet()) {   // the pools name their tiers in lower case
                if (tier.getKey().equalsIgnoreCase(RARITY_ORDER[i]) && tier.getValue().contains(option)) return i;
            }
        }
        return 0;
    }

    public static void set(DragonBase d, int rowIndex, int option) {
        List<Row> rows = rowsOf(d);
        if (rowIndex < 0 || rowIndex >= rows.size()) return;
        Row row = rows.get(rowIndex);
        if (option < 0 || option >= row.options().size()) return;
        apply(d, row, row.options().get(option));
    }

    /** The sketchbook's edit: refused unless the owner's {@code bond} (0 to 1000) unlocks the option's rarity. */
    static boolean set(DragonBase d, String locus, int slot, ResourceLocation choice, int bond) {
        for (Row row : rowsOf(d)) {
            if (row.locus().name().equals(locus) && row.slot() == slot && row.options().contains(choice)) {
                if (bondNeeded(d.speciesId(), row, bond)[row.options().indexOf(choice)] > 0) return false;
                return apply(d, row, choice);
            }
        }
        return false;
    }

    private static boolean apply(DragonBase d, Row row, ResourceLocation choice) {
        GenotypeComponent component = d.componentRegistry.get(GenotypeComponent.class);
        if (component == null || component.getPaletteDraws() == null) return false;

        PaletteDraws draws = component.getPaletteDraws();
        if (row.locus().getCategory() == Locus.LocusCategory.COLOR) {
            draws.setColorDraw(row.locus(), row.slot(), choice, choice);
        } else {
            draws.setStructureDraw(row.locus(), row.slot(), choice, choice, null);
        }
        d.setGenotypeAndDraws(d.getGenotype(), draws);   // re-applies and re-syncs the look to clients
        return true;
    }

    private static List<Row> rowsOf(DragonBase d) {
        List<Row> rows = new ArrayList<>();
        GenotypeComponent component = d.componentRegistry.get(GenotypeComponent.class);
        if (component == null || !d.hasGenotype() || component.getPaletteDraws() == null) return rows;
        try {
            PaletteDraws draws = component.getPaletteDraws();
            for (Locus locus : Locus.values()) {
                Locus.LocusCategory cat = locus.getCategory();
                if (cat != Locus.LocusCategory.COLOR && cat != Locus.LocusCategory.STRUCTURE) continue;
                List<ResourceLocation> options = options(d.speciesId(), locus);
                if (options.size() < 2) continue;
                List<Integer> slots = new ArrayList<>();
                for (int slot = 0; slot < MAX_SLOTS; slot++) {
                    if (draws.getC1(locus, slot) != null || draws.getC2(locus, slot) != null) slots.add(slot);
                }
                for (int slot : slots) {
                    rows.add(new Row(locus, slot, slots.size(), options, current(draws, locus, slot, options)));
                }
            }
        } catch (RuntimeException ex) {
            rows.clear();
        }
        return rows;
    }

    private static List<ResourceLocation> options(String species, Locus locus) {
        Map<String, List<ResourceLocation>> pool = PalettePoolRegistry.getPoolMap(species, locus.name());
        if (pool == null) return List.of();
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (String rarity : RARITY_ORDER) {
            List<ResourceLocation> tier = pool.get(rarity);
            if (tier != null) out.addAll(tier);
        }
        pool.values().forEach(out::addAll);   // any tier names outside the usual four
        return new ArrayList<>(out);
    }

    /** The draw that is actually showing: the first parent that is not dormant, else whatever is there. */
    private static int current(PaletteDraws draws, Locus locus, int slot, List<ResourceLocation> options) {
        ResourceLocation shown = null;
        for (String parent : PARENTS) {
            ResourceLocation rl = draws.getColorDraw(locus, slot, parent);
            if (rl != null && !draws.isDormant(locus, slot, parent)) {
                shown = rl;
                break;
            }
        }
        if (shown == null) shown = draws.getC1(locus, slot) != null ? draws.getC1(locus, slot) : draws.getC2(locus, slot);
        return Math.max(0, options.indexOf(shown));
    }

    /** Last path segment without a ".png" extension; the pools hold full texture paths. */
    private static String baseName(ResourceLocation rl) {
        String name = rl.getPath().substring(rl.getPath().lastIndexOf('/') + 1);
        return name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
    }

    /** "bookofdragons:textures/entity/genetics/nightfury/d_dorsal_spots.png" becomes "Dorsal spots"; a leading one-letter locus tag is dropped. */
    static String prettify(ResourceLocation rl) {
        String name = baseName(rl);
        if (name.equals(BLANK)) return "None";
        if (name.length() > 2 && name.charAt(1) == '_') name = name.substring(2);
        name = name.replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
