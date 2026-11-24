package io.github.west3436.ss13.area;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.ss13.api.area.Area;
import io.github.west3436.ss13.api.area.AreaClaim;
import io.github.west3436.ss13.api.area.AreaStore;
import io.github.west3436.ss13.api.area.Areas;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * {@code /ss13 area}: the operators' view of areas, and a way to draw stored ones without
 * blueprints. Needs permission level 2. Everything is in the level the command runs in.
 *
 * <ul>
 *   <li>{@code /ss13 area} or {@code /ss13 area at [<pos>]}: the area at a block, here by default,
 *       with every provider's answer there, the winner first
 *   <li>{@code /ss13 area list}: the level's stored areas, with their blocks and bounds
 *   <li>{@code /ss13 area define <id> <name>}, {@code rename <id> <name>}, {@code remove <id>}
 *   <li>{@code /ss13 area assign <id> <from> <to>}: puts a box of blocks in a stored area
 *   <li>{@code /ss13 area clear <from> <to>}: takes a box of blocks out of every stored area
 * </ul>
 *
 * <p>An id typed without a namespace is in {@code ss13_framework}'s. The messages are plain English,
 * as this is an operators' debugging tool.
 *
 * <p>Registered under the shared {@code /ss13} root; Brigadier merges it with the other {@code
 * /ss13} subcommands, so the root's requirement must match theirs: permission level 2.
 */
public final class AreaCommand {
    /** The permission level {@code /ss13} needs. */
    static final int PERMISSION = 2;

    private AreaCommand() {}

    /**
     * Adds {@code /ss13 area} to a dispatcher.
     *
     * @param dispatcher the server's command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        SuggestionProvider<CommandSourceStack> storedIds = (c, builder) -> SharedSuggestionProvider.suggestResource(
                store(c).areas().stream().map(Area::id), builder);
        var area = Commands.literal("area")
                .requires(s -> s.hasPermission(PERMISSION))
                .executes(
                        c -> at(c.getSource(), BlockPos.containing(c.getSource().getPosition())))
                .then(Commands.literal("at")
                        .executes(c -> at(
                                c.getSource(), BlockPos.containing(c.getSource().getPosition())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> at(c.getSource(), BlockPosArgument.getBlockPos(c, "pos")))))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("define")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> change(c, s -> {
                                            Area a = s.define(id(c), StringArgumentType.getString(c, "name"));
                                            return "Defined " + describe(a);
                                        })))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(storedIds)
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> change(c, s -> {
                                            Area a = s.rename(id(c), StringArgumentType.getString(c, "name"));
                                            return "Renamed it " + describe(a);
                                        })))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(storedIds)
                                .executes(c -> change(c, s -> {
                                    if (!s.remove(id(c))) {
                                        throw new IllegalArgumentException("There is no area " + id(c) + " here");
                                    }
                                    return "Removed " + id(c);
                                }))))
                .then(Commands.literal("assign")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(storedIds)
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(c -> change(c, s -> {
                                                    int n = s.assign(id(c), box(c));
                                                    Area a = s.get(id(c)).orElseThrow();
                                                    return "Put " + n + " blocks in " + describe(a);
                                                }))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(c -> change(
                                                c, s -> "Took " + s.clear(box(c)) + " blocks out of their areas")))));
        dispatcher.register(Commands.literal("ss13")
                .requires(s -> s.hasPermission(PERMISSION))
                .then(area));
    }

    /** Shows every provider's answer at a block, the winner first. */
    private static int at(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        List<AreaClaim> claims = Areas.claims(level, pos);
        String where = pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " in "
                + level.dimension().location();
        if (claims.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No area at " + where), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(describe(claims.getFirst().area()) + " at " + where), false);
        for (int i = 0; i < claims.size(); i++) {
            AreaClaim claim = claims.get(i);
            String line = (i == 0 ? "  answered by " : "  under it: " + describe(claim.area()) + ", from ")
                    + claim.provider() + " (priority " + claim.priority() + ")";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return claims.size();
    }

    /** Lists the level's stored areas. */
    private static int list(CommandSourceStack source) {
        AreaStore store = Areas.store(source.getLevel());
        List<Area> areas =
                store.areas().stream().sorted(Comparator.comparing(Area::id)).toList();
        String level = source.getLevel().dimension().location().toString();
        source.sendSuccess(() -> Component.literal(areas.size() + " stored areas in " + level), false);
        for (Area area : areas) {
            int size = store.size(area.id());
            String where = store.bounds(area.id())
                    .map(b -> ", " + b.minX() + " " + b.minY() + " " + b.minZ() + " to " + b.maxX() + " " + b.maxY()
                            + " " + b.maxZ())
                    .orElse("");
            source.sendSuccess(() -> Component.literal("  " + describe(area) + ": " + size + " blocks" + where), false);
        }
        return areas.size();
    }

    /** Runs a change to the level's store, reporting what it did or why it couldn't. */
    private static int change(CommandContext<CommandSourceStack> c, Function<AreaStore, String> change) {
        CommandSourceStack source = c.getSource();
        try {
            String done = change.apply(Areas.store(source.getLevel()));
            source.sendSuccess(() -> Component.literal(done), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static AreaStore store(CommandContext<CommandSourceStack> c) {
        return Areas.store(c.getSource().getLevel());
    }

    /** The id argument, in {@code ss13_framework}'s namespace if it was typed without one. */
    private static ResourceLocation id(CommandContext<CommandSourceStack> c) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "id");
        String typed = c.getInput();
        return id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) && !typed.contains(id.toString())
                ? ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, id.getPath())
                : id;
    }

    private static BoundingBox box(CommandContext<CommandSourceStack> c) {
        return BoundingBox.fromCorners(BlockPosArgument.getBlockPos(c, "from"), BlockPosArgument.getBlockPos(c, "to"));
    }

    private static String describe(Area area) {
        return area.name() + " [" + area.id() + "]";
    }
}
