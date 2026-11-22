package com.chimi.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Safety net for payments: reads the lines that actually appeared in your chat window, so a "X paid you $Y" line is caught even if the
 * server delivered it in a way the normal message event doesn't report. Uses reflection, so it doesn't depend on exact game names.
 */
public final class ChatScanner {
    private ChatScanner() {}

    private static Field listField;
    private static Method contentMethod;
    private static Object lastSeen;
    private static boolean primed;
    private static boolean broken;

    public static void reset() {
        lastSeen = null;
        primed = false;
    }

    private static Object chatComponent(Minecraft mc) throws Exception {
        Object gui = mc.gui;
        Method m = gui.getClass().getMethod("getChat");
        return m.invoke(gui);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> messages(Object chat) throws Exception {
        if (listField == null) {
            for (Field f : chat.getClass().getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                List<Object> l = (List<Object>) f.get(chat);
                if (l == null || l.isEmpty()) continue;
                Object first = l.get(0);
                if (first == null || !first.getClass().getSimpleName().equals("GuiMessage")) continue;
                Method c = first.getClass().getMethod("content");
                if (!Component.class.isAssignableFrom(c.getReturnType())) continue;
                listField = f;
                contentMethod = c;
                break;
            }
            if (listField == null) return null; // chat is empty so far, try again later
        }
        return (List<Object>) listField.get(chat);
    }

    /** Sends every chat line that appeared since the last call to the consumer, oldest first. */
    public static void poll(Minecraft mc, Consumer<String> sink) {
        if (broken || mc.player == null) return;
        try {
            List<Object> all = messages(chatComponent(mc));
            if (all == null || all.isEmpty()) return;
            Object newest = all.get(0);
            if (!primed) { // don't re-read old history when joining
                primed = true;
                lastSeen = newest;
                return;
            }
            List<String> fresh = new ArrayList<>();
            for (int i = 0; i < all.size() && i < 30; i++) {
                Object m = all.get(i);
                if (m == lastSeen) break;
                fresh.add(((Component) contentMethod.invoke(m)).getString());
            }
            lastSeen = newest;
            Collections.reverse(fresh);
            for (String s : fresh) sink.accept(s);
        } catch (Exception e) {
            broken = true; // reflection didn't fit this version; the normal message event still works
        }
    }
}
