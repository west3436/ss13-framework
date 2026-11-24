package io.github.west3436.ss13.api.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Asking the registered movers. The registry is global, so this registers its movers once, with
 * ids no other test uses, and every question is about positions only they answer for. The movers
 * never read the level, so none is made.
 */
class BlockMoversTest {
    private static final BlockPos MOVING = new BlockPos(1_000_003, 64, 0);
    private static final Vec3 IN_PLOT = new Vec3(2_000_000.5, 64, 0.5);
    private static final Vec3 ON_SHIP = new Vec3(10.5, 70, 10.5);

    static {
        BlockMovers.register(id("z_test_ship"), new BlockMover() {
            @Override
            public boolean isMoving(ServerLevel level, BlockPos pos) {
                return pos.equals(MOVING);
            }

            @Override
            public Vec3 shownAt(Level level, Vec3 point) {
                return point.equals(IN_PLOT) ? ON_SHIP : point;
            }
        });
        BlockMovers.register(id("a_test_idle"), (level, pos) -> false);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ss13_framework_test", path);
    }

    @Test
    void a_block_is_moving_if_any_mover_says_so() {
        assertTrue(BlockMovers.isMoving(null, MOVING));
        assertFalse(BlockMovers.isMoving(null, MOVING.above()));
    }

    @Test
    void a_point_is_shown_where_a_mover_with_a_say_shows_it() {
        assertEquals(ON_SHIP, BlockMovers.shownAt(null, IN_PLOT), "the mover that has a say");
        Vec3 elsewhere = new Vec3(3.5, 64, 3.5);
        assertEquals(elsewhere, BlockMovers.shownAt(null, elsewhere), "no mover has a say");
    }

    @Test
    void an_id_is_registered_once() {
        assertTrue(BlockMovers.isRegistered(id("z_test_ship")));
        assertFalse(BlockMovers.isRegistered(id("never")));
        assertThrows(
                IllegalArgumentException.class, () -> BlockMovers.register(id("a_test_idle"), (level, pos) -> true));
    }
}
