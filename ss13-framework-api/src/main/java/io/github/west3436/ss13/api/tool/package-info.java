/**
 * The SS13 tools' contracts. The framework holds the tools (multitool, wrench, crowbar,
 * screwdriver, wirecutters and welder); each mod supplies what they do to its blocks through
 * capabilities: {@link io.github.west3436.ss13.api.tool.Linkable} for the multitool's links,
 * {@link io.github.west3436.ss13.api.tool.Wrenchable} for the wrench, {@link
 * io.github.west3436.ss13.api.tool.Pryable} for the crowbar. {@link
 * io.github.west3436.ss13.api.tool.WeldingTool} is how a mod welds with any welder, and {@link
 * io.github.west3436.ss13.api.tool.ToolTags} names the tools' item tags.
 */
package io.github.west3436.ss13.api.tool;
