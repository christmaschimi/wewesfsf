package com.chimi.menu;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Custom skies. The sky is painted as a grid of small coloured cells (gradient, aurora curtains, galaxy band, stars, moon)
 * over every pixel of sky you can see. Terrain, trees and buildings that block the sky are found with a horizon scan
 * (rays around you, refreshed continuously), so the sky is only drawn where real sky is visible and it stays hidden
 * behind hills, trees and roofs. First person, overworld only.
 */
public final class SkyOverlay {
    private SkyOverlay() {}

    /** Colours are RGB. aurora = green curtains. nebulaA/B = galaxy band colours (0 = no band). moon = disc colour (0 = none). */
    public record Preset(String name, int top, int horizon, int stars, int starTint, boolean aurora, int nebulaA, int nebulaB, int moon) {
        public int dot() { return top == 0 ? 0xFFA0A0A0 : 0xFF000000 | horizon; }
    }

    public static final Preset[] PRESETS = {
            new Preset("Default", 0, 0, 0, 0, false, 0, 0, 0),
            new Preset("Aurora", 0x040A26, 0x0C2C5E, 220, 0xDFFFF4, true, 0, 0, 0),
            new Preset("Galaxy", 0x07021A, 0x2A0B4A, 300, 0xFFFFFF, false, 0x7B3FE4, 0xFF4FD8, 0),
            new Preset("Golden Sunset", 0x3A1C6B, 0xFFB03A, 0, 0xFFFFFF, false, 0, 0, 0),
            new Preset("Pink Dusk", 0x4A5BA8, 0xFFA6C8, 40, 0xFFFFFF, false, 0, 0, 0),
            new Preset("Neon", 0x0A0020, 0x7A1A8A, 200, 0xFFFFFF, false, 0xFF2BD6, 0x2BE7FF, 0),
            new Preset("Blood Moon", 0x120004, 0x6A0A12, 140, 0xFFC8C8, false, 0, 0, 0xD02020),
            new Preset("Midnight", 0x02030A, 0x0A1A3A, 300, 0xFFFFFF, false, 0, 0, 0),
    };

    public static Preset current() {
        for (Preset p : PRESETS) if (p.name().equals(Config.I.skyPreset)) return p;
        return PRESETS[0];
    }

    // ---------- fixed star field ----------
    private static final int MAX_STARS = 300;
    private static final double[][] STAR = new double[MAX_STARS][5]; // x, y, z (unit), size, phase
    static {
        Random r = new Random(1337);
        for (int i = 0; i < MAX_STARS; i++) {
            double th = r.nextDouble() * Math.PI * 2, y = 0.03 + r.nextDouble() * 0.97, rr = Math.sqrt(1 - y * y);
            STAR[i][0] = rr * Math.cos(th);
            STAR[i][1] = y;
            STAR[i][2] = rr * Math.sin(th);
            STAR[i][3] = r.nextDouble() < 0.12 ? 2 : 1;
            STAR[i][4] = r.nextDouble() * 6.28;
        }
    }

    // ---------- horizon scan: highest blocked elevation (radians) for each compass direction ----------
    private static final int N = 120;                         // directions around you (3 degrees apart)
    private static final double RANGE = 192;                  // how far the scan looks
    private static final double[] SCAN_DEG = {0, 2, 4, 7, 10, 14, 19, 25, 33, 43, 55, 70};
    private static final double[] horizon = new double[N];
    private static int scanCursor;
    private static final int SCAN_PER_FRAME = 2;

    private static boolean blocked(Minecraft mc, Vec3 eye, double az, double elevRad) {
        Vec3 to = eye.add(-Math.sin(az) * Math.cos(elevRad) * RANGE, Math.sin(elevRad) * RANGE, Math.cos(az) * Math.cos(elevRad) * RANGE);
        var hit = mc.level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() != HitResult.Type.MISS;
    }

    private static void scan(Minecraft mc, Vec3 eye) {
        for (int n = 0; n < SCAN_PER_FRAME; n++) {
            int i = scanCursor;
            scanCursor = (scanCursor + 1) % N;
            double az = i * (Math.PI * 2 / N);
            double result = Math.PI / 2;
            for (int k = 0; k < SCAN_DEG.length; k++) {
                if (blocked(mc, eye, az, Math.toRadians(SCAN_DEG[k]))) continue;
                if (k == 0) { result = 0; break; }
                // the edge lies between the last blocked ray and this free one: narrow it down
                double lo = SCAN_DEG[k - 1], hi = SCAN_DEG[k];
                for (int it = 0; it < 2; it++) {
                    double mid = (lo + hi) / 2;
                    if (blocked(mc, eye, az, Math.toRadians(mid))) lo = mid; else hi = mid;
                }
                result = Math.toRadians((lo + hi) / 2);
                break;
            }
            horizon[i] = result;
        }
    }

    private static double horizonAt(double az) {
        double t = az / (Math.PI * 2);
        t -= Math.floor(t);
        double f = t * N;
        int i0 = (int) f % N, i1 = (i0 + 1) % N;
        double u = f - Math.floor(f);
        return horizon[i0] * (1 - u) + horizon[i1] * u;
    }

    // ---------- colour helpers ----------
    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static final double BAND_NX, BAND_NY, BAND_NZ;
    static {
        double x = 0.35, y = 0.75, z = 0.55, l = Math.sqrt(x * x + y * y + z * z);
        BAND_NX = x / l; BAND_NY = y / l; BAND_NZ = z / l;
    }

    // ---------- camera ----------
    private static double fx, fy, fz, rx, rz, ux, uy, uz, focal;
    private static int cxs, cys;

    private static double[] project(double x, double y, double z) {
        double d = x * fx + y * fy + z * fz;
        if (d < 0.08) return null;
        double sx = x * rx + z * rz, sy = x * ux + y * uy + z * uz;
        return new double[]{cxs + sx / d * focal, cys - sy / d * focal, d};
    }

    // ---------- drawing (real screen pixels, exact horizon edge, runs of similar colour merged into single fills) ----------
    private static double dnx, dny, dnz;
    private static int[] cellColors = new int[0];
    private static int[] fullRows = new int[0], partRows = new int[0];

    /** Unit view direction of a screen pixel (real pixels). */
    private static void dir(double x, double y) {
        double a = (x - cxs) / focal, b = -(y - cys) / focal;
        double vx = fx + a * rx + b * ux, vy = fy + b * uy, vz = fz + a * rz + b * uz;
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        dnx = vx / len; dny = vy / len; dnz = vz / len;
    }

    private static boolean skyAt(double x, double y) {
        dir(x, y);
        if (dny <= 0) return false;
        return Math.asin(dny) >= horizonAt(Math.atan2(-dnx, dnz));
    }

    /** Sky colour (RGB) for the direction last set by dir(). */
    private static int shade(Preset p, double t, boolean band) {
        double nx = dnx, ny = dny, nz = dnz;
        double el = Math.asin(ny), az = Math.atan2(-nx, nz);
        int c = mix(p.horizon(), p.top(), Math.pow(el / (Math.PI / 2), 0.55));
        if (band) {
            double d = nx * BAND_NX + ny * BAND_NY + nz * BAND_NZ;
            double env = Math.exp(-(d * d) / 0.035);
            double m = 0.55 + 0.45 * Math.sin(nx * 9 + Math.sin(nz * 7 + t * 0.02) * 2) * Math.sin(nz * 8 + ny * 6);
            int nc = mix(p.nebulaA(), p.nebulaB(), 0.5 + 0.5 * Math.sin(nx * 4 + nz * 5));
            c = mix(c, nc, env * Math.max(0, m) * 0.85);
        }
        if (p.aurora()) {
            double deg = Math.toDegrees(el);
            double env = smooth(3, 11, deg) * Math.exp(-Math.max(0, deg - 12) / 14.0);
            double ray = 0.5 + 0.5 * Math.sin(az * 34 + t * 0.8 + 2.2 * Math.sin(az * 5 + t * 0.35));
            ray = Math.pow(ray, 2.2);
            double mask = 0.6 + 0.4 * Math.sin(az * 2.3 + t * 0.15);
            double amt = env * (ray * 0.95 + 0.18) * mask;
            int ac = mix(0x2BFFA0, 0x6A5BFF, (deg - 12) / 40.0);
            c = mix(c, ac, amt);
        }
        return c;
    }

    private static boolean close(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) <= 2
                && Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) <= 2
                && Math.abs((a & 255) - (b & 255)) <= 2;
    }

    public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Preset p = current();
        Minecraft mc = Minecraft.getInstance();
        if (!Config.I.skyOverlay || p.top() == 0 || mc.player == null || mc.level == null) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;
        if (!mc.options.getCameraType().isFirstPerson() || mc.player.isUnderWater()) return;

        float pt = delta.getGameTimeDeltaPartialTick(true);
        Vec3 eye = mc.player.getEyePosition(pt);
        scan(mc, eye);

        int rw = mc.getWindow().getWidth(), rh = mc.getWindow().getHeight();
        double yaw = Math.toRadians(mc.player.getViewYRot(pt)), pit = Math.toRadians(mc.player.getViewXRot(pt));
        fx = -Math.sin(yaw) * Math.cos(pit);
        fy = -Math.sin(pit);
        fz = Math.cos(yaw) * Math.cos(pit);
        rx = -Math.cos(yaw);
        rz = -Math.sin(yaw);
        ux = -rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy;
        cxs = rw / 2;
        cys = rh / 2;
        double fov = Math.max(30, Math.min(140, mc.options.fov().get()));
        focal = (rh / 2.0) / Math.tan(Math.toRadians(fov) / 2);

        double t = System.currentTimeMillis() / 1000.0;
        boolean band = p.nebulaA() != 0;

        // draw in real screen pixels instead of GUI pixels (this is what removes the chunky look)
        float inv = (float) (1.0 / Math.max(1.0, mc.getWindow().getGuiScale()));
        g.pose().pushMatrix();
        g.pose().scale(inv, inv);

        // cell size in real pixels: small, but never more than Config.skyCells cells (lower = faster)
        int cw = 8, ch = 6;
        double k = Math.sqrt(((double) rw / cw) * ((double) rh / ch) / Math.max(3000, Config.I.skyCells));
        if (k > 1) { cw = (int) Math.ceil(cw * k); ch = (int) Math.ceil(ch * k); }
        int ncols = (rw + cw - 1) / cw, nrows = (rh + ch - 1) / ch;
        if (cellColors.length < ncols * nrows) cellColors = new int[ncols * nrows];
        if (fullRows.length < ncols) { fullRows = new int[ncols]; partRows = new int[ncols]; }

        // 1) per column: colours of the cells that are fully sky, and the exact pixel row where terrain starts
        for (int c = 0; c < ncols; c++) {
            double xc = Math.min(rw - 1, c * cw + cw / 2.0);
            int full = 0, part = 0;
            for (int r = 0; r < nrows; r++) {
                int y0 = r * ch, y1 = Math.min(rh, y0 + ch);
                if (skyAt(xc, y1 - 1)) { // whole cell is sky
                    dir(xc, (y0 + y1) / 2.0);
                    cellColors[c * nrows + r] = shade(p, t, band);
                    full++;
                    continue;
                }
                double lo = y0, hi = y1 - 1; // lo = last row known to be sky (or the cell's top), hi = blocked
                if (!skyAt(xc, lo)) { lo = y0 - 1; } else {
                    for (int it = 0; it < 5; it++) {
                        double mid = (lo + hi) / 2;
                        if (skyAt(xc, mid)) lo = mid; else hi = mid;
                    }
                }
                part = (int) Math.floor(lo) + 1 - y0;
                if (part > 0) {
                    dir(xc, y0 + part / 2.0);
                    cellColors[c * nrows + r] = shade(p, t, band);
                } else part = 0;
                break;
            }
            fullRows[c] = full;
            partRows[c] = part;
        }

        // 2) fill row by row, merging neighbouring cells of (nearly) the same colour into one fill
        for (int r = 0; r < nrows; r++) {
            int y0 = r * ch, y1 = Math.min(rh, y0 + ch);
            int c = 0;
            while (c < ncols) {
                if (fullRows[c] > r) {
                    int start = c, base = cellColors[c * nrows + r];
                    c++;
                    while (c < ncols && fullRows[c] > r && close(base, cellColors[c * nrows + r])) c++;
                    g.fill(start * cw, y0, Math.min(rw, c * cw), y1, 0xFF000000 | base);
                } else {
                    if (fullRows[c] == r && partRows[c] > 0) {
                        g.fill(c * cw, y0, Math.min(rw, (c + 1) * cw), y0 + partRows[c], 0xFF000000 | cellColors[c * nrows + r]);
                    }
                    c++;
                }
            }
        }

        // moon (soft glow + disc)
        if (p.moon() != 0) {
            double mx = 0.55, my = 0.62, mz = -0.55, ml = Math.sqrt(mx * mx + my * my + mz * mz);
            mx /= ml; my /= ml; mz /= ml;
            double[] m = project(mx, my, mz);
            if (m != null && Math.asin(my) >= horizonAt(Math.atan2(-mx, mz))) {
                int r = (int) Math.max(10, focal * 0.06 / m[2]);
                disc(g, (int) m[0], (int) m[1], (int) (r * 1.7), (0x22 << 24) | p.moon());
                disc(g, (int) m[0], (int) m[1], (int) (r * 1.3), (0x33 << 24) | p.moon());
                disc(g, (int) m[0], (int) m[1], r, 0xE0000000 | p.moon());
            }
        }

        // stars
        double tw = System.currentTimeMillis() / 700.0;
        int n = Math.min(MAX_STARS, p.stars());
        for (int i = 0; i < n; i++) {
            double sx = STAR[i][0], sy = STAR[i][1], sz = STAR[i][2];
            if (Math.asin(sy) < horizonAt(Math.atan2(-sx, sz))) continue;
            double[] s = project(sx, sy, sz);
            if (s == null) continue;
            int x = (int) s[0], y = (int) s[1];
            if (x < 0 || y < 0 || x >= rw || y >= rh) continue;
            int al = (int) (170 + 85 * Math.sin(tw + STAR[i][4]));
            int size = STAR[i][3] == 2 ? 3 : 2;
            g.fill(x, y, x + size, y + size, (Math.max(90, Math.min(255, al)) << 24) | p.starTint());
        }
        g.pose().popMatrix();
    }

    private static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy < r; dy++) {
            int hw = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(cx - hw, cy + dy, cx + hw, cy + dy + 1, argb);
        }
    }
}
