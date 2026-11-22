package com.chimi.menu;

import net.minecraft.client.Minecraft;

/** Where each HUD box sits and how big it is. 0 = Profit, 1 = Spotify, 2 = Auction. */
public final class HudLayout {
    private HudLayout() {}

    public static final String[] NAMES = {"Profit", "Spotify", "Auction"};

    public static int getX(int id) { Config c = Config.I; return id == 0 ? c.profitX : id == 1 ? c.spotX : c.aucHudX; }
    public static int getY(int id) { Config c = Config.I; return id == 0 ? c.profitY : id == 1 ? c.spotY : c.aucHudY; }
    public static int getScale(int id) { Config c = Config.I; return id == 0 ? c.profitScale : id == 1 ? c.spotScale : c.aucHudScale; }

    public static void setX(int id, int v) { Config c = Config.I; if (id == 0) c.profitX = v; else if (id == 1) c.spotX = v; else c.aucHudX = v; }
    public static void setY(int id, int v) { Config c = Config.I; if (id == 0) c.profitY = v; else if (id == 1) c.spotY = v; else c.aucHudY = v; }
    public static void setScale(int id, int v) {
        v = Math.max(5, Math.min(30, v));
        Config c = Config.I;
        if (id == 0) c.profitScale = v; else if (id == 1) c.spotScale = v; else c.aucHudScale = v;
    }

    public static float scale(int id) { return Math.max(5, Math.min(30, getScale(id))) / 10f; }

    public static void reset(int id) {
        setX(id, -1);
        setY(id, id == 2 ? 6 : -1);
        setScale(id, 10);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(Math.max(lo, hi), v)); }

    /**
     * Top-left corner (screen pixels) of the box. w and h are the box's size before scaling.
     * assumeProfit: pretend the profit HUD is showing (the editor does this so Spotify sits where it will in game).
     */
    public static int[] pos(int id, int w, int h, boolean assumeProfit) {
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.I;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        float s = scale(id);
        int ws = Math.round(w * s), hs = Math.round(h * s);
        int x, y;
        if (id == 2) {
            x = c.aucHudX >= 0 ? c.aucHudX : (sw - ws) / 2;
            y = c.aucHudY;
        } else if (getX(id) >= 0 && getY(id) >= 0) {
            x = getX(id);
            y = getY(id);
        } else {
            int corner = id == 0 ? c.hudCorner : c.spotifyCorner;
            x = (corner == 1 || corner == 3) ? sw - ws - 4 : 4;
            y = (corner == 2 || corner == 3) ? sh - hs - 4 : 4;
            if (id == 1 && corner == c.hudCorner && c.hudEnabled && (assumeProfit || ChimiMenuClient.session.isActive())) {
                int step = Math.round(16 * scale(0));
                y += (corner == 2 || corner == 3) ? -step : step;
            }
        }
        return new int[]{clamp(x, 0, sw - ws), clamp(y, 0, sh - hs)};
    }
}
