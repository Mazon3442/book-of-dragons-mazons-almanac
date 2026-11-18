package local.bodaddon.stable;

import java.util.List;

import local.bodaddon.BodAddonConfig;
import net.magister.liberanimalium.roost.RoostBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The Stable Plate. Right-click a Dragon Roost with it to link it to that roost, then place it like a sign anywhere within the
 * configured radius (24 blocks by default) of that roost. A placed sign switches on the stay hold for the roost; see
 * {@link StableEvents}.
 */
public final class StablePlateItem extends BlockItem {
    private static final String ROOST = "Roost";
    private static final String DIM = "Dim";

    public StablePlateItem(Block block, Properties properties) {
        super(block, properties);
    }

    private static CompoundTag link(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof RoostBlock)) return super.useOn(context);
        ItemStack stack = context.getItemInHand();
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
            tag.putLong(ROOST, pos.asLong());
            tag.putString(DIM, level.dimension().location().toString());
        }));
        Player player = context.getPlayer();
        if (player != null) {
            player.displayClientMessage(Component.translatable("bodaddon.stable.linked", pos.getX(), pos.getY(), pos.getZ(),
                Math.round(BodAddonConfig.STABLE_RADIUS.get())), true);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4F, 1.4F);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        CompoundTag tag = link(context.getItemInHand());
        String problem = null;
        double radius = BodAddonConfig.STABLE_RADIUS.get();
        BlockPos at = context.getClickedPos();
        if (!tag.contains(ROOST)) {
            problem = "bodaddon.stable.unlinked";
        } else if (!level.dimension().location().toString().equals(tag.getString(DIM))) {
            problem = "bodaddon.stable.wrong_dimension";
        } else if (at.getCenter().distanceToSqr(BlockPos.of(tag.getLong(ROOST)).getCenter()) > radius * radius) {
            problem = "bodaddon.stable.too_far";
        }
        if (problem != null) {
            if (player != null && level.isClientSide) player.displayClientMessage(Component.translatable(problem, Math.round(radius)), true);
            return InteractionResult.FAIL;
        }
        InteractionResult result = super.place(context);
        if (result.consumesAction() && level instanceof ServerLevel server) {
            PlateData.get(server).put(at, BlockPos.of(tag.getLong(ROOST)));
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bodaddon.stable_plate.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bodaddon.stable_plate.desc2", Math.round(BodAddonConfig.STABLE_RADIUS.get()))
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bodaddon.stable_plate.desc3").withStyle(ChatFormatting.GRAY));
        CompoundTag tag = link(stack);
        if (tag.contains(ROOST)) {
            BlockPos pos = BlockPos.of(tag.getLong(ROOST));
            tooltip.add(Component.translatable("item.bodaddon.stable_plate.linked", pos.getX(), pos.getY(), pos.getZ())
                .withStyle(ChatFormatting.GOLD));
        }
    }
}
