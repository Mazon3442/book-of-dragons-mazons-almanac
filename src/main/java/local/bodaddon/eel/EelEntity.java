package local.bodaddon.eel;

import local.bodaddon.ModItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A shy, slippery fish of rivers and swamps. Swims, flops and goes in a bucket like the vanilla fish; also bites on a line. */
public class EelEntity extends AbstractFish {
    public EelEntity(EntityType<? extends EelEntity> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, 6.0);
    }

    @Override public ItemStack getBucketItemStack() { return new ItemStack(ModItems.EEL_BUCKET.get()); }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SALMON_AMBIENT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SALMON_DEATH; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SALMON_HURT; }
    @Override protected SoundEvent getFlopSound() { return SoundEvents.SALMON_FLOP; }
}
