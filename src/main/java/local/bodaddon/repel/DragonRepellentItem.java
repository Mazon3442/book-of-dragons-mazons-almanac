package local.bodaddon.repel;

import java.util.List;
import local.bodaddon.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** A bottle of eel and dragon root. Drinking it gives the Dragon Scent effect for a while. */
public class DragonRepellentItem extends Item {
    public static final int DURATION_TICKS = 8 * 60 * 20;

    public DragonRepellentItem(Properties properties) { super(properties); }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 32; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Player player = entity instanceof Player p ? p : null;
        if (!level.isClientSide) entity.addEffect(new MobEffectInstance(ModEffects.DRAGON_SCENT, DURATION_TICKS));
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
            stack.consume(1, player);
        }
        if (player == null || !player.hasInfiniteMaterials()) {
            if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (player != null) player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bodaddon.dragon_repellent.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bodaddon.dragon_repellent.desc2").withStyle(ChatFormatting.GRAY));
    }
}
