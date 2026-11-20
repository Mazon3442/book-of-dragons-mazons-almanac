package local.bodaddon.editor;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * One thing about a dragon's looks the editor can change: a label, the locus and slot it edits, whether it is a colour or a
 * pattern, the options it offers (display names and resource ids, same order), the chosen one, and for each option the
 * bond (0 to 100) the dragon still needs before the sketchbook may offer it; 0 means it is free to pick.
 */
public record VariantRow(String label, String locus, int slot, boolean color, String[] options, String[] ids, int current, int[] bondNeeded) {

    static void write(RegistryFriendlyByteBuf buf, VariantRow row) {
        buf.writeUtf(row.label);
        buf.writeUtf(row.locus);
        buf.writeVarInt(row.slot);
        buf.writeBoolean(row.color);
        buf.writeVarInt(row.options.length);
        for (String o : row.options) buf.writeUtf(o);
        for (String id : row.ids) buf.writeUtf(id);
        buf.writeVarInt(row.current);
        for (int need : row.bondNeeded) buf.writeVarInt(need);
    }

    static VariantRow read(RegistryFriendlyByteBuf buf) {
        String label = buf.readUtf();
        String locus = buf.readUtf();
        int slot = buf.readVarInt();
        boolean color = buf.readBoolean();
        String[] options = new String[buf.readVarInt()];
        String[] ids = new String[options.length];
        for (int i = 0; i < options.length; i++) options[i] = buf.readUtf();
        for (int i = 0; i < ids.length; i++) ids[i] = buf.readUtf();
        int current = buf.readVarInt();
        int[] needed = new int[options.length];
        for (int i = 0; i < needed.length; i++) needed[i] = buf.readVarInt();
        return new VariantRow(label, locus, slot, color, options, ids, current, needed);
    }
}
