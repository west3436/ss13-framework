package io.github.west3436.ss13.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.label.Labelled;
import io.github.west3436.ss13.api.label.LabelledMenu;
import io.github.west3436.ss13.api.label.Labels;
import io.github.west3436.ss13.label.LabelPayload;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Labels on devices with no gas in them ({@link TestDevices}): set with a name tag, from an item's
 * name and from a window's title bar, read from any block of a device made of several, and kept
 * only by a device that takes one.
 */
@GameTestHolder(FrameworkApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LabelTests {
    private LabelTests() {}

    private static TestDevices.Device device(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, TestDevices.device());
        return h.getBlockEntity(pos);
    }

    private static FakePlayer player(GameTestHelper h, String name) {
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static ItemStack tag(@Nullable String name) {
        ItemStack stack = new ItemStack(Items.NAME_TAG, 2);
        if (name != null) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        return stack;
    }

    /** Uses what the player holds on a block, as a right click does, with the game's events. */
    private static void use(GameTestHelper h, FakePlayer player, ItemStack stack, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = h.absolutePos(pos);
        player.gameMode.useItemOn(
                player,
                h.getLevel(),
                stack,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    /**
     * A named tag labels a device and is used up; the same tag again changes nothing and is kept; a
     * tag with no name clears the label and is kept. The label is cleaned as it is set.
     */
    @GameTest(template = "empty")
    public static void a_name_tag_labels_a_device(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TestDevices.Device d = device(h, pos);
        FakePlayer player = player(h, "ss13_label_tag");

        ItemStack named = tag("§cSupply  1 ");
        use(h, player, named, pos);
        h.assertValueEqual(d.label(), "Supply 1", "the label a tag set");
        h.assertValueEqual(named.getCount(), 1, "tags left after labelling");
        h.assertValueEqual(Labels.of(h.getLevel(), h.absolutePos(pos)), "Supply 1", "the label read from the block");
        h.assertValueEqual(Labels.named(h.getLevel(), h.absolutePos(pos)), "Supply 1", "what a message calls it");

        use(h, player, named, pos);
        h.assertValueEqual(named.getCount(), 1, "a tag used on the label it already has was used up");

        ItemStack blank = tag(null);
        use(h, player, blank, pos);
        h.assertValueEqual(d.label(), "", "the label after a blank tag");
        h.assertValueEqual(blank.getCount(), 2, "a blank tag was used up");
        BlockPos abs = h.absolutePos(pos);
        h.assertValueEqual(
                Labels.named(h.getLevel(), abs),
                abs.getX() + " " + abs.getY() + " " + abs.getZ(),
                "what a message calls a device with no label");
        h.succeed();
    }

    /** A device that takes no label keeps none, and a name tag on it does nothing. */
    @GameTest(template = "empty")
    public static void a_device_that_takes_no_label_keeps_none(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, TestDevices.plainDevice());
        TestDevices.PlainDevice plain = h.getBlockEntity(pos);
        h.assertFalse(plain.setLabel("Supply 1"), "a device that takes no label took one");
        ItemStack named = tag("Supply 1");
        use(h, player(h, "ss13_label_plain"), named, pos);
        h.assertValueEqual(plain.label(), "", "its label");
        h.assertValueEqual(named.getCount(), 2, "the tag was used up");
        h.assertTrue(Labels.at(h.getLevel(), h.absolutePos(pos)) == null, "Labels.at found a device");
        h.succeed();
    }

    /** Any block of a device made of several reads, and takes, the label its own block keeps. */
    @GameTest(template = "empty")
    public static void a_device_of_several_blocks_has_one_label(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TestDevices.Device d = device(h, pos);
        h.setBlock(pos.above(), TestDevices.part());
        BlockPos upper = h.absolutePos(pos.above());
        h.assertTrue(Labels.at(h.getLevel(), upper) == d, "the upper block doesn't lead to the device");

        use(h, player(h, "ss13_label_part"), tag("Dock"), pos.above());
        h.assertValueEqual(d.label(), "Dock", "the label set from the upper block");
        h.assertValueEqual(Labels.of(h.getLevel(), upper), "Dock", "the label read from the upper block");
        h.succeed();
    }

    /**
     * An item renamed in an anvil gives the device it places its name; a copy of the device is named
     * by its label; and the window's title carries the label and the kind.
     */
    @GameTest(template = "empty")
    public static void a_label_comes_from_and_goes_to_an_item_name(GameTestHelper h) {
        TestDevices.Device d = device(h, new BlockPos(1, 1, 1));
        ItemStack placed = new ItemStack(Items.STONE);
        placed.set(DataComponents.CUSTOM_NAME, Component.literal("Mixing  room"));
        d.applyComponentsFromItemStack(placed);
        h.assertValueEqual(d.label(), "Mixing room", "the label from the item's name");

        Component copied = d.collectComponents().get(DataComponents.CUSTOM_NAME);
        h.assertTrue(copied != null && copied.getString().equals("Mixing room"), "a copy's name: " + copied);

        Component title = d.getDisplayName();
        h.assertValueEqual(Labels.labelIn(title), "Mixing room", "the label in the title");
        h.assertValueEqual(Labels.kindIn(title), d.kind(), "the kind in the title");
        h.assertValueEqual(d.getName().getString(), "Mixing room", "its name");
        d.setLabel(null);
        h.assertTrue(d.getCustomName() == null, "a custom name with no label");
        h.assertValueEqual(d.getDisplayName(), d.kind(), "the title with no label");
        h.succeed();
    }

    /** A window of a device that takes a label, open on the server. */
    private static final class Window extends AbstractContainerMenu implements LabelledMenu {
        private final Labelled device;

        Window(int id, Labelled device) {
            super(null, id);
            this.device = device;
        }

        @Override
        public @Nullable Labelled labelled() {
            return device;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    /**
     * A label typed in a window's title bar reaches the device whose window the player has open, and
     * only from a player who may act there.
     */
    @GameTest(template = "empty")
    public static void a_label_typed_in_a_window_reaches_its_device(GameTestHelper h) {
        TestDevices.Device d = device(h, new BlockPos(1, 1, 1));
        FakePlayer player = player(h, "ss13_label_window");
        player.containerMenu = new Window(42, d);
        try {
            h.assertFalse(LabelPayload.apply(player, new LabelPayload(41, "Wrong")), "another window's label taken");
            h.assertValueEqual(d.label(), "", "the label after another window's");

            h.assertTrue(LabelPayload.apply(player, new LabelPayload(42, " Hot\tside§l ")), "the label refused");
            h.assertValueEqual(d.label(), "Hot side", "the label typed");

            player.setGameMode(GameType.SPECTATOR);
            h.assertFalse(LabelPayload.apply(player, new LabelPayload(42, "Spy")), "a spectator's label taken");
            h.assertValueEqual(d.label(), "Hot side", "the label after a spectator's");
        } finally {
            player.setGameMode(GameType.SURVIVAL);
            player.containerMenu = player.inventoryMenu;
        }
        h.succeed();
    }
}
