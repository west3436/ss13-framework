package io.github.west3436.ss13.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

/** Where the claim check looks in a box, without the game. */
class ClaimCheckTest {
    /** A box across four chunks: x -8 to 23, z 4 to 19, y 60 to 70. */
    private static final BoundingBox BOX = new BoundingBox(-8, 60, 4, 23, 70, 19);

    @Test
    void looks_only_inside_the_box_and_the_chunk() {
        BlockPos spawn = new BlockPos(1_000, 64, -1_000);
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = 0; cz <= 1; cz++) {
                Set<BlockPos> samples = ClaimCheck.samples(BOX, cx, cz, spawn);
                assertTrue(samples.size() >= 1 && samples.size() <= 6, "samples: " + samples);
                for (BlockPos p : samples) {
                    assertTrue(BOX.isInside(p), p + " is outside the box");
                    assertEquals(cx, p.getX() >> 4, p + " is outside chunk x " + cx);
                    assertEquals(cz, p.getZ() >> 4, p + " is outside chunk z " + cz);
                }
            }
        }
    }

    @Test
    void looks_at_each_column_corner_and_the_centre() {
        // The part of the box in chunk (0, 0): x 0 to 15, z 4 to 15.
        Set<BlockPos> samples = ClaimCheck.samples(BOX, 0, 0, new BlockPos(7, 64, 9));
        assertTrue(samples.contains(new BlockPos(0, 60, 4)), "samples: " + samples);
        assertTrue(samples.contains(new BlockPos(15, 60, 15)), "samples: " + samples);
        assertTrue(samples.contains(new BlockPos(0, 70, 15)), "samples: " + samples);
        assertTrue(samples.contains(new BlockPos(15, 70, 4)), "samples: " + samples);
        assertTrue(samples.contains(new BlockPos(7, 65, 9)), "samples: " + samples);
    }

    @Test
    void looks_at_the_position_nearest_the_spawn() {
        // Spawn protection is a square around the spawn: the nearest position is the one to ask.
        BlockPos spawn = new BlockPos(40, 64, -30);
        Set<BlockPos> samples = ClaimCheck.samples(BOX, 1, 0, spawn);
        assertTrue(samples.contains(new BlockPos(23, 65, 4)), "samples: " + samples);
    }

    @Test
    void a_one_block_box_is_looked_at_once() {
        BlockPos only = new BlockPos(5, 64, 5);
        assertEquals(Set.of(only), ClaimCheck.samples(new BoundingBox(only), 0, 0, new BlockPos(100, 64, 100)));
    }
}
