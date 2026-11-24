/**
 * SS13-style machine construction: frames, circuit boards, stock parts and their four tiers.
 *
 * <p>A mod makes a machine buildable in three steps: it registers a board item of its own and
 * says what it builds with {@link io.github.west3436.ss13.api.construction.MachineBoards#register}
 * and a {@link io.github.west3436.ss13.api.construction.MachineBoard}; the machine's block entity
 * implements {@link io.github.west3436.ss13.api.construction.PartHolder}; and the machine sets its
 * stats from its {@link io.github.west3436.ss13.api.construction.InstalledParts}. Parts are found
 * by the item tags in {@link io.github.west3436.ss13.api.construction.PartTags}, and tools by those
 * in {@link io.github.west3436.ss13.api.construction.ConstructionTags}. The machine keeps its plain
 * crafting recipe as well; one placed from its item holds tier-1 parts.
 */
package io.github.west3436.ss13.api.construction;
