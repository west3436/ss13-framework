package io.github.west3436.ss13.api.area;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.ss13.api.area.Areas.Registration;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

/**
 * How providers' answers resolve: the highest priority with an answer wins, and among equal
 * priorities the id that sorts last. The level is never looked at by these providers, so none is
 * made.
 */
class AreasTest {
    private static final BlockPos POS = new BlockPos(3, 64, -7);

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static Area area(String path) {
        return new Area(id(path), path);
    }

    private static Registration reg(String id, int priority, Optional<Area> answer) {
        return new Registration(id(id), priority, (level, pos) -> answer);
    }

    private static Optional<Area> resolve(Registration... registrations) {
        return Areas.resolve(Areas.sorted(List.of(registrations)), null, POS);
    }

    private static List<AreaClaim> claims(Registration... registrations) {
        return Areas.claims(Areas.sorted(List.of(registrations)), null, POS);
    }

    @Test
    void no_provider_gives_no_area() {
        assertEquals(Optional.empty(), resolve());
        assertEquals(List.of(), claims());
    }

    @Test
    void a_room_wins_over_the_region_it_is_in() {
        Area ship = area("ship");
        Area bridge = area("bridge");
        Registration region = reg("ships", Areas.REGION, Optional.of(ship));
        Registration room = reg("rooms", Areas.ROOM, Optional.of(bridge));
        assertEquals(Optional.of(bridge), resolve(region, room));
        assertEquals(Optional.of(bridge), resolve(room, region));
    }

    @Test
    void a_provider_with_no_answer_lets_the_next_one_answer() {
        Area ship = area("ship");
        assertEquals(
                Optional.of(ship),
                resolve(reg("ships", Areas.REGION, Optional.of(ship)), reg("rooms", Areas.ROOM, Optional.empty())));
    }

    @Test
    void equal_priorities_go_to_the_id_that_sorts_last() {
        Registration a = reg("a", Areas.ROOM, Optional.of(area("from_a")));
        Registration b = reg("b", Areas.ROOM, Optional.of(area("from_b")));
        assertEquals(Optional.of(area("from_b")), resolve(a, b));
        assertEquals(Optional.of(area("from_b")), resolve(b, a));
    }

    @Test
    void claims_list_every_answer_winner_first() {
        Area ship = area("ship");
        Area bridge = area("bridge");
        List<AreaClaim> claims = claims(
                reg("ships", Areas.REGION, Optional.of(ship)),
                reg("nothing", Areas.ROOM + 5, Optional.empty()),
                reg("rooms", Areas.ROOM, Optional.of(bridge)));
        assertEquals(
                List.of(new AreaClaim(id("rooms"), Areas.ROOM, bridge), new AreaClaim(id("ships"), Areas.REGION, ship)),
                claims);
    }

    @Test
    void a_null_answer_is_refused() {
        Registration bad = new Registration(id("bad"), Areas.ROOM, (level, pos) -> null);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> resolve(bad));
        assertTrue(e.getMessage().contains("test:bad"), e.getMessage());
    }

    @Test
    void registering_an_id_twice_is_refused() {
        ResourceLocation id = id("areas_test_twice");
        assertFalse(Areas.isRegistered(id));
        Areas.register(id, Areas.ROOM, (level, pos) -> Optional.empty());
        assertTrue(Areas.isRegistered(id));
        assertThrows(
                IllegalArgumentException.class,
                () -> Areas.register(id, Areas.REGION, (level, pos) -> Optional.empty()));
    }

    @Test
    void a_move_maps_positions_as_a_structure_placed_about_a_pivot() {
        BlockPos pivot = new BlockPos(10, 64, 10);
        BlockPos target = new BlockPos(100, 70, -50);
        assertEquals(target, Areas.map(pivot, pivot, target, Rotation.CLOCKWISE_90));
        assertEquals(new BlockPos(102, 71, -47), Areas.map(new BlockPos(12, 65, 13), pivot, target, Rotation.NONE));
        // A quarter turn clockwise takes +x to +z and +z to -x.
        assertEquals(
                new BlockPos(97, 71, -48), Areas.map(new BlockPos(12, 65, 13), pivot, target, Rotation.CLOCKWISE_90));
    }

    @Test
    void the_stored_provider_is_the_frameworks() {
        assertEquals("ss13_framework:stored", Areas.STORED.toString());
    }
}
