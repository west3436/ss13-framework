package io.github.west3436.ss13.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.ss13.api.preset.Preset;
import io.github.west3436.ss13.api.preset.PresetOption;
import io.github.west3436.ss13.api.preset.PresetStore;
import io.github.west3436.ss13.api.preset.PresetType;
import io.github.west3436.ss13.api.preset.Presets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A world's preset file: where it is read from and saved to, and that the choice survives a
 * restart, made here by reading the files again with a new {@link WorldPresets}. Two test mods,
 * {@code test_alpha} and {@code test_beta}, register an option each.
 */
class WorldPresetsTest {
    private static final PresetOption<Boolean> FLAG = Presets.register(
            ResourceLocation.fromNamespaceAndPath("test_alpha", "flag"),
            PresetType.BOOLEAN,
            false,
            true,
            "A test mod's switch");
    private static final PresetOption<Double> RATE = Presets.register(
            ResourceLocation.fromNamespaceAndPath("test_beta", "rate"),
            PresetType.number(0, 10, "per second"),
            1.0,
            0.5,
            "Another test mod's rate");

    @TempDir
    Path dir;

    private Path shared;
    private Path worldCopy;
    private PresetStore previous;

    @BeforeEach
    void paths() {
        shared = dir.resolve("config").resolve(Presets.FILE_NAME);
        worldCopy = dir.resolve("saves/World/serverconfig").resolve(Presets.FILE_NAME);
    }

    @AfterEach
    void restore() {
        if (previous != null) {
            Presets.install(previous);
        }
    }

    /** A world's presets, loaded and installed as the framework's store. */
    private WorldPresets start(boolean dedicated) {
        WorldPresets world = new WorldPresets(shared, worldCopy, dedicated);
        world.load();
        PresetStore was = Presets.install(world);
        if (previous == null) {
            previous = was;
        }
        return world;
    }

    @Test
    void a_new_server_writes_the_shared_file_with_every_option() throws IOException {
        WorldPresets world = start(true);
        assertEquals(shared, world.file());
        String text = Files.readString(shared);
        assertTrue(text.contains("preset = \"NORMAL\""), text);
        assertTrue(text.contains("flag = \"preset\""), text);
        assertTrue(text.contains("rate = \"preset\""), text);
        assertTrue(text.contains("Normal: 1. Hardcore: 0.5."), text);
        assertFalse(Files.exists(worldCopy));
    }

    @Test
    void two_mods_options_switch_together_and_survive_a_restart() {
        WorldPresets world = start(true);
        Presets.switchTo(Preset.HARDCORE);
        assertTrue(FLAG.get());
        assertEquals(0.5, RATE.get());
        RATE.setOverride(2.0);

        world.unload();
        assertFalse(Presets.isLoaded());
        assertFalse(FLAG.get(), "no world: the Normal default");

        start(true);
        assertEquals(Preset.HARDCORE, Presets.active());
        assertTrue(FLAG.get());
        assertEquals(2.0, RATE.get());
        assertEquals(PresetOption.Source.OVERRIDE, RATE.source());
    }

    @Test
    void singleplayer_saves_to_the_worlds_own_copy_and_leaves_the_shared_file() throws IOException {
        WorldPresets world = start(false);
        String sharedBefore = Files.readString(shared);
        assertFalse(world.hasOwnCopy());

        WorldPresets.Saved saved = world.switchAndSave(Preset.HARDCORE);
        assertTrue(saved.newCopy());
        assertEquals(worldCopy, saved.file());
        assertTrue(world.hasOwnCopy());
        assertEquals(sharedBefore, Files.readString(shared), "the shared file");

        WorldPresets.Saved again = world.overrideAndSave(FLAG.id(), false);
        assertFalse(again.newCopy());
        assertEquals(worldCopy, again.file());

        // A restart reads the world's copy; another world still reads the shared file.
        world.unload();
        WorldPresets reloaded = start(false);
        assertEquals(worldCopy, reloaded.file());
        assertEquals(Preset.HARDCORE, Presets.active());
        assertFalse(FLAG.get(), "the override");
        assertEquals(0.5, RATE.get());

        WorldPresets other = new WorldPresets(shared, dir.resolve("saves/Other/serverconfig/x.toml"), false);
        other.load();
        assertEquals(Preset.NORMAL, other.preset());
    }

    @Test
    void a_dedicated_server_saves_in_place_even_to_the_shared_file() {
        WorldPresets world = start(true);
        WorldPresets.Saved saved = world.switchAndSave(Preset.HARDCORE);
        assertFalse(saved.newCopy());
        assertEquals(shared, saved.file());
        assertFalse(Files.exists(worldCopy));
    }

    @Test
    void where_a_save_goes() {
        assertEquals(WorldPresets.Target.IN_PLACE, WorldPresets.target(true, shared, worldCopy));
        assertEquals(WorldPresets.Target.IN_PLACE, WorldPresets.target(true, worldCopy, worldCopy));
        assertEquals(WorldPresets.Target.NEW_COPY, WorldPresets.target(false, shared, worldCopy));
        assertEquals(WorldPresets.Target.IN_PLACE, WorldPresets.target(false, worldCopy, worldCopy));
    }

    @Test
    void a_world_with_its_own_copy_reads_it_first() throws IOException {
        PresetFile.write(shared, Preset.NORMAL, Map.of());
        PresetFile.write(worldCopy, Preset.HARDCORE, Map.of(RATE.id(), 3.0));
        WorldPresets world = start(false);
        assertEquals(worldCopy, world.file());
        assertEquals(Preset.HARDCORE, Presets.active());
        assertEquals(3.0, RATE.get());
    }

    @Test
    void overrides_of_options_no_mod_registers_are_kept() throws IOException {
        ResourceLocation gone = ResourceLocation.fromNamespaceAndPath("removed_mod", "setting");
        PresetFile.write(shared, Preset.NORMAL, Map.of(gone, 7L));
        WorldPresets world = start(true);
        world.switchAndSave(Preset.HARDCORE);

        PresetFile.Contents contents = PresetFile.read(shared);
        assertEquals(Preset.HARDCORE, contents.preset());
        assertEquals(7, ((Number) contents.overrides().get(gone)).intValue());
        assertTrue(contents.listed().contains(FLAG.id()));
    }

    @Test
    void a_value_an_option_refuses_is_dropped_with_a_warning() throws IOException {
        Files.createDirectories(shared.getParent());
        Files.writeString(shared, """
                preset = "hardcore"
                [options.test_beta]
                rate = 99.0
                [options.test_alpha]
                flag = "true"
                """);
        PresetFile.Contents contents = PresetFile.read(shared);
        assertEquals(Preset.HARDCORE, contents.preset());
        assertEquals(Map.of(FLAG.id(), "true"), contents.overrides());
        assertEquals(1, contents.warnings().size(), contents.warnings().toString());
        assertTrue(contents.warnings().getFirst().contains("options.test_beta.rate"));

        Files.writeString(shared, "preset = \"easy\"\n");
        PresetFile.Contents bad = PresetFile.read(shared);
        assertEquals(Preset.NORMAL, bad.preset());
        assertEquals(1, bad.warnings().size());
    }

    @Test
    void a_hand_edit_applies_and_a_broken_one_changes_nothing() throws IOException {
        WorldPresets world = start(true);
        assertEquals(Preset.NORMAL, Presets.active());

        String text = Files.readString(shared).replace("preset = \"NORMAL\"", "preset = \"HARDCORE\"");
        Files.writeString(shared, text);
        touch(shared, 10_000);
        world.pickUpEdits();
        assertEquals(Preset.HARDCORE, Presets.active());

        Files.writeString(shared, "preset = [not toml");
        touch(shared, 20_000);
        world.pickUpEdits();
        assertEquals(Preset.HARDCORE, Presets.active(), "a broken file leaves the preset as it was");
    }

    @Test
    void nothing_changes_before_a_load() {
        WorldPresets world = new WorldPresets(shared, worldCopy, true);
        assertFalse(world.isLoaded());
        assertEquals(Optional.empty(), world.override(FLAG.id()));
        assertThrows(IllegalStateException.class, () -> world.switchAndSave(Preset.HARDCORE));
    }

    /** Moves a file's time on, so a change made within the clock's resolution is seen. */
    private static void touch(Path file, long millis) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + millis));
    }
}
