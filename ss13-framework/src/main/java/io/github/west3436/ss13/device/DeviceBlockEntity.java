package io.github.west3436.ss13.device;

import io.github.west3436.ss13.api.device.BlockMovers;
import io.github.west3436.ss13.api.label.Labelled;
import io.github.west3436.ss13.api.label.Labels;
import io.github.west3436.ss13.api.power.DevicePower;
import io.github.west3436.ss13.api.power.PoweredDevice;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * The base of every device's block entity: something that steps once per device tick, a second, on
 * the server.
 *
 * <p><b>Its step.</b> While it is loaded on a server, the block entity is a member of its level's
 * {@link DeviceTicker} schedule, which calls {@link #deviceTick} once per device tick on the server
 * thread, spread across game ticks. It joins when it loads or is placed and leaves when it is
 * removed or its chunk unloads, so a device in an unloaded chunk never steps. The schedule steps it
 * only while its chunk ticks block entities, as vanilla's block entities tick ({@link #ticking()});
 * otherwise it waits as it is. A device whose block a {@linkplain BlockMovers mover} is moving
 * waits too.
 *
 * <p><b>What it saves.</b> It writes a format version ({@link DeviceData}) and brings an older save
 * forward before any subclass reads it, so a subclass only reads and writes its own fields.
 *
 * <p><b>Its label.</b> A device a player can label names {@link Labelled}, and this keeps its
 * label: saved with the block under {@code Label}, sent to the clients that have it loaded, taken
 * from the name of the item that placed it and given back as an item's name, as the game does for a
 * chest. A device that doesn't name it never has one.
 *
 * <p><b>How hard it works.</b> A device says what it did in each step ({@link #reportActivity},
 * {@link #reportRunning}, {@link #reportStopped}), and nearby players are told its {@link
 * DeviceActivity} level, so their clients play its loop ({@code DeviceLoops}).
 *
 * <p><b>Its power.</b> A device that draws power names {@link PoweredDevice}, gives its {@link
 * DevicePower} {@link #now()} as its clock, and works only while it is powered. The base keeps its
 * lease while it waits, and has it wait with the provider that powers it.
 */
public abstract class DeviceBlockEntity extends BlockEntity implements DeviceSchedule.Member {
    /** The key the label is saved under. */
    public static final String LABEL_KEY = "Label";

    /** How hard it reads as working: made by its first report, so most pay nothing. */
    private @Nullable DeviceActivity activity;

    private @Nullable LevelChunk chunk;

    /** What a player called it, as {@link Labels#clean} leaves it; empty for none. */
    private String label = "";

    protected DeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- What it saves ----------------------------------------------------------------------

    /**
     * The steps that bring an older save of this device forward, oldest first: entry {@code i}
     * changes version {@code i}'s tag, in place, into version {@code i + 1}'s, and the version saved
     * is the list's size. The default is {@link DeviceData#FIRST_FORMAT}. A device whose format
     * changes returns a longer list, the same one every time.
     *
     * @return the steps
     */
    protected List<Consumer<CompoundTag>> formatSteps() {
        return DeviceData.FIRST_FORMAT;
    }

    /** Writes the format version of what this block entity saves, and its label if it has one. */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(DeviceData.VERSION_KEY, formatSteps().size());
        writeLabel(tag);
    }

    /** Brings an older save forward, before any subclass reads it. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        DeviceData.migrate(tag, formatSteps());
        readLabel(tag);
    }

    // ---- Its label --------------------------------------------------------------------------

    /** The label a player gave it, or the empty string: always empty for a device that takes none. */
    public final String label() {
        return label;
    }

    /**
     * Sets the label, on the server: saved, and sent to the clients that have the block loaded. The
     * text goes through {@link Labels#clean} first, so nothing else is ever kept. A device that isn't
     * {@link Labelled} takes none.
     *
     * @param text what a player typed; null or nothing left of it clears the label
     * @return whether the label changed
     */
    public final boolean setLabel(@Nullable String text) {
        String cleaned = this instanceof Labelled ? Labels.clean(text) : "";
        if (cleaned.equals(label)) {
            return false;
        }
        label = cleaned;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    private void writeLabel(CompoundTag tag) {
        if (!label.isEmpty()) {
            tag.putString(LABEL_KEY, label);
        }
    }

    /** Reads a label as saved or sent, cleaned again: a save or a server could hold anything. */
    private void readLabel(@Nullable CompoundTag tag) {
        label = tag != null && this instanceof Labelled ? Labels.clean(tag.getString(LABEL_KEY)) : "";
    }

    /** An item renamed in an anvil gives the block it places its name as a label, as it does a chest. */
    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        if (!(this instanceof Labelled)) {
            return; // asking for the name would take it from a device that keeps its item's components
        }
        Component name = input.get(DataComponents.CUSTOM_NAME);
        if (name != null) {
            label = Labels.clean(name.getString());
        }
    }

    /** An item that copies the block, as a creative player's pick does, is named by its label. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!label.isEmpty()) {
            components.set(DataComponents.CUSTOM_NAME, Component.literal(label));
        }
    }

    /** The label is the item's name there, not also in its copy of the block's data. */
    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(LABEL_KEY);
    }

    /** Clients are sent the label, and nothing else. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeLabel(tag);
        return tag;
    }

    /** Only a device that takes a label has anything to send when its block is updated. */
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return this instanceof Labelled ? ClientboundBlockEntityDataPacket.create(this) : null;
    }

    /** A client reads only the label from what it is sent, with its chunk. */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readLabel(tag);
    }

    /** And when the block is updated: nothing sent is a label cleared. */
    @Override
    public void onDataPacket(
            Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        readLabel(packet.getTag());
    }

    // ---- How hard it works ------------------------------------------------------------------

    /**
     * Says how much work this step does, so nearby players can hear and see how hard the device is
     * working. A device calls this from {@link #deviceTick}, with what it has decided to do, on the
     * {@link DeviceActivity} scale: a device that moves gas passes the moles it moves. A step with
     * no call counts as doing none, and the level then falls after a short hold.
     *
     * @param amount the work done in this step; more than one call adds up
     */
    protected final void reportActivity(double amount) {
        if (amount > 0) {
            if (activity == null) {
                activity = new DeviceActivity();
            }
            activity.add(amount);
        }
    }

    /**
     * Says a device whose work has no amount, such as a heater, is running in this step, from {@link
     * #deviceTick}. It reads as {@linkplain DeviceActivity#WORKING working} for as long as it says
     * so.
     */
    protected final void reportRunning() {
        reportActivity(DeviceActivity.RUNNING);
    }

    /**
     * Says the device is off, has no power, or works too little to show, from {@link #deviceTick}: it
     * reads as idle after this step, with no hold, so its sound stops when it does. A device switched
     * between steps follows this with {@link #finishActivity}, so it reads as idle at once.
     */
    protected final void reportStopped() {
        if (activity != null) {
            activity.stopNow();
        }
    }

    /**
     * Ends what has been reported so far and passes the level on, if this device has ever reported any.
     * The base does this at the end of each step. A device switched between steps does it too, so that
     * its level changes when its switch does.
     *
     * @param server the device's level
     */
    protected final void finishActivity(ServerLevel server) {
        if (activity != null) {
            DeviceActivitySync.report(server, worldPosition, activity.finishStep(), DeviceTicks.now(server));
        }
    }

    // ---- Its place in the schedule ----------------------------------------------------------

    /**
     * The chunk that saves this block entity, on a server. It is kept from the first time it is asked
     * for, because the level stops handing out a chunk that has left full status, though it still
     * saves the block entity when it unloads. Asking loads nothing.
     *
     * @return the chunk, or null if it isn't in a server level or hasn't been in a chunk in full status
     */
    public @Nullable LevelChunk savedIn() {
        if (chunk == null && level instanceof ServerLevel server) {
            chunk = server.getChunkSource()
                    .getChunkNow(
                            SectionPos.blockToSectionCoord(worldPosition.getX()),
                            SectionPos.blockToSectionCoord(worldPosition.getZ()));
        }
        return chunk;
    }

    /**
     * One device tick of work, {@link DeviceTicks#SECONDS} seconds. Called on the server thread,
     * once per device tick, only while the block entity is loaded and {@link #readyToStep()}.
     *
     * @param level the level, a server level
     */
    protected abstract void deviceTick(ServerLevel level);

    /**
     * Runs before each {@link #deviceTick}, for what every device of a kind does whatever its own
     * step is, such as a network node's share of its network's tick. The default does nothing. It
     * may remove the block entity, and {@link #deviceTick} is then skipped.
     *
     * @param level the level, a server level
     */
    protected void beforeDeviceTick(ServerLevel level) {}

    /**
     * Whether the device can step now. The default is true. A device returns false while something
     * it started must finish first, and is then asked again next game tick.
     */
    @Override
    public boolean readyToStep() {
        return true;
    }

    /**
     * Whether the schedule steps the device when it is due: whether it is {@link #stepping()}. A
     * device that isn't is passed over, and {@link #whilePassedOver} is called. Asking loads nothing.
     */
    @Override
    public final boolean ticking() {
        if (isRemoved() || !(level instanceof ServerLevel)) {
            return true; // the step does nothing
        }
        if (stepping()) {
            return true;
        }
        whilePassedOver();
        return false;
    }

    /**
     * Whether the device is working now: it is in a server level, its chunk ticks block entities
     * ({@link LoadedBlocks#ticking}), and it doesn't {@linkplain #waitsForAnother() wait for another
     * device}. A chunk at the edge of what is loaded, kept in full only by its neighbour, doesn't
     * tick, and a device there waits as it is: a step there would read its neighbours through the
     * level, which keeps the chunk loaded after every player has gone. What needs to know whether a
     * device works, rather than whether its block can be read, asks this. Asking loads nothing.
     */
    public final boolean stepping() {
        return !isRemoved()
                && level instanceof ServerLevel server
                && LoadedBlocks.ticking(server, worldPosition)
                && !waitsForAnother();
    }

    /**
     * Whether this device waits because another it depends on waits. The default: a {@link
     * PoweredDevice} waits with the device that last powered it, while that one is loaded and its
     * chunk doesn't tick, as the provider powers nothing while it waits. Asking must load nothing.
     *
     * @return true to be passed over while its own chunk ticks
     */
    protected boolean waitsForAnother() {
        return this instanceof PoweredDevice part
                && part.power().source() instanceof DeviceBlockEntity provider
                && provider != this
                && !provider.isRemoved()
                && DeviceTicker.isScheduled(provider)
                && provider.level instanceof ServerLevel there
                && !LoadedBlocks.ticking(there, provider.worldPosition);
    }

    /**
     * Called each time the schedule passes the device over because it isn't {@link #stepping()}, so
     * that it can hold what would otherwise lapse while it waits. The default holds a {@link
     * PoweredDevice}'s power lease and claim ({@link DevicePower#hold}), so the wait never reads as a
     * power cut.
     */
    protected void whilePassedOver() {
        if (this instanceof PoweredDevice part) {
            part.power().hold();
        }
    }

    /**
     * Called by the schedule, and by tests that step a device at once; runs {@link #deviceTick} if
     * the block entity is still in a server level and its chunk is loaded in full. A chunk next to a
     * loaded one that drops out of full status keeps its block entities in memory without unloading
     * them; a device there waits, since reading its own block would load the chunk again.
     *
     * <p>A device whose block is being moved ({@link BlockMovers#isMoving}) waits too. A mover that
     * takes a box away or sets it down over several ticks leaves the device beside air where a
     * neighbour was or will be. It steps again once the move is over, with its neighbours back.
     */
    @Override
    public final void step() {
        if (!isRemoved()
                && level instanceof ServerLevel server
                && server.isLoaded(worldPosition)
                && !BlockMovers.isMoving(server, worldPosition)) {
            beforeDeviceTick(server);
            if (!isRemoved()) {
                deviceTick(server);
            }
            if (!isRemoved()) {
                finishActivity(server);
            }
        }
    }

    /** The current game tick, as {@link DeviceTicks#now}; only on the server, 0 elsewhere. */
    protected long now() {
        return level instanceof ServerLevel server ? DeviceTicks.now(server) : 0L;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            DeviceTicker.add(server, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            DeviceTicker.remove(server, this);
            forgetActivity(server);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) {
            DeviceTicker.remove(server, this);
            forgetActivity(server);
        }
    }

    private void forgetActivity(ServerLevel server) {
        if (activity != null) {
            DeviceActivitySync.remove(server, worldPosition);
        }
    }
}
