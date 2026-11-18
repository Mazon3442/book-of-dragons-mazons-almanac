package local.bodaddon.editor;

import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Re-applies edited personality, which the engine can rebuild. */
public final class DragonEditEvents {
    private static final int PERIOD = 40;

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof DragonBase d) || !(d.level() instanceof ServerLevel)) return;
        if ((d.tickCount + d.getId()) % PERIOD != 0) return;
        if (d.getPersistentData().contains(DragonEdits.TRAITS_KEY, Tag.TAG_LIST)) DragonEdits.applyTraits(d);
    }

    private DragonEditEvents() {}
}
