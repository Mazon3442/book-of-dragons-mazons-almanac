package local.bodaddon.item;

import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

public final class DragonTreatItem extends DragonCareItem {
    // Bond uses 0..1000 internally: 15 points is 1.5 on the player's 0..100 scale.
    static final int BOND_PER_TREAT = 15;
    static final int MAX_BOND = 1000;

    public DragonTreatItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean apply(ServerLevel level, Player player, DragonBase dragon) {
        int bond = dragon.getBond(player.getUUID());
        if (bond >= MAX_BOND || dragon.faunaMind() == null) {
            player.displayClientMessage(Component.translatable("bodaddon.care.bond_full"), true);
            return false;
        }
        dragon.setBond(player.getUUID(), Math.min(MAX_BOND, Math.max(0, bond) + BOND_PER_TREAT));
        level.sendParticles(ParticleTypes.HEART, dragon.getX(), dragon.getEyeY(), dragon.getZ(), 5, 0.4, 0.2, 0.4, 0.02);
        level.playSound(null, dragon.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, 1.0F);
        return true;
    }
}
