package local.bodaddon.editor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

import local.bodaddon.ModItems;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** A consumed book authorizes one open session with one specific dragon. */
final class SketchbookSessions {
    /** {@code original} is the look the dragon had when the book opened, for Revert. */
    private record Session(UUID token, WeakReference<DragonBase> target, List<VariantEdits.Look> original) {
        DragonBase dragon() { return target.get(); }
    }
    private static final Map<ServerPlayer, Session> OPEN = new WeakHashMap<>();

    private SketchbookSessions() {}

    static EditorPayloads.SketchbookPage open(ServerPlayer player, DragonBase dragon, InteractionHand hand) {
        ItemStack book = player.getItemInHand(hand);
        if (!book.is(ModItems.SKETCHBOOK.get()) || !canEdit(player, dragon)) return null;
        VariantRow[] rows = VariantEdits.rows(dragon, dragon.getBond(player.getUUID()));
        if (rows.length == 0) return null;
        Session session = new Session(UUID.randomUUID(), new WeakReference<>(dragon), VariantEdits.snapshot(dragon));
        if (!player.isCreative()) book.shrink(1);
        OPEN.put(player, session);
        return page(session, dragon, rows, true, dragon.getBond(player.getUUID()));
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
        return page(session, dragon, VariantEdits.rows(dragon, bond), false, bond);
    }

    /** Puts the dragon back to how it looked when the book was opened. */
    static EditorPayloads.SketchbookPage revert(ServerPlayer player, UUID token) {
        Session session = live(player, token);
        if (session == null) return null;
        DragonBase dragon = session.dragon();
        VariantEdits.restore(dragon, session.original());
        int bond = dragon.getBond(player.getUUID());
        return page(session, dragon, VariantEdits.rows(dragon, bond), false, bond);
    }

    /** Tears the dragon's current look off as a Sketchbook Page and hands it over. */
    static void saveLook(ServerPlayer player, UUID token) {
        Session session = live(player, token);
        if (session == null) return;
        DragonBase dragon = session.dragon();
        ItemStack stack = SketchbookPageItem.create(ModItems.SKETCHBOOK_PAGE.get(), dragon.speciesId(),
            dragon.getDisplayName().getString(), VariantEdits.snapshot(dragon));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.displayClientMessage(Component.translatable("bodaddon.sketchbook.saved"), true);
    }

    /** Applies the page in the given inventory slot if it is of the dragon's species. The page is kept. */
    static EditorPayloads.SketchbookPage paste(ServerPlayer player, UUID token, int slot) {
        Session session = live(player, token);
        if (session == null) return null;
        DragonBase dragon = session.dragon();
        var inventory = player.getInventory();
        ItemStack page = slot >= 0 && slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
        if (!page.is(ModItems.SKETCHBOOK_PAGE.get()) || !SketchbookPageItem.species(page).equals(dragon.speciesId())) {
            player.displayClientMessage(Component.translatable("bodaddon.sketchbook.no_page"), true);
            return null;
        }
        int bond = dragon.getBond(player.getUUID());
        int[] result = VariantEdits.paste(dragon, SketchbookPageItem.looks(page), bond);
        player.displayClientMessage(Component.translatable(result[1] > 0 ? "bodaddon.sketchbook.pasted_locked" : "bodaddon.sketchbook.pasted",
            result[0], result[1]), true);
        return page(session, dragon, VariantEdits.rows(dragon, bond), false, bond);
    }

    /** The open session for this token, or null (dropping it if the player can no longer edit). */
    private static Session live(ServerPlayer player, UUID token) {
        Session session = OPEN.get(player);
        if (session == null || !session.token().equals(token)) return null;
        if (!canEdit(player, session.dragon())) {
            OPEN.remove(player);
            return null;
        }
        return session;
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

    private static EditorPayloads.SketchbookPage page(Session session, DragonBase dragon, VariantRow[] rows, boolean opening, int bond) {
        int next = VariantEdits.nextUnlock(rows);
        return new EditorPayloads.SketchbookPage(session.token(), dragon.getId(), dragon.getDisplayName().getString(), rows, opening,
            bond / 10, next, VariantEdits.tierFor(next));
    }
}
