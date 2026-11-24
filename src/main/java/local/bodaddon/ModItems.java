package local.bodaddon;

import local.bodaddon.editor.DragonEditorItem;
import local.bodaddon.editor.SketchbookItem;
import local.bodaddon.editor.SketchbookPageItem;
import local.bodaddon.item.DragonPoulticeItem;
import local.bodaddon.item.DragonTreatItem;
import local.bodaddon.item.DragonNipItem;
import local.bodaddon.item.SagefruitItem;
import local.bodaddon.repel.DragonRepellentItem;
import local.bodaddon.stable.StablePlateItem;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, BodAddon.MOD_ID);

    /** Gray, non-italic tooltip lines that also show in JEI, EMI and REI, where nothing else says where an item comes from. */
    static Item.Properties hint(Item.Properties properties, String... translationKeys) {
        Style style = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false);
        return properties.component(DataComponents.LORE, new ItemLore(Arrays.stream(translationKeys)
            .<Component>map(key -> Component.translatable(key).withStyle(style)).toList()));
    }

    public static final DeferredHolder<Item, DragonEditorItem> DRAGON_EDITOR = ITEMS.register("dragon_editor",
        () -> new DragonEditorItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredHolder<Item, SketchbookItem> SKETCHBOOK = ITEMS.register("sketchbook",
        () -> new SketchbookItem(hint(new Item.Properties().stacksTo(1), "item.bodaddon.sketchbook.hint1", "item.bodaddon.sketchbook.hint2")));

    public static final DeferredHolder<Item, SketchbookPageItem> SKETCHBOOK_PAGE = ITEMS.register("sketchbook_page",
        () -> new SketchbookPageItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, DragonTreatItem> DRAGON_TREAT = ITEMS.register("dragon_treat",
        () -> new DragonTreatItem(new Item.Properties()));

    public static final DeferredHolder<Item, DragonNipItem> DRAGON_NIP = ITEMS.register("dragon_nip",
        () -> new DragonNipItem(new Item.Properties()));

    public static final DeferredHolder<Item, SagefruitItem> SAGEFRUIT = ITEMS.register("sagefruit",
        () -> new SagefruitItem(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredHolder<Item, DragonPoulticeItem> DRAGON_POULTICE = ITEMS.register("dragon_poultice",
        () -> new DragonPoulticeItem(new Item.Properties()));

    public static final DeferredHolder<Item, StablePlateItem> STABLE_PLATE = ITEMS.register("stable_plate",
        () -> new StablePlateItem(ModBlocks.STABLE_SIGN.get(), new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, Item> EEL = ITEMS.register("eel",
        () -> new Item(hint(new Item.Properties(), "item.bodaddon.eel.hint").food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).build())));

    // Same food values as cooked cod.
    public static final DeferredHolder<Item, Item> COOKED_EEL = ITEMS.register("cooked_eel",
        () -> new Item(hint(new Item.Properties(), "item.bodaddon.cooked_eel.hint").food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F).build())));

    public static final DeferredHolder<Item, MobBucketItem> EEL_BUCKET = ITEMS.register("eel_bucket",
        () -> new MobBucketItem(ModEntities.EEL.get(), Fluids.WATER, SoundEvents.BUCKET_EMPTY_FISH, new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, DeferredSpawnEggItem> EEL_SPAWN_EGG = ITEMS.register("eel_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.EEL, 0x35503F, 0xC2BF7A, new Item.Properties()));

    public static final DeferredHolder<Item, DragonRepellentItem> DRAGON_REPELLENT = ITEMS.register("dragon_repellent",
        () -> new DragonRepellentItem(new Item.Properties().stacksTo(16)));

    public static void register(IEventBus modBus) { ITEMS.register(modBus); }

    private ModItems() {}
}
