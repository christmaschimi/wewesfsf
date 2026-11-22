package com.chimi.menu;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights the block you are looking at. Drawn as a screen overlay (same approach as the sky): the block's corners
 * are projected onto the screen and the edges / visible faces are drawn with the GUI fill calls.
 * Styles: Outline+Fill, Fill only, Outline. The inside is the "Fill" with Light / Medium / Strong strength.
 */
public final class BlockOutline {
    private BlockOutline() {}

    public static final String[] STYLES = {"Outline+Fill", "Fill only", "Outline"};
    public static final String[] THICKNESS = {"Thin", "Normal", "Thick", "Chunky"};
    /** Line width in real screen pixels (converted to GUI pixels using the GUI scale). */
    private static final int[] THICK_PX = {2, 4, 7, 12};
    public static final String[] FILL = {"Light", "Medium", "Strong"};
    public static final int[] FILL_ALPHA = {60, 110, 170};

    private static final double NEAR = 0.05;

    public static int thickIndex() {
        for (int i = 0; i < THICKNESS.length; i++) if (THICKNESS[i].equals(Config.I.outlineThickness)) return i;
        return 1;
    }

    public static int fillAlpha() {
        for (int i = 0; i < FILL.length; i++) if (FILL[i].equals(Config.I.outlineFill)) return FILL_ALPHA[i];
        return FILL_ALPHA[1];
    }

    public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Config cfg = Config.I;
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.outlineOn || mc.player == null || mc.level == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        if (shape.isEmpty()) return;
        AABB box = shape.bounds().move(pos);

        float pt = delta.getGameTimeDeltaPartialTick(true);
        Vec3 eye = mc.player.getEyePosition(pt);
        double yaw = Math.toRadians(mc.player.getViewYRot(pt)), pit = Math.toRadians(mc.player.getViewXRot(pt));
        double fx = -Math.sin(yaw) * Math.cos(pit), fy = -Math.sin(pit), fz = Math.cos(yaw) * Math.cos(pit);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;

        // Everything below is worked out in real screen pixels (not GUI pixels) so edges are fine instead of chunky.
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        double fov = Math.max(30, Math.min(140, mc.options.fov().get()));
        double focal = (h / 2.0) / Math.tan(Math.toRadians(fov) / 2);
        float inv = (float) (1.0 / Math.max(1.0, mc.getWindow().getGuiScale()));
        g.pose().pushMatrix();
        g.pose().scale(inv, inv);

        // corners in camera space: {right, up, depth}
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) {
            double x = ((i & 1) != 0 ? box.maxX : box.minX) - eye.x;
            double y = ((i & 2) != 0 ? box.maxY : box.minY) - eye.y;
            double z = ((i & 4) != 0 ? box.maxZ : box.minZ) - eye.z;
            c[i] = new double[]{x * rx + z * rz, x * ux + y * uy + z * uz, x * fx + y * fy + z * fz};
        }

        // vis[axis][side]: is that face turned towards the camera?
        boolean[][] vis = new boolean[3][2];
        vis[0][0] = eye.x < box.minX; vis[0][1] = eye.x > box.maxX;
        vis[1][0] = eye.y < box.minY; vis[1][1] = eye.y > box.maxY;
        vis[2][0] = eye.z < box.minZ; vis[2][1] = eye.z > box.maxZ;

        String style = cfg.outlineStyle;
        boolean doFill = !"Outline".equals(style);
        boolean doLine = !"Fill only".equals(style);

        if (doFill) {
            int rgb = Palette.resolve(cfg.outlineFillColor);
            int col = Palette.withAlpha(rgb, fillAlpha());
            for (int axis = 0; axis < 3; axis++) {
                for (int side = 0; side < 2; side++) {
                    if (!vis[axis][side]) continue;
                    int b = (axis + 1) % 3, d = (axis + 2) % 3;
                    int[] idx = new int[4];
                    int[][] order = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                    for (int k = 0; k < 4; k++) idx[k] = (side << axis) | (order[k][0] << b) | (order[k][1] << d);
                    List<double[]> poly = new ArrayList<>();
                    for (int k = 0; k < 4; k++) poly.add(c[idx[k]]);
                    fillPolygon(g, clipNear(poly), focal, w, h, col);
                }
            }
        }

        if (doLine) {
            int thick = THICK_PX[thickIndex()];
            int col = Palette.resolve(cfg.outlineColor);
            for (int i = 0; i < 8; i++) {
                for (int axis = 0; axis < 3; axis++) {
                    if ((i & (1 << axis)) != 0) continue; // each edge once: start from the low corner
                    int j = i | (1 << axis);
                    int b = (axis + 1) % 3, d = (axis + 2) % 3;
                    boolean show = vis[b][(i >> b) & 1] || vis[d][(i >> d) & 1];
                    if (!show) continue;
                    line(g, c[i], c[j], focal, w, h, thick, col);
                }
            }
        }
        g.pose().popMatrix();
    }

    // ---------- geometry ----------

    /** Sutherland-Hodgman clip of a camera-space polygon against the near plane. */
    private static List<double[]> clipNear(List<double[]> in) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            double[] a = in.get(i), b = in.get((i + 1) % in.size());
            boolean ain = a[2] >= NEAR, bin = b[2] >= NEAR;
            if (ain) out.add(a);
            if (ain != bin) {
                double t = (NEAR - a[2]) / (b[2] - a[2]);
                out.add(new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, NEAR});
            }
        }
        return out;
    }

    private static void fillPolygon(GuiGraphicsExtractor g, List<double[]> poly, double focal, int w, int h, int color) {
        int n = poly.size();
        if (n < 3) return;
        double[] xs = new double[n], ys = new double[n];
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double[] p = poly.get(i);
            xs[i] = w / 2.0 + p[0] / p[2] * focal;
            ys[i] = h / 2.0 - p[1] / p[2] * focal;
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(h - 1, (int) Math.ceil(maxY));
        int step = Math.max(1, (y1 - y0) / 160); // at most ~160 fills per face
        for (int y = y0; y <= y1; y += step) {
            double yc = y + step / 2.0, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double ya = ys[i], yb = ys[j];
                if ((ya <= yc && yb > yc) || (yb <= yc && ya > yc)) {
                    double x = xs[i] + (yc - ya) * (xs[j] - xs[i]) / (yb - ya);
                    lo = Math.min(lo, x);
                    hi = Math.max(hi, x);
                }
            }
            if (lo > hi) continue;
            int xa = Math.max(0, (int) Math.floor(lo)), xb = Math.min(w, (int) Math.ceil(hi));
            if (xb > xa) g.fill(xa, y, xb, y + step, color);
        }
    }

    private static void line(GuiGraphicsExtractor g, double[] a, double[] b, double focal, int w, int h, int thick, int color) {
        // clip to the near plane
        if (a[2] < NEAR && b[2] < NEAR) return;
        double[] p = a, q = b;
        if (p[2] < NEAR) { double t = (NEAR - p[2]) / (q[2] - p[2]); p = new double[]{p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t, NEAR}; }
        if (q[2] < NEAR) { double t = (NEAR - q[2]) / (p[2] - q[2]); q = new double[]{q[0] + (p[0] - q[0]) * t, q[1] + (p[1] - q[1]) * t, NEAR}; }
        double x0 = w / 2.0 + p[0] / p[2] * focal, y0 = h / 2.0 - p[1] / p[2] * focal;
        double x1 = w / 2.0 + q[0] / q[2] * focal, y1 = h / 2.0 - q[1] / q[2] * focal;

        // clip to the screen (Liang-Barsky) so huge off-screen lines stay cheap
        double m = thick + 2, dx = x1 - x0, dy = y1 - y0, t0 = 0, t1 = 1;
        double[] pp = {-dx, dx, -dy, dy}, qq = {x0 + m, w + m - x0, y0 + m, h + m - y0};
        for (int i = 0; i < 4; i++) {
            if (pp[i] == 0) { if (qq[i] < 0) return; continue; }
            double r = qq[i] / pp[i];
            if (pp[i] < 0) { if (r > t1) return; t0 = Math.max(t0, r); }
            else { if (r < t0) return; t1 = Math.min(t1, r); }
        }
        double ax = x0 + dx * t0, ay = y0 + dy * t0, bx = x0 + dx * t1, by = y0 + dy * t1;

        double len = Math.hypot(bx - ax, by - ay);
        // A real rotated bar, so diagonals are smooth instead of a staircase of squares. A faint wider bar under it softens the edge.
        int half = thick / 2, ext = Math.max(1, half);
        int length = (int) Math.ceil(len);
        int soft = (0x55 << 24) | (color & 0xFFFFFF);
        g.pose().pushMatrix();
        g.pose().translate((float) ax, (float) ay);
        g.pose().rotate((float) Math.atan2(by - ay, bx - ax));
        g.fill(-ext, -half - 1, length + ext, thick - half + 1, soft);
        g.fill(-ext, -half, length + ext, thick - half, color);
        g.pose().popMatrix();
    }
}
