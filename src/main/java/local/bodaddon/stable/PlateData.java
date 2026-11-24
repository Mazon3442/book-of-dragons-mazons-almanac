package local.bodaddon.stable;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** The Stable Signs placed in one dimension, each with the roost it was linked to, saved with the world. */
public final class PlateData extends SavedData {
    private static final String NAME = "bodaddon_stable_signs";
    private static final String KEY = "Signs";

    /** Sign position to its roost position. */
    private final Long2LongOpenHashMap signs = new Long2LongOpenHashMap();

    public static PlateData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlateData::new, PlateData::load, null), NAME);
    }

    private static PlateData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlateData data = new PlateData();
        long[] pairs = tag.getLongArray(KEY);
        for (int i = 0; i + 1 < pairs.length; i += 2) data.signs.put(pairs[i], pairs[i + 1]);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] pairs = new long[signs.size() * 2];
        int i = 0;
        for (var e : signs.long2LongEntrySet()) {
            pairs[i++] = e.getLongKey();
            pairs[i++] = e.getLongValue();
        }
        tag.putLongArray(KEY, pairs);
        return tag;
    }

    public void put(BlockPos sign, BlockPos roost) {
        signs.put(sign.asLong(), roost.asLong());
        setDirty();
    }

    /** The roost this sign was linked to, or null if it is not a known sign. */
    public BlockPos roostOf(BlockPos sign) {
        return signs.containsKey(sign.asLong()) ? BlockPos.of(signs.get(sign.asLong())) : null;
    }

    public void removeSign(BlockPos sign) {
        if (signs.containsKey(sign.asLong())) {
            signs.remove(sign.asLong());
            setDirty();
        }
    }

    /** Every (sign, roost) pair; a copy, so the caller may remove while looping. */
    public List<BlockPos[]> pairs() {
        List<BlockPos[]> out = new ArrayList<>();
        for (var e : signs.long2LongEntrySet()) out.add(new BlockPos[] {BlockPos.of(e.getLongKey()), BlockPos.of(e.getLongValue())});
        return out;
    }

    /**
     * Whether the point is within {@code radius} blocks of a roost that has a Stable Sign within the same distance of it.
     * (A sign placed farther away than that is refused when you place it, but the config radius may have shrunk since.)
     */
    public boolean nearActiveRoost(Vec3 point, double radius) {
        double limit = radius * radius;
        for (var e : signs.long2LongEntrySet()) {
            Vec3 roost = BlockPos.of(e.getLongValue()).getCenter();
            if (roost.distanceToSqr(point) <= limit && BlockPos.of(e.getLongKey()).getCenter().distanceToSqr(roost) <= limit) return true;
        }
        return false;
    }
}
