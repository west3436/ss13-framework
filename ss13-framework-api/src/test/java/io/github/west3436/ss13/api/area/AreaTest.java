package io.github.west3436.ss13.api.area;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** An area's id and name. */
class AreaTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "engineering");

    @Test
    void a_name_must_not_be_blank() {
        assertThrows(IllegalArgumentException.class, () -> new Area(ID, ""));
        assertThrows(IllegalArgumentException.class, () -> new Area(ID, "   "));
        assertThrows(NullPointerException.class, () -> new Area(ID, null));
        assertThrows(NullPointerException.class, () -> new Area(null, "Engineering"));
    }

    @Test
    void a_name_has_at_most_64_characters() {
        new Area(ID, "x".repeat(Area.MAX_NAME_LENGTH));
        assertThrows(IllegalArgumentException.class, () -> new Area(ID, "x".repeat(Area.MAX_NAME_LENGTH + 1)));
    }

    @Test
    void renaming_keeps_the_id() {
        Area area = new Area(ID, "Engineering");
        Area renamed = area.withName("Engine Room");
        assertEquals(ID, renamed.id());
        assertEquals("Engine Room", renamed.name());
        assertNotEquals(area, renamed);
    }
}
