package local.bodaddon.editor;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

import local.bodaddon.ModItems;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** A consumed book authorizes one open session with one specific dragon. */
final class SketchbookSessions {
    private record Session(UUID token, WeakReference<DragonBase> target) {
        DragonBase dragon() { return target.get(); }
    }
    private static final Map<ServerPlayer, Session> OPEN = new WeakHashMap<>();

    private SketchbookSessions() {}

    static EditorPayloads.SketchbookPage open(ServerPlayer player, DragonBase dragon, InteractionHand hand) {
        ItemStack book = player.getItemInHand(hand);
        if (!book.is(ModItems.SKETCHBOOK.get()) || !canEdit(player, dragon)) return null;
        VariantRow[] rows = VariantEdits.rows(dragon, dragon.getBond(player.getUUID()));
        if (rows.length == 0) return null;
        Session session = new Session(UUID.randomUUID(), new WeakReference<>(dragon));
        if (!player.isCreative()) book.shrink(1);
        OPEN.put(player, session);
        return page(session, dragon, rows, true);
    }

    static EditorPayloads.SketchbookPage change(ServerPlayer player, EditorPayloads.SketchbookChoice choice) {
        Session session = OPEN.get(player);
        if (session == null || !session.token().equals(choice.token())) return null;
        DragonBase dragon = session.dragon();
        if (!canEdit(player, dragon)) {
            OPEN.remove(player);
            return null;
        }
        int bond = dragon.getBond(player.getUUID());
        if (!VariantEdits.set(dragon, choice.locus(), choice.slot(), choice.palette(), bond)) return null;
        return page(session, dragon, VariantEdits.rows(dragon, bond), false);
    }

    static void close(ServerPlayer player, UUID token) {
        Session open = OPEN.get(player);
        if (open != null && open.token().equals(token)) OPEN.remove(player);
    }

    static void tick(ServerPlayer player) {
        Session session = OPEN.get(player);
        if (session != null && !canEdit(player, session.dragon())) OPEN.remove(player);
    }

    static boolean canEdit(ServerPlayer player, DragonBase dragon) {
        return dragon != null && player.isAlive() && !player.isSpectator() && dragon.isAlive() && player.level() == dragon.level()
            && player.canInteractWithEntity(dragon, 0.0) && player.distanceToSqr(dragon) <= DragonEdits.REACH_SQ
            && dragon.isTame() && player.getUUID().equals(dragon.ownerId());
    }

    private static EditorPayloads.SketchbookPage page(Session session, DragonBase dragon, VariantRow[] rows, boolean opening) {
        return new EditorPayloads.SketchbookPage(session.token(), dragon.getId(), dragon.getDisplayName().getString(), rows, opening);
    }
}
