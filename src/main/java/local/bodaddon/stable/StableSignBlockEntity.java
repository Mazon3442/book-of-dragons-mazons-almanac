package local.bodaddon.stable;

import local.bodaddon.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds the text on a Stable Sign, the stable's name and the name of the player who placed it, and sends both to nearby clients for the renderer. */
public final class StableSignBlockEntity extends BlockEntity {
    public static final int MAX_NAME = 16;
    private String name = "";
    private String owner = "";

    public StableSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.STABLE_SIGN_ENTITY.get(), pos, state);
    }

    public String name() { return name; }

    public String owner() { return owner; }

    public void setOwner(String value) {
        String cleaned = clean(value);
        if (cleaned.equals(owner)) return;
        owner = cleaned;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Strips formatting codes and control characters, trims, and limits the length. */
    public static String clean(String raw) {
        StringBuilder out = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c >= ' ' && c != 127 && c != '§') out.append(c);
        }
        String s = out.toString().strip();
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
    }

    public void setName(String value) {
        String cleaned = clean(value);
        if (cleaned.equals(name)) return;
        name = cleaned;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Name", name);
        tag.putString("Owner", owner);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = clean(tag.getString("Name"));
        owner = clean(tag.getString("Owner"));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
