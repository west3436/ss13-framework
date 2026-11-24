package io.github.west3436.ss13.testkit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The sounds a server played at a position during GameTests: a test counts what a block did from
 * where the block is. Tests in other places can be playing at the same time, so each asks only about
 * the sounds at its own block. Use it on the server thread, where GameTests run.
 */
public final class SoundLog {
    /** How far from a block's centre a sound still counts as that block's: its neighbours' don't. */
    private static final double NEAR = 0.5;

    private static final List<Played> SEEN = new ArrayList<>();
    private static boolean listening;

    /**
     * A sound the server played.
     *
     * @param id the sound event's id
     * @param channel its slider
     * @param volume its volume
     * @param pitch its pitch
     * @param at where it played
     */
    public record Played(ResourceLocation id, SoundSource channel, float volume, float pitch, Vec3 at) {
        /**
         * Whether it is this event, played at volume 1 and pitch 1 on the Blocks slider.
         *
         * @param event the sound event
         * @return whether it is that event, played that way
         */
        public boolean is(DeferredHolder<SoundEvent, SoundEvent> event) {
            return is(event.getId());
        }

        /**
         * Whether it is the sound event with this id, played at volume 1 and pitch 1 on the Blocks
         * slider.
         *
         * @param event the sound event's id
         * @return whether it is that event, played that way
         */
        public boolean is(ResourceLocation event) {
            return id.equals(event) && channel == SoundSource.BLOCKS && volume == 1 && pitch == 1;
        }
    }

    private SoundLog() {}

    /** Starts recording, if nothing has yet. Call it before the first sound a test looks for. */
    public static void listen() {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((PlayLevelSoundEvent.AtPosition e) -> {
                if (!e.getLevel().isClientSide() && e.getSound() != null) {
                    SEEN.add(new Played(
                            e.getSound().value().getLocation(),
                            e.getSource(),
                            e.getNewVolume(),
                            e.getNewPitch(),
                            e.getPosition()));
                }
            });
        }
    }

    /**
     * Takes the sounds recorded at a block: they aren't reported again.
     *
     * @param at the block's centre, such as {@code Vec3.atCenterOf(pos)}
     * @return the sounds played there, oldest first
     */
    public static List<Played> take(Vec3 at) {
        List<Played> out = new ArrayList<>();
        for (Iterator<Played> it = SEEN.iterator(); it.hasNext(); ) {
            Played sound = it.next();
            if (at.distanceTo(sound.at()) <= NEAR) {
                out.add(sound);
                it.remove();
            }
        }
        return out;
    }
}
