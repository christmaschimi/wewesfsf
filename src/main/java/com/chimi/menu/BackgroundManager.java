package com.chimi.menu;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.stream.Stream;

/** Imports images from your PC (png/jpg/gif/bmp), stores them as PNG, and draws one behind your inventory. */
public final class BackgroundManager {
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("chimismenu").resolve("backgrounds");
    private static final Identifier TEX = Identifier.fromNamespaceAndPath(ChimiMenuClient.ID, "custom_bg");

    private static boolean loaded;
    private static String loadedName = "";
    private static int imgW, imgH;

    public static final Path THUMBS = DIR.resolveSibling("thumbs");
    public record Thumb(Identifier id, int w, int h) {}
    private static final Map<String, Thumb> thumbs = new HashMap<>();
    private static final Set<String> failed = new HashSet<>();

    private BackgroundManager() {}

    /** Small preview for the gallery. Made once per image and cached on disk next to the backgrounds folder. */
    public static Thumb thumb(String name) {
        Thumb t = thumbs.get(name);
        if (t != null) return t;
        if (failed.contains(name)) return null;
        try {
            Files.createDirectories(THUMBS);
            Path tf = THUMBS.resolve(name);
            if (!Files.exists(tf)) {
                BufferedImage src = ImageIO.read(DIR.resolve(name).toFile());
                if (src == null) throw new IOException("unreadable");
                int w = Math.min(192, src.getWidth());
                int h = Math.max(1, Math.round(src.getHeight() * (w / (float) src.getWidth())));
                BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                var g2 = out.createGraphics();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(src, 0, 0, w, h, null);
                g2.dispose();
                ImageIO.write(out, "png", tf.toFile());
            }
            Identifier id = Identifier.fromNamespaceAndPath(ChimiMenuClient.ID, "thumb_" + name.toLowerCase().replaceAll("[^a-z0-9_-]", "_"));
            try (InputStream in = Files.newInputStream(tf)) {
                NativeImage ni = NativeImage.read(in);
                t = new Thumb(id, ni.getWidth(), ni.getHeight());
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "chimismenu_thumb", ni));
            }
            thumbs.put(name, t);
            return t;
        } catch (Exception e) {
            failed.add(name);
            return null;
        }
    }

    public static void delete(String name) {
        if (name == null || name.isEmpty()) return;
        Thumb t = thumbs.remove(name);
        if (t != null) Minecraft.getInstance().getTextureManager().release(t.id());
        try {
            Files.deleteIfExists(DIR.resolve(name));
            Files.deleteIfExists(THUMBS.resolve(name));
        } catch (IOException ignored) {}
        if (name.equals(Config.I.bgImage)) select("");
    }

    public static List<String> list() {
        List<String> names = new ArrayList<>();
        try {
            Files.createDirectories(DIR);
            try (Stream<Path> s = Files.list(DIR)) {
                s.map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase().endsWith(".png"))
                        .forEach(names::add);
            }
        } catch (IOException ignored) {}
        Collections.sort(names);
        return names;
    }

    /** Copies any readable image into the backgrounds folder as PNG and selects it. */
    public static String importImage(Path src) throws IOException {
        BufferedImage img = ImageIO.read(src.toFile());
        if (img == null) throw new IOException("Not a supported image (use PNG, JPG, GIF or BMP)");
        Files.createDirectories(DIR);
        String base = src.getFileName().toString().replaceAll("\\.[^.]+$", "").replaceAll("[^A-Za-z0-9_-]", "_");
        if (base.isEmpty()) base = "background";
        String name = base + ".png";
        int n = 2;
        while (Files.exists(DIR.resolve(name))) name = base + "_" + n++ + ".png";
        ImageIO.write(img, "png", DIR.resolve(name).toFile());
        select(name);
        return name;
    }

    public static void select(String name) {
        Config.I.bgImage = name == null ? "" : name;
        Config.save();
        loaded = false; // reloaded on next draw
    }

    private static void ensureLoaded() {
        String want = Config.I.bgImage == null ? "" : Config.I.bgImage;
        if (loaded && want.equals(loadedName)) return;
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().release(TEX);
        loaded = false;
        loadedName = want;
        if (want.isEmpty()) return;
        Path file = DIR.resolve(want);
        if (!Files.exists(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage ni = NativeImage.read(in);
            imgW = ni.getWidth();
            imgH = ni.getHeight();
            mc.getTextureManager().register(TEX, new DynamicTexture(() -> "chimismenu_bg", ni));
            loaded = true;
        } catch (Exception e) {
            loaded = false;
        }
    }

    /**
     * Draws the image only inside the inventory panel (cropped, not stretched), then a dark layer and a box behind every
     * slot so the grid stays readable. Runs after the screen background and before the slots' items, so your items,
     * the player model and tooltips are all drawn on top. Works for any inventory-style screen: the panel is worked out
     * from the menu's slots (panel width is 176; the hotbar row sits 24px above the panel's bottom edge).
     */
    public static void drawPanel(GuiGraphicsExtractor g, net.minecraft.client.gui.screens.Screen screen) {
        if (!Config.I.bgEnabled || Config.I.bgImage == null || Config.I.bgImage.isEmpty()) return;
        if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) return;
        ensureLoaded();
        if (!loaded) return;

        var slots = cs.getMenu().slots;
        if (slots.isEmpty()) return;
        int maxY = 0;
        for (var s : slots) maxY = Math.max(maxY, s.y);
        int pw = 176, ph = maxY + 24;
        int left = (screen.width - pw) / 2, top = (screen.height - ph) / 2;

        float scale = Math.max((float) pw / imgW, (float) ph / imgH);
        int regionW = Math.min(imgW, Math.round(pw / scale));
        int regionH = Math.min(imgH, Math.round(ph / scale));
        float u = (imgW - regionW) / 2f;
        float v = (imgH - regionH) / 2f;

        // The player preview box in the survival inventory is left alone, so your character shows instead of being painted over.
        List<int[]> pieces = new ArrayList<>();
        boolean hole = screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
        int hx0 = 26, hy0 = 8, hx1 = 76, hy1 = 78;
        if (hole) {
            pieces.add(new int[]{0, 0, pw, hy0});
            pieces.add(new int[]{0, hy1, pw, ph});
            pieces.add(new int[]{0, hy0, hx0, hy1});
            pieces.add(new int[]{hx1, hy0, pw, hy1});
        } else {
            pieces.add(new int[]{0, 0, pw, ph});
        }
        for (int[] r : pieces) {
            int w = r[2] - r[0], h = r[3] - r[1];
            if (w <= 0 || h <= 0) continue;
            float pu = u + r[0] * regionW / (float) pw, pv = v + r[1] * regionH / (float) ph;
            int rw = Math.max(1, Math.round(w * regionW / (float) pw)), rh = Math.max(1, Math.round(h * regionH / (float) ph));
            g.blit(RenderPipelines.GUI_TEXTURED, TEX, left + r[0], top + r[1], pu, pv, w, h, rw, rh, imgW, imgH);
            if (Config.I.bgDim > 0) g.fill(left + r[0], top + r[1], left + r[2], top + r[3], (Config.I.bgDim << 24));
        }
        if (hole) {
            int fx0 = left + hx0, fy0 = top + hy0, fx1 = left + hx1, fy1 = top + hy1;
            g.fill(fx0 - 1, fy0 - 1, fx1 + 1, fy0, 0xAAFFFFFF);
            g.fill(fx0 - 1, fy1, fx1 + 1, fy1 + 1, 0xAAFFFFFF);
            g.fill(fx0 - 1, fy0, fx0, fy1, 0xAAFFFFFF);
            g.fill(fx1, fy0, fx1 + 1, fy1, 0xAAFFFFFF);
        }

        // thin frame around the panel
        int frame = 0xAAFFFFFF;
        g.fill(left - 1, top - 1, left + pw + 1, top, frame);
        g.fill(left - 1, top + ph, left + pw + 1, top + ph + 1, frame);
        g.fill(left - 1, top, left, top + ph, frame);
        g.fill(left + pw, top, left + pw + 1, top + ph, frame);

        // slot boxes so you can see where every slot is
        for (var s : slots) {
            int x = left + s.x, y = top + s.y;
            g.fill(x, y, x + 16, y + 16, 0x66000000);
            g.fill(x - 1, y - 1, x + 17, y, 0x55FFFFFF);
            g.fill(x - 1, y + 16, x + 17, y + 17, 0x55FFFFFF);
            g.fill(x - 1, y, x, y + 16, 0x55FFFFFF);
            g.fill(x + 16, y, x + 17, y + 16, 0x55FFFFFF);
        }
    }
}
