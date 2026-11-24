package io.github.west3436.ss13.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.ss13.data.DatapackErrors.Problem;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The form of a datapack problem, the record of the last load, and fields that name themselves. */
class DatapackErrorsTest {
    /** A format with a field of each sort. */
    private record Thing(double mass, Optional<String> label, int count) {
        static final Codec<Thing> CODEC = RecordCodecBuilder.create(i -> i.group(
                        DatapackErrors.field(Codec.DOUBLE, "mass").forGetter(Thing::mass),
                        DatapackErrors.optionalField(Codec.STRING, "label").forGetter(Thing::label),
                        DatapackErrors.optionalField(Codec.INT, "count", 1).forGetter(Thing::count))
                .apply(i, Thing::new));
    }

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("mypack", "foo");

    @Test
    void a_file_is_named_by_its_path_in_the_pack() {
        assertEquals("data/mypack/zas/gas/foo.json", DatapackErrors.file("zas/gas", ID));
        assertEquals(
                "data/mypack/zas/gas/sub/bar.json",
                DatapackErrors.file("zas/gas", ResourceLocation.fromNamespaceAndPath("mypack", "sub/bar")));
    }

    @Test
    void a_problem_reads_as_the_log_line() {
        assertEquals(
                "Skipping gas definition data/mypack/zas/gas/foo.json: molar_mass: must be positive",
                new Problem("gas definition", "data/mypack/zas/gas/foo.json", "molar_mass: must be positive", true)
                        .toString());
        assertEquals(
                "Ignoring part of gas definition data/mypack/zas/gas/foo.json: colour: unknown",
                new Problem("gas definition", "data/mypack/zas/gas/foo.json", "colour: unknown", false).toString());
    }

    @Test
    void each_load_forgets_only_its_own_kinds_problems() {
        String first = "test thing";
        String second = "test other thing";
        DatapackErrors.beginLoad(first);
        DatapackErrors.beginLoad(second);
        DatapackErrors.skip(first, "things", ID, "mass: is required");
        DatapackErrors.warn(first, "things", ID, "label: ignored");
        DatapackErrors.skip(second, "others", ID, "count: too big");
        assertEquals(
                List.of(
                        new Problem(first, "data/mypack/things/foo.json", "mass: is required", true),
                        new Problem(first, "data/mypack/things/foo.json", "label: ignored", false)),
                DatapackErrors.recent(first));
        assertTrue(DatapackErrors.recent().containsAll(DatapackErrors.recent(second)));

        DatapackErrors.beginLoad(first);
        assertEquals(List.of(), DatapackErrors.recent(first));
        assertEquals(1, DatapackErrors.recent(second).size(), "another kind's problems were forgotten");
        DatapackErrors.beginLoad(second);
    }

    @Test
    void a_missing_required_field_is_named() {
        assertEquals("mass: is required", error(Thing.CODEC.parse(JsonOps.INSTANCE, json("{\"count\": 2}"))));
    }

    @Test
    void a_wrong_field_is_named_first_in_the_error() {
        assertTrue(error(Thing.CODEC.parse(JsonOps.INSTANCE, json("{\"mass\": \"heavy\"}")))
                .startsWith("mass: "));
        assertTrue(error(Thing.CODEC.parse(JsonOps.INSTANCE, json("{\"mass\": 1, \"label\": []}")))
                .startsWith("label: "));
        assertTrue(error(Thing.CODEC.parse(JsonOps.INSTANCE, json("{\"mass\": 1, \"count\": \"many\"}")))
                .startsWith("count: "));
    }

    @Test
    void optional_fields_may_be_left_out() {
        DataResult<Thing> parsed = Thing.CODEC.parse(JsonOps.INSTANCE, json("{\"mass\": 2.5}"));
        assertEquals(Optional.of(new Thing(2.5, Optional.empty(), 1)), parsed.result());
    }

    @Test
    void a_format_still_writes_its_fields() {
        Thing thing = new Thing(2.5, Optional.of("tank"), 3);
        JsonElement written = Thing.CODEC.encodeStart(JsonOps.INSTANCE, thing).getOrThrow();
        assertEquals(json("{\"mass\": 2.5, \"label\": \"tank\", \"count\": 3}"), written);
        assertFalse(written.getAsJsonObject().has("missing"));
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static String error(DataResult<?> result) {
        return result.error()
                .orElseThrow(() -> new AssertionError("parsed: " + result))
                .message();
    }
}
