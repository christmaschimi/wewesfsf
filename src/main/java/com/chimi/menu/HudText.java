package com.chimi.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Text for the HUDs in the font chosen on the Style page. "Minecraft" is the normal game font; the others are bundled
 * (assets/chimismenu/font/*.json). If a font can't be applied for any reason, the normal font is used instead.
 */
public final class HudText {
    private HudText() {}

    public static final String[] FONTS = {"Minecraft", "Pixelify", "Silkscreen", "Tektur", "Big Shoulders", "Erica One", "Outfit"};
    private static final String[] IDS = {null, "pixelify", "silkscreen", "tektur", "bigshoulders", "ericaone", "outfit"};

    private static final Map<String, Style> CACHE = new HashMap<>();

    private static Style style() {
        String name = Config.I.hudFont;
        for (int i = 1; i < FONTS.length; i++) {
            if (FONTS[i].equals(name)) return CACHE.computeIfAbsent(IDS[i], HudText::build);
        }
        return null;
    }

    /** Style.withFont takes an Identifier or a font description object depending on the game version, so it is called by reflection. */
    private static Style build(String id) {
        try {
            Identifier rl = Identifier.fromNamespaceAndPath(ChimiMenuClient.ID, id);
            for (Method m : Style.class.getMethods()) {
                if (!m.getName().equals("withFont") || m.getParameterCount() != 1) continue;
                Class<?> pt = m.getParameterTypes()[0];
                Object arg = null;
                if (pt.isInstance(rl)) {
                    arg = rl;
                } else {
                    for (Class<?> inner : pt.getDeclaredClasses()) {
                        try {
                            arg = inner.getConstructor(Identifier.class).newInstance(rl);
                            break;
                        } catch (ReflectiveOperationException ignored) {
                            // not the "resource font" variant, try the next one
                        }
                    }
                }
                if (arg != null) return (Style) m.invoke(Style.EMPTY, arg);
            }
        } catch (Exception ignored) {
            // fall back to the normal font
        }
        return null;
    }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, String s, int x, int y, int color) {
        Style st = style();
        if (st == null) g.text(mc.font, s, x, y, color);
        else g.text(mc.font, Component.literal(s).withStyle(st), x, y, color);
    }

    public static int width(Minecraft mc, String s) {
        Style st = style();
        return st == null ? mc.font.width(s) : mc.font.width(Component.literal(s).withStyle(st));
    }
}
