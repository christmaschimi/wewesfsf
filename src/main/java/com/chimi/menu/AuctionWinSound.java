package com.chimi.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays the auction-win sound on repeat for as long as the win pop-up (WinCelebration) is on screen.
 * Driven from the client tick, so it keeps looping even if the HUD is hidden (F1), and stops the moment the pop-up ends.
 * The sound file is assets/chimismenu/sounds/auction_win.ogg (registered in assets/chimismenu/sounds.json).
 */
public final class AuctionWinSound {
    private AuctionWinSound() {}

    private static final SoundEvent EVENT =
            SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ChimiMenuClient.ID, "auction_win"));
    /** Length of the clip; a new copy starts each time this much time has passed. */
    private static final long CLIP_MS = 4880;

    private static final List<SimpleSoundInstance> playing = new ArrayList<>();
    private static long lastPlay;
    private static boolean wasActive;

    /** Call every client tick with WinCelebration.active(). */
    public static void update(boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (active) {
            long now = System.currentTimeMillis();
            if (!wasActive || now - lastPlay >= CLIP_MS) {
                SimpleSoundInstance s = SimpleSoundInstance.forUI(EVENT, 1.0f, 1.0f);
                mc.getSoundManager().play(s);
                playing.add(s);
                lastPlay = now;
            }
            wasActive = true;
        } else if (wasActive) {
            stopAll();
        }
    }

    /** Cuts the sound off immediately (win pop-up finished, or the player left the world). */
    public static void stopAll() {
        Minecraft mc = Minecraft.getInstance();
        for (SimpleSoundInstance s : playing) mc.getSoundManager().stop(s);
        playing.clear();
        wasActive = false;
    }
}
