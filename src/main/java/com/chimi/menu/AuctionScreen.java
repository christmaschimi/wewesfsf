package com.chimi.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Auction set-up menu: search any item in the game, click its icon, set the timer, then start. Open with U (or from the main menu).
 * The item you pick (not something from your inventory) is what the auction HUD shows at the top of the screen.
 */
public class AuctionScreen extends Screen {
    public static final int[] TIMERS = {15, 30, 60, 120, 180, 300};
    private static final String[] TIMER_LABELS = {"15s", "30s", "1m", "2m", "3m", "5m"};

    private static final int COLS = 17, ROWS = 3, PER = COLS * ROWS, STRIDE = 18;

    /** One searchable item: its icon stack, display name and registry id. */
    private record Entry(Item item, ItemStack stack, String name, String search, String id) {}

    private static List<Entry> allItems;
    private static Map<String, Entry> byId;

    private static Entry selected;
    private static String query = "";
    private static boolean mineOnly;
    private static int page;
    private static List<Entry> results = new ArrayList<>();
    private static String displayName = "";
    private static String status = "";

    private record Btn(String label, int x, int y, int w, int h, boolean active, int color) {}
    private final List<Btn> btns = new ArrayList<>();
    private EditBox searchBox, nameBox, minBox;
    private int minX, minW;
    private int px, py, pw, ph;
    private int searchX, searchY, searchW, gridX, gridY, selY, nameX, nameY, nameW, timerY, row1Y, actionY, testY;
    private int pagerLeftX, pagerRightX;

    public AuctionScreen() { super(Component.literal("Auction")); }

    // ---------- item list ----------
    private static List<Entry> all() {
        if (allItems != null) return allItems;
        List<Entry> list = new ArrayList<>();
        Map<String, Entry> ids = new HashMap<>();
        for (Item it : BuiltInRegistries.ITEM) {
            if (it == Items.AIR) continue;
            try {
                ItemStack st = new ItemStack(it);
                if (st.isEmpty()) continue;
                String name = st.getHoverName().getString();
                String full = BuiltInRegistries.ITEM.getKey(it).toString();
                String id = full.startsWith("minecraft:") ? full.substring("minecraft:".length()) : full;
                String search = (name + " " + id.replace('_', ' ').replace(':', ' ')).toLowerCase(Locale.ROOT);
                Entry e = new Entry(it, st, name, search, id);
                list.add(e);
                ids.put(id, e);
            } catch (Exception ignored) {
                // an item that can't be turned into a stack is simply left out of the list
            }
        }
        list.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
        allItems = list;
        byId = ids;
        return list;
    }

    private static Entry selected() {
        if (selected == null && Config.I.aucItem != null && !Config.I.aucItem.isEmpty()) {
            all();
            selected = byId.get(Config.I.aucItem);
        }
        return selected;
    }

    private static Set<Item> inventoryItems() {
        Set<Item> set = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return set;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!s.isEmpty()) set.add(s.getItem());
        }
        return set;
    }

    private static void refilter() {
        String[] tok = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        Set<Item> mine = mineOnly ? inventoryItems() : null;
        List<Entry> out = new ArrayList<>();
        for (Entry e : all()) {
            if (mine != null && !mine.contains(e.item())) continue;
            boolean ok = true;
            for (String t : tok) {
                if (!t.isEmpty() && !e.search().contains(t)) { ok = false; break; }
            }
            if (ok) out.add(e);
        }
        if (!tok[0].isEmpty()) out.sort(Comparator.comparingInt(e -> e.search().startsWith(tok[0]) ? 0 : 1)); // stable: names that start with the search first
        results = out;
        page = Math.max(0, Math.min(page, pageCount() - 1));
    }

    private static int pageCount() { return Math.max(1, (results.size() + PER - 1) / PER); }

    private static void pick(int cell) {
        int idx = page * PER + cell;
        if (idx < 0 || idx >= results.size()) return;
        selected = results.get(idx);
        Config.I.aucItem = selected.id();
        Config.save();
        status = "";
    }

    // ---------- shared actions (also used by the K key) ----------
    /** Starts an auction from the saved settings, or ends the running one. */
    public static void quickToggle(Minecraft mc) {
        var a = ChimiMenuClient.auction;
        if (a != null && !a.isOver()) { a.endNow(); return; }
        start(mc);
    }

    public static void start(Minecraft mc) {
        if (mc.player == null) return;
        Entry sel = selected();
        String prize = !displayName.isBlank() ? displayName.trim() : sel != null ? sel.name() : "";
        if (prize.isEmpty()) { status = "Search for an item and click it first (or type a display name)."; return; }
        ChimiMenuClient.auctionStack = sel == null ? ItemStack.EMPTY : sel.stack().copy();
        int t = TIMERS[Math.floorMod(Config.I.aucTimerIdx, TIMERS.length)];
        ChimiMenuClient.queueAuction(prize, t, ChimiScreen.parseMoney(Config.I.aucMin), 0, 0);
        status = "Auction started: " + prize;
    }

    private static String payName(Minecraft mc) {
        return mc.player == null ? "<you>" : mc.player.getName().getString();
    }

    // ---------- layout ----------
    private void btn(String label, int x, int y, int w, int h, boolean active, Runnable r) {
        addRenderableWidget(Button.builder(Component.empty(), b -> r.run()).bounds(x, y, w, h).build());
        btns.add(new Btn(label, x, y, w, h, active, 0));
    }

    @Override
    protected void init() {
        if (MenuScale.apply(this)) { rebuildWidgets(); return; }
        btns.clear();
        pw = Math.min(width - 8, 340);
        ph = Math.min(height - 8, 238);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int ix = px + 10, iw = pw - 20, gap = 4;

        // search row
        searchX = ix; searchY = py + 19; searchW = iw - 58;
        searchBox = new EditBox(font, searchX + 5, searchY + 3, searchW - 10, 10, Component.empty());
        searchBox.setBordered(false);
        searchBox.setMaxLength(40);
        searchBox.setValue(query);
        searchBox.setResponder(s -> { query = s; page = 0; refilter(); });
        addRenderableWidget(searchBox);
        btn("Mine: " + (mineOnly ? "ON" : "OFF"), ix + iw - 54, searchY, 54, 14, mineOnly, () -> { mineOnly = !mineOnly; page = 0; refilter(); rebuildWidgets(); });

        // icon grid: the buttons stay put, what each one holds changes with the search and the page
        gridX = ix + (iw - COLS * STRIDE) / 2;
        gridY = py + 37;
        for (int i = 0; i < PER; i++) {
            final int cell = i;
            int x = gridX + (i % COLS) * STRIDE, y = gridY + (i / COLS) * STRIDE;
            addRenderableWidget(Button.builder(Component.empty(), b -> pick(cell)).bounds(x, y, STRIDE - 1, STRIDE - 1).build());
        }

        // selected item + pager
        selY = gridY + ROWS * STRIDE + 3;
        pagerRightX = ix + iw - 16;
        pagerLeftX = pagerRightX - 16 - 38;
        btn("<", pagerLeftX, selY, 16, 16, false, () -> { page = Math.max(0, page - 1); });
        btn(">", pagerRightX, selY, 16, 16, false, () -> { page = Math.min(pageCount() - 1, page + 1); });

        // display name (optional)
        nameX = ix + 70; nameY = selY + 19; nameW = iw - 70;
        nameBox = new EditBox(font, nameX + 5, nameY + 4, nameW - 10, 10, Component.empty());
        nameBox.setBordered(false);
        nameBox.setMaxLength(40);
        nameBox.setValue(displayName);
        nameBox.setResponder(s -> displayName = s);
        addRenderableWidget(nameBox);

        // timer
        timerY = nameY + 20;
        int tb = (iw - 70 - 5 * 4) / 6;
        for (int i = 0; i < TIMERS.length; i++) {
            final int idx = i;
            btn(TIMER_LABELS[i], ix + 70 + i * (tb + 4), timerY, tb, 16, Config.I.aucTimerIdx == i, () -> { Config.I.aucTimerIdx = idx; Config.save(); rebuildWidgets(); });
        }

        // settings row: theme | - HUD x + | board | alerts
        row1Y = timerY + 20;
        int bw = (iw - 3 * gap) / 4;
        btn("Theme: " + AuctionHud.theme().name(), ix, row1Y, bw, 16, false, () -> { Config.I.aucTheme = (Config.I.aucTheme + 1) % AuctionHud.THEMES.length; Config.save(); });
        int mw = 16, hudW = bw - 2 * mw - 4;
        btn("-", ix + bw + gap, row1Y, mw, 16, false, () -> { Config.I.aucHudScale = Math.max(5, Config.I.aucHudScale - 1); Config.save(); });
        btn("+", ix + bw + gap + mw + 2 + hudW + 2, row1Y, mw, 16, false, () -> { Config.I.aucHudScale = Math.min(30, Config.I.aucHudScale + 1); Config.save(); });
        btn("Board: Top " + Config.I.aucBoard, ix + 2 * (bw + gap), row1Y, bw, 16, false, () -> { Config.I.aucBoard = Config.I.aucBoard % 5 + 1; Config.save(); rebuildWidgets(); });
        btn("Bid alerts: " + (Config.I.aucAlerts ? "ON" : "OFF"), ix + 3 * (bw + gap), row1Y, bw, 16, Config.I.aucAlerts, () -> { Config.I.aucAlerts = !Config.I.aucAlerts; Config.save(); rebuildWidgets(); });

        // action row
        actionY = row1Y + 20;
        int sw = (iw - 3 * gap) * 34 / 100, ow = (iw - 3 * gap - sw) / 3;
        btn("> START AUCTION", ix, actionY, sw, 18, true, () -> start(minecraft));
        btn("END NOW", ix + sw + gap, actionY, ow, 18, false, () -> { var a = ChimiMenuClient.auction; if (a != null) a.endNow(); });
        btn("+15s", ix + sw + gap + ow + gap, actionY, ow, 18, false, () -> { var a = ChimiMenuClient.auction; if (a != null) a.extend(15); });
        btn("CANCEL", ix + sw + gap + 2 * (ow + gap), actionY, ow, 18, false, () -> { ChimiMenuClient.cancelAuction(); status = "Cancelled."; });

        // test row
        testY = actionY + 22;
        btn("Test Bid", ix, testY, 60, 14, false, AuctionHud::testBid);
        btn("Test Win", ix + 64, testY, 60, 14, false, () -> { AuctionHud.testWin(); onClose(); });
        btn("Test Cosmic", ix + 128, testY, 70, 14, false, () -> { AuctionHud.testCosmic(); onClose(); });

        // minimum payment: anything paid below this is skipped and never shows on the list
        minX = ix + 232; minW = ix + iw - minX;
        minBox = new EditBox(font, minX + 5, testY + 2, minW - 10, 10, Component.empty());
        minBox.setBordered(false);
        minBox.setMaxLength(14);
        minBox.setValue("0".equals(Config.I.aucMin) ? "" : Config.I.aucMin);
        minBox.setResponder(s -> { Config.I.aucMin = s.isBlank() ? "0" : s.trim(); Config.save(); });
        addRenderableWidget(minBox);

        refilter();
    }

    // ---------- render ----------
    private static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int border, int fill) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private String fit(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        AuctionHud.Theme th = AuctionHud.theme();
        int ix = px + 10, iw = pw - 20;
        box(g, px - 1, py - 1, pw + 2, ph + 2, th.accent(), th.bg());
        box(g, px, py, pw, ph, th.border(), th.bg());
        g.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, th.bg());

        String name = minecraft != null && minecraft.player != null ? payName(minecraft).toUpperCase() : "AUCTION";
        String title = "* " + name + " AUCTION *";
        g.text(font, title, px + (pw - font.width(title)) / 2, py + 7, th.accent());

        // search box background (the EditBox itself is drawn by super below)
        box(g, searchX, searchY, searchW, 14, th.border(), 0xFF000000);
        box(g, minX, testY, minW, 14, th.border(), 0xFF000000);

        super.extractRenderState(g, mx, my, delta);

        if (searchBox != null && searchBox.getValue().isEmpty()) {
            g.text(font, "Search items (diamond, elytra, totem...)", searchX + 5, searchY + 3, 0xFF5A6078);
        }

        // icon grid
        Entry hoverEntry = null;
        Entry sel = selected();
        for (int i = 0; i < PER; i++) {
            int x = gridX + (i % COLS) * STRIDE, y = gridY + (i / COLS) * STRIDE, s = STRIDE - 1;
            int idx = page * PER + i;
            boolean has = idx < results.size();
            boolean hover = has && mx >= x && mx < x + s && my >= y && my < y + s;
            g.fill(x, y, x + s, y + s, hover ? 0xFF3A4470 : 0xFF1A2236);
            if (!has) continue;
            Entry e = results.get(idx);
            g.item(e.stack(), x + 1, y + 1);
            if (hover) hoverEntry = e;
            if (sel != null && e.item() == sel.item()) {
                g.fill(x, y, x + s, y + 1, th.accent());
                g.fill(x, y + s - 1, x + s, y + s, th.accent());
                g.fill(x, y, x + 1, y + s, th.accent());
                g.fill(x + s - 1, y, x + s, y + s, th.accent());
            }
        }
        if (results.isEmpty()) {
            String none = mineOnly ? "None of your items match. Turn off \"Mine\" to search every item." : "No items match that search.";
            g.text(font, none, gridX + (COLS * STRIDE - font.width(none)) / 2, gridY + ROWS * STRIDE / 2 - 4, 0xFF9AA4C0);
        }

        // selected item
        if (sel != null) {
            g.item(sel.stack(), ix, selY);
            g.text(font, fit("Selected: " + sel.name(), pagerLeftX - ix - 26), ix + 20, selY + 4, th.accent());
        } else {
            g.text(font, "Click an item above to select it", ix, selY + 4, 0xFF9AA4C0);
        }
        String pl = (page + 1) + "/" + pageCount();
        g.text(font, pl, pagerLeftX + 16 + (38 - font.width(pl)) / 2, selY + 4, 0xFFE0E0E0);

        g.text(font, "Display name", ix, nameY + 4, 0xFF9AA4C0);
        box(g, nameX, nameY, nameW, 16, th.border(), 0xFF000000);
        if (nameBox != null && nameBox.getValue().isEmpty()) {
            g.text(font, "Optional - leave empty to use the item name", nameX + 5, nameY + 4, 0xFF5A6078);
        }
        g.text(font, "Timer", ix, timerY + 4, 0xFF9AA4C0);
        g.text(font, "Min $", minX - 4 - font.width("Min $"), testY + 3, 0xFF9AA4C0);
        if (minBox != null && minBox.getValue().isEmpty()) g.text(font, "none (e.g. 500K)", minX + 5, testY + 3, 0xFF5A6078);

        for (Btn b : btns) {
            boolean hover = mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h();
            int fill = b.active() ? mixWhite(th.panel(), 0.12f) : 0xFF4A4A4A;
            if (hover) fill = mixWhite(fill, 0.15f);
            int border = b.active() ? th.accent() : 0xFF1C1C1C;
            box(g, b.x(), b.y(), b.w(), b.h(), border, fill);
            int color = b.label().contains("START") ? 0xFF5CE08A
                    : b.label().contains("END") ? 0xFFFFB347
                    : b.label().contains("CANCEL") ? 0xFFFF5555
                    : b.active() ? th.accent() : 0xFFE0E0E0;
            if (b.label().equals("-") || b.label().equals("+") || b.label().equals("<") || b.label().equals(">")) color = 0xFFFFFFFF;
            g.text(font, b.label(), b.x() + (b.w() - font.width(b.label())) / 2, b.y() + (b.h() - 8) / 2, color);
        }
        // HUD scale label between - and +
        int bw = (iw - 12) / 4, mw = 16;
        int hx = ix + bw + 4 + mw, hudW = bw - 2 * mw - 4;
        String hs = "HUD " + (Config.I.aucHudScale / 10) + "." + (Config.I.aucHudScale % 10) + "x";
        g.text(font, hs, hx + (hudW + 4 - font.width(hs)) / 2, row1Y + 4, 0xFFE0E0E0);

        String line = status.isEmpty()
                ? "Ready. Viewers bid with /pay " + (minecraft == null ? "" : payName(minecraft)) + " <amount>"
                : status;
        var live = ChimiMenuClient.auction;
        if (live != null && !live.isOver() && status.isEmpty()) {
            long sl = live.secondsLeft();
            line = "LIVE " + sl / 60 + ":" + String.format("%02d", sl % 60) + " - " + live.bidders() + " bids"
                    + (live.minBid > 0 ? " - min $" + EconomyCore.format(live.minBid) : "");
            var top = live.leaderboard();
            if (!top.isEmpty()) line += " - * " + top.get(0).getKey() + "  $" + EconomyCore.format(top.get(0).getValue()) + " *";
        }
        g.text(font, fit(line, pw - 20), px + (pw - Math.min(pw - 20, font.width(line))) / 2, py + ph - 24, 0xFFFFFFFF);
        String last = ChimiMenuClient.lastPaidLine;
        if (last.isEmpty()) {
            String keys = "Keys: U menu - K start/end";
            g.text(font, keys, px + (pw - font.width(keys)) / 2, py + ph - 13, 0xFF6A7390);
        } else {
            String s = fit((ChimiMenuClient.lastPaidParsed ? "Last payment: " : "Could not read this line: ") + last, pw - 20);
            g.text(font, s, px + (pw - font.width(s)) / 2, py + ph - 13, ChimiMenuClient.lastPaidParsed ? 0xFF5CE08A : 0xFFFFB347);
        }

        // item name tooltip
        if (hoverEntry != null) {
            String t = hoverEntry.name();
            int tw = font.width(t) + 8;
            int tx = Math.min(mx + 8, width - tw - 2), ty = Math.max(2, my - 16);
            box(g, tx, ty, tw, 14, th.accent(), 0xF0101018);
            g.text(font, t, tx + 4, ty + 3, 0xFFFFFFFF);
        }
    }

    private static int mixWhite(int c, float t) {
        int r = (int) (((c >> 16) & 255) * (1 - t) + 255 * t), gg = (int) (((c >> 8) & 255) * (1 - t) + 255 * t), b = (int) ((c & 255) * (1 - t) + 255 * t);
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    @Override
    public void removed() { MenuScale.restore(); super.removed(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
