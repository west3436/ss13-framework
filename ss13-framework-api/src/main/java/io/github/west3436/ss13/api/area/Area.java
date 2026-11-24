package io.github.west3436.ss13.api.area;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * A named region of a level, like SS13's {@code /area}: a station's Engineering, a ship's bridge, a
 * whole shuttle. Every block is in at most one area at a time, the one {@link Areas#at} answers.
 * Areas are not ZAS³'s zones: a zone is the air a room holds and changes whenever a wall does, an
 * area is a name a builder or a mod gave a set of blocks, and stays until they change it.
 *
 * <p>What hangs off an area is keyed by its {@link #id()}, which stays the same when the area is
 * renamed and when its blocks move: the APC that powers it and its power channels, its lights, the
 * area air controller that watches it. Two areas are the same area if their ids are equal; the name
 * is only what players read.
 *
 * <p>Immutable, and may be used from any thread.
 *
 * @param id the area's id, in the namespace of the mod that made it, unique in its level
 * @param name what players read, such as {@code "Engineering"}: not blank, at most {@link
 *     #MAX_NAME_LENGTH} characters
 */
public record Area(ResourceLocation id, String name) {
    /** The longest name an area may have, in characters. */
    public static final int MAX_NAME_LENGTH = 64;

    /**
     * Checks the id and the name.
     *
     * @throws NullPointerException if either is null
     * @throws IllegalArgumentException if the name is blank or longer than {@link #MAX_NAME_LENGTH}
     */
    public Area {
        Objects.requireNonNull(id, "id");
        checkName(name);
    }

    /**
     * The same area with another name.
     *
     * @param name the new name: not blank, at most {@link #MAX_NAME_LENGTH} characters
     * @return the area, with this one's id
     * @throws IllegalArgumentException if the name is blank or too long
     */
    public Area withName(String name) {
        return new Area(id, name);
    }

    /**
     * Checks an area's name.
     *
     * @param name the name
     * @return the name
     * @throws NullPointerException if it is null
     * @throws IllegalArgumentException if it is blank or longer than {@link #MAX_NAME_LENGTH}
     */
    public static String checkName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("an area's name can't be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "an area's name has at most " + MAX_NAME_LENGTH + " characters, not " + name.length());
        }
        return name;
    }
}
