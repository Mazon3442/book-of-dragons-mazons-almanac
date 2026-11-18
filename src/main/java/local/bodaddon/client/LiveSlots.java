package local.bodaddon.client;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.NativeImage;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.chassis.component.GenotypeComponent;
import net.magister.liberanimalium.genetics.GeneticsLayer;
import net.magister.liberanimalium.genetics.Locus;
import net.magister.liberanimalium.genetics.PaletteDraws;
import net.magister.liberanimalium.genetics.client.GeneticsTextureCompositor;
import net.magister.liberanimalium.genetics.client.PaletteLayerResolver;
import net.magister.liberanimalium.genetics.config.SpeciesGeneticsConfig;
import net.magister.liberanimalium.genetics.config.SpeciesGeneticsConfigRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Works out which colour slots of a dragon actually reach the screen. A dragon stores more colour draws than it shows:
 * the texture compositor walks the layers in render order and, for every pattern slot, hands out the next colour index
 * of that layer's colour locus to each paintable channel in the pattern's texture, then skips the rest of the
 * channels the layer reserves. A blank or single-channel pattern therefore leaves some stored colours unused, and
 * editing those does nothing. This repeats that walk (using the same textures, through the mod's own resolver and
 * channel detector) so the editor can hide the dead slots.
 */
final class LiveSlots {
    private static final Map<ResourceLocation, boolean[]> ACTIVE_CHANNELS = new java.util.HashMap<>();
    private static Method loadImage;
    private static Method detectChannels;

    private LiveSlots() {}

    /** The dragon's synced palette draws, or null if it is not a dragon with a genotype on this client. */
    private static PaletteDraws draws(Entity e) {
        if (!(e instanceof DragonBase d) || !d.hasGenotype()) return null;
        GenotypeComponent component = d.componentRegistry.get(GenotypeComponent.class);
        return component == null ? null : component.getPaletteDraws();
    }

    /** Changes whenever any look draw changes, so the screen knows to recompute. */
    static String key(Entity e) {
        PaletteDraws draws = draws(e);
        return draws == null ? "" : draws.toCacheString();
    }

    /** Set of "locus:index" for every colour slot in use, or null when it cannot be worked out (then nothing is hidden). */
    static Set<String> compute(Entity e) {
        PaletteDraws draws = draws(e);
        if (draws == null) return null;
        DragonBase dragon = (DragonBase) e;
        try {
            SpeciesGeneticsConfig cfg = SpeciesGeneticsConfigRegistry.getConfig(dragon.speciesId());
            PaletteLayerResolver resolver = new PaletteLayerResolver();
            init();
            Map<Locus, Integer> consumed = new EnumMap<>(Locus.class);
            Set<String> live = new HashSet<>();
            for (GeneticsLayer layer : GeneticsLayer.getLayersInRenderOrder()) {
                if (!resolver.shouldRenderLayer(layer, cfg) || layer.usesNativeTexture()) continue;
                Locus structure = layer.getStructureLocus();
                Locus color = layer.getColorLocus();
                int expressions = structure == null ? 1 : cfg.getExpressionCount(structure);
                int reserved = structure == null ? 0 : draws.channels(structure, cfg);
                for (int expression = 0; expression < expressions; expression++) {
                    ResourceLocation texture = resolver.getGrayscaleTexture(layer, dragon.getGenotype(), cfg, dragon.speciesId(), expression, draws);
                    if (texture == null) continue;
                    boolean[] active = activeChannels(texture);
                    if (active == null) continue;
                    int used = 0;
                    for (boolean on : active) if (on) used++;
                    int base = consumed.getOrDefault(color, 0);
                    for (int i = 0; i < used; i++) live.add(color.name() + ":" + (base + i));
                    consumed.put(color, base + Math.max(used, reserved));
                }
            }
            return live.isEmpty() ? null : live;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static void init() throws ReflectiveOperationException {
        if (loadImage != null) return;
        Method load = GeneticsTextureCompositor.class.getDeclaredMethod("loadNativeImage", ResourceLocation.class);
        Method detect = GeneticsTextureCompositor.class.getDeclaredMethod("detectActiveChannels", NativeImage.class);
        load.setAccessible(true);
        detect.setAccessible(true);
        loadImage = load;
        detectChannels = detect;
    }

    private static boolean[] activeChannels(ResourceLocation texture) throws ReflectiveOperationException {
        boolean[] cached = ACTIVE_CHANNELS.get(texture);
        if (cached != null) return cached;
        GeneticsTextureCompositor compositor = new GeneticsTextureCompositor();
        NativeImage image = (NativeImage) loadImage.invoke(compositor, texture);
        if (image == null) return null;
        try {
            boolean[] active = (boolean[]) detectChannels.invoke(compositor, image);
            ACTIVE_CHANNELS.put(texture, active);
            return active;
        } finally {
            image.close();
        }
    }
}
