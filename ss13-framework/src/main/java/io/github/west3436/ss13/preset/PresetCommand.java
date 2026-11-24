package io.github.west3436.ss13.preset;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.logging.LogUtils;
import io.github.west3436.ss13.api.preset.Preset;
import io.github.west3436.ss13.api.preset.PresetOption;
import io.github.west3436.ss13.api.preset.Presets;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * {@code /ss13 preset}: the world-wide preset and every mod's options, each with its value and
 * whether that comes from the preset or an override. {@code normal} and {@code hardcore} switch,
 * {@code override <option> <value>} and {@code clear <option>} set and remove an override. Every
 * change is saved to the world's preset file at once ({@link WorldPresets}). Needs permission level
 * 2.
 *
 * <p>An option is named by its id, such as {@code zas:combustion_forms_zones}, or by its key alone
 * where no other mod's option has that key. Under a mod's alias, {@code /<namespace> preset} (see
 * {@link Presets#addCommandAlias}), a key alone names that mod's option first; the alias has no
 * permission check of its own, so the mod's root decides.
 */
final class PresetCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The framework's command root. */
    static final String ROOT = "ss13";

    private static final String KEY = "commands.ss13_framework.preset.";

    private static final DynamicCommandExceptionType UNKNOWN_OPTION =
            new DynamicCommandExceptionType(id -> tr("error.unknown_option", "Unknown preset option: %s", id));
    private static final Dynamic2CommandExceptionType AMBIGUOUS_OPTION = new Dynamic2CommandExceptionType((key, ids) ->
            tr("error.ambiguous_option", "%s names more than one option: %s; give its mod's too", key, ids));
    private static final Dynamic3CommandExceptionType INVALID_VALUE = new Dynamic3CommandExceptionType(
            (id, value, valid) -> tr("error.invalid_value", "Not a value for %s: %s; use %s", id, value, valid));
    private static final SimpleCommandExceptionType NO_WORLD =
            new SimpleCommandExceptionType(tr("error.no_world", "No world's preset is loaded"));

    private PresetCommand() {}

    /**
     * Registers {@code /ss13 preset}, and {@code preset} under each alias's root.
     *
     * @param dispatcher the server's command dispatcher
     */
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(ROOT).then(node(null).requires(source -> source.hasPermission(2))));
        for (String namespace : Presets.commandAliases()) {
            CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild(namespace);
            if (root == null) {
                LOGGER.warn("No /{} command to offer /{} preset under", namespace, namespace);
            } else if (root.getChild("preset") != null) {
                LOGGER.warn("/{} has a preset command of its own, so /{} preset isn't added", namespace, ROOT);
            } else {
                root.addChild(node(namespace).build());
            }
        }
    }

    /** The {@code preset} node, under {@code /ss13} or under an alias's root. */
    private static LiteralArgumentBuilder<CommandSourceStack> node(@Nullable String alias) {
        SuggestionProvider<CommandSourceStack> options =
                (ctx, builder) -> SharedSuggestionProvider.suggest(optionNames(alias), builder);
        SuggestionProvider<CommandSourceStack> values = (ctx, builder) -> {
            Optional<PresetOption<?>> option = resolve(ResourceLocationArgument.getId(ctx, "option"), alias);
            return SharedSuggestionProvider.suggest(
                    option.map(PresetCommand::suggestions).orElse(List.of()), builder);
        };
        return Commands.literal("preset")
                .executes(ctx -> report(ctx.getSource()))
                .then(Commands.literal("normal").executes(ctx -> switchTo(ctx.getSource(), Preset.NORMAL)))
                .then(Commands.literal("hardcore").executes(ctx -> switchTo(ctx.getSource(), Preset.HARDCORE)))
                .then(Commands.literal("override")
                        .then(Commands.argument("option", ResourceLocationArgument.id())
                                .suggests(options)
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests(values)
                                        .executes(ctx -> override(ctx, alias)))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("option", ResourceLocationArgument.id())
                                .suggests(options)
                                .executes(ctx -> clear(ctx, alias))));
    }

    // ---- Subcommands ----------------------------------------------------------------------

    /** The preset, the file, and every option with its source. */
    private static int report(CommandSourceStack source) throws CommandSyntaxException {
        WorldPresets world = world();
        for (Component line : reportLines(source, world)) {
            source.sendSuccess(() -> line, false);
        }
        return Presets.overridden();
    }

    private static int switchTo(CommandSourceStack source, Preset preset) throws CommandSyntaxException {
        WorldPresets world = world();
        if (Presets.active() == preset) {
            source.sendSuccess(() -> tr("unchanged", "The preset is already %s", name(preset)), false);
            return report(source);
        }
        Optional<WorldPresets.Saved> saved = save(source, () -> world.switchAndSave(preset));
        int kept = Presets.overridden();
        source.sendSuccess(
                () -> tr("switched", "Switched every mod to the %s preset; overrides kept: %s", name(preset), kept),
                true);
        copied(source, world, saved);
        return report(source);
    }

    private static int override(CommandContext<CommandSourceStack> ctx, @Nullable String alias)
            throws CommandSyntaxException {
        WorldPresets world = world();
        return override(ctx.getSource(), world, option(ctx, alias), StringArgumentType.getString(ctx, "value"));
    }

    private static <T> int override(CommandSourceStack source, WorldPresets world, PresetOption<T> option, String text)
            throws CommandSyntaxException {
        T value = option.type()
                .parse(text)
                .orElseThrow(() -> INVALID_VALUE.create(
                        option.id().toString(), text, option.type().describe()));
        Optional<WorldPresets.Saved> saved = save(
                source, () -> world.overrideAndSave(option.id(), option.type().write(value)));
        source.sendSuccess(
                () -> tr(
                        "overridden",
                        "Overrode %s with %s under either preset",
                        option.id().toString(),
                        option.format(value)),
                true);
        source.sendSuccess(() -> line(option), false);
        copied(source, world, saved);
        source.sendSuccess(() -> fileLine(source, world), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx, @Nullable String alias)
            throws CommandSyntaxException {
        WorldPresets world = world();
        CommandSourceStack source = ctx.getSource();
        PresetOption<?> option = option(ctx, alias);
        if (option.source() != PresetOption.Source.OVERRIDE) {
            source.sendSuccess(
                    () -> tr(
                            "not_overridden",
                            "%s isn't overridden; it follows the preset",
                            option.id().toString()),
                    false);
            source.sendSuccess(() -> line(option), false);
            return 0;
        }
        Optional<WorldPresets.Saved> saved = save(source, () -> world.clearAndSave(option.id()));
        source.sendSuccess(
                () -> tr(
                        "cleared",
                        "%s follows the preset again, now %s",
                        option.id().toString(),
                        name(Presets.active())),
                true);
        source.sendSuccess(() -> line(option), false);
        copied(source, world, saved);
        source.sendSuccess(() -> fileLine(source, world), false);
        return 1;
    }

    /** Runs a change's save; if the file can't be written, says so; the change applies all the same. */
    private static Optional<WorldPresets.Saved> save(CommandSourceStack source, Supplier<WorldPresets.Saved> change) {
        try {
            return Optional.of(change.get());
        } catch (UncheckedIOException e) {
            source.sendFailure(tr(
                    "error.unwritten",
                    "Couldn't write the preset file (%s); the change applies until the server stops",
                    String.valueOf(e.getCause().getMessage())));
            return Optional.empty();
        }
    }

    /** Says so when a save made the world's own copy of the preset file. */
    private static void copied(CommandSourceStack source, WorldPresets world, Optional<WorldPresets.Saved> saved) {
        saved.filter(WorldPresets.Saved::newCopy)
                .ifPresent(s -> source.sendSuccess(
                        () -> tr(
                                "copied",
                                "This world had no preset file of its own, so it now has one: %s. The shared %s,"
                                        + " which other worlds read, is unchanged",
                                shown(source, s.file()),
                                shown(source, world.sharedFile())),
                        true));
    }

    // ---- Output ------------------------------------------------------------------------

    /** The report: the preset, the file, and each option. */
    static List<Component> reportLines(CommandSourceStack source, WorldPresets world) {
        List<Component> out = new ArrayList<>();
        out.add(tr(
                "header",
                "Preset: %s, %s of %s options overridden",
                name(Presets.active()),
                Presets.overridden(),
                Presets.options().size()));
        out.add(fileLine(source, world));
        for (PresetOption<?> option : Presets.options()) {
            out.add(line(option));
        }
        if (Presets.options().isEmpty()) {
            out.add(tr("no_options", "  No mod registers an option"));
        }
        return out;
    }

    /** One option: its id, its effective value, its source and its two defaults. */
    static <T> Component line(PresetOption<T> option) {
        return tr(
                "option",
                "  %s = %s [%s] (Normal %s, Hardcore %s)",
                option.id().toString(),
                option.format(option.get()),
                option.source().id(),
                option.format(option.defaultFor(Preset.NORMAL)),
                option.format(option.defaultFor(Preset.HARDCORE)));
    }

    private static Component fileLine(CommandSourceStack source, WorldPresets world) {
        return tr("file", "  Preset file: %s", shown(source, world.file()));
    }

    /** A path relative to the server's directory when it is inside it, with forward slashes. */
    private static String shown(CommandSourceStack source, Path path) {
        Path shown = path.toAbsolutePath().normalize();
        Path root = source.getServer().getServerDirectory().toAbsolutePath().normalize();
        if (shown.startsWith(root)) {
            shown = root.relativize(shown);
        }
        return shown.toString().replace('\\', '/');
    }

    private static Component name(Preset preset) {
        return Component.translatableWithFallback(
                "ss13_framework.preset." + preset.id(), preset == Preset.HARDCORE ? "Hardcore" : "Normal");
    }

    private static Component tr(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(KEY + key, fallback, args);
    }

    // ---- Naming options ----------------------------------------------------------------

    private static PresetOption<?> option(CommandContext<CommandSourceStack> ctx, @Nullable String alias)
            throws CommandSyntaxException {
        ResourceLocation typed = ResourceLocationArgument.getId(ctx, "option");
        List<PresetOption<?>> found = candidates(typed, alias);
        if (found.isEmpty()) {
            throw UNKNOWN_OPTION.create(isBare(typed) ? typed.getPath() : typed.toString());
        }
        if (found.size() > 1) {
            throw AMBIGUOUS_OPTION.create(
                    typed.getPath(),
                    String.join(", ", found.stream().map(o -> o.id().toString()).toList()));
        }
        return found.getFirst();
    }

    /** The option a typed name stands for, as {@link #option} finds it, or empty. */
    static Optional<PresetOption<?>> resolve(ResourceLocation typed, @Nullable String alias) {
        List<PresetOption<?>> found = candidates(typed, alias);
        return found.size() == 1 ? Optional.of(found.getFirst()) : Optional.empty();
    }

    /**
     * The options a typed name may stand for: the one with that id; for a key alone, the alias's
     * mod's option with that key, else every option with that key.
     */
    private static List<PresetOption<?>> candidates(ResourceLocation typed, @Nullable String alias) {
        if (!isBare(typed)) {
            return Presets.option(typed).stream().toList();
        }
        String key = typed.getPath();
        if (alias != null && ResourceLocation.tryBuild(alias, key) instanceof ResourceLocation own) {
            Optional<PresetOption<?>> option = Presets.option(own);
            if (option.isPresent()) {
                return List.of(option.get());
            }
        }
        return Presets.options().stream()
                .filter(o -> o.id().getPath().equals(key))
                .toList();
    }

    /**
     * Whether a typed name had no namespace. The argument reads one without a namespace as {@code
     * minecraft:}, which no option has.
     */
    private static boolean isBare(ResourceLocation typed) {
        return typed.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE);
    }

    /** Every option's id, and under an alias the keys of that mod's options as well. */
    private static List<String> optionNames(@Nullable String alias) {
        Set<String> names = new LinkedHashSet<>();
        for (PresetOption<?> option : Presets.options()) {
            if (option.id().getNamespace().equals(alias)) {
                names.add(option.id().getPath());
            }
        }
        for (PresetOption<?> option : Presets.options()) {
            names.add(option.id().toString());
        }
        return List.copyOf(names);
    }

    private static <T> List<String> suggestions(PresetOption<T> option) {
        Set<String> values = new LinkedHashSet<>(option.type().suggestions());
        values.add(option.format(option.defaultFor(Preset.NORMAL)));
        values.add(option.format(option.defaultFor(Preset.HARDCORE)));
        return List.copyOf(values);
    }

    private static WorldPresets world() throws CommandSyntaxException {
        return PresetService.world().orElseThrow(NO_WORLD::create);
    }
}
