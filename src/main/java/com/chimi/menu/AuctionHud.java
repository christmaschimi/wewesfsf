package com.chimi.menu;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Map;

/** The in-game auction panel (top of the screen by default). Scale and position come from Config. */
public final class AuctionHud {
    private AuctionHud() {}

    public record Theme(String name, int accent, int bg, int panel, int border) {}
    public static final Theme[] THEMES = {
            new Theme("Aqua", 0xFF5CE8FF, 0xFF0B1426, 0xFF101C36, 0xFF3FC8E8),
            new Theme("Amethyst", 0xFFB48CFF, 0xFF14102E, 0xFF1D1A4A, 0xFF7B63E0),
            new Theme("Ruby", 0xFFFF6B7D, 0xFF1E0E14, 0xFF2C1520, 0xFFE0506A),
            new Theme("Gold", 0xFFFFD166, 0xFF24180A, 0xFF33240F, 0xFFE0A93A),
            new Theme("Mono", 0xFFF2F2F2, 0xFF101010, 0xFF1A1A1A, 0xFF8A8A8A)};

    public static Theme theme() {
        return THEMES[Math.floorMod(Config.I.aucTheme, THEMES.length)];
    }

    // test popups from the menu
    private static String testAlert = "";
    private static long testAlertUntil;
    private static boolean testWin;

    public static void testBid() { testAlert = "TestPlayer paid $1.00M"; testWin = false; testAlertUntil = System.currentTimeMillis() + 3000; }
    public static void testWin() { WinCelebration.test(false); }
    public static void testCosmic() { WinCelebration.test(true); }

    private static String fmtTime(long s) {
        return (s >= 3600 ? (s / 3600) + ":" : "") + String.format("%02d:%02d", (s % 3600) / 60, s % 60);
    }

    private static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int border, int fill) {
        HudShape.box(g, x, y, w, h, border, fill);
    }

    public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        drawPanel(g, mc);
        WinCelebration.maybeStart(ChimiMenuClient.auction);
        WinCelebration.draw(g);
    }

    private static void drawPanel(GuiGraphicsExtractor g, Minecraft mc) {
        var a = ChimiMenuClient.auction;
        boolean pending = ChimiMenuClient.auctionPending();
        boolean test = System.currentTimeMillis() < testAlertUntil;
        if (a == null && !pending && !test) return;

        Theme th = theme();
        float scale = HudLayout.scale(2);
        int w = 150;
        int rows = a == null ? 0 : Math.min(Math.max(1, Config.I.aucBoard), 5);
        int h = 36 + rows * 10 + (a != null && !a.leaderboard().isEmpty() ? 0 : 0);
        int[] hp = HudLayout.pos(2, w, h, false);
        int x = Math.round(hp[0] / scale), y = Math.round(hp[1] / scale);

        g.pose().pushMatrix();
        g.pose().scale(scale, scale);

        if (a != null || pending) {
            box(g, x, y, w, h, th.border(), th.panel());
            g.fill(x + 1, y + 1, x + w - 1, y + 3, th.accent());
            if (a == null) {
                HudText.draw(g, mc, "Auction starts in " + ChimiMenuClient.pendingSecondsLeft() + "s", x + 6, y + 8, th.accent());
                HudText.draw(g, mc, ChimiMenuClient.pendingPrize(), x + 6, y + 20, 0xFFFFFFFF);
            } else {
                if (!ChimiMenuClient.auctionStack.isEmpty()) g.item(ChimiMenuClient.auctionStack, x + 4, y + 6);
                int tx = ChimiMenuClient.auctionStack.isEmpty() ? x + 6 : x + 24;
                HudText.draw(g, mc, a.prize, tx, y + 7, 0xFFFFFFFF);
                String t = a.isOver() ? "ENDED" : fmtTime(a.secondsLeft());
                boolean snipe = !a.isOver() && a.secondsLeft() <= 10;
                HudText.draw(g, mc, t, tx, y + 18, a.isOver() ? 0xFF5CE08A : snipe ? 0xFFFF5555 : th.accent());
                float frac = a.isOver() || a.durationSeconds <= 0 ? 0f : Math.min(1f, (float) a.secondsLeft() / a.durationSeconds);
                g.fill(x + 4, y + 30, x + w - 4, y + 33, 0xFF000000);
                g.fill(x + 4, y + 30, x + 4 + Math.round((w - 8) * frac), y + 33, snipe ? 0xFFFF5555 : th.accent());
                List<Map.Entry<String, Double>> lb = a.leaderboard();
                for (int i = 0; i < rows; i++) {
                    int ry = y + 37 + i * 10;
                    if (i < lb.size()) {
                        var e = lb.get(i);
                        HudText.draw(g, mc, "#" + (i + 1) + " " + e.getKey(), x + 6, ry, i == 0 ? th.accent() : 0xFFDDDDDD);
                        String amt = EconomyCore.format(e.getValue());
                        HudText.draw(g, mc, amt, x + w - 6 - HudText.width(mc, amt), ry, i == 0 ? th.accent() : 0xFFDDDDDD);
                    } else {
                        HudText.draw(g, mc, "#" + (i + 1) + " -", x + 6, ry, 0xFF777777);
                    }
                }
                // bid alert popup under the panel
                if (Config.I.aucAlerts && System.currentTimeMillis() - a.lastBidAt < 3000 && !a.lastBidPlayer.isEmpty()) {
                    String s = "* " + a.lastBidPlayer + "  $" + EconomyCore.format(a.lastBidAmount) + " *";
                    box(g, x, y + h + 3, w, 14, th.border(), th.bg());
                    HudText.draw(g, mc, s, x + (w - HudText.width(mc, s)) / 2, y + h + 6, 0xFFFFFFFF);
                }
                if (a.isOver()) {
                    String s = a.winner().map(e -> "Winner: " + e.getKey() + " (" + EconomyCore.format(e.getValue()) + ")").orElse("No bids");
                    box(g, x, y + h + 3, w, 14, 0xFF5CE08A, th.bg());
                    HudText.draw(g, mc, s, x + (w - HudText.width(mc, s)) / 2, y + h + 6, 0xFF5CE08A);
                }
            }
        }
        if (test) {
            int ty = (a != null || pending) ? y + h + 22 : y;
            box(g, x, ty, w, 14, testWin ? 0xFF5CE08A : th.border(), th.bg());
            HudText.draw(g, mc, testAlert, x + (w - HudText.width(mc, testAlert)) / 2, ty + 3, testWin ? 0xFF5CE08A : 0xFFFFFFFF);
        }
        g.pose().popMatrix();
    }
}
