package io.github.west3436.ss13.api.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The tool tags' ids, which other mods' data names; they must never change. */
class ToolTagsTest {
    @Test
    void the_tool_tags_are_the_common_ones() {
        assertEquals("c:tools/multitool", ToolTags.MULTITOOLS.location().toString());
        assertEquals("c:tools/wrench", ToolTags.WRENCHES.location().toString());
        assertEquals("c:tools/crowbar", ToolTags.CROWBARS.location().toString());
        assertEquals("c:tools/screwdriver", ToolTags.SCREWDRIVERS.location().toString());
        assertEquals("c:tools/wire_cutter", ToolTags.WIRE_CUTTERS.location().toString());
        assertEquals("c:tools/welder", ToolTags.WELDERS.location().toString());
        assertEquals(
                "ss13_framework:welding_protection",
                ToolTags.WELDING_PROTECTION.location().toString());
    }
}
