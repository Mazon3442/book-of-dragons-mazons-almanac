package local.bodaddon;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BodAddon.MOD_ID);
    public static final DeferredHolder<MobEffect, MobEffect> CALMED = EFFECTS.register("calmed",
        () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x83B65B) {});

    public static final DeferredHolder<MobEffect, MobEffect> DRAGON_SCENT = EFFECTS.register("dragon_scent",
        local.bodaddon.repel.DragonScentEffect::new);

    public static void register(IEventBus modBus) { EFFECTS.register(modBus); }

    private ModEffects() {}
}
