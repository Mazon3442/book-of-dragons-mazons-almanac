package local.bodaddon.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import local.bodaddon.BodAddonConfig;
import local.bodaddon.ModItems;
import net.magister.bookofdragons.entity.ModEntities;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bodaddon")
@PrefixGameTestTemplate(false)
public final class SketchbookGameTests {
    @GameTest(template = "empty")
    public static void paidSessionChangesOnlyVariants(GameTestHelper helper) {
        DragonBase dragon = dragon(helper);
        ServerPlayer owner = player(helper, dragon);
        ServerPlayer stranger = player(helper, dragon);
        dragon.adopt(owner.getUUID());
        dragon.setBond(owner.getUUID(), 1000);   // a fully bonded dragon offers every look, so the random next option is never locked
        dragon.setHealth(dragon.getMaxHealth() / 2);
        holdBook(owner);
        List<Packet<?>> sent = new ArrayList<>();
        capturePackets(owner, sent);
        float health = dragon.getHealth();
        helper.assertTrue(owner.interactOn(dragon, InteractionHand.OFF_HAND).consumesAction(),
            "Normal right-click with an off-hand sketchbook should open it");
        var open = sent.stream().filter(ClientboundCustomPayloadPacket.class::isInstance).map(ClientboundCustomPayloadPacket.class::cast)
            .map(ClientboundCustomPayloadPacket::payload).filter(EditorPayloads.SketchbookPage.class::isInstance)
            .map(EditorPayloads.SketchbookPage.class::cast).findFirst().orElseThrow();
        helper.assertTrue(open.opening(), "The server should send an opening packet on right-click");
        helper.assertTrue(owner.getOffhandItem().isEmpty(), "Opening in survival should consume one book");

        VariantRow row = open.rows()[0];
        int option = (row.current() + 1) % row.options().length;
        var choice = new EditorPayloads.SketchbookChoice(open.token(), row.locus(), row.slot(), ResourceLocation.parse(row.ids()[option]));
        var forged = new EditorPayloads.SketchbookChoice(UUID.randomUUID(), row.locus(), row.slot(), choice.palette());
        helper.assertTrue(SketchbookSessions.change(owner, forged) == null, "A forged session token must be rejected");
        helper.assertTrue(SketchbookSessions.change(stranger, choice) == null, "A session token must never work for another player");
        var invalidPalette = new EditorPayloads.SketchbookChoice(open.token(), row.locus(), row.slot(),
            ResourceLocation.fromNamespaceAndPath("bodaddon", "invalid_palette"));
        helper.assertTrue(SketchbookSessions.change(owner, invalidPalette) == null, "A palette must belong to the edited locus");
        var updated = SketchbookSessions.change(owner, choice);
        helper.assertTrue(updated != null && updated.rows()[0].current() == option, "The chosen variant should be applied while the session is open");
        var nextChoice = new EditorPayloads.SketchbookChoice(open.token(), row.locus(), row.slot(),
            ResourceLocation.parse(row.ids()[row.current()]));
        helper.assertTrue(SketchbookSessions.change(owner, nextChoice) != null, "One consumed book should cover the entire session");

        DragonEdits.handle(owner, new EditorPayloads.Act(dragon.getId(), EditAction.SET_HEALTH, 0, 1));
        DragonEdits.handle(owner, new EditorPayloads.Act(dragon.getId(), EditAction.SET_VARIANT, 0, option));
        helper.assertTrue(dragon.getHealth() == health && VariantEdits.rows(dragon)[0].current() == row.current(),
            "The creative packet must stay unavailable to survival players, including players with an open book");
        SketchbookSessions.close(owner, open.token());
        helper.assertTrue(SketchbookSessions.change(owner, choice) == null, "Closing should invalidate the session");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rareLooksAreLockedUntilTheDragonIsBondedEnough(GameTestHelper helper) {
        DragonBase dragon = dragon(helper);
        ServerPlayer owner = player(helper, dragon);
        dragon.adopt(owner.getUUID());
        DragonEdits.setBond(dragon, owner.getUUID(), 0);
        helper.assertTrue(dragon.getBond(owner.getUUID()) == 0, "The test needs an unbonded dragon");
        holdBook(owner);
        List<Packet<?>> sent = new ArrayList<>();
        capturePackets(owner, sent);
        helper.assertTrue(owner.interactOn(dragon, InteractionHand.OFF_HAND).consumesAction(), "The sketchbook should open");
        var open = sent.stream().filter(ClientboundCustomPayloadPacket.class::isInstance).map(ClientboundCustomPayloadPacket.class::cast)
            .map(ClientboundCustomPayloadPacket::payload).filter(EditorPayloads.SketchbookPage.class::isInstance)
            .map(EditorPayloads.SketchbookPage.class::cast).findFirst().orElseThrow();
        VariantRow locked = null;
        int lockedOption = -1;
        for (VariantRow row : open.rows()) {
            for (int i = 0; i < row.bondNeeded().length && locked == null; i++) {
                if (row.bondNeeded()[i] > 0) {
                    locked = row;
                    lockedOption = i;
                }
            }
        }
        helper.assertTrue(locked != null, "An unbonded dragon must have some locked looks");
        helper.assertTrue(locked.bondNeeded()[locked.current()] == 0, "The look the dragon already wears is never locked");
        var choice = new EditorPayloads.SketchbookChoice(open.token(), locked.locus(), locked.slot(), ResourceLocation.parse(locked.ids()[lockedOption]));
        helper.assertTrue(SketchbookSessions.change(owner, choice) == null, "A locked look must be refused by the server");
        dragon.setBond(owner.getUUID(), 1000);
        var after = SketchbookSessions.change(owner, choice);
        helper.assertTrue(after != null, "A fully bonded dragon must accept every look");
        for (VariantRow row : after.rows()) {
            for (int need : row.bondNeeded()) helper.assertTrue(need == 0, "Nothing stays locked at full bond");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void turningOffTheBondLockUnlocksEveryLook(GameTestHelper helper) {
        DragonBase dragon = dragon(helper);
        ServerPlayer owner = player(helper, dragon);
        dragon.adopt(owner.getUUID());
        DragonEdits.setBond(dragon, owner.getUUID(), 0);
        VariantRow[] withLock = VariantEdits.rows(dragon, 0);
        int lockedRow = -1, lockedOption = -1;
        for (int r = 0; r < withLock.length && lockedRow < 0; r++) {
            for (int i = 0; i < withLock[r].bondNeeded().length; i++) {
                if (withLock[r].bondNeeded()[i] > 0) {
                    lockedRow = r;
                    lockedOption = i;
                    break;
                }
            }
        }
        helper.assertTrue(lockedRow >= 0, "With the lock on, an unbonded dragon has locked looks");
        BodAddonConfig.SKETCHBOOK_REQUIRE_BOND.set(false);
        try {
            holdBook(owner);
            List<Packet<?>> sent = new ArrayList<>();
            capturePackets(owner, sent);
            helper.assertTrue(owner.interactOn(dragon, InteractionHand.OFF_HAND).consumesAction(), "The sketchbook should open");
            var open = sent.stream().filter(ClientboundCustomPayloadPacket.class::isInstance).map(ClientboundCustomPayloadPacket.class::cast)
                .map(ClientboundCustomPayloadPacket::payload).filter(EditorPayloads.SketchbookPage.class::isInstance)
                .map(EditorPayloads.SketchbookPage.class::cast).findFirst().orElseThrow();
            for (VariantRow row : open.rows()) {
                for (int need : row.bondNeeded()) helper.assertTrue(need == 0, "With the lock off, nothing is locked at bond 0");
            }
            VariantRow row = open.rows()[lockedRow];
            var choice = new EditorPayloads.SketchbookChoice(open.token(), row.locus(), row.slot(), ResourceLocation.parse(row.ids()[lockedOption]));
            helper.assertTrue(SketchbookSessions.change(owner, choice) != null, "With the lock off, the server accepts a rarer look at bond 0");
            helper.assertTrue(dragon.getBond(owner.getUUID()) == 0, "Editing must not change the bond");
        } finally {
            BodAddonConfig.SKETCHBOOK_REQUIRE_BOND.set(true);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sketchbookRecipeNeedsAnyDragonScale(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("bodaddon", "sketchbook")).orElseThrow();
        var ingredients = holder.value().getIngredients();
        helper.assertTrue(ingredients.size() == 4, "The sketchbook is a book, an ink sac, a feather and a scale");
        for (String scale : List.of("nightfury_scales", "gronckle_scales", "hobgobbler_scales")) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("bookofdragons", scale));
            helper.assertTrue(ingredients.stream().anyMatch(i -> i.test(new ItemStack(item))), "Any dragon scale must work: " + scale);
        }
        helper.assertTrue(ingredients.stream().noneMatch(i -> i.test(new ItemStack(net.minecraft.world.item.Items.STICK))), "A stick is not a scale");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sketchbookRequiresABookAndAnOwnedDragonInReach(GameTestHelper helper) {
        DragonBase dragon = dragon(helper);
        ServerPlayer owner = player(helper, dragon);
        ServerPlayer stranger = player(helper, dragon);
        holdBook(owner);
        helper.assertTrue(SketchbookSessions.open(owner, dragon, InteractionHand.OFF_HAND) == null, "A wild dragon should be rejected");
        helper.assertTrue(owner.getOffhandItem().is(ModItems.SKETCHBOOK.get()), "A rejected attempt should keep the book");
        dragon.adopt(owner.getUUID());
        holdBook(stranger);
        helper.assertTrue(SketchbookSessions.open(stranger, dragon, InteractionHand.OFF_HAND) == null, "A non-owner should be rejected");
        helper.assertTrue(SketchbookSessions.open(owner, dragon, InteractionHand.MAIN_HAND) == null, "The interacting hand must hold a book");
        var open = SketchbookSessions.open(owner, dragon, InteractionHand.OFF_HAND);
        helper.assertTrue(open != null, "Owner within reach should be able to open the book");
        VariantRow row = open.rows()[0];
        var choice = new EditorPayloads.SketchbookChoice(open.token(), row.locus(), row.slot(), ResourceLocation.parse(row.ids()[0]));
        owner.setPos(dragon.getX() + 30, dragon.getY(), dragon.getZ());
        SketchbookSessions.tick(owner);
        owner.setPos(dragon.position());
        helper.assertTrue(SketchbookSessions.change(owner, choice) == null, "Leaving reach must invalidate an existing session");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeEditorCanStillEditWildDragons(GameTestHelper helper) {
        DragonBase dragon = dragon(helper);
        var level = helper.getLevel();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "SketchbookCreative"), false);
        ServerPlayer creative = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override public boolean isCreative() { return true; }
        };
        creative.setPos(dragon.position());
        capturePackets(creative, new ArrayList<>());
        DragonEdits.handle(creative, new EditorPayloads.Act(dragon.getId(), EditAction.SET_HEALTH, 0, 1));
        helper.assertTrue(!dragon.isTame() && dragon.getHealth() == 1, "Creative mode can still edit a wild dragon's health");
        helper.succeed();
    }

    /** What a lasso or capture item does: write the dragon to NBT, remove it, and later read it into a fresh entity. */
    @GameTest(template = "empty")
    public static void editedLooksSurviveSavingAndLoading(GameTestHelper helper) {
        DragonBase dragon = dragon(helper);
        VariantRow[] before = VariantEdits.rows(dragon);
        for (int i = 0; i < before.length; i++) VariantEdits.set(dragon, i, (before[i].current() + 1) % before[i].options().length);
        VariantRow[] edited = VariantEdits.rows(dragon);

        DragonBase loaded = ModEntities.TERRIBLE_TERROR.get().create(helper.getLevel());
        loaded.load(dragon.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        VariantRow[] after = VariantEdits.rows(loaded);
        helper.assertTrue(after.length == edited.length, "The loaded dragon must offer the same rows: " + edited.length + " vs " + after.length);
        for (int i = 0; i < edited.length; i++) {
            helper.assertTrue(after[i].current() == edited[i].current(),
                "Row " + i + " (" + edited[i].label() + ") changed after saving and loading: " + edited[i].current() + " -> " + after[i].current());
        }
        helper.succeed();
    }

    /** A dragon saved before palette draws existed has only a genotype: edit it, save, load, and the edit must remain. */
    @GameTest(template = "empty")
    public static void legacyDragonEditsSurviveSavingAndLoading(GameTestHelper helper) {
        DragonBase original = dragon(helper);
        net.minecraft.nbt.CompoundTag tag = original.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        net.minecraft.nbt.CompoundTag genetics = tag.getCompound("Genetics");
        genetics.remove("PaletteDraws");
        genetics.remove("Layout");

        DragonBase legacy = ModEntities.TERRIBLE_TERROR.get().create(helper.getLevel());
        legacy.load(tag);
        VariantRow[] before = VariantEdits.rows(legacy);
        for (int i = 0; i < before.length; i++) VariantEdits.set(legacy, i, (before[i].current() + 1) % before[i].options().length);
        VariantRow[] edited = VariantEdits.rows(legacy);

        DragonBase loaded = ModEntities.TERRIBLE_TERROR.get().create(helper.getLevel());
        loaded.load(legacy.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        VariantRow[] after = VariantEdits.rows(loaded);
        helper.assertTrue(before.length > 0, "A legacy dragon has no editable rows");
        helper.assertTrue(after.length == edited.length, "Rows differ after reload: " + edited.length + " vs " + after.length);
        for (int i = 0; i < edited.length; i++) {
            helper.assertTrue(after[i].current() == edited[i].current(),
                "Row " + i + " (" + edited[i].label() + ") changed after saving and loading: " + edited[i].current() + " -> " + after[i].current());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pageReportsBondAndTheNextUnlock(GameTestHelper helper) {
        DragonBase dragon = dragon(helper);
        ServerPlayer owner = player(helper, dragon);
        dragon.adopt(owner.getUUID());
        DragonEdits.setBond(dragon, owner.getUUID(), 420);
        holdBook(owner);
        var open = SketchbookSessions.open(owner, dragon, InteractionHand.OFF_HAND);
        helper.assertTrue(open.bond() == 42, "Bond is sent on the 0 to 100 scale: " + open.bond());
        helper.assertTrue(open.nextNeed() > 42 && open.nextNeed() == VariantEdits.nextUnlock(open.rows()),
            "The next unlock is the smallest bond still missing: " + open.nextNeed());
        helper.assertTrue(open.nextTier() >= 1 && open.nextTier() <= 3, "The next unlock names its rarity tier: " + open.nextTier());
        dragon.setBond(owner.getUUID(), 1000);
        holdBook(owner);
        var full = SketchbookSessions.open(owner, dragon, InteractionHand.OFF_HAND);
        helper.assertTrue(full.bond() == 100 && full.nextNeed() == 0, "A fully bonded dragon has nothing left to unlock");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void revertPutsBackTheLookFromWhenTheBookOpened(GameTestHelper helper) {
        DragonBase dragon = dragon(helper);
        ServerPlayer owner = player(helper, dragon);
        dragon.adopt(owner.getUUID());
        dragon.setBond(owner.getUUID(), 1000);
        holdBook(owner);
        var open = SketchbookSessions.open(owner, dragon, InteractionHand.OFF_HAND);
        int[] opening = new int[open.rows().length];
        for (int i = 0; i < opening.length; i++) opening[i] = open.rows()[i].current();
        VariantRow row = open.rows()[0];
        int other = (row.current() + 1) % row.options().length;
        var changed = SketchbookSessions.change(owner, new EditorPayloads.SketchbookChoice(open.token(), row.locus(), row.slot(),
            ResourceLocation.parse(row.ids()[other])));
        helper.assertTrue(changed.rows()[0].current() == other, "The edit must apply before it can be reverted");

        helper.assertTrue(SketchbookSessions.revert(owner, UUID.randomUUID()) == null, "A forged token must not revert anything");
        var back = SketchbookSessions.revert(owner, open.token());
        for (int i = 0; i < opening.length; i++) {
            helper.assertTrue(back.rows()[i].current() == opening[i], "Row " + i + " must go back to how it was when the book opened");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedPageCanBePastedOntoAnotherDragonOfTheSameKind(GameTestHelper helper) {
        DragonBase source = dragon(helper);
        DragonBase target = dragon(helper);
        ServerPlayer owner = player(helper, source);
        capturePackets(owner, new ArrayList<>());
        source.adopt(owner.getUUID());
        target.adopt(owner.getUUID());
        source.setBond(owner.getUUID(), 1000);
        target.setBond(owner.getUUID(), 1000);
        holdBook(owner);
        var open = SketchbookSessions.open(owner, source, InteractionHand.OFF_HAND);
        VariantRow row = open.rows()[0];
        int shown = (row.current() + 1) % row.options().length;
        SketchbookSessions.change(owner, new EditorPayloads.SketchbookChoice(open.token(), row.locus(), row.slot(), ResourceLocation.parse(row.ids()[shown])));

        SketchbookSessions.saveLook(owner, UUID.randomUUID());
        helper.assertTrue(pageCount(owner) == 0, "A forged token must not hand out a page");
        SketchbookSessions.saveLook(owner, open.token());
        ItemStack page = ItemStack.EMPTY;
        int pageSlot = -1;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            if (owner.getInventory().getItem(i).is(ModItems.SKETCHBOOK_PAGE.get())) {
                page = owner.getInventory().getItem(i);
                pageSlot = i;
            }
        }
        helper.assertTrue(!page.isEmpty() && SketchbookPageItem.species(page).equals(source.speciesId()), "Saving must give a page of the dragon's kind");
        SketchbookSessions.close(owner, open.token());

        // Put the target on a different look than the page, then paste.
        VariantRow targetRow = VariantEdits.rows(target)[0];
        int different = (shown + 1) % targetRow.options().length;
        VariantEdits.set(target, 0, different);
        holdBook(owner);
        var second = SketchbookSessions.open(owner, target, InteractionHand.OFF_HAND);
        helper.assertTrue(SketchbookSessions.paste(owner, UUID.randomUUID(), pageSlot) == null, "A forged token must not paste");
        helper.assertTrue(SketchbookSessions.paste(owner, second.token(), -1) == null, "A slot outside the inventory must not paste");
        var pasted = SketchbookSessions.paste(owner, second.token(), pageSlot);
        helper.assertTrue(pasted != null && pasted.rows()[0].ids()[pasted.rows()[0].current()].equals(row.ids()[shown]),
            "The pasted look must show on the other dragon");
        helper.assertTrue(pageCount(owner) == 1, "The page is kept after pasting");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pasteSkipsLooksTheBondHasNotUnlockedAndIgnoresOtherKinds(GameTestHelper helper) {
        DragonBase dragon = dragon(helper);
        ServerPlayer owner = player(helper, dragon);
        capturePackets(owner, new ArrayList<>());
        dragon.adopt(owner.getUUID());
        DragonEdits.setBond(dragon, owner.getUUID(), 0);
        VariantRow locked = null;
        int lockedOption = -1;
        for (VariantRow row : VariantEdits.rows(dragon, 0)) {
            for (int i = 0; i < row.bondNeeded().length && locked == null; i++) {
                if (row.bondNeeded()[i] > 0) {
                    locked = row;
                    lockedOption = i;
                }
            }
        }
        helper.assertTrue(locked != null, "An unbonded dragon must have a locked look");
        var look = List.of(new VariantEdits.Look(locked.locus(), locked.slot(), ResourceLocation.parse(locked.ids()[lockedOption])));
        int[] result = VariantEdits.paste(dragon, look, 0);
        helper.assertTrue(result[0] == 0 && result[1] == 1, "A look the bond has not unlocked is counted, not applied");
        for (VariantRow row : VariantEdits.rows(dragon, 0)) {
            if (row.locus().equals(locked.locus()) && row.slot() == locked.slot()) {
                helper.assertTrue(row.current() == locked.current(), "The locked look must not have been applied");
            }
        }

        holdBook(owner);
        var open = SketchbookSessions.open(owner, dragon, InteractionHand.OFF_HAND);
        owner.getInventory().add(SketchbookPageItem.create(ModItems.SKETCHBOOK_PAGE.get(), "not_this_kind", "Elsewhere", look));
        int foreign = -1;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            if (owner.getInventory().getItem(i).is(ModItems.SKETCHBOOK_PAGE.get())) foreign = i;
        }
        helper.assertTrue(SketchbookSessions.paste(owner, open.token(), foreign) == null, "A page of another kind of dragon must be ignored");
        helper.succeed();
    }

    /**
     * Reports said the Sketchbook drops loyalty and turns dragons against their owners. Everything the book can do
     * (open, change, undo, save, paste, close) and some ticks afterward must leave the owner's opinion untouched.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sketchbookNeverChangesTheOwnersBondOrOpinion(GameTestHelper helper) {
        DragonBase dragon = dragon(helper);
        DragonBase other = dragon(helper);
        ServerPlayer owner = player(helper, dragon);
        capturePackets(owner, new ArrayList<>());
        dragon.adopt(owner.getUUID());
        other.adopt(owner.getUUID());
        DragonEdits.setBond(dragon, owner.getUUID(), 450);
        DragonEdits.setBond(other, owner.getUUID(), 450);
        String before = opinion(dragon, owner);
        String otherBefore = opinion(other, owner);

        holdBook(owner);
        var open = SketchbookSessions.open(owner, dragon, InteractionHand.OFF_HAND);
        helper.assertTrue(opinion(dragon, owner).equals(before), "Opening the book changed the opinion: " + before + " -> " + opinion(dragon, owner));
        for (VariantRow row : open.rows()) {
            for (int i = 0; i < row.options().length; i++) {
                if (i == row.current() || row.bondNeeded()[i] > 0) continue;
                SketchbookSessions.change(owner, new EditorPayloads.SketchbookChoice(open.token(), row.locus(), row.slot(), ResourceLocation.parse(row.ids()[i])));
                break;
            }
        }
        helper.assertTrue(opinion(dragon, owner).equals(before), "Changing looks changed the opinion: " + before + " -> " + opinion(dragon, owner));
        SketchbookSessions.saveLook(owner, open.token());
        int slot = -1;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            if (owner.getInventory().getItem(i).is(ModItems.SKETCHBOOK_PAGE.get())) slot = i;
        }
        SketchbookSessions.revert(owner, open.token());
        SketchbookSessions.paste(owner, open.token(), slot);
        helper.assertTrue(opinion(dragon, owner).equals(before), "Undo/Save/Paste changed the opinion: " + before + " -> " + opinion(dragon, owner));
        SketchbookSessions.close(owner, open.token());

        // Pasting this page onto a second dragon, and letting the world tick, must not move either dragon's opinion.
        holdBook(owner);   // opening consumed the first book
        var second = SketchbookSessions.open(owner, other, InteractionHand.OFF_HAND);
        SketchbookSessions.paste(owner, second.token(), slot);
        SketchbookSessions.close(owner, second.token());
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(opinion(dragon, owner).equals(before), "Ticking after using the book changed the opinion: " + before + " -> " + opinion(dragon, owner));
            helper.assertTrue(opinion(other, owner).equals(otherBefore), "The pasted-on dragon's opinion changed: " + otherBefore + " -> " + opinion(other, owner));
            helper.assertTrue(dragon.isTame() && dragon.getTarget() == null, "The dragon must still be tame and not hunting anyone");
            helper.succeed();
        });
    }

    private static String opinion(DragonBase dragon, ServerPlayer owner) {
        var o = dragon.faunaMind().knowledge().social().of(owner.getUUID());
        return "bond=" + dragon.getBond(owner.getUUID()) + " like=" + o.like() + " fear=" + o.fear() + " anger=" + o.anger()
            + " familiarity=" + o.familiarity();
    }

    private static int pageCount(ServerPlayer player) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.SKETCHBOOK_PAGE.get())) n++;
        }
        return n;
    }

    private static DragonBase dragon(GameTestHelper helper) {
        DragonBase dragon = helper.spawnWithNoFreeWill(ModEntities.TERRIBLE_TERROR.get(), 2, 2, 2);
        dragon.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(dragon.blockPosition()), MobSpawnType.COMMAND, null);
        return dragon;
    }

    private static ServerPlayer player(GameTestHelper helper, DragonBase dragon) {
        var level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "SketchbookTest"),
            ClientInformation.createDefault());
        player.setPos(dragon.position());
        return player;
    }

    private static void holdBook(ServerPlayer player) {
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SKETCHBOOK.get()));
    }

    private static void capturePackets(ServerPlayer player, List<Packet<?>> sent) {
        var cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        player.connection = new ServerGamePacketListenerImpl(player.serverLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player, cookie) {
            @Override public void send(Packet<?> packet) { sent.add(packet); }
        };
    }
}
