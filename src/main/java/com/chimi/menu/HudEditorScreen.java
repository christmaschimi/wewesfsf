package com.chimi.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Drag each HUD box to where you want it. Click a box (or its name below) to select it, then use - / + to resize,
 * the arrow buttons to nudge, Reset to put it back. Dragging uses the left mouse button.
 */
public class HudEditorScreen extends Screen {
    private static final int BAR_H = 56;

    private record Btn(String label, int x, int y, int w, int h, boolean active) {}
    private final List<Btn> btns = new ArrayList<>();
    private int selected = 0;
    private boolean dragging, wasDown;
    private int grabDX, grabDY;

    public HudEditorScreen() { super(Component.literal("HUD editor")); }

    // ---------- sample sizes (same maths as the real HUDs, with example text) ----------
    private int[] sampleSize(int id) {
        var font = Minecraft.getInstance().font;
        return switch (id) {
            case 0 -> new int[]{font.width(sampleText(0)) + 8, 14};
            case 1 -> new int[]{font.width(sampleText(1)) + 8, 14};
            default -> new int[]{150, 36 + Math.min(Math.max(1, Config.I.aucBoard), 5) * 10};
        };
    }

    private String sampleText(int id) {
        Config c = Config.I;
        if (id == 0) return c.profitText.replace("{profit}", "+12.40K").replace("{perhour}", "1.20M");
        return c.spotifyPlayingText.replace("{title}", "Song name").replace("{artist}", "Artist");
    }

    private int[] rect(int id) {
        int[] sz = sampleSize(id);
        int[] p = HudLayout.pos(id, sz[0], sz[1], true);
        float s = HudLayout.scale(id);
        return new int[]{p[0], p[1], Math.round(sz[0] * s), Math.round(sz[1] * s)};
    }

    // ---------- widgets ----------
    private void btn(String label, int x, int y, int w, int h, boolean active, Runnable r) {
        addRenderableWidget(Button.builder(Component.empty(), b -> r.run()).bounds(x, y, w, h).build());
        btns.add(new Btn(label, x, y, w, h, active));
    }

    private void nudge(int dx, int dy) {
        int[] r = rect(selected);
        HudLayout.setX(selected, Math.max(0, r[0] + dx));
        HudLayout.setY(selected, Math.max(0, r[1] + dy));
        Config.save();
    }

    private void resize(int delta) {
        int[] r = rect(selected);
        HudLayout.setX(selected, r[0]);
        HudLayout.setY(selected, r[1]);
        HudLayout.setScale(selected, HudLayout.getScale(selected) + delta);
        Config.save();
    }

    @Override
    protected void init() {
        btns.clear();
        int y1 = height - BAR_H + 6, y2 = height - BAR_H + 30;
        int total = 3 * 58 + 3 * 4 + 22 + 4 + 22 + 4 + 44;
        int x = (width - total) / 2;
        for (int i = 0; i < 3; i++) {
            final int id = i;
            btn(HudLayout.NAMES[i], x, y1, 58, 18, selected == i, () -> { selected = id; rebuildWidgets(); });
            x += 62;
        }
        btn("-", x, y1, 22, 18, false, () -> resize(-1)); x += 26;
        btn("+", x, y1, 22, 18, false, () -> resize(1)); x += 26;
        btn("Reset", x, y1, 44, 18, false, () -> { HudLayout.reset(selected); Config.save(); });

        int nx = (width - (4 * 22 + 3 * 4 + 8 + 60 + 4 + 60)) / 2;
        btn("<", nx, y2, 22, 18, false, () -> nudge(-2, 0)); nx += 26;
        btn("^", nx, y2, 22, 18, false, () -> nudge(0, -2)); nx += 26;
        btn("v", nx, y2, 22, 18, false, () -> nudge(0, 2)); nx += 26;
        btn(">", nx, y2, 22, 18, false, () -> nudge(2, 0)); nx += 34;
        btn("Reset all", nx, y2, 60, 18, false, () -> { for (int i = 0; i < 3; i++) HudLayout.reset(i); Config.save(); }); nx += 64;
        btn("Done", nx, y2, 60, 18, true, this::onClose);
    }

    // ---------- drawing ----------
    private void drawSample(GuiGraphicsExtractor g, int id, boolean sel) {
        Minecraft mc = Minecraft.getInstance();
        int[] sz = sampleSize(id);
        int[] r = rect(id);
        float s = HudLayout.scale(id);
        int x = Math.round(r[0] / s), y = Math.round(r[1] / s);
        g.pose().pushMatrix();
        g.pose().scale(s, s);
        if (id == 2) {
            AuctionHud.Theme th = AuctionHud.theme();
            g.fill(x, y, x + sz[0], y + sz[1], th.border());
            g.fill(x + 1, y + 1, x + sz[0] - 1, y + sz[1] - 1, th.panel());
            g.fill(x + 1, y + 1, x + sz[0] - 1, y + 3, th.accent());
            g.text(mc.font, "Auction HUD", x + 6, y + 8, 0xFFFFFFFF);
            g.text(mc.font, "01:30", x + 6, y + 19, th.accent());
            int rows = Math.min(Math.max(1, Config.I.aucBoard), 5);
            for (int i = 0; i < rows; i++) g.text(mc.font, "#" + (i + 1) + " Player", x + 6, y + 37 + i * 10, 0xFFDDDDDD);
        } else {
            g.fill(x, y, x + sz[0], y + sz[1], 0x99000000);
            int col = id == 0 ? 0xFF55FF55 : Palette.resolve(Config.I.spotifyColor);
            g.text(mc.font, sampleText(id), x + 4, y + 3, col);
        }
        g.pose().popMatrix();
        int frame = sel ? 0xFFFFFFFF : 0x88FFFFFF;
        g.fill(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1], frame);
        g.fill(r[0] - 1, r[1] + r[3], r[0] + r[2] + 1, r[1] + r[3] + 1, frame);
        g.fill(r[0] - 1, r[1], r[0], r[1] + r[3], frame);
        g.fill(r[0] + r[2], r[1], r[0] + r[2] + 1, r[1] + r[3], frame);
        String label = HudLayout.NAMES[id] + " " + (HudLayout.getScale(id) / 10) + "." + (HudLayout.getScale(id) % 10) + "x";
        g.text(font, label, r[0], Math.max(2, r[1] - 10), sel ? 0xFFFFFFFF : 0xFFBBBBBB);
    }

    private boolean leftDown() {
        return minecraft != null && minecraft.mouseHandler.isLeftPressed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x66000000);
        g.text(font, "Drag a box to move it. Click its name below to select it, then - / + to resize.", 6, 6, 0xFFFFFFFF);

        // drag handling (polled, so no mouse overrides are needed)
        boolean down = leftDown();
        if (down && !wasDown && my < height - BAR_H) {
            for (int id = 2; id >= 0; id--) {
                int[] r = rect(id);
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    selected = id;
                    dragging = true;
                    grabDX = mx - r[0];
                    grabDY = my - r[1];
                    rebuildWidgets();
                    break;
                }
            }
        }
        if (down && dragging) {
            int[] r = rect(selected);
            int nx = Math.max(0, Math.min(width - r[2], mx - grabDX));
            int ny = Math.max(0, Math.min(height - BAR_H - r[3], my - grabDY));
            HudLayout.setX(selected, nx);
            HudLayout.setY(selected, ny);
        }
        if (!down && dragging) { dragging = false; Config.save(); }
        wasDown = down;

        for (int id = 0; id < 3; id++) if (id != selected) drawSample(g, id, false);
        drawSample(g, selected, true);

        g.fill(0, height - BAR_H, width, height, 0xE0101420);
        g.fill(0, height - BAR_H, width, height - BAR_H + 1, 0xFF3A4470);
        super.extractRenderState(g, mx, my, delta);
        for (Btn b : btns) {
            boolean hover = mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h();
            int fill = b.active() ? 0xFF3A4A8A : hover ? 0xFF5A5A5A : 0xFF424242;
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), b.active() ? 0xFF8FB8FF : 0xFF1C1C1C);
            g.fill(b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + b.h() - 1, fill);
            g.text(font, b.label(), b.x() + (b.w() - font.width(b.label())) / 2, b.y() + (b.h() - 8) / 2, 0xFFFFFFFF);
        }
    }

    @Override
    public void removed() { Config.save(); super.removed(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
