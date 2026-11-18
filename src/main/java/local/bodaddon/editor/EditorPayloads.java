package local.bodaddon.editor;

import local.bodaddon.BodAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class EditorPayloads {
    private static final String VERSION = "1";

    /** Server to client: open or refresh the editor menu. */
    public record Open(EditorState state) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "editor_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = EditorState.CODEC.map(Open::new, Open::state);
        @Override public Type<Open> type() { return TYPE; }
    }

    /** Client to server: one edit. The server re-checks creative mode and distance. */
    public record Act(int entityId, EditAction action, int index, float value) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "editor_act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Act::entityId,
            ByteBufCodecs.idMapper(i -> EditAction.values()[i], EditAction::ordinal), Act::action,
            ByteBufCodecs.VAR_INT, Act::index,
            ByteBufCodecs.FLOAT, Act::value,
            Act::new);
        @Override public Type<Act> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(VERSION);
        registrar.playToClient(Open.TYPE, Open.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> openOnClient(payload)));
        registrar.playToServer(Act.TYPE, Act.CODEC, EditorPayloads::onAct);
        local.bodaddon.stable.StablePayloads.register(registrar);
    }

    private static void openOnClient(Open payload) {
        // Kept in its own class so a dedicated server never loads client-only screen code.
        local.bodaddon.client.EditorClient.show(payload.state());
    }

    private static void onAct(Act act, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) DragonEdits.handle(player, act);
        });
    }

    public static void sendOpen(ServerPlayer player, EditorState state) {
        PacketDistributor.sendToPlayer(player, new Open(state));
    }

    private EditorPayloads() {}
}
