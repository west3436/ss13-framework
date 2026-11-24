package io.github.west3436.ss13.data;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * How a datapack loader reports problems, the same way in every mod that nests the framework: an
 * invalid entry is logged as an error naming its file and the field at fault, and skipped; the
 * rest of the pack still loads, and nothing is thrown at the game. Ignored content is logged as a
 * warning in the same form.
 *
 * <p>A message reads {@code Skipping gas definition data/mypack/zas/gas/foo.json: molar_mass: must
 * be finite and positive, got -1}. Codec-based formats get the field into the message by declaring
 * their fields with {@link #field} and {@link #optionalField}.
 *
 * <p>The problems of the most recent load of each kind are kept for commands and tests ({@link
 * #recent}). The record is shared by every mod, so a kind names what the file defines clearly
 * enough not to be another mod's too, such as {@code "gas definition"} or {@code "planet"}.
 * Loaders run on the server's reload thread; the record is synchronized.
 */
public final class DatapackErrors {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Problem> RECENT = new ArrayList<>();

    /**
     * One problem with a datapack file.
     *
     * @param kind what the file defines, such as {@code "gas definition"}
     * @param file the file's path in the pack, such as {@code data/mypack/zas/gas/foo.json}
     * @param message what is wrong, starting with the field at fault
     * @param skipped whether the entry was skipped (an error) or only part of it ignored (a warning)
     */
    public record Problem(String kind, String file, String message, boolean skipped) {
        /** The line the log gets: {@code Skipping <kind> <file>: <message>}, or {@code Ignoring part of ...}. */
        @Override
        public String toString() {
            return (skipped ? "Skipping " : "Ignoring part of ") + kind + " " + file + ": " + message;
        }
    }

    private DatapackErrors() {}

    /**
     * The path in the pack of the file a {@code SimpleJsonResourceReloadListener} loaded as {@code
     * id}.
     *
     * @param directory the listener's directory, such as {@code zas/gas}
     * @param id the id the listener gave the file
     * @return the path, such as {@code data/mypack/zas/gas/foo.json}
     */
    public static String file(String directory, ResourceLocation id) {
        return "data/" + id.getNamespace() + "/" + directory + "/" + id.getPath() + ".json";
    }

    /**
     * Starts a load of one kind: forgets that kind's problems from the last load.
     *
     * @param kind what the files define
     */
    public static void beginLoad(String kind) {
        synchronized (RECENT) {
            RECENT.removeIf(p -> p.kind().equals(kind));
        }
    }

    /**
     * Logs and records an entry that is skipped, as an error.
     *
     * @param kind what the file defines
     * @param directory the loader's directory
     * @param id the id the loader gave the file
     * @param message what is wrong, starting with the field at fault
     */
    public static void skip(String kind, String directory, ResourceLocation id, String message) {
        Problem p = new Problem(kind, file(directory, id), message, true);
        record(p);
        LOGGER.error("{}", p);
    }

    /**
     * Logs and records something in an entry that is ignored while the rest of it loads, as a
     * warning.
     *
     * @param kind what the file defines
     * @param directory the loader's directory
     * @param id the id the loader gave the file
     * @param message what is ignored and why, starting with the field
     */
    public static void warn(String kind, String directory, ResourceLocation id, String message) {
        Problem p = new Problem(kind, file(directory, id), message, false);
        record(p);
        LOGGER.warn("{}", p);
    }

    /**
     * The problems of the most recent load of each kind, every mod's, in the order they were found.
     *
     * @return the problems
     */
    public static List<Problem> recent() {
        synchronized (RECENT) {
            return List.copyOf(RECENT);
        }
    }

    /**
     * The problems of the most recent load of one kind, in the order they were found.
     *
     * @param kind what the files define
     * @return the problems
     */
    public static List<Problem> recent(String kind) {
        synchronized (RECENT) {
            return RECENT.stream().filter(p -> p.kind().equals(kind)).toList();
        }
    }

    private static void record(Problem p) {
        synchronized (RECENT) {
            RECENT.add(p);
        }
    }

    // ---- Codec helpers ----------------------------------------------------------------

    /**
     * A required field whose errors start with its name: {@code "name: <error>"}, or {@code "name:
     * is required"} if it is missing.
     *
     * @param <A> the field's type
     * @param codec the field's codec
     * @param name the field's name
     * @return the field
     */
    public static <A> MapCodec<A> field(Codec<A> codec, String name) {
        return named(codec.fieldOf(name), name, true);
    }

    /**
     * An optional field whose errors start with its name.
     *
     * @param <A> the field's type
     * @param codec the field's codec
     * @param name the field's name
     * @return the field, empty if missing
     */
    public static <A> MapCodec<Optional<A>> optionalField(Codec<A> codec, String name) {
        return named(codec.optionalFieldOf(name), name, false);
    }

    /**
     * An optional field with a default, whose errors start with its name.
     *
     * @param <A> the field's type
     * @param codec the field's codec
     * @param name the field's name
     * @param fallback the value if the field is missing
     * @return the field
     */
    public static <A> MapCodec<A> optionalField(Codec<A> codec, String name, A fallback) {
        return named(codec.optionalFieldOf(name, fallback), name, false);
    }

    private static <A> MapCodec<A> named(MapCodec<A> inner, String name, boolean required) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return inner.keys(ops);
            }

            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                if (required && input.get(name) == null) {
                    return DataResult.error(() -> name + ": is required");
                }
                return inner.decode(ops, input).mapError(message -> name + ": " + message);
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return inner.encode(input, ops, prefix);
            }

            @Override
            public String toString() {
                return "DatapackErrors.field[" + name + "]";
            }
        };
    }
}
