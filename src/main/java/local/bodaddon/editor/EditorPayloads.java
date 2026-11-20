package local.bodaddon.editor;

import java.util.UUID;

import local.bodaddon.BodAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class EditorPayloads {
    private static final String VERSION = "2";

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

    public record SketchbookPage(UUID token, int entityId, String name, VariantRow[] rows, boolean opening)
        implements CustomPacketPayload {
        public static final Type<SketchbookPage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "sketchbook_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SketchbookPage> CODEC = StreamCodec.of(
            (buf, page) -> {
                buf.writeUUID(page.token());
                buf.writeVarInt(page.entityId());
                buf.writeUtf(page.name());
                buf.writeVarInt(page.rows().length);
                for (VariantRow row : page.rows()) VariantRow.write(buf, row);
                buf.writeBoolean(page.opening());
            },
            buf -> {
                UUID token = buf.readUUID();
                int id = buf.readVarInt();
                String name = buf.readUtf();
                VariantRow[] rows = new VariantRow[buf.readVarInt()];
                for (int i = 0; i < rows.length; i++) rows[i] = VariantRow.read(buf);
                return new SketchbookPage(token, id, name, rows, buf.readBoolean());
            });
        @Override public Type<SketchbookPage> type() { return TYPE; }
    }

    public record SketchbookChoice(UUID token, String locus, int slot, ResourceLocation palette) implements CustomPacketPayload {
        public static final Type<SketchbookChoice> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "sketchbook_choice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SketchbookChoice> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SketchbookChoice::token,
            ByteBufCodecs.stringUtf8(32), SketchbookChoice::locus,
            ByteBufCodecs.VAR_INT, SketchbookChoice::slot,
            ResourceLocation.STREAM_CODEC, SketchbookChoice::palette,
            SketchbookChoice::new);
        @Override public Type<SketchbookChoice> type() { return TYPE; }
    }

    public record SketchbookClose(UUID token) implements CustomPacketPayload {
        public static final Type<SketchbookClose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "sketchbook_close"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SketchbookClose> CODEC =
            UUIDUtil.STREAM_CODEC.map(SketchbookClose::new, SketchbookClose::token).cast();
        @Override public Type<SketchbookClose> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(VERSION);
        registrar.playToClient(Open.TYPE, Open.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> openOnClient(payload)));
        registrar.playToServer(Act.TYPE, Act.CODEC, EditorPayloads::onAct);
        registrar.playToClient(SketchbookPage.TYPE, SketchbookPage.CODEC,
            (page, ctx) -> ctx.enqueueWork(() -> local.bodaddon.client.EditorClient.showSketchbook(page)));
        registrar.playToServer(SketchbookChoice.TYPE, SketchbookChoice.CODEC, (choice, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                SketchbookPage page = SketchbookSessions.change(player, choice);
                if (page != null) sendSketchbook(player, page);
            }
        }));
        registrar.playToServer(SketchbookClose.TYPE, SketchbookClose.CODEC, (close, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) SketchbookSessions.close(player, close.token());
        }));
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

    static void sendSketchbook(ServerPlayer player, SketchbookPage page) {
        PacketDistributor.sendToPlayer(player, page);
    }

    private EditorPayloads() {}
}
