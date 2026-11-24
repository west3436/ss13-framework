package io.github.west3436.ss13.preset;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.WritingException;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import io.github.west3436.ss13.api.preset.Preset;
import io.github.west3436.ss13.api.preset.PresetOption;
import io.github.west3436.ss13.api.preset.Presets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The preset file, {@value Presets#FILE_NAME}: the preset under {@code preset}, and each option
 * under {@code [options.<namespace>]}, as {@value PresetOption#FOLLOW_PRESET} or an override.
 *
 * <p>Reading keeps the overrides of options no loaded mod registers, so a mod that is removed for a
 * while finds its overrides again; writing puts them back. An option's override the option's type
 * doesn't accept is dropped with a warning, so the option follows the preset.
 */
public final class PresetFile {
    private static final String PRESET = "preset";
    private static final String OPTIONS = "options";

    /**
     * What a file holds.
     *
     * @param preset the preset, {@link Preset#NORMAL} if the file doesn't give a valid one
     * @param overrides every override, by option id, in file order; none follows the preset
     * @param listed the ids of every option the file lists, overridden or not
     * @param warnings what was wrong with the file, for the log
     */
    public record Contents(
            Preset preset,
            Map<ResourceLocation, Object> overrides,
            Set<ResourceLocation> listed,
            List<String> warnings) {
        /** The defaults: Normal, with no override. */
        public static final Contents DEFAULTS = new Contents(Preset.NORMAL, Map.of(), Set.of(), List.of());

        /**
         * Whether the file lists every registered option, so writing it again would add nothing.
         *
         * @return true if it does
         */
        public boolean listsEveryOption() {
            return Presets.options().stream().allMatch(o -> listed.contains(o.id()));
        }
    }

    private PresetFile() {}

    /**
     * Reads a file.
     *
     * @param file the file
     * @return what it holds
     * @throws IOException if it can't be read
     * @throws ParsingException if it isn't TOML
     */
    public static Contents read(Path file) throws IOException {
        CommentedConfig config = new TomlParser().parse(Files.readString(file));
        List<String> warnings = new ArrayList<>();
        Preset preset = Preset.NORMAL;
        Object rawPreset = config.get(PRESET);
        if (rawPreset != null) {
            Optional<Preset> parsed = rawPreset instanceof String s ? Preset.byName(s) : Optional.empty();
            if (parsed.isPresent()) {
                preset = parsed.get();
            } else {
                warnings.add("preset can't be " + rawPreset + ", so it is NORMAL");
            }
        }
        Map<ResourceLocation, Object> overrides = new LinkedHashMap<>();
        Set<ResourceLocation> listed = new LinkedHashSet<>();
        if (config.get(OPTIONS) instanceof UnmodifiableConfig options) {
            for (UnmodifiableConfig.Entry namespace : options.entrySet()) {
                if (!(namespace.getRawValue() instanceof UnmodifiableConfig values)) {
                    warnings.add("options." + namespace.getKey() + " isn't a table, so it is left out");
                    continue;
                }
                for (UnmodifiableConfig.Entry entry : values.entrySet()) {
                    String where = "options." + namespace.getKey() + "." + entry.getKey();
                    ResourceLocation id = ResourceLocation.tryBuild(namespace.getKey(), entry.getKey());
                    if (id == null) {
                        warnings.add(where + " isn't an option id, so it is left out");
                        continue;
                    }
                    listed.add(id);
                    Object raw = entry.getRawValue();
                    if (PresetOption.followsPreset(raw)) {
                        continue;
                    }
                    Optional<PresetOption<?>> option = Presets.option(id);
                    if (option.isPresent() && option.get().type().parse(raw).isEmpty()) {
                        warnings.add(where + " can't be " + raw + ", so it follows the preset; use "
                                + option.get().type().describe());
                        continue;
                    }
                    overrides.put(id, raw);
                }
            }
        } else if (config.contains(OPTIONS)) {
            warnings.add("options isn't a table, so it is left out");
        }
        return new Contents(preset, Map.copyOf(overrides), Set.copyOf(listed), List.copyOf(warnings));
    }

    /**
     * Writes a file, replacing it whole: the preset, every registered option, and the overrides of
     * options no loaded mod registers. Makes its directory if need be.
     *
     * @param file the file
     * @param preset the preset
     * @param overrides every override, by option id
     * @throws UncheckedIOException if it can't be written
     */
    public static void write(Path file, Preset preset, Map<ResourceLocation, Object> overrides) {
        try {
            Path dir = file.toAbsolutePath().getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            new TomlWriter().write(toConfig(preset, overrides), file, WritingMode.REPLACE_ATOMIC);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (WritingException e) {
            throw new UncheckedIOException(new IOException(e.getMessage(), e));
        }
    }

    /** The file's contents as TOML, with a comment on the preset and on each option. */
    static CommentedConfig toConfig(Preset preset, Map<ResourceLocation, Object> overrides) {
        CommentedConfig config = TomlFormat.newConfig(LinkedHashMap::new);
        config.set(PRESET, preset.name());
        config.setComment(
                PRESET,
                comment("The world-wide difficulty preset, NORMAL or HARDCORE, for every mod that uses SS13"
                        + " Framework. It sets the default of each option under [options]; an option"
                        + " given a value there instead of \"" + PresetOption.FOLLOW_PRESET + "\" keeps"
                        + " it under either preset. /ss13 preset shows every option and switches the"
                        + " preset. A world's serverconfig/" + Presets.FILE_NAME + ", if it has one, is"
                        + " read instead of config/" + Presets.FILE_NAME + "."));
        Map<ResourceLocation, Object> unknown = new LinkedHashMap<>(overrides);
        for (PresetOption<?> option : Presets.options()) {
            Object value = unknown.remove(option.id());
            put(config, option.id(), value == null ? PresetOption.FOLLOW_PRESET : value, optionComment(option));
        }
        for (Map.Entry<ResourceLocation, Object> entry : unknown.entrySet()) {
            put(config, entry.getKey(), entry.getValue(), "No loaded mod registers this option; it is kept as it is.");
        }
        return config;
    }

    private static void put(CommentedConfig config, ResourceLocation id, Object value, String comment) {
        List<String> path = List.of(OPTIONS, id.getNamespace(), id.getPath());
        config.set(path, value);
        config.setComment(path, comment);
    }

    private static <T> String optionComment(PresetOption<T> option) {
        List<String> lines = new ArrayList<>(wrap(option.description()));
        lines.add(option.type().describe() + ", or \"" + PresetOption.FOLLOW_PRESET + "\" to follow the preset.");
        lines.add("Normal: " + option.format(option.defaultFor(Preset.NORMAL)) + ". Hardcore: "
                + option.format(option.defaultFor(Preset.HARDCORE)) + ".");
        return String.join("\n", lines);
    }

    private static String comment(String text) {
        return String.join("\n", wrap(text));
    }

    /** Text split into comment lines of at most about 90 characters, at spaces. */
    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > 90) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        lines.add(line.toString());
        return lines;
    }
}
