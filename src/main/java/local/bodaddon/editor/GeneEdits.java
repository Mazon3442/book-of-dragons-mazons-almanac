package local.bodaddon.editor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.body.physics.PhysicsSheet;
import net.magister.liberanimalium.body.stats.FaunaStatResolver;
import net.magister.liberanimalium.body.stats.FaunaStatSheet;
import net.magister.liberanimalium.body.stats.PrimaryAttribute;
import net.magister.liberanimalium.chassis.component.GenotypeComponent;
import net.magister.liberanimalium.genetics.Allele;
import net.magister.liberanimalium.genetics.Genotype;
import net.magister.liberanimalium.genetics.Locus;
import net.magister.liberanimalium.genetics.config.SpeciesGeneticsConfig;
import net.magister.liberanimalium.genetics.config.SpeciesGeneticsConfigRegistry;

/**
 * Gene-level edits. Max health comes from the CON attribute: a pair of alleles CON1..CON10 whose dominance values
 * average into a 0-100 score (FaunaStatResolver.resolveScore). FaunaStatSheet.effective then maps that whole 0-100 score
 * onto the species' floor..ceiling stat range, so every allele combination is a valid dragon and the range limits are
 * already built into the resulting health. Each combination is one slider step.
 */
public final class GeneEdits {
    /** Max health for each allowed CON combination, lowest first, plus which one the dragon has now. */
    public record HealthSteps(float[] health, int current) {
        public static final HealthSteps NONE = new HealthSteps(new float[0], 0);
    }

    private GeneEdits() {}

    /** CON allele dominance sums, 2..20 (higher sum = lower score = less health). */
    private static int scoreOf(int sum) {
        return Math.round((10.0F - sum / 2.0F) / 9.0F * 100.0F);
    }

    private static Allele con(int dominance) {
        return Allele.of("CON" + dominance, dominance);
    }

    private static List<Integer> allowedSums() {
        List<Integer> sums = new ArrayList<>();
        for (int s = 20; s >= 2; s--) sums.add(s);              // ascending score, lowest health first
        return sums;
    }

    private static int currentSum(Genotype g, int pair) {
        Allele fallback = con(5);
        return g.getAllele1Safe(pair, fallback).getDominance() + g.getAllele2Safe(pair, fallback).getDominance();
    }

    public static HealthSteps healthSteps(DragonBase d) {
        if (!d.hasGenotype()) return HealthSteps.NONE;
        try {
            SpeciesGeneticsConfig cfg = SpeciesGeneticsConfigRegistry.getConfig(d.speciesId());
            Genotype g = d.getGenotype();
            int pair = cfg.getPairIndexOffset(Locus.CON);
            int cur = currentSum(g, pair);
            List<Integer> sums = allowedSums();
            PhysicsSheet ps = PhysicsSheet.of(d);
            var weight = ps.weight(d.getWeightClass());
            var plan = ps.plan(d.gameplay().plan());
            Map<PrimaryAttribute, Integer> raw = FaunaStatResolver.resolveAll(g, cfg);
            float[] hp = new float[sums.size()];
            int current = 0;
            for (int i = 0; i < sums.size(); i++) {
                Map<PrimaryAttribute, Integer> trial = new EnumMap<>(raw);
                trial.put(PrimaryAttribute.CON, scoreOf(sums.get(i)));
                var effective = FaunaStatSheet.effective(trial, cfg, 0.0F);
                hp[i] = FaunaStatSheet.compute(effective, ps.stats(), weight, plan).health();
                if (sums.get(i) == cur) current = i;
            }
            return new HealthSteps(hp, current);
        } catch (RuntimeException ex) {
            return HealthSteps.NONE;
        }
    }

    /** Applies one of the {@link #healthSteps} to the dragon's genotype and recalculates its stats. */
    public static void setHealthStep(DragonBase d, int step) {
        if (!d.hasGenotype()) return;
        SpeciesGeneticsConfig cfg = SpeciesGeneticsConfigRegistry.getConfig(d.speciesId());
        Genotype g = d.getGenotype();
        int pair = cfg.getPairIndexOffset(Locus.CON);
        List<Integer> sums = allowedSums();
        if (step < 0 || step >= sums.size()) return;
        int sum = sums.get(step);
        int low = Math.max(1, Math.min(10, sum / 2));
        int high = Math.max(1, Math.min(10, sum - low));

        Genotype.Builder b = Genotype.builder();
        for (int i = 0; i < g.getPairCount(); i++) {
            if (i == pair) {
                b.addPair(con(low), con(high));
            } else {
                Allele[] existing = g.getAllelePair(i);
                b.addPair(existing[0], existing[1]);
            }
        }
        GenotypeComponent component = d.componentRegistry.get(GenotypeComponent.class);
        float fraction = d.getMaxHealth() > 0 ? d.getHealth() / d.getMaxHealth() : 1.0F;
        d.setGenotypeAndDraws(b.build(), component != null ? component.getPaletteDraws() : null);
        d.recalculateStats();
        d.applyStats();
        d.setHealth(Math.max(1.0F, fraction * d.getMaxHealth()));
    }
}
