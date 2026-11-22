package com.chimi.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Corner styles for HUD boxes (chosen on the Style page): straight, pixel, rounded, bubble, pill, chamfer. */
public final class HudShape {
    private HudShape() {}

    public static final String[] SHAPES = {"Straight", "Pixel", "Rounded", "Bubble", "Pill", "Chamfer"};

    private static int kind() {
        for (int i = 0; i < SHAPES.length; i++) if (SHAPES[i].equals(Config.I.hudShape)) return i;
        return 0;
    }

    private static int radius(int kind, int h) {
        return switch (kind) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 5;
            case 4 -> Math.max(1, Math.min(8, h / 2));
            case 5 -> 3;
            default -> 0;
        };
    }

    /** How far row d (counted from the top or bottom edge) is pulled in from the side. */
    private static int inset(int kind, int r, int d) {
        if (d >= r) return 0;
        return switch (kind) {
            case 1 -> 1;
            case 5 -> r - d;
            default -> {
                double dy = r - (d + 0.5);
                yield (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            }
        };
    }

    private static void shape(GuiGraphicsExtractor g, int x, int y, int w, int h, int kind, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { g.fill(x, y, x + w, y + h, color); return; }
        for (int d = 0; d < r; d++) {
            int in = inset(kind, r, d);
            g.fill(x + in, y + d, x + w - in, y + d + 1, color);
            g.fill(x + in, y + h - 1 - d, x + w - in, y + h - d, color);
        }
        if (h > 2 * r) g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** A filled HUD background in the chosen shape. */
    public static void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        int k = kind();
        shape(g, x, y, w, h, k, radius(k, h), color);
    }

    /** A bordered HUD box in the chosen shape. */
    public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int border, int fill) {
        int k = kind();
        int r = radius(k, h);
        shape(g, x, y, w, h, k, r, border);
        shape(g, x + 1, y + 1, w - 2, h - 2, k, Math.max(0, r - 1), fill);
    }
}
