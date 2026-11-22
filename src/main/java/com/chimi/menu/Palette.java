package com.chimi.menu;

/** Shared colour list for the block outline, fill, crosshair and Spotify text. "Rainbow" fades through every hue. */
public final class Palette {
    private Palette() {}

    public static final String[] NAMES = {
            "White", "Black", "Red", "Orange", "Yellow", "Lime", "Green", "Aqua",
            "Blue", "Purple", "Pink", "Magenta", "Gray", "Brown", "Rainbow"};
    private static final int[] COLORS = {
            0xFFFFFFFF, 0xFF000000, 0xFFFF3B3B, 0xFFFF9A2E, 0xFFFFE14D, 0xFF9DFF3B, 0xFF2EC24A, 0xFF3FE8E8,
            0xFF3B6BFF, 0xFF9B4DFF, 0xFFFF7FC8, 0xFFFF2BD6, 0xFF8A8A8A, 0xFF8B5A2B, 0};

    /** The crosshair uses a shorter list. */
    public static final String[] CROSSHAIR = {
            "White", "Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Pink", "Black", "Rainbow"};

    /** Marker colour a swatch button uses to mean "draw the animated rainbow". */
    public static final int RAINBOW_SWATCH = 1;

    public static int resolve(String name) {
        if ("Rainbow".equals(name)) return rainbow();
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return COLORS[i];
        return 0xFFFFFFFF;
    }

    public static int swatchColor(String name) {
        return "Rainbow".equals(name) ? RAINBOW_SWATCH : resolve(name);
    }

    /** Smoothly fading rainbow, one full cycle every 6 seconds. */
    public static int rainbow() {
        float hue = (System.currentTimeMillis() % 6000L) / 6000f;
        return hsv(hue, 0.85f, 1f);
    }

    public static int hsv(float h, float s, float v) {
        float r = 0, g = 0, b = 0;
        float f = (h - (float) Math.floor(h)) * 6f;
        int i = (int) f;
        float p = v * (1 - s), q = v * (1 - s * (f - i)), t = v * (1 - s * (1 - (f - i)));
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }
}
