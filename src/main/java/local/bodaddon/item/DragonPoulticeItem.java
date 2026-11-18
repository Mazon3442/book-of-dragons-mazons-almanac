package local.bodaddon.item;

import java.util.List;
import local.bodaddon.editor.DragonEdits;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class DragonPoulticeItem extends DragonCareItem {
    static final String TREATMENT_KEY = "bodaddon_poultice_ticks";
    static final int TREATMENT_TICKS = 60 * 20;
    static final int RECOVERY_REDUCTION_TICKS = 60 * 60 * 20;

    public DragonPoulticeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bodaddon.dragon_poultice.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bodaddon.dragon_poultice.desc2").withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected boolean apply(ServerLevel level, Player player, DragonBase dragon) {
        if (!DragonEdits.isInjured(dragon)) {
            player.displayClientMessage(Component.translatable("bodaddon.care.not_injured"), true);
            return false;
        }
        if (dragon.getPersistentData().getInt(TREATMENT_KEY) > 0) {
            player.displayClientMessage(Component.translatable("bodaddon.care.treating"), true);
            return false;
        }
        // Remaining loaded ticks survive saves and pause while the dragon is unloaded.
        dragon.getPersistentData().putInt(TREATMENT_KEY, TREATMENT_TICKS);
        player.displayClientMessage(Component.translatable("bodaddon.care.treatment_started"), true);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, dragon.getX(), dragon.getEyeY(), dragon.getZ(), 6, 0.4, 0.2, 0.4, 0.02);
        level.playSound(null, dragon.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 0.7F, 1.0F);
        return true;
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof DragonBase dragon) || !(dragon.level() instanceof ServerLevel level)) return;
        int remaining = dragon.getPersistentData().getInt(TREATMENT_KEY);
        if (remaining <= 0) return;
        if (!dragon.isAlive() || !DragonEdits.isInjured(dragon)) {
            dragon.getPersistentData().remove(TREATMENT_KEY);
        } else if (remaining > 1) {
            dragon.getPersistentData().putInt(TREATMENT_KEY, remaining - 1);
        } else {
            dragon.getPersistentData().remove(TREATMENT_KEY);
            DragonEdits.reduceInjuryTime(dragon, RECOVERY_REDUCTION_TICKS);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, dragon.getX(), dragon.getEyeY(), dragon.getZ(), 12, 0.6, 0.3, 0.6, 0.03);
            level.playSound(null, dragon.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 0.4F, 1.4F);
        }
    }
}
