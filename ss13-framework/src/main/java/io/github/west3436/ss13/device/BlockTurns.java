package io.github.west3436.ss13.device;

import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * How a device's block turns with what it is part of: a structure placed a quarter turn round, a
 * Create contraption, or a ship whose blocks are put back into the world the way the ship was
 * turned. The game turns a block with {@link BlockState#rotate} and {@link BlockState#mirror}, which
 * leave a state as it is unless its block says otherwise; a block that didn't kept facing the way it
 * faced before, so a vent came back facing away from its pipe.
 *
 * <p>These turn every way a state faces: each direction property, an axis, and for a block that is
 * {@link Sides}, a property for each face, such as a pipe's arms. A block whose state says more than
 * that, such as a filter's side port, adds its own turn on top. A block calls them from its own
 * {@code rotate} and {@code mirror}.
 */
public final class BlockTurns {
    /**
     * A block whose state has a boolean property for each face it may join on, such as a pipe's
     * arms. {@link BlockTurns} moves each one's value to the face it turns to.
     */
    @FunctionalInterface
    public interface Sides {
        /**
         * Its property for each face. A face missing from the map, or whose property the state
         * lacks, is left alone.
         *
         * @return the properties, by face
         */
        Map<Direction, BooleanProperty> sideProperties();
    }

    private BlockTurns() {}

    /**
     * A state turned about the vertical.
     *
     * @param state the state
     * @param rotation the turn
     * @return the state facing each way turned by {@code rotation}
     */
    public static BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return state;
        }
        BlockState out = turn(state, rotation::rotate);
        if (rotation != Rotation.CLOCKWISE_180) {
            out = swapAxis(out);
        }
        return out;
    }

    /**
     * A state mirrored.
     *
     * @param state the state
     * @param mirror the mirror
     * @return the state facing each way mirrored by {@code mirror}
     */
    public static BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : turn(state, mirror::mirror);
    }

    private static BlockState turn(BlockState state, UnaryOperator<Direction> way) {
        BlockState out = state;
        for (Property<?> p : state.getProperties()) {
            if (p instanceof DirectionProperty facing) {
                Direction to = way.apply(state.getValue(facing));
                if (facing.getPossibleValues().contains(to)) {
                    out = out.setValue(facing, to);
                }
            }
        }
        if (state.getBlock() instanceof Sides sides) {
            Map<Direction, BooleanProperty> faces = sides.sideProperties();
            for (Map.Entry<Direction, BooleanProperty> face : faces.entrySet()) {
                BooleanProperty to = faces.get(way.apply(face.getKey()));
                if (to != null && state.hasProperty(face.getValue()) && out.hasProperty(to)) {
                    out = out.setValue(to, state.getValue(face.getValue()));
                }
            }
        }
        return out;
    }

    /** A quarter turn takes an x axis to z and z to x. */
    private static BlockState swapAxis(BlockState state) {
        BlockState out = state;
        for (Property<?> p : state.getProperties()) {
            if (p.getValueClass() == Direction.Axis.class) {
                @SuppressWarnings("unchecked")
                EnumProperty<Direction.Axis> axis = (EnumProperty<Direction.Axis>) p;
                Direction.Axis to = switch (state.getValue(axis)) {
                    case X -> Direction.Axis.Z;
                    case Z -> Direction.Axis.X;
                    case Y -> Direction.Axis.Y;
                };
                if (axis.getPossibleValues().contains(to)) {
                    out = out.setValue(axis, to);
                }
            }
        }
        return out;
    }
}
