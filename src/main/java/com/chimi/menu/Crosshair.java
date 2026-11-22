package com.chimi.menu;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/** Custom crosshairs. Style "Vanilla" leaves the normal Minecraft crosshair alone. */
public final class Crosshair {
    private Crosshair() {}

    public static final String[] STYLES = {
            "Vanilla", "Cross", "Thick cross", "Gap cross", "Cross + dot", "Dot", "Circle", "Circle dot",
            "X", "Square", "T", "Arrow", "Heart", "Diamond", "Chevron"};
    public static final int MIN_RADIUS = 2, MAX_RADIUS = 20;

    // Minecraft's own attack indicator textures
    private static final Identifier ATTACK_FULL = Identifier.withDefaultNamespace("textures/gui/sprites/hud/crosshair_attack_indicator_full.png");
    private static final Identifier ATTACK_BG = Identifier.withDefaultNamespace("textures/gui/sprites/hud/crosshair_attack_indicator_background.png");
    private static final Identifier ATTACK_PROGRESS = Identifier.withDefaultNamespace("textures/gui/sprites/hud/crosshair_attack_indicator_progress.png");

    private static final String[] HEART = {
            ".##.##.",
            "#######",
            "#######",
            ".#####.",
            "..###..",
            "...#..."};

    /** Crosshair size in pixels from the centre. Old configs that only had Small/Medium/Big carry over. */
    public static int radius() {
        int r = Config.I.crossRadius;
        if (r < 0) {
            r = switch (String.valueOf(Config.I.crossSize)) {
                case "Small" -> 4;
                case "Big" -> 9;
                default -> 6;
            };
        }
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, r));
    }

    /** HUD hook: draws at the middle of the screen. */
    public static void drawHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        int cx = mc.getWindow().getGuiScaledWidth() / 2, cy = mc.getWindow().getGuiScaledHeight() / 2;
        draw(g, cx, cy);
        attackIndicator(g, mc, cx, cy);
    }

    /** Minecraft's own attack indicator (the little sword / recharge bar), same look and rules as vanilla. */
    private static void attackIndicator(GuiGraphicsExtractor g, Minecraft mc, int cx, int cy) {
        if (mc.options.attackIndicator().get() != net.minecraft.client.AttackIndicatorStatus.CROSSHAIR) return;
        float str = mc.player.getAttackStrengthScale(0.0f);
        boolean full = false;
        if (str >= 1.0f && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target) {
            full = mc.player.getCurrentItemAttackStrengthDelay() > 5.0f && target.isAlive();
        }
        // vanilla spot is 9px under the centre; move it down only if the crosshair is bigger than that
        int y = Math.max(cy + 9, cy + radius() + 3);
        int x = cx - 8;
        if (full) {
            g.blit(RenderPipelines.GUI_TEXTURED, ATTACK_FULL, x, y, 0f, 0f, 16, 16, 16, 16, 16, 16);
        } else if (str < 1.0f) {
            int w = (int) (str * 17.0f);
            g.blit(RenderPipelines.GUI_TEXTURED, ATTACK_BG, x, y, 0f, 0f, 16, 4, 16, 4, 16, 4);
            if (w > 0) g.blit(RenderPipelines.GUI_TEXTURED, ATTACK_PROGRESS, x, y, 0f, 0f, Math.min(w, 16), 4, Math.min(w, 16), 4, 16, 4);
        }
    }

    private static void px(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, c);
    }

    public static void draw(GuiGraphicsExtractor g, int cx, int cy) {
        int c = Palette.resolve(Config.I.crossColor);
        int r = radius();
        int thick = Math.max(2, r / 3 + 1);
        int gap = Math.max(2, r / 3);
        int dot = r < 6 ? 1 : r < 14 ? 2 : 3;
        switch (Config.I.crossStyle) {
            case "Cross" -> {
                px(g, cx - r, cy, 2 * r + 1, 1, c);
                px(g, cx, cy - r, 1, 2 * r + 1, c);
            }
            case "Thick cross" -> {
                px(g, cx - r, cy - thick / 2, 2 * r + 1, thick, c);
                px(g, cx - thick / 2, cy - r, thick, 2 * r + 1, c);
            }
            case "Gap cross" -> gapCross(g, cx, cy, r, gap, c);
            case "Cross + dot" -> {
                gapCross(g, cx, cy, r, gap, c);
                px(g, cx - dot / 2, cy - dot / 2, dot, dot, c);
            }
            case "Dot" -> disc(g, cx, cy, Math.max(1, r / 3), c);
            case "Circle" -> ring(g, cx, cy, r, c);
            case "Circle dot" -> {
                ring(g, cx, cy, r, c);
                px(g, cx - dot / 2, cy - dot / 2, dot, dot, c);
            }
            case "X" -> {
                int t = r / 9 + 1;
                for (int i = -r; i <= r; i++) {
                    px(g, cx + i, cy + i, t, t, c);
                    px(g, cx + i, cy - i, t, t, c);
                }
            }
            case "Square" -> {
                px(g, cx - r, cy - r, 2 * r + 1, 1, c);
                px(g, cx - r, cy + r, 2 * r + 1, 1, c);
                px(g, cx - r, cy - r, 1, 2 * r + 1, c);
                px(g, cx + r, cy - r, 1, 2 * r + 1, c);
            }
            case "T" -> {
                px(g, cx - r, cy, 2 * r + 1, 1, c);
                px(g, cx, cy, 1, r + 1, c);
            }
            case "Arrow" -> {
                px(g, cx, cy - r, 1, 2 * r + 1, c);
                int head = Math.max(2, r / 2 + 1);
                for (int i = 1; i <= head; i++) {
                    px(g, cx - i, cy - r + i, 1, 1, c);
                    px(g, cx + i, cy - r + i, 1, 1, c);
                }
            }
            case "Heart" -> {
                int s = Math.max(1, r / 3);
                int x0 = cx - (7 * s) / 2, y0 = cy - (6 * s) / 2;
                for (int row = 0; row < HEART.length; row++)
                    for (int col = 0; col < 7; col++)
                        if (HEART[row].charAt(col) == '#') px(g, x0 + col * s, y0 + row * s, s, s, c);
            }
            case "Diamond" -> {
                for (int dx = -r; dx <= r; dx++) {
                    int dy = r - Math.abs(dx);
                    px(g, cx + dx, cy + dy, 1, 1, c);
                    if (dy != 0) px(g, cx + dx, cy - dy, 1, 1, c);
                }
            }
            case "Chevron" -> {
                int len = Math.max(3, r);
                for (int i = 0; i <= len; i++) {
                    px(g, cx - i, cy - len / 2 + i, 1, 1, c);
                    if (i != 0) px(g, cx + i, cy - len / 2 + i, 1, 1, c);
                }
            }
            default -> { }
        }
    }

    private static void gapCross(GuiGraphicsExtractor g, int cx, int cy, int r, int gap, int c) {
        int len = r - gap + 1;
        px(g, cx - r, cy, len, 1, c);
        px(g, cx + gap, cy, len, 1, c);
        px(g, cx, cy - r, 1, len, c);
        px(g, cx, cy + gap, 1, len, c);
    }

    private static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int c) {
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++)
                if (dx * dx + dy * dy <= r * r + 1) px(g, cx + dx, cy + dy, 1, 1, c);
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int c) {
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++)
                if (Math.abs(Math.sqrt(dx * dx + dy * dy) - r) <= 0.55) px(g, cx + dx, cy + dy, 1, 1, c);
    }
}
