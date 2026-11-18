package local.bodaddon.stable;

import local.bodaddon.BodAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Opening the name box on a Stable Sign, and saving what the player typed. */
public final class StablePayloads {
    private static final double REACH_SQ = 8.0 * 8.0;

    /** Server to client: open the name box. */
    public record Open(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "stable_name_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Open::pos, ByteBufCodecs.stringUtf8(64), Open::name, Open::new);
        @Override public Type<Open> type() { return TYPE; }
    }

    /** Client to server: the new name. The server checks reach and the block, and cleans the text. */
    public record Set(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "stable_name_set"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Set> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Set::pos, ByteBufCodecs.stringUtf8(64), Set::name, Set::new);
        @Override public Type<Set> type() { return TYPE; }
    }

    /** Called from the one registrar the addon creates (see EditorPayloads.register). */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Open.TYPE, Open.CODEC, (payload, ctx) -> ctx.enqueueWork(() ->
            // its own class, so a dedicated server never loads the screen
            local.bodaddon.client.StableClient.show(payload.pos(), payload.name())));
        registrar.playToServer(Set.TYPE, Set.CODEC, StablePayloads::onSet);
    }

    private static void onSet(Set payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.distanceToSqr(payload.pos().getCenter()) > REACH_SQ) return;
            if (player.level().getBlockEntity(payload.pos()) instanceof StableSignBlockEntity sign) sign.setName(payload.name());
        });
    }

    public static void sendOpen(ServerPlayer player, BlockPos pos, String name) {
        PacketDistributor.sendToPlayer(player, new Open(pos, name));
    }

    private StablePayloads() {}
}
