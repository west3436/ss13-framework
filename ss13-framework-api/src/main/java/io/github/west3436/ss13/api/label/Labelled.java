package io.github.west3436.ss13.api.label;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A device a player can label, so several of one kind can be told apart where a controller lists
 * them or a tool links them.
 *
 * <p>Only a block entity implements it. The framework's device base ({@code
 * io.github.west3436.ss13.device.DeviceBlockEntity}, in the {@code ss13_framework} mod) keeps the
 * label, saves it and sends it to clients, so a device extending it takes labels by naming this
 * interface and nothing more. A label is plain text that {@link Labels#clean} has been through, at
 * most {@link Labels#MAX_LENGTH} characters, and the empty string is no label.
 *
 * <p>It is the game's own {@link Nameable}, so what reads a block's custom name reads the label:
 * Jade shows it as the block's name.
 */
public interface Labelled extends Nameable {
    /**
     * The label.
     *
     * @return the label, or the empty string if it has none
     */
    String label();

    /**
     * Sets the label to what {@link Labels#clean} makes of some text, or clears it if that is
     * nothing. Server side. What allows a player to set one is the caller's to check.
     *
     * @param text what a player typed; null clears it
     * @return whether the label changed
     */
    boolean setLabel(@Nullable String text);

    /**
     * What kind of device it is: its block's name.
     *
     * @return the block's name
     */
    default Component kind() {
        return ((BlockEntity) this).getBlockState().getBlock().getName();
    }

    /**
     * Its label, or its kind without one.
     *
     * @return the name to show
     */
    @Override
    default Component getName() {
        return label().isEmpty() ? kind() : Component.literal(label());
    }

    /**
     * Its label as a custom name.
     *
     * @return the label, or null without one
     */
    @Override
    default @Nullable Component getCustomName() {
        return label().isEmpty() ? null : Component.literal(label());
    }

    /**
     * What its window's title bar shows, which the window reads its label back from ({@link
     * Labels#title}).
     *
     * @return the title
     */
    @Override
    default Component getDisplayName() {
        return Labels.title(label(), kind());
    }
}
