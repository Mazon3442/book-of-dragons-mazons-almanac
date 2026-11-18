package local.bodaddon.editor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public static VariantRow[] rows(DragonBase d) {
        return rowsOf(d).stream().map(r -> new VariantRow(
            r.locus().getDisplayName(),
            r.locus().name(),
            r.slot(),
            r.locus().getCategory() == Locus.LocusCategory.COLOR,
            r.options().stream().map(VariantEdits::prettify).toArray(String[]::new),
            r.options().stream().map(ResourceLocation::toString).toArray(String[]::new),
            r.current())).toArray(VariantRow[]::new);
    }

    public static void set(DragonBase d, int rowIndex, int option) {
        List<Row> rows = rowsOf(d);
        if (rowIndex < 0 || rowIndex >= rows.size()) return;
        Row row = rows.get(rowIndex);
        if (option < 0 || option >= row.options().size()) return;
        GenotypeComponent component = d.componentRegistry.get(GenotypeComponent.class);
        if (component == null || component.getPaletteDraws() == null) return;

        PaletteDraws draws = component.getPaletteDraws();
        ResourceLocation choice = row.options().get(option);
        if (row.locus().getCategory() == Locus.LocusCategory.COLOR) {
            draws.setColorDraw(row.locus(), row.slot(), choice, choice);
        } else {
            draws.setStructureDraw(row.locus(), row.slot(), choice, choice, null);
        }
        d.setGenotypeAndDraws(d.getGenotype(), draws);   // re-applies and re-syncs the look to clients
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
