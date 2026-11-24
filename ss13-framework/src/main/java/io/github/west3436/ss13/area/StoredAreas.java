package io.github.west3436.ss13.area;

import com.mojang.logging.LogUtils;
import io.github.west3436.ss13.api.area.Area;
import io.github.west3436.ss13.api.area.AreaStore;
import io.github.west3436.ss13.api.area.Areas;
import io.github.west3436.ss13.api.area.AreasChangedEvent;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * A level's stored areas ({@link AreaStore}), saved with the level as {@code
 * data/ss13_framework_areas.dat}.
 *
 * <p>Each area has a small index, 1 up, and each 16³ section that holds a stored block keeps an
 * index per block, 0 for none, as Minecraft keeps block states. A section with no stored block is
 * dropped. On disk each section has its own palette of area ids, so indices never leave memory:
 *
 * <pre>
 * format: 1
 * areas: [{id: "ns:path", name: "Engineering"}, ...]
 * sections: [{pos: SectionPos.asLong, palette: ["ns:path", ...], cells: byte[4096] or int[4096]}]
 * </pre>
 *
 * <p>where a cell is 0 for no area or a palette index plus one, and {@code cells} is a byte array
 * while the palette has fewer than 128 ids.
 */
public final class StoredAreas extends SavedData implements AreaStore {
    /** The saved data's name: the file is {@code data/ss13_framework_areas.dat} in the level's folder. */
    public static final String NAME = "ss13_framework_areas";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FORMAT = 1;
    private static final int SECTION_CELLS = 16 * 16 * 16;

    /** A section's area indices, by {@link #cell}. */
    private static final class Section {
        final short[] cells = new short[SECTION_CELLS];
        int filled;
    }

    private final ServerLevel level;
    /** Each index's area; index 0 and freed indices are null. */
    private final List<Area> areas = new ArrayList<>();
    /** How many blocks each index has. */
    private final IntArrayList counts = new IntArrayList();

    private final Map<ResourceLocation, Short> indexOf = new HashMap<>();
    private final Deque<Short> free = new ArrayDeque<>();
    private final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();

    private StoredAreas(ServerLevel level) {
        this.level = level;
        areas.add(null);
        counts.add(0);
    }

    /**
     * A level's store, loaded from its file the first time it is asked for.
     *
     * @param level the level
     * @return its store
     */
    public static StoredAreas of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(level), NAME);
    }

    /**
     * How a level's store is made and loaded.
     *
     * @param level the level
     * @return the factory
     */
    public static SavedData.Factory<StoredAreas> factory(ServerLevel level) {
        return new SavedData.Factory<>(() -> new StoredAreas(level), (tag, registries) -> load(level, tag));
    }

    // ---- Reading ------------------------------------------------------------------------

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public Optional<Area> at(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        requireServerThread();
        return Optional.ofNullable(areas.get(get(pos.getX(), pos.getY(), pos.getZ())));
    }

    @Override
    public Optional<Area> get(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        requireServerThread();
        Short index = indexOf.get(id);
        return index == null ? Optional.empty() : Optional.of(areas.get(index));
    }

    @Override
    public List<Area> areas() {
        requireServerThread();
        return areas.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public int size(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        requireServerThread();
        Short index = indexOf.get(id);
        return index == null ? 0 : counts.getInt(index);
    }

    @Override
    public Optional<BoundingBox> bounds(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        requireServerThread();
        Short index = indexOf.get(id);
        if (index == null || counts.getInt(index) == 0) {
            return Optional.empty();
        }
        Bounds bounds = new Bounds();
        forEachCell((x, y, z, value) -> {
            if (value == index) {
                bounds.add(x, y, z);
            }
        });
        return bounds.box();
    }

    // ---- Changing -----------------------------------------------------------------------

    @Override
    public Area define(ResourceLocation id, String name) {
        Objects.requireNonNull(id, "id");
        requireServerThread();
        if (indexOf.containsKey(id)) {
            throw new IllegalArgumentException(level.dimension().location() + " already has the area " + id);
        }
        if (indexOf.size() >= MAX_AREAS) {
            throw new IllegalStateException(level.dimension().location() + " already has " + MAX_AREAS + " areas");
        }
        Area area = new Area(id, name);
        add(area);
        setDirty();
        return area;
    }

    @Override
    public Area rename(ResourceLocation id, String name) {
        short index = indexOrThrow(id);
        Area area = areas.get(index).withName(name);
        areas.set(index, area);
        setDirty();
        bounds(id).ifPresent(this::fire);
        return area;
    }

    @Override
    public boolean remove(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        requireServerThread();
        Short index = indexOf.get(id);
        if (index == null) {
            return false;
        }
        Bounds changed = new Bounds();
        if (counts.getInt(index) > 0) {
            forEachCell((x, y, z, value) -> {
                if (value == index) {
                    set(x, y, z, (short) 0);
                    changed.add(x, y, z);
                }
            });
        }
        release(index);
        setDirty();
        changed.box().ifPresent(this::fire);
        return true;
    }

    @Override
    public int assign(ResourceLocation id, BoundingBox box) {
        short index = indexOrThrow(id);
        return fill(box, index);
    }

    @Override
    public int assign(ResourceLocation id, Iterable<BlockPos> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        short index = indexOrThrow(id);
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos pos : blocks) {
            if (list.size() == MAX_BLOCKS_PER_CALL) {
                throw new IllegalArgumentException("more than " + MAX_BLOCKS_PER_CALL + " blocks in one call");
            }
            list.add(pos.immutable());
        }
        Bounds changed = new Bounds();
        int count = 0;
        for (BlockPos pos : list) {
            if (set(pos.getX(), pos.getY(), pos.getZ(), index) != index) {
                count++;
                changed.add(pos.getX(), pos.getY(), pos.getZ());
            }
        }
        finish(changed);
        return count;
    }

    @Override
    public int clear(BoundingBox box) {
        requireServerThread();
        return fill(box, (short) 0);
    }

    /** Sets every block of a box to an index, and says how many changed. */
    private int fill(BoundingBox box, short index) {
        Objects.requireNonNull(box, "box");
        long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        if (volume > MAX_BLOCKS_PER_CALL) {
            throw new IllegalArgumentException(
                    "a box of " + volume + " blocks is over " + MAX_BLOCKS_PER_CALL + " in one call: " + box);
        }
        Bounds changed = new Bounds();
        int count = 0;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    if (set(x, y, z, index) != index) {
                        count++;
                        changed.add(x, y, z);
                    }
                }
            }
        }
        finish(changed);
        return count;
    }

    @Override
    public int move(BoundingBox box, ServerLevel to, BlockPos pivot, BlockPos target, Rotation rotation) {
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(pivot, "pivot");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rotation, "rotation");
        requireServerThread();

        // The areas wholly inside the box: every block they have is in it.
        int[] inside = new int[areas.size()];
        forEachCellIn(box, (x, y, z, value) -> inside[value]++);
        Set<Short> moving = new LinkedHashSet<>();
        for (int i = 1; i < inside.length; i++) {
            if (inside[i] > 0 && inside[i] == counts.getInt(i)) {
                moving.add((short) i);
            }
        }
        if (moving.isEmpty()) {
            return 0;
        }
        StoredAreas destination = to == level ? this : of(to);
        if (destination != this) {
            long arriving = moving.stream()
                    .filter(i -> !destination.indexOf.containsKey(areas.get(i).id()))
                    .count();
            if (destination.indexOf.size() + arriving > MAX_AREAS) {
                LOGGER.warn(
                        "{} areas couldn't move from {} to {}: it would have more than {}",
                        moving.size(),
                        level.dimension().location(),
                        to.dimension().location(),
                        MAX_AREAS);
                return 0;
            }
        }

        // Take their blocks out first, so a destination that overlaps the box is written whole.
        LongArrayList blocks = new LongArrayList();
        ShortArrayList values = new ShortArrayList();
        forEachCellIn(box, (x, y, z, value) -> {
            if (moving.contains(value)) {
                blocks.add(BlockPos.asLong(x, y, z));
                values.add(value);
            }
        });
        for (int i = 0; i < blocks.size(); i++) {
            long p = blocks.getLong(i);
            set(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p), (short) 0);
        }

        // Their indices where they arrive.
        short[] arrivingAs = new short[areas.size()];
        for (short i : moving) {
            Area area = areas.get(i);
            if (destination == this) {
                arrivingAs[i] = i;
            } else {
                release(i);
                arrivingAs[i] = destination.arrive(area);
            }
        }
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos arrival = Areas.map(BlockPos.of(blocks.getLong(i)), pivot, target, rotation);
            destination.set(arrival.getX(), arrival.getY(), arrival.getZ(), arrivingAs[values.getShort(i)]);
        }

        setDirty();
        destination.setDirty();
        BoundingBox arrived = BoundingBox.fromCorners(
                Areas.map(new BlockPos(box.minX(), box.minY(), box.minZ()), pivot, target, rotation),
                Areas.map(new BlockPos(box.maxX(), box.maxY(), box.maxZ()), pivot, target, rotation));
        if (destination == this) {
            fire(BoundingBox.encapsulatingBoxes(List.of(box, arrived)).orElseThrow());
        } else {
            fire(box);
            destination.fire(arrived);
        }
        return moving.size();
    }

    /** An area arriving from another level: defined here, or merged with the one of its id. */
    private short arrive(Area area) {
        Short index = indexOf.get(area.id());
        if (index != null) {
            areas.set(index, area);
            return index;
        }
        return add(area);
    }

    // ---- Cells --------------------------------------------------------------------------

    private interface CellVisitor {
        void visit(int x, int y, int z, short value);
    }

    /** The index of a block in its section. */
    private static int cell(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    private short get(int x, int y, int z) {
        Section section = sections.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
        return section == null ? 0 : section.cells[cell(x, y, z)];
    }

    /** Sets a block's index, keeping the counts, and returns the one it had. */
    private short set(int x, int y, int z, short value) {
        long key = SectionPos.asLong(x >> 4, y >> 4, z >> 4);
        Section section = sections.get(key);
        if (section == null) {
            if (value == 0) {
                return 0;
            }
            section = new Section();
            sections.put(key, section);
        }
        int cell = cell(x, y, z);
        short old = section.cells[cell];
        if (old == value) {
            return old;
        }
        section.cells[cell] = value;
        if (old == 0) {
            section.filled++;
        } else {
            counts.set(old, counts.getInt(old) - 1);
        }
        if (value == 0) {
            section.filled--;
        } else {
            counts.set(value, counts.getInt(value) + 1);
        }
        if (section.filled == 0) {
            sections.remove(key);
        }
        return old;
    }

    /** Visits every stored block. The visitor may clear the block it is given. */
    private void forEachCell(CellVisitor visitor) {
        forEachCellIn(BoundingBox.infinite(), visitor);
    }

    /** Visits every stored block inside a box. The visitor may clear the block it is given. */
    private void forEachCellIn(BoundingBox box, CellVisitor visitor) {
        // By a copy of the keys: the visitor may empty a section, which drops it from the map.
        for (long key : sections.keySet().toLongArray()) {
            Section section = sections.get(key);
            if (section == null) {
                continue;
            }
            int minX = SectionPos.x(key) << 4;
            int minY = SectionPos.y(key) << 4;
            int minZ = SectionPos.z(key) << 4;
            if (!box.intersects(new BoundingBox(minX, minY, minZ, minX + 15, minY + 15, minZ + 15))) {
                continue;
            }
            short[] cells = section.cells;
            for (int cell = 0; cell < SECTION_CELLS; cell++) {
                short value = cells[cell];
                if (value == 0) {
                    continue;
                }
                int x = minX + (cell & 15);
                int y = minY + (cell >> 8);
                int z = minZ + (cell >> 4 & 15);
                if (box.isInside(x, y, z)) {
                    visitor.visit(x, y, z, value);
                }
            }
        }
    }

    // ---- Indices ------------------------------------------------------------------------

    private short add(Area area) {
        short index;
        if (free.isEmpty()) {
            index = (short) areas.size();
            areas.add(area);
            counts.add(0);
        } else {
            index = free.pop();
            areas.set(index, area);
            counts.set(index, 0);
        }
        indexOf.put(area.id(), index);
        return index;
    }

    /** Frees an index whose blocks are all cleared. */
    private void release(short index) {
        indexOf.remove(areas.get(index).id());
        areas.set(index, null);
        counts.set(index, 0);
        free.push(index);
    }

    private short indexOrThrow(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        requireServerThread();
        Short index = indexOf.get(id);
        if (index == null) {
            throw new IllegalArgumentException(level.dimension().location() + " has no area " + id);
        }
        return index;
    }

    // ---- Events and threads -------------------------------------------------------------

    private void finish(Bounds changed) {
        changed.box().ifPresent(box -> {
            setDirty();
            fire(box);
        });
    }

    private void fire(BoundingBox box) {
        NeoForge.EVENT_BUS.post(new AreasChangedEvent(level, box));
    }

    private void requireServerThread() {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("stored areas are used on the server thread, not "
                    + Thread.currentThread().getName());
        }
    }

    /** The box around some blocks, grown as they are added. */
    private static final class Bounds {
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        void add(int x, int y, int z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        Optional<BoundingBox> box() {
            return minX > maxX ? Optional.empty() : Optional.of(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    // ---- Saving -------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT);
        ListTag list = new ListTag();
        for (Area area : areas) {
            if (area != null) {
                CompoundTag a = new CompoundTag();
                a.putString("id", area.id().toString());
                a.putString("name", area.name());
                list.add(a);
            }
        }
        tag.put("areas", list);

        ListTag sectionList = new ListTag();
        for (Long2ObjectMap.Entry<Section> entry : sections.long2ObjectEntrySet()) {
            short[] cells = entry.getValue().cells;
            Map<Short, Integer> palette = new HashMap<>();
            ListTag ids = new ListTag();
            int[] packed = new int[SECTION_CELLS];
            for (int cell = 0; cell < SECTION_CELLS; cell++) {
                short value = cells[cell];
                if (value != 0) {
                    packed[cell] = palette.computeIfAbsent(value, v -> {
                                ids.add(StringTag.valueOf(areas.get(v).id().toString()));
                                return ids.size() - 1;
                            })
                            + 1;
                }
            }
            CompoundTag s = new CompoundTag();
            s.putLong("pos", entry.getLongKey());
            s.put("palette", ids);
            if (ids.size() <= Byte.MAX_VALUE) {
                byte[] bytes = new byte[SECTION_CELLS];
                for (int cell = 0; cell < SECTION_CELLS; cell++) {
                    bytes[cell] = (byte) packed[cell];
                }
                s.put("cells", new ByteArrayTag(bytes));
            } else {
                s.put("cells", new IntArrayTag(packed));
            }
            sectionList.add(s);
        }
        tag.put("sections", sectionList);
        return tag;
    }

    /**
     * Loads a level's store from what {@link #save} wrote. An entry it can't read is skipped and
     * logged, so one bad area never costs the others.
     *
     * @param level the level
     * @param tag the saved tag
     * @return the store
     */
    public static StoredAreas load(ServerLevel level, CompoundTag tag) {
        StoredAreas store = new StoredAreas(level);
        if (tag.getInt("format") > FORMAT) {
            LOGGER.warn(
                    "{} areas were saved by a newer framework (format {}); reading what this one knows",
                    level.dimension().location(),
                    tag.getInt("format"));
        }
        for (Tag t : tag.getList("areas", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) t;
            ResourceLocation id = ResourceLocation.tryParse(a.getString("id"));
            try {
                if (id == null || store.indexOf.containsKey(id) || store.indexOf.size() >= MAX_AREAS) {
                    throw new IllegalArgumentException("a bad or repeated id");
                }
                store.add(new Area(id, a.getString("name")));
            } catch (IllegalArgumentException e) {
                LOGGER.warn(
                        "Skipped the area {} of {}: {}", a, level.dimension().location(), e.getMessage());
            }
        }
        for (Tag t : tag.getList("sections", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            ListTag ids = s.getList("palette", Tag.TAG_STRING);
            short[] palette = new short[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(ids.getString(i));
                Short index = id == null ? null : store.indexOf.get(id);
                palette[i] = index == null ? 0 : index;
            }
            int[] cells = cellsOf(s.get("cells"));
            if (cells == null) {
                LOGGER.warn(
                        "Skipped a section of {}'s areas with no cells",
                        level.dimension().location());
                continue;
            }
            long key = s.getLong("pos");
            int minX = SectionPos.x(key) << 4;
            int minY = SectionPos.y(key) << 4;
            int minZ = SectionPos.z(key) << 4;
            for (int cell = 0; cell < SECTION_CELLS; cell++) {
                int p = cells[cell];
                if (p > 0 && p <= palette.length && palette[p - 1] != 0) {
                    store.set(minX + (cell & 15), minY + (cell >> 8), minZ + (cell >> 4 & 15), palette[p - 1]);
                }
            }
        }
        return store;
    }

    /** A section's saved cells, palette indices plus one; null if they aren't 4096 of them. */
    private static int[] cellsOf(Tag tag) {
        if (tag instanceof ByteArrayTag bytes && bytes.getAsByteArray().length == SECTION_CELLS) {
            int[] cells = new int[SECTION_CELLS];
            byte[] b = bytes.getAsByteArray();
            for (int i = 0; i < SECTION_CELLS; i++) {
                cells[i] = b[i] & 0xFF;
            }
            return cells;
        }
        if (tag instanceof IntArrayTag ints && ints.getAsIntArray().length == SECTION_CELLS) {
            return ints.getAsIntArray();
        }
        return null;
    }
}
