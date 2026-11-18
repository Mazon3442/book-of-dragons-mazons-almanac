package local.bodaddon.eel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import local.bodaddon.BodAddon;
import local.bodaddon.ModItems;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Turns a small share of fish catches into an eel. The conditions in the JSON say where (rivers and swamps) and for which
 * loot table; this only swaps a fish for an eel, so treasure and junk are never touched.
 */
public class EelFishingModifier extends LootModifier {
    public static final MapCodec<EelFishingModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst)
        .and(Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(m -> m.chance)).apply(inst, EelFishingModifier::new));

    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, BodAddon.MOD_ID);
    static {
        SERIALIZERS.register("eel_fishing", () -> CODEC);
    }

    public static void register(net.neoforged.bus.api.IEventBus modBus) { SERIALIZERS.register(modBus); }

    private final float chance;

    public EelFishingModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (loot.stream().anyMatch(stack -> stack.is(ItemTags.FISHES)) && context.getRandom().nextFloat() < chance) {
            loot.clear();
            loot.add(new ItemStack(ModItems.EEL.get()));
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
