package local.bodaddon.editor;

import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Creative-only wand: right-click a dragon to open the editor menu. */
public class DragonEditorItem extends Item {
    public DragonEditorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DragonBase dragon)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!player.isCreative()) {
            player.displayClientMessage(Component.translatable("bodaddon.editor.creative_only"), true);
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer sp) EditorPayloads.sendOpen(sp, DragonEdits.snapshot(sp, dragon));
        return InteractionResult.CONSUME;
    }
}
