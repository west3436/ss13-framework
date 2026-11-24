package io.github.west3436.ss13.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Unit;
import org.junit.jupiter.api.Test;

/** The format version of what data components save on an item. */
class ItemDataTest {
    /** An amount no float can hold, so a lost digit shows. */
    private static final double AMOUNT = 12.345678901234567;

    /** A component with fields, as a stored charge or a held setting would be. */
    private record Charge(double amount, String kind) {
        static final Codec<Charge> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.DOUBLE.fieldOf("amount").forGetter(Charge::amount),
                        Codec.STRING.fieldOf("kind").forGetter(Charge::kind))
                .apply(i, Charge::new));
    }

    private static final Charge CHARGE = new Charge(AMOUNT, "fe");

    private static CompoundTag saved(int version, Tag value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DeviceData.VERSION_KEY, version);
        tag.put(ItemData.VALUE_KEY, value);
        return tag;
    }

    /** A charge as it was saved before versions were added. */
    private static CompoundTag oldCharge() {
        CompoundTag charge = new CompoundTag();
        charge.putDouble("amount", AMOUNT);
        charge.putString("kind", "fe");
        return charge;
    }

    private static <A> A read(Codec<A> format, Tag tag) {
        return ItemData.versioned(format).parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void a_value_is_saved_with_its_version() {
        Tag written = ItemData.versioned(Codec.DOUBLE)
                .encodeStart(NbtOps.INSTANCE, 5.0)
                .getOrThrow();
        assertEquals(saved(1, DoubleTag.valueOf(5)), written);
    }

    /** Every shape a component took before versions were added reads as the first format. */
    @Test
    void a_value_with_no_version_reads_as_the_first_format() {
        assertEquals(5.0, read(Codec.DOUBLE, DoubleTag.valueOf(5)));
        assertEquals(1.5f, read(Codec.FLOAT, FloatTag.valueOf(1.5f)));
        assertEquals(1234, read(Codec.INT, IntTag.valueOf(1234)));
        assertEquals(true, read(Codec.BOOL, ByteTag.valueOf(true)));
        assertEquals(false, read(Codec.BOOL, ByteTag.valueOf(false)));
        assertEquals(Unit.INSTANCE, read(Codec.unit(Unit.INSTANCE), new CompoundTag()));
        assertEquals(CHARGE, read(Charge.CODEC, oldCharge()));

        ListTag list = new ListTag();
        list.add(StringTag.valueOf("a"));
        list.add(StringTag.valueOf("b"));
        assertEquals(List.of("a", "b"), read(Codec.STRING.listOf(), list));
    }

    @Test
    void a_value_saved_as_current_reads_back() {
        assertEquals(5.0, read(Codec.DOUBLE, saved(1, DoubleTag.valueOf(5))));
        assertEquals(Unit.INSTANCE, read(Codec.unit(Unit.INSTANCE), saved(1, new CompoundTag())));
        assertEquals(CHARGE, read(Charge.CODEC, saved(1, oldCharge())));
    }

    /** Old and current read to the same value in memory, holding every digit. */
    @Test
    void old_and_current_read_as_the_same_value() {
        Codec<Charge> codec = ItemData.versioned(Charge.CODEC);
        Tag current = codec.encodeStart(NbtOps.INSTANCE, CHARGE).getOrThrow();
        Charge fromOld = read(Charge.CODEC, oldCharge());
        Charge fromCurrent = read(Charge.CODEC, current);
        assertEquals(fromOld, fromCurrent);
        assertEquals(fromOld.hashCode(), fromCurrent.hashCode());
        assertEquals(AMOUNT, fromCurrent.amount());
    }

    /** A recipe or a loot table writes a component in JSON, in the form it always had. */
    @Test
    void json_with_no_version_reads_as_the_first_format() {
        String json = "{\"amount\":" + AMOUNT + ",\"kind\":\"fe\"}";
        Charge read = ItemData.versioned(Charge.CODEC)
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow();
        assertEquals(CHARGE, read);
    }

    /** Steps that change a value's unit, as a real format change would. */
    private static List<Consumer<CompoundTag>> scales(List<String> ran) {
        return List.of(
                tag -> ran.add("0 to 1"),
                tag -> {
                    ran.add("1 to 2");
                    tag.putDouble(ItemData.VALUE_KEY, tag.getDouble(ItemData.VALUE_KEY) * 1000);
                },
                tag -> ran.add("2 to 3"));
    }

    @Test
    void steps_run_in_order_from_the_saved_version() {
        List<String> ran = new ArrayList<>();
        Codec<Double> codec = ItemData.versioned(Codec.DOUBLE, scales(ran));

        assertEquals(5000.0, codec.parse(NbtOps.INSTANCE, DoubleTag.valueOf(5)).getOrThrow());
        assertEquals(List.of("0 to 1", "1 to 2", "2 to 3"), ran);

        ran.clear();
        assertEquals(
                5000.0,
                codec.parse(NbtOps.INSTANCE, saved(1, DoubleTag.valueOf(5))).getOrThrow());
        assertEquals(List.of("1 to 2", "2 to 3"), ran);

        ran.clear();
        assertEquals(
                5.0,
                codec.parse(NbtOps.INSTANCE, saved(2, DoubleTag.valueOf(5))).getOrThrow());
        assertEquals(List.of("2 to 3"), ran);

        ran.clear();
        assertEquals(
                5.0,
                codec.parse(NbtOps.INSTANCE, saved(3, DoubleTag.valueOf(5))).getOrThrow());
        assertEquals(List.of(), ran);
    }

    @Test
    void the_version_written_follows_from_the_steps() {
        Tag written = ItemData.versioned(Codec.DOUBLE, scales(new ArrayList<>()))
                .encodeStart(NbtOps.INSTANCE, 5.0)
                .getOrThrow();
        assertEquals(saved(3, DoubleTag.valueOf(5)), written);
    }

    /** A value from a newer mod runs no step, and is read by name. */
    @Test
    void a_newer_version_is_read_by_name() {
        List<String> ran = new ArrayList<>();
        Codec<Double> codec = ItemData.versioned(Codec.DOUBLE, scales(ran));
        assertEquals(
                5.0,
                codec.parse(NbtOps.INSTANCE, saved(4, DoubleTag.valueOf(5))).getOrThrow());
        assertEquals(List.of(), ran);
    }

    /** A value that can't be read is an error for the game to handle, as it always was, not a crash. */
    @Test
    void a_value_that_cant_be_read_is_an_error() {
        Codec<Double> codec = ItemData.versioned(Codec.DOUBLE);
        CompoundTag none = new CompoundTag();
        none.putInt(DeviceData.VERSION_KEY, 1);
        assertTrue(codec.parse(NbtOps.INSTANCE, none).isError());
        assertTrue(codec.parse(NbtOps.INSTANCE, saved(1, StringTag.valueOf("nonsense")))
                .isError());
        assertTrue(codec.parse(NbtOps.INSTANCE, StringTag.valueOf("nonsense")).isError());
    }
}
