package local.bodaddon.editor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Snapshot of a dragon the editor menu shows. Traits are in {@link Traits} order, each 0..1.
 * {@code healthSteps} lists the max health of every gene combination the species allows, lowest first;
 * {@code healthStep} is the one the dragon has now.
 */
public record EditorState(int entityId, String name, boolean tame, int bond, float health, float maxHealth,
                          float[] traits, boolean injured, int breedingTicks, float[] healthSteps, int healthStep, VariantRow[] variants) {

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorState> CODEC = StreamCodec.of(
        (buf, s) -> {
            buf.writeVarInt(s.entityId);
            buf.writeUtf(s.name);
            buf.writeBoolean(s.tame);
            buf.writeVarInt(s.bond);
            buf.writeFloat(s.health);
            buf.writeFloat(s.maxHealth);
            for (float t : s.traits) buf.writeFloat(t);
            buf.writeBoolean(s.injured);
            buf.writeVarInt(s.breedingTicks);
            buf.writeVarInt(s.healthSteps.length);
            for (float h : s.healthSteps) buf.writeFloat(h);
            buf.writeVarInt(s.healthStep);
            buf.writeVarInt(s.variants.length);
            for (VariantRow v : s.variants) VariantRow.write(buf, v);
        },
        buf -> {
            int id = buf.readVarInt();
            String name = buf.readUtf();
            boolean tame = buf.readBoolean();
            int bond = buf.readVarInt();
            float health = buf.readFloat();
            float max = buf.readFloat();
            float[] traits = new float[Traits.COUNT];
            for (int i = 0; i < traits.length; i++) traits[i] = buf.readFloat();
            boolean injured = buf.readBoolean();
            int breeding = buf.readVarInt();
            float[] steps = new float[buf.readVarInt()];
            for (int i = 0; i < steps.length; i++) steps[i] = buf.readFloat();
            int step = buf.readVarInt();
            VariantRow[] variants = new VariantRow[buf.readVarInt()];
            for (int i = 0; i < variants.length; i++) variants[i] = VariantRow.read(buf);
            return new EditorState(id, name, tame, bond, health, max, traits, injured, breeding, steps, step, variants);
        });
}
