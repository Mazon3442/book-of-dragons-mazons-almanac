package local.bodaddon.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

/** A page torn from the Sketchbook with one dragon's colors and markings on it, kept in the stack's custom data. */
public final class SketchbookPageItem extends Item {
    private static final String SPECIES = "Species";
    private static final String NAME = "Name";
    private static final String LOOK = "Look";

    public SketchbookPageItem(Properties properties) {
        super(properties);
    }

    static ItemStack create(Item item, String species, String dragonName, List<VariantEdits.Look> looks) {
        ListTag list = new ListTag();
        for (VariantEdits.Look look : looks) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Locus", look.locus());
            entry.putInt("Slot", look.slot());
            entry.putString("Palette", look.palette().toString());
            list.add(entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(SPECIES, species);
        tag.putString(NAME, dragonName);
        tag.put(LOOK, list);
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** The species the page was drawn from, or empty for a blank page. */
    public static String species(ItemStack stack) {
        return data(stack).getString(SPECIES);
    }

    /** The name of the dragon the page was drawn from. */
    public static String dragonName(ItemStack stack) {
        return data(stack).getString(NAME);
    }

    static List<VariantEdits.Look> looks(ItemStack stack) {
        List<VariantEdits.Look> looks = new ArrayList<>();
        for (Tag raw : data(stack).getList(LOOK, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            ResourceLocation palette = ResourceLocation.tryParse(entry.getString("Palette"));
            if (palette != null) looks.add(new VariantEdits.Look(entry.getString("Locus"), entry.getInt("Slot"), palette));
        }
        return looks;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = data(stack);
        if (tag.getString(SPECIES).isEmpty()) {
            tooltip.add(Component.translatable("item.bodaddon.sketchbook_page.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.bodaddon.sketchbook_page.of", tag.getString(NAME), tag.getString(SPECIES))
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bodaddon.sketchbook_page.use").withStyle(ChatFormatting.GRAY));
    }
}
