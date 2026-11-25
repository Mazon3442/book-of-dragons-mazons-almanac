package local.bodaddon.trough;

import local.bodaddon.BodAddonConfig;
import local.bodaddon.ModBlocks;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.fauna.api.Order;
import net.magister.liberanimalium.chassis.component.simulation.FaunaNeedsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A 27-slot food store. When a dragon within the configured radius (default 16 blocks) is hungry it sets one food from the dragon's diet
 * (favourites first) on the trough as a lure the dragons walk to and eat with their own animation; a dragon that ignores it
 * is fed directly after a while. Being a vanilla container it also works with hoppers and shows the chest screen.
 */
public class FeedingTroughBlockEntity extends BaseContainerBlockEntity {
    static final int SIZE = 27;
    private static final int CHECK_INTERVAL = 40;
    /** Fed until full: a dragon below this food level (of 100) keeps getting meals. */
    private static final int HUNGRY_BELOW = 100;

    private static final int MAX_STATUS_DRAGONS = 8;

    private static final int LURE_STEP = 10;
    /** Dragons that have not eaten the lure by now are fed directly: 20 seconds. */
    private static final int LURE_TICKS = 400;

    private UUID lure;
    private int lureAge;
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public FeedingTroughBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.TROUGH_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FeedingTroughBlockEntity trough) {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        if (trough.lure != null) {
            if (now % LURE_STEP == 0) trough.tickLure(server, state);
        } else if (now % CHECK_INTERVAL == 0 && !trough.isEmpty()) {
            trough.startLure(server, state);
        }
    }

    /** A hungry dragon in reach makes the trough set one food item from its diet on top of itself. */
    private void startLure(ServerLevel server, BlockState state) {
        for (DragonBase dragon : server.getEntitiesOfClass(DragonBase.class, area(state))) {
            if (!hungry(dragon)) continue;
            ItemStack stack = bestFoodFor(dragon);
            if (stack.isEmpty()) continue;
            BlockPos head = worldPosition.relative(state.getValue(HorizontalDirectionalBlock.FACING).getClockWise());
            Vec3 at = worldPosition.getCenter().add(head.getCenter()).scale(0.5);
            ItemEntity lureItem = new ItemEntity(server, at.x, worldPosition.getY() + 1.0, at.z, stack.split(1));
            lureItem.setNeverPickUp();
            lureItem.setDeltaMovement(Vec3.ZERO);
            server.addFreshEntity(lureItem);
            lure = lureItem.getUUID();
            lureAge = 0;
            setChanged();
            return;
        }
    }

    /**
     * Walks the nearest hungry dragon to the lure and, once it is close, has it eat it with its own eating animation (which
     * consumes the item and applies the meal). Gone means eaten. If that has not happened in time, or nobody is hungry any
     * more, the food goes back in the trough and any hungry dragon is fed directly.
     */
    private void tickLure(ServerLevel server, BlockState state) {
        lureAge += LURE_STEP;
        if (!(server.getEntity(lure) instanceof ItemEntity item) || !item.isAlive()) {
            lure = null;
            setChanged();
            return;
        }
        DragonBase eater = null;
        for (DragonBase dragon : server.getEntitiesOfClass(DragonBase.class, area(state))) {
            if (dragon.isEating()) return;   // somebody is mid-meal: leave it be
            if (hungry(dragon) && mayWalk(dragon) && (eater == null || dragon.distanceToSqr(item) < eater.distanceToSqr(item))) eater = dragon;
        }
        if (eater != null) {
            double dx = eater.getX() - item.getX(), dz = eater.getZ() - item.getZ();
            double reach = eater.getBbWidth() * 0.5 + 2.0;
            if (dx * dx + dz * dz <= reach * reach) {
                eater.startEating(item);
                return;
            }
            if (lureAge < LURE_TICKS) {
                eater.getNavigation().moveTo(item.getX(), item.getY(), item.getZ(), 1.0);
                return;
            }
        }
        ItemStack rest = HopperBlockEntity.addItem(null, this, item.getItem(), null);
        if (!rest.isEmpty()) Containers.dropItemStack(server, item.getX(), item.getY(), item.getZ(), rest);
        item.discard();
        lure = null;
        feedDirect(server, state);
        setChanged();
    }

    /** Fallback for dragons that never came: apply a meal straight away, with a quiet eating sound. */
    private void feedDirect(ServerLevel server, BlockState state) {
        for (DragonBase dragon : server.getEntitiesOfClass(DragonBase.class, area(state))) {
            if (!hungry(dragon)) continue;
            ItemStack food = bestFoodFor(dragon);
            if (food.isEmpty() || !dragon.getNeedsSystem().eat(food)) continue;
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food), dragon.getX(), dragon.getEyeY(), dragon.getZ(), 8, 0.3, 0.2, 0.3, 0.05);
            server.playSound(null, dragon.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.35F, 1.0F);
            food.shrink(1);   // eat() applies the meal but leaves the stack to the caller, as the dragon's own feeding does
        }
    }

    /** What sneak-right-click shows: how full the trough is and how each dragon in reach is doing. */
    public List<Component> status(ServerLevel server) {
        List<Component> lines = new ArrayList<>();
        int used = 0;
        int count = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                used++;
                count += stack.getCount();
            }
        }
        lines.add(Component.translatable("bodaddon.trough.status.store", used, SIZE, count));
        List<DragonBase> dragons = server.getEntitiesOfClass(DragonBase.class, area(getBlockState()));
        if (dragons.isEmpty()) {
            lines.add(Component.translatable("bodaddon.trough.status.none", Math.round(BodAddonConfig.TROUGH_RADIUS.get())));
            return lines;
        }
        lines.add(Component.translatable("bodaddon.trough.status.dragons", dragons.size()));
        for (int i = 0; i < dragons.size() && i < MAX_STATUS_DRAGONS; i++) {
            DragonBase dragon = dragons.get(i);
            FaunaNeedsSystem needs = dragon.getNeedsSystem();
            if (needs == null) continue;
            long food = Math.round(needs.getFoodLevel());
            String key = needs.getFoodLevel() >= HUNGRY_BELOW ? "full"
                : bestFoodFor(dragon).isEmpty() ? "hungry_nofood" : "hungry_food";
            lines.add(Component.translatable("bodaddon.trough.status.dragon." + key, dragon.getDisplayName(), food));
        }
        if (dragons.size() > MAX_STATUS_DRAGONS) {
            lines.add(Component.translatable("bodaddon.trough.status.more", dragons.size() - MAX_STATUS_DRAGONS));
        }
        return lines;
    }

    /**
     * Whether the trough may walk this dragon over to the lure. Not one that is ridden or under a stay order: the trough must
     * never pull a dragon away from its rider or its post. Those are fed in place once the lure runs out (see feedDirect).
     */
    private static boolean mayWalk(DragonBase dragon) {
        return !dragon.isVehicle() && dragon.order() != Order.STAY;
    }

    private static boolean hungry(DragonBase dragon) {
        FaunaNeedsSystem needs = dragon.getNeedsSystem();
        return needs != null && !dragon.isEating() && needs.getFoodLevel() < HUNGRY_BELOW;
    }

    /** Both halves of the trough, plus the reach around them. */
    private AABB area(BlockState state) {
        BlockPos head = worldPosition.relative(state.getValue(HorizontalDirectionalBlock.FACING).getClockWise());
        return new AABB(worldPosition).minmax(new AABB(head)).inflate(BodAddonConfig.TROUGH_RADIUS.get());
    }

    /** The stack in the trough this dragon likes best among foods it will eat, or empty. */
    private ItemStack bestFoodFor(DragonBase dragon) {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = 0;
        for (ItemStack stack : items) {
            if (stack.isEmpty() || !dragon.isFood(stack) || dragon.isDislikedFood(stack)) continue;
            int score = dragon.isFavoriteFood(stack) ? 3 : dragon.isSpecialFood(stack) ? 2 : 1;
            if (score > bestScore) {
                best = stack;
                bestScore = score;
            }
        }
        return best;
    }

    /** The renderer draws the contents, so every change goes to nearby clients. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override protected Component getDefaultName() { return Component.translatable("block.bodaddon.feeding_trough"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SIZE; }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (lure != null) {
            tag.putUUID("Lure", lure);
            tag.putInt("LureAge", lureAge);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        lure = tag.hasUUID("Lure") ? tag.getUUID("Lure") : null;
        lureAge = tag.getInt("LureAge");
    }
}
