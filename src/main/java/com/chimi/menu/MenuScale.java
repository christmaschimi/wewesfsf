package com.chimi.menu;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Menu size. While one of this mod's menus is open the game's GUI scale is changed (base scale x menu size %),
 * and it is put back when the menu closes. Your normal GUI scale and HUD are not affected.
 */
public final class MenuScale {
    private MenuScale() {}

    public static final int[] OPTIONS = {70, 85, 100, 125, 150};
    private static Integer base;

    /** Call at the start of init(). Returns true if the scale was changed; the screen must then rebuild itself and stop. */
    public static boolean apply(Screen s) {
        Window w = Minecraft.getInstance().getWindow();
        int current = (int) Math.round(w.getGuiScale());
        if (base == null) base = current;
        int target = Math.max(1, (int) Math.round(base * Config.I.menuScale / 100.0));
        // never scale so far that the menu no longer fits the window (it needs about 260 GUI pixels of height)
        int fit = Math.max(1, w.getHeight() / 260);
        if (Config.I.menuScale > 100) target = Math.min(target, Math.max(base, fit));
        if (current == target) return false;
        w.setGuiScale(target);
        s.width = w.getGuiScaledWidth();
        s.height = w.getGuiScaledHeight();
        return true;
    }

    /** Call from removed(). */
    public static void restore() {
        if (base == null) return;
        Window w = Minecraft.getInstance().getWindow();
        if ((int) Math.round(w.getGuiScale()) != base) w.setGuiScale(base);
        base = null;
    }
}
