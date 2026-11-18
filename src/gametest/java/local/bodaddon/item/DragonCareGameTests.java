package local.bodaddon.item;

import java.util.UUID;
import java.util.List;
import local.bodaddon.ModItems;
import local.bodaddon.editor.DragonEdits;
import net.magister.bookofdragons.entity.ModEntities;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.chassis.component.IncapacitationComponent;
import net.magister.liberanimalium.body.data.CrippledFeature;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bodaddon")
@PrefixGameTestTemplate(false)
public final class DragonCareGameTests {
    @GameTest(template = "empty")
    public static void survivalRecipesCraftAndReturnBottles(GameTestHelper helper) {
        for (var fish : List.of(Items.COOKED_COD, Items.COOKED_SALMON)) {
            CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(fish), new ItemStack(Items.WHEAT),
                new ItemStack(Items.HONEY_BOTTLE), ItemStack.EMPTY));
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
            ItemStack output = recipe.value().assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(output.is(ModItems.DRAGON_TREAT.get()) && output.getCount() == 2,
                "Either cooked fish must craft two treats");
            helper.assertTrue(recipe.value().getRemainingItems(input).get(2).is(Items.GLASS_BOTTLE),
                "Treat recipe must return the honey bottle");
        }
        CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.MOSS_BLOCK),
            new ItemStack(Items.HONEY_BOTTLE), ItemStack.EMPTY));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        ItemStack output = recipe.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(output.is(ModItems.DRAGON_POULTICE.get()) && output.getCount() == 1,
            "Paper, moss, and honey must craft a poultice");
        helper.assertTrue(recipe.value().getRemainingItems(input).get(2).is(Items.GLASS_BOTTLE),
            "Poultice recipe must return the honey bottle");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void itemsUseNormalInteractionHook(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase dragon = dragon(helper, player);
        dragon.setBond(player.getUUID(), 0);
        ItemStack treat = new ItemStack(ModItems.DRAGON_TREAT.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, treat);
        player.interactOn(dragon, InteractionHand.MAIN_HAND);
        helper.assertTrue(dragon.getBond(player.getUUID()) == 15 && treat.getCount() == 1,
            "Normal interaction must reach the treat instead of mounting or feeding normally");
        dragon.componentRegistry.get(IncapacitationComponent.class).enterIncapacitation();
        ItemStack poultice = new ItemStack(ModItems.DRAGON_POULTICE.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, poultice);
        player.interactOn(dragon, InteractionHand.MAIN_HAND);
        helper.assertTrue(dragon.getPersistentData().getInt(DragonPoulticeItem.TREATMENT_KEY) == 1200
            && poultice.getCount() == 1, "Normal interaction must apply the poultice to an incapacitated dragon");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void treatmentRunsFromServerEvents(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase dragon = dragon(helper, player);
        dragon.setNoGravity(true);
        injure(dragon);
        use(new ItemStack(ModItems.DRAGON_POULTICE.get()), player, dragon);
        dragon.getPersistentData().putInt(DragonPoulticeItem.TREATMENT_KEY, 4);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(!DragonEdits.isInjured(dragon), "Registered server tick events must complete treatment");
            helper.assertTrue(!dragon.getPersistentData().contains(DragonPoulticeItem.TREATMENT_KEY),
                "Server tick completion must clear treatment state");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void treatsConsumeOneAndClamp(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase dragon = dragon(helper, player);
        dragon.setBond(player.getUUID(), 0);
        ItemStack treats = new ItemStack(ModItems.DRAGON_TREAT.get(), 64);
        for (int i = 0; i < 64; i++) {
            helper.assertTrue(use(treats, player, dragon).consumesAction(), "Each treat must work");
        }
        helper.assertTrue(dragon.getBond(player.getUUID()) == 960, "64 treats must add 96 loyalty");
        helper.assertTrue(treats.isEmpty(), "One item must be consumed per use");
        treats = new ItemStack(ModItems.DRAGON_TREAT.get(), 4);
        for (int i = 0; i < 3; i++) use(treats, player, dragon);
        helper.assertTrue(dragon.getBond(player.getUUID()) == 1000, "Bond must clamp at 1000");
        helper.assertTrue(use(treats, player, dragon) == InteractionResult.FAIL && treats.getCount() == 1,
            "At maximum bond, no item may be consumed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ownerAndTamingAreRequired(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase dragon = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 2, 2, 2);
        ItemStack treats = new ItemStack(ModItems.DRAGON_TREAT.get(), 2);
        helper.assertTrue(use(treats, player, dragon) == InteractionResult.FAIL, "Wild dragons must be rejected");
        UUID otherOwner = UUID.randomUUID();
        dragon.adopt(otherOwner);
        dragon.setBond(otherOwner, 200);
        helper.assertTrue(use(treats, player, dragon) == InteractionResult.FAIL, "Other owners' dragons must be rejected");
        ItemStack poultice = new ItemStack(ModItems.DRAGON_POULTICE.get());
        injure(dragon);
        helper.assertTrue(use(poultice, player, dragon) == InteractionResult.FAIL, "Treatment must also require ownership");
        helper.assertTrue(treats.getCount() == 2 && poultice.getCount() == 1, "Rejected uses must cost nothing");
        helper.assertTrue(dragon.getBond(otherOwner) == 200, "Other owner's bond must not change");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeAndNonDragonInteractions(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        DragonBase dragon = dragon(helper, player);
        dragon.setBond(player.getUUID(), 0);
        ItemStack treats = new ItemStack(ModItems.DRAGON_TREAT.get());
        use(treats, player, dragon);
        helper.assertTrue(treats.getCount() == 1 && dragon.getBond(player.getUUID()) == 15,
            "Creative use must increase bond without consuming the item");
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, 5, 2, 2);
        helper.assertTrue(treats.getItem().interactLivingEntity(treats, player, cow, InteractionHand.MAIN_HAND)
            == InteractionResult.PASS, "Unrelated entities must pass through");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poulticeWaitsSixtySecondsAndDoesNotHealHealth(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase dragon = dragon(helper, player);
        ItemStack poultice = new ItemStack(ModItems.DRAGON_POULTICE.get(), 2);
        helper.assertTrue(use(poultice, player, dragon) == InteractionResult.FAIL, "Healthy dragons need no treatment");
        injure(dragon, 4, 10000);
        IncapacitationComponent injury = dragon.componentRegistry.get(IncapacitationComponent.class);
        int before = injury.ticksToMend();
        float health = dragon.getHealth();
        helper.assertTrue(use(poultice, player, dragon).consumesAction(), "Injury treatment must start");
        helper.assertTrue(DragonEdits.isInjured(dragon), "Treatment must not cure immediately");
        helper.assertTrue(use(poultice, player, dragon) == InteractionResult.FAIL && poultice.getCount() == 1,
            "Reapplying must not consume another item or restart the timer");
        for (int i = 0; i < 1199; i++) DragonPoulticeItem.onTick(new EntityTickEvent.Post(dragon));
        helper.assertTrue(injury.ticksToMend() == before, "Recovery bonus must wait until tick 1200");
        DragonPoulticeItem.onTick(new EntityTickEvent.Post(dragon));
        helper.assertTrue(injury.ticksToMend() == before - 72000, "Poultice must remove exactly one hour");
        helper.assertTrue(DragonEdits.isInjured(dragon) && injury.crippledFeatures().size() == 2,
            "Longer injuries must retain their remaining stages and limb restrictions");
        helper.assertTrue(dragon.getHealth() == Math.min(health, dragon.getMaxHealth()),
            "Poultice must preserve health, clamped to the recalculated maximum: before=" + health
                + ", after=" + dragon.getHealth() + ", max=" + dragon.getMaxHealth());
        helper.assertTrue(!dragon.getPersistentData().contains(DragonPoulticeItem.TREATMENT_KEY), "Completed treatment must clear");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneHourReductionClampsAtRecoveryAndKeepsHistory(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase dragon = dragon(helper, player);
        IncapacitationComponent injury = dragon.componentRegistry.get(IncapacitationComponent.class);
        injure(dragon, 3, 35999);
        DragonEdits.reduceInjuryTime(dragon, 72000);
        helper.assertTrue(injury.ticksToMend() == 1 && injury.crippledFeatures().size() == 1,
            "An hour and one tick of injury must leave one tick");
        DragonEdits.reduceInjuryTime(dragon, 72000);
        helper.assertTrue(injury.ticksToMend() == 0 && !DragonEdits.isInjured(dragon),
            "Short injuries must finish without negative recovery time or lingering debuffs");
        CompoundTag state = new CompoundTag();
        injury.save(state);
        helper.assertTrue(state.getInt("Incap_Count") == 1, "Poultices must preserve prior injury history");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void woundGroundingAlsoLosesOneHour(GameTestHelper helper) throws ReflectiveOperationException {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase dragon = dragon(helper, player);
        var timer = DragonBase.class.getDeclaredField("woundGroundedTicks");
        var grounded = DragonBase.class.getDeclaredField("DATA_WOUND_GROUNDED");
        timer.setAccessible(true);
        grounded.setAccessible(true);
        timer.setInt(dragon, 90000);
        setGrounded(dragon, grounded.get(null));
        DragonEdits.reduceInjuryTime(dragon, 72000);
        helper.assertTrue(timer.getInt(dragon) == 18000 && dragon.isGroundedByWound(),
            "Wounds longer than an hour must stay grounded with the correct remaining time");
        DragonEdits.reduceInjuryTime(dragon, 72000);
        helper.assertTrue(timer.getInt(dragon) == 0 && !dragon.isGroundedByWound(),
            "A wound with less than an hour remaining must finish");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void treatmentSurvivesSaveAndLoad(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DragonBase dragon = dragon(helper, player);
        injure(dragon);
        use(new ItemStack(ModItems.DRAGON_POULTICE.get()), player, dragon);
        for (int i = 0; i < 100; i++) DragonPoulticeItem.onTick(new EntityTickEvent.Post(dragon));
        CompoundTag saved = dragon.saveWithoutId(new CompoundTag());
        DragonBase restored = ModEntities.TERRIBLE_TERROR.get().create(helper.getLevel());
        restored.load(saved);
        helper.assertTrue(restored.getPersistentData().getInt(DragonPoulticeItem.TREATMENT_KEY) == 1100,
            "Remaining treatment time must survive saving and loading");
        DragonEdits.clearInjury(dragon);
        DragonPoulticeItem.onTick(new EntityTickEvent.Post(dragon));
        helper.assertTrue(!dragon.getPersistentData().contains(DragonPoulticeItem.TREATMENT_KEY),
            "Natural or editor recovery must cancel pending treatment");
        helper.succeed();
    }

    private static DragonBase dragon(GameTestHelper helper, Player player) {
        DragonBase dragon = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 2, 2, 2);
        dragon.adopt(player.getUUID());
        return dragon;
    }

    private static void injure(DragonBase dragon) {
        injure(dragon, 1, 0);
    }

    private static void injure(DragonBase dragon, int stages, int elapsed) {
        CompoundTag state = new CompoundTag();
        state.putInt("Incap_Count", 1);
        state.putInt("Incap_CurrentStage", stages);
        state.putInt("Incap_OriginalStageCount", stages);
        state.putInt("Incap_StageTimerTicks", elapsed);
        ListTag features = new ListTag();
        for (int i = 0; i < stages; i++) features.add(StringTag.valueOf(CrippledFeature.values()[i].name()));
        state.put("Incap_CrippledFeatures", features);
        dragon.componentRegistry.get(IncapacitationComponent.class).load(state);
    }

    @SuppressWarnings("unchecked")
    private static void setGrounded(DragonBase dragon, Object accessor) {
        dragon.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<Boolean>) accessor, true);
    }

    private static InteractionResult use(ItemStack stack, Player player, DragonBase dragon) {
        return stack.getItem().interactLivingEntity(stack, player, dragon, InteractionHand.MAIN_HAND);
    }
}
