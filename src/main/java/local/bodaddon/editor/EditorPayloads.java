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
    private static final String VERSION = "3";

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

    /** {@code bond} is 0 to 100; {@code nextNeed} the bond that unlocks more (0 when nothing is locked) and {@code nextTier} its rarity (1 to 3, 0 unknown). */
    public record SketchbookPage(UUID token, int entityId, String name, VariantRow[] rows, boolean opening, int bond, int nextNeed, int nextTier)
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
                buf.writeVarInt(page.bond());
                buf.writeVarInt(page.nextNeed());
                buf.writeVarInt(page.nextTier());
            },
            buf -> {
                UUID token = buf.readUUID();
                int id = buf.readVarInt();
                String name = buf.readUtf();
                VariantRow[] rows = new VariantRow[buf.readVarInt()];
                for (int i = 0; i < rows.length; i++) rows[i] = VariantRow.read(buf);
                boolean opening = buf.readBoolean();
                return new SketchbookPage(token, id, name, rows, opening, buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
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

    /** Client to server: put the dragon back to how it looked when the book opened. */
    public record SketchbookRevert(UUID token) implements CustomPacketPayload {
        public static final Type<SketchbookRevert> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "sketchbook_revert"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SketchbookRevert> CODEC =
            UUIDUtil.STREAM_CODEC.map(SketchbookRevert::new, SketchbookRevert::token).cast();
        @Override public Type<SketchbookRevert> type() { return TYPE; }
    }

    /** Client to server: give me a Sketchbook Page of the dragon's current look. */
    public record SketchbookSaveLook(UUID token) implements CustomPacketPayload {
        public static final Type<SketchbookSaveLook> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "sketchbook_save_look"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SketchbookSaveLook> CODEC =
            UUIDUtil.STREAM_CODEC.map(SketchbookSaveLook::new, SketchbookSaveLook::token).cast();
        @Override public Type<SketchbookSaveLook> type() { return TYPE; }
    }

    /** Client to server: apply the Sketchbook Page in this inventory slot to the dragon. */
    public record SketchbookPaste(UUID token, int slot) implements CustomPacketPayload {
        public static final Type<SketchbookPaste> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "sketchbook_paste"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SketchbookPaste> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SketchbookPaste::token,
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, SketchbookPaste::slot,
            SketchbookPaste::new);
        @Override public Type<SketchbookPaste> type() { return TYPE; }
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
        registrar.playToServer(SketchbookRevert.TYPE, SketchbookRevert.CODEC, (revert, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                SketchbookPage page = SketchbookSessions.revert(player, revert.token());
                if (page != null) sendSketchbook(player, page);
            }
        }));
        registrar.playToServer(SketchbookSaveLook.TYPE, SketchbookSaveLook.CODEC, (save, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) SketchbookSessions.saveLook(player, save.token());
        }));
        registrar.playToServer(SketchbookPaste.TYPE, SketchbookPaste.CODEC, (paste, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                SketchbookPage page = SketchbookSessions.paste(player, paste.token(), paste.slot());
                if (page != null) sendSketchbook(player, page);
            }
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
