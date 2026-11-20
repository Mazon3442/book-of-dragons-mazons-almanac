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

public final class SketchbookItem extends Item {
    public SketchbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DragonBase dragon)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.FAIL;
        var page = SketchbookSessions.open(serverPlayer, dragon, hand);
        if (page == null) {
            String message = SketchbookSessions.canEdit(serverPlayer, dragon)
                ? "bodaddon.sketchbook.no_variants" : "bodaddon.sketchbook.owner_only";
            player.displayClientMessage(Component.translatable(message), true);
            return InteractionResult.FAIL;
        }
        EditorPayloads.sendSketchbook(serverPlayer, page);
        return InteractionResult.CONSUME;
    }
}
