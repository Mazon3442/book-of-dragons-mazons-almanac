package local.bodaddon.repel;

import java.util.List;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Drives wild dragons away from a point: used by the repelling blocks and by the Dragon Scent effect. */
public final class DragonRepel {
    /** Dragons are sent away in hops this long, so each route is short enough to plan quickly over real terrain. */
    private static final double HOP = 12.0;

    /** The wild (untamed) dragons within {@code radius} blocks of the centre. */
    public static List<DragonBase> wildDragonsNear(ServerLevel level, Vec3 centre, double radius) {
        return level.getEntitiesOfClass(DragonBase.class, AABB.ofSize(centre, radius * 2, radius * 2, radius * 2),
            d -> d.isAlive() && !d.isTame() && d.faunaMind() != null && d.position().distanceToSqr(centre) <= radius * radius);
    }

    /** Drops each wild dragon's target and, unless it is still on its last route, sends it another hop away from the centre. */
    public static void repel(ServerLevel level, Vec3 centre, double radius) {
        for (DragonBase dragon : wildDragonsNear(level, centre, radius)) {
            dragon.setTarget(null);
            var route = dragon.faunaMind().route();
            if (route.hasRoute() || route.solving()) continue;
            Vec3 away = new Vec3(dragon.getX() - centre.x, 0, dragon.getZ() - centre.z);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            BlockPos exit = BlockPos.containing(dragon.getX() + away.x * HOP, dragon.getY(), dragon.getZ() + away.z * HOP);
            if (!level.hasChunkAt(exit)) continue;
            if (!dragon.isFlying()) exit = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, exit);
            dragon.faunaMind().forceRoute(exit);
        }
    }

    private DragonRepel() {}
}
