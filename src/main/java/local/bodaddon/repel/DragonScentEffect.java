package local.bodaddon.repel;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** While it lasts, wild dragons within {@link #RADIUS} blocks of the bearer are driven away. */
public class DragonScentEffect extends MobEffect {
    public static final double RADIUS = 24.0;

    public DragonScentEffect() { super(MobEffectCategory.BENEFICIAL, 0x6B8E3A); }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 20 == 0; }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) DragonRepel.repel(level, entity.position(), RADIUS);
        return true;
    }
}
