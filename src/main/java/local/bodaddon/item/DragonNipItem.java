package local.bodaddon.item;

import java.util.List;
import local.bodaddon.ModEffects;
import local.bodaddon.editor.DragonEdits;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class DragonNipItem extends Item {
    public static final int DURATION_TICKS = 5 * 60 * 20;
    private static final String APPLIED_KEY = "bodaddon_nip_applied";

    public DragonNipItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DragonBase dragon)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (stack.isEmpty() || !dragon.isAlive() || dragon.faunaMind() == null) return InteractionResult.FAIL;
        if (dragon.ownerId() != null && !player.getUUID().equals(dragon.ownerId())) {
            player.displayClientMessage(Component.translatable("bodaddon.nip.owner_only"), true);
            return InteractionResult.FAIL;
        }
        if (dragon.hasEffect(ModEffects.CALMED)) {
            player.displayClientMessage(Component.translatable("bodaddon.nip.already_calmed"), true);
            return InteractionResult.FAIL;
        }
        if (!dragon.addEffect(new MobEffectInstance(ModEffects.CALMED, DURATION_TICKS))) return InteractionResult.FAIL;
        applyCalming(dragon);
        dragon.setTarget(null);
        dragon.setLastHurtByMob(null);
        var engagement = dragon.faunaMind().engagement();
        if (engagement != null) engagement.reset(dragon.faunaMind().handle());
        if (!player.getAbilities().instabuild) stack.shrink(1);
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, dragon.getX(), dragon.getEyeY(), dragon.getZ(), 8, 0.5, 0.3, 0.5, 0.02);
        level.playSound(null, dragon.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, 1.1F);
        player.displayClientMessage(Component.translatable("bodaddon.nip.applied"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bodaddon.dragon_nip.desc1").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bodaddon.dragon_nip.desc2").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    private static void applyCalming(DragonBase dragon) {
        dragon.getPersistentData().putBoolean(APPLIED_KEY, true);
        DragonEdits.applyTraits(dragon);
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof DragonNipItem nip) || !(event.getTarget() instanceof DragonBase dragon)) return;
        // The dragon's own interaction handlers can consume the click before Item.interactLivingEntity runs.
        event.setCancellationResult(nip.interactLivingEntity(stack, event.getEntity(), dragon, event.getHand()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void beforeTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof DragonBase dragon && dragon.level() instanceof ServerLevel
            && dragon.hasEffect(ModEffects.CALMED)) applyCalming(dragon);
    }

    @SubscribeEvent
    public static void afterTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof DragonBase dragon && dragon.level() instanceof ServerLevel
            && dragon.getPersistentData().getBoolean(APPLIED_KEY) && !dragon.hasEffect(ModEffects.CALMED)) {
            DragonEdits.applyTraits(dragon);
            dragon.getPersistentData().remove(APPLIED_KEY);
        }
    }
}
