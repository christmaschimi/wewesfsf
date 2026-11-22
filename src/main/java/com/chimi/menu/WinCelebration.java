package com.chimi.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Full-screen win animation when an auction ends with a winner.
 * A winning bid of Config.aucCosmicAt (500M) or more gets "COSMIC WIN" (rainbow rays, confetti, orbiting prize icons).
 * Smaller wins get "AUCTION WON" (gold version of the same show).
 */
public final class WinCelebration {
    private WinCelebration() {}

    private static final long TOTAL_MS = 9000;
    private static final String[] STAR = {
            "...#...",
            "...#...",
            "#######",
            ".#####.",
            "..###..",
            ".##.##.",
            ".#...#."};

    private static EconomyCore.Auction celebrated;
    private static long start;
    private static String name = "", prize = "";
    private static double amount;
    private static boolean cosmic, running;
    private static ItemStack stack = ItemStack.EMPTY;

    // ---------- control ----------
    /** Called every frame: starts the show once when the given auction has ended with a winner. */
    public static void maybeStart(EconomyCore.Auction a) {
        if (a == null || !a.isOver() || celebrated == a) return;
        celebrated = a;
        var w = a.winner();
        if (w.isEmpty()) return;
        begin(w.get().getKey(), w.get().getValue(), a.prize, ChimiMenuClient.auctionStack);
    }

    public static void begin(String winner, double bid, String prizeName, ItemStack item) {
        name = winner;
        amount = bid;
        prize = prizeName;
        stack = item == null ? ItemStack.EMPTY : item.copy();
        cosmic = bid >= Config.I.aucCosmicAt;
        start = System.currentTimeMillis();
        running = true;
    }

    /** Menu test buttons. */
    public static void test(boolean cosmicWin) {
        ItemStack s = ChimiMenuClient.auctionStack;
        if (s.isEmpty()) s = new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE);
        begin("Alex", cosmicWin ? Math.max(Config.I.aucCosmicAt, 750_000_000) * 1.0 : 25_000_000,
                ChimiMenuClient.auction != null ? ChimiMenuClient.auction.prize : "Golden Apple", s);
    }

    public static boolean active() { return running && System.currentTimeMillis() - start < TOTAL_MS; }

    // ---------- helpers ----------
    private static int rgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c;
        float r, g, b;
        switch ((int) (h * 6) % 6) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return ((int) ((r + m) * 255) << 16) | ((int) ((g + m) * 255) << 8) | (int) ((b + m) * 255);
    }

    private static int argb(int rgb, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static double frac(double v) { return v - Math.floor(v); }

    private static void star(GuiGraphicsExtractor g, int x, int y, int px, int color) {
        for (int r = 0; r < STAR.length; r++)
            for (int c = 0; c < STAR[r].length(); c++)
                if (STAR[r].charAt(c) == '#') g.fill(x + c * px, y + r * px, x + (c + 1) * px, y + (r + 1) * px, color);
    }

    /** Text centred on cx, scaled, each letter coloured by the colour function (rainbow or gold shimmer). */
    private static void shimmerText(GuiGraphicsExtractor g, Minecraft mc, String text, float cx, float y, float scale, double t, float alpha, boolean rainbow) {
        int total = HudText.width(mc, text);
        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().scale(scale, scale);
        int x = -total / 2;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int col;
            if (rainbow) col = rgb((float) (t * 0.5 + i * 0.07), 0.75f, 1f);
            else {
                float k = (float) (0.5 + 0.5 * Math.sin(t * 3 + i * 0.6));
                col = mix(0xFFC21A, 0xFFFFFF, k);
            }
            HudText.draw(g, mc, ch, x + 1, 1, argb(0x000000, alpha * 0.6f)); // shadow
            HudText.draw(g, mc, ch, x, 0, argb(col, alpha));
            x += HudText.width(mc, ch);
        }
        g.pose().popMatrix();
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static void centered(GuiGraphicsExtractor g, Minecraft mc, String s, float cx, float y, float scale, int argb) {
        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().scale(scale, scale);
        int w = HudText.width(mc, s);
        HudText.draw(g, mc, s, -w / 2 + 1, 1, (argb & 0xFF000000) == 0 ? argb : ((argb >>> 24) / 2 << 24));
        HudText.draw(g, mc, s, -w / 2, 0, argb);
        g.pose().popMatrix();
    }

    // ---------- drawing ----------
    public static void draw(GuiGraphicsExtractor g) {
        if (!active()) { running = false; return; }
        Minecraft mc = Minecraft.getInstance();
        long e = System.currentTimeMillis() - start;
        double t = e / 1000.0;
        float fade = (float) Math.min(1.0, Math.min(e / 350.0, (TOTAL_MS - e) / 900.0));
        int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
        int pw = Math.min(W - 12, 300), ph = Math.min(H - 12, 180);
        int px = (W - pw) / 2, py = (H - ph) / 2;
        float cx = px + pw / 2f, cy = py + ph / 2f;

        // opening flash across the whole screen
        if (e < 450) g.fill(0, 0, W, H, argb(cosmic ? 0xFFFFFF : 0xFFE9A0, (1 - e / 450f) * 0.55f));

        // panel
        g.fill(px, py, px + pw, py + ph, argb(0x070A1A, 0.92f * fade));

        // rays from the middle, ending exactly at the panel edge
        int rays = cosmic ? 30 : 18;
        double hw = pw / 2.0 - 2, hh = ph / 2.0 - 2;
        for (int i = 0; i < rays; i++) {
            double ang = t * (cosmic ? 0.35 : 0.22) + i * (Math.PI * 2 / rays);
            double c = Math.cos(ang), s = Math.sin(ang);
            double len = Math.min(Math.abs(c) < 1e-6 ? 1e9 : hw / Math.abs(c), Math.abs(s) < 1e-6 ? 1e9 : hh / Math.abs(s));
            int col = cosmic ? rgb((float) (i / (double) rays + t * 0.25), 0.7f, 1f) : (i % 2 == 0 ? 0xFFC21A : 0xFFE9A0);
            float pulse = (float) (0.55 + 0.45 * Math.sin(t * 4 + i));
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().rotate((float) ang);
            g.fill(26, -2, (int) len, 2, argb(col, 0.13f * fade * pulse));
            g.fill(26, -1, (int) len, 1, argb(col, 0.55f * fade * pulse));
            g.pose().popMatrix();
        }

        // confetti
        int confetti = cosmic ? 80 : 34;
        for (int i = 0; i < confetti; i++) {
            double sp = 0.12 + 0.18 * frac(i * 0.7548776662);
            double fx = frac(i * 0.6180339887 + Math.sin(t * 1.3 + i) * 0.015);
            double fy = frac(t * sp + i * 0.37);
            int x = px + 2 + (int) (fx * (pw - 6)), y = py + 2 + (int) (fy * (ph - 6));
            int sz = i % 3 == 0 ? 3 : 2;
            int col = cosmic ? rgb((float) (i * 0.11), 0.8f, 1f) : (i % 2 == 0 ? 0xFFC21A : 0xFFFFFF);
            g.fill(x, y, x + sz, y + sz, argb(col, 0.9f * fade));
        }

        // prize icons orbiting the winner tile
        if (fade > 0.5f) {
            int n = cosmic ? 8 : 5;
            for (int i = 0; i < n; i++) {
                double ang = t * 0.7 + i * (Math.PI * 2 / n);
                double rx = pw * 0.36, ry = ph * 0.30;
                int ix = (int) (cx + Math.cos(ang) * rx) - 8, iy = (int) (cy + Math.sin(ang) * ry + Math.sin(t * 3 + i) * 3) - 8;
                if (!stack.isEmpty()) g.item(stack, ix, iy);
                else star(g, ix + 4, iy + 4, 1, argb(0xFFD84A, fade));
            }
        }

        // frame (rainbow for cosmic, gold otherwise)
        int segs = 16;
        for (int i = 0; i < segs; i++) {
            int col = cosmic ? rgb((float) (i / (double) segs + t * 0.3), 0.8f, 1f) : mix(0xB8860B, 0xFFE27A, (float) (0.5 + 0.5 * Math.sin(t * 2 + i)));
            int x0 = px + i * pw / segs, x1 = px + (i + 1) * pw / segs;
            int y0 = py + i * ph / segs, y1 = py + (i + 1) * ph / segs;
            int c = argb(col, fade);
            g.fill(x0, py, x1, py + 2, c);
            g.fill(x0, py + ph - 2, x1, py + ph, c);
            g.fill(px, y0, px + 2, y1, c);
            g.fill(px + pw - 2, y0, px + pw, y1, c);
        }

        // title with pop-in
        String title = cosmic ? "COSMIC WIN" : "AUCTION WON";
        float pop = (float) (e < 500 ? 0.4 + 0.6 * (e / 500.0) + Math.sin(e / 500.0 * Math.PI) * 0.25 : 1.0);
        float ts = 2f * pop;
        float ty = py + 10;
        shimmerText(g, mc, title, cx, ty, ts, t, fade, cosmic);
        int tw = (int) (HudText.width(mc, title) * ts);
        int starPx = 2, starSize = 7 * starPx;
        for (int k = 0; k < 2; k++) {
            int sx = (int) (k == 0 ? cx - tw / 2f - starSize - 6 : cx + tw / 2f + 6);
            double bob = Math.sin(t * 4 + k * 2) * 1.5;
            int col = cosmic ? rgb((float) (t * 0.5 + k * 0.5), 0.7f, 1f) : 0xFFD84A;
            star(g, sx, (int) (ty + 2 + bob), starPx, argb(col, fade));
        }

        // winner tile: the prize (or first letter of the name) in a glowing frame
        int tile = cosmic ? 48 : 42;
        int tx = (int) cx - tile / 2, tyy = (int) cy - tile / 2 - 6;
        float glow = (float) (0.5 + 0.5 * Math.sin(t * 5));
        int frame = cosmic ? rgb((float) (t * 0.6), 0.85f, 1f) : mix(0xFFC21A, 0xFFF3B0, glow);
        g.fill(tx - 5, tyy - 5, tx + tile + 5, tyy + tile + 5, argb(frame, (0.15f + 0.15f * glow) * fade));
        g.fill(tx - 3, tyy - 3, tx + tile + 3, tyy + tile + 3, argb(frame, fade));
        g.fill(tx, tyy, tx + tile, tyy + tile, argb(0x0B0F24, fade));
        if (!stack.isEmpty()) {
            float k = (tile - 6) / 16f;
            g.pose().pushMatrix();
            g.pose().translate(tx + 3, tyy + 3 + (float) Math.sin(t * 3) * 1.5f);
            g.pose().scale(k, k);
            g.item(stack, 0, 0);
            g.pose().popMatrix();
        } else {
            String ini = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            centered(g, mc, ini, cx, tyy + tile / 2f - 12, 3f, argb(0xFFFFFF, fade));
        }

        // names and amounts
        float by = tyy + tile + 9;
        centered(g, mc, name, cx, by, 1.6f, argb(cosmic ? rgb((float) (t * 0.4), 0.35f, 1f) : 0xFFFFFF, fade));
        centered(g, mc, "won " + prize, cx, by + 16, 1f, argb(0xBFD4FF, fade));
        centered(g, mc, "with a bid of $" + EconomyCore.format(amount), cx, by + 28, 1f, argb(0xFFD84A, fade));
    }
}
