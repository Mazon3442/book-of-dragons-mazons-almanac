package local.bodaddon.editor;

import local.bodaddon.ModItems;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Re-applies edited personality, which the engine can rebuild. */
public final class DragonEditEvents {
    private static final int PERIOD = 40;

    @SubscribeEvent
    public static void onDragonInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof DragonBase dragon) || !event.getItemStack().is(ModItems.HICCUPS_SKETCHBOOK.get())) return;
        // The dragon can handle the interaction itself before vanilla reaches the held item.
        event.setCancellationResult(ModItems.HICCUPS_SKETCHBOOK.get()
            .interactLivingEntity(event.getItemStack(), event.getEntity(), dragon, event.getHand()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) SketchbookSessions.tick(player);
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof DragonBase d) || !(d.level() instanceof ServerLevel)) return;
        if ((d.tickCount + d.getId()) % PERIOD != 0) return;
        if (d.getPersistentData().contains(DragonEdits.TRAITS_KEY, Tag.TAG_LIST)) DragonEdits.applyTraits(d);
    }

    private DragonEditEvents() {}
}
