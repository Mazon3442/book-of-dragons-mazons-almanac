package local.bodaddon.item;

import java.util.List;
import local.bodaddon.dragon.DragonFriendships;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class SagefruitItem extends Item {
    public SagefruitItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DragonBase dragon)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (stack.isEmpty() || !dragon.isAlive() || dragon.faunaMind() == null) return InteractionResult.FAIL;
        if (dragon.ownerId() != null && !player.getUUID().equals(dragon.ownerId())) {
            player.displayClientMessage(Component.translatable("bodaddon.sagefruit.owner_only"), true);
            return InteractionResult.FAIL;
        }
        int added = DragonFriendships.befriendNearby(dragon);
        if (added == 0) {
            player.displayClientMessage(Component.translatable("bodaddon.sagefruit.no_new_friends"), true);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.HEART, dragon.getX(), dragon.getEyeY(), dragon.getZ(), 8, 0.5, 0.3, 0.5, 0.02);
        level.playSound(null, dragon.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, 1.0F);
        player.displayClientMessage(Component.translatable("bodaddon.sagefruit.befriended", added), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bodaddon.sagefruit.desc1").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bodaddon.sagefruit.desc2").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SagefruitItem fruit) || !(event.getTarget() instanceof DragonBase dragon)) return;
        event.setCancellationResult(fruit.interactLivingEntity(stack, event.getEntity(), dragon, event.getHand()));
        event.setCanceled(true);
    }
}
