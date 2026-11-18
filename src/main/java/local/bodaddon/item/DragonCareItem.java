package local.bodaddon.item;

import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

abstract class DragonCareItem extends Item {
    protected DragonCareItem(Properties properties) {
        super(properties);
    }

    @Override
    public final InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DragonBase dragon)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (stack.isEmpty() || !dragon.isAlive()) return InteractionResult.FAIL;
        if (!dragon.isTame() || !player.getUUID().equals(dragon.ownerId())) {
            player.displayClientMessage(Component.translatable("bodaddon.care.owner_only"), true);
            return InteractionResult.FAIL;
        }
        if (!apply((ServerLevel) player.level(), player, dragon)) return InteractionResult.FAIL;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.sidedSuccess(false);
    }

    protected abstract boolean apply(ServerLevel level, Player player, DragonBase dragon);
}
