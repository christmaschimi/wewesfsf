package com.chimi.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chimi's Menu: sidebar of pages, rounded cards, themes. All custom-drawn on top of invisible click areas. */
public class ChimiScreen extends Screen {
    private static final String[] PAGES = {"Overview", "Auction", "Profits", "Customize", "Inventory", "Sky", "Spotify", "Outline", "Crosshair", "Style"};
    /** Sidebar order (page numbers stay the same): Style sits right under Customize. */
    private static final int[] ORDER = {0, 1, 2, 3, 9, 4, 5, 6, 7, 8};
    private static final String[] SUBTITLES = {
            "Your day at a glance - money in, money out.",
            "Run an auction in chat - bids come in as payments and the top bid wins.",
            "Live session profit, balance and history.",
            "Theme every HUD and edit its text.",
            "Pick a background for your inventory screen.",
            "Pick a sky preset for the world.",
            "See what's playing and skip songs.",
            "Style the highlight on the block you look at.",
            "Pick a crosshair, colour and size.",
            "Shape and font for every HUD."};
    private static final int[] DURS = {30, 60, 120, 300, 600, 1800, 3600};
    private static final int[] ALPHAS = {120, 160, 200, 235};
    private static final String[] CORNERS = {"Top left", "Top right", "Bottom left", "Bottom right"};
    private static final int[] DOTS = {
            0xFFFFD166, 0xFFFFB347, 0xFFFF8C42, 0xFFFF6B6B, 0xFFFF5C8A, 0xFFE879F9, 0xFFB48CFF, 0xFF8F9BFF, 0xFF6EA8FF, 0xFF5CD6FF, 0xFF6FE3E0, 0xFF7CFF6B,
            0xFFFFE9A8, 0xFFFFD0B0, 0xFFFFB0B0, 0xFFFFB0D0, 0xFFF0C0FF, 0xFFD0C0FF, 0xFFC0D0FF, 0xFFB0E8FF, 0xFFB0FFE8, 0xFFD0FFB0, 0xFFF2F2F2, 0xFFA0A0A0};

    public record Theme(String name, int accent, int bg, int panel, int card, int border) {}
    public static final Theme[] THEMES = {
            new Theme("Amethyst", 0xFFB48CFF, 0xFF14102E, 0xFF1D1A4A, 0xFF262266, 0xFF4A43B0),
            new Theme("Midnight", 0xFF8FB8FF, 0xFF0B1020, 0xFF121A33, 0xFF1A2547, 0xFF33467A),
            new Theme("Ruby", 0xFFFF6B7D, 0xFF1E0E14, 0xFF2C1520, 0xFF3A1B2A, 0xFF7A3347),
            new Theme("Ocean", 0xFF6FE3E0, 0xFF08202A, 0xFF0F2F3D, 0xFF163F50, 0xFF2F7A8A),
            new Theme("Sunset", 0xFFFFC857, 0xFF24120E, 0xFF33201A, 0xFF442A22, 0xFF8A5535),
            new Theme("Mono", 0xFFF2F2F2, 0xFF101010, 0xFF1A1A1A, 0xFF242424, 0xFF4A4A4A)};

    private static final int TXT = 0xFFEDEBFF, DIM = 0xFF9A97C8, GOOD = 0xFF5CE08A, BAD = 0xFFFF6B6B;
    private static final int SB = 92, TOP = 26, BOT = 24;
    private static final Pattern MONEY = Pattern.compile("\\$?\\s*([\\d,]*\\.?\\d+)\\s*([kKmMbB])?");

    // remembered between opens
    private static int page = 0;
    private static int selectedSlot = -1;
    private static int durIdx = 0;
    private static int galleryPage = 0;
    private static String auctionName = "";
    private static String startBid = "500K";
    private static String worth = "50M";
    private static String delaySec = "0";
    private static String itemSearch = "";
    private static String notice = "";

    private record Btn(String label, int x, int y, int w, int h, boolean active, int color) {}
    private record Rect(int x, int y, int w, int h) {}
    private record Txt(String s, int x, int y, int color) {}
    private record Hint(EditBox box, String s, int x, int y) {}
    private record ThumbCell(String name, int x, int y, int w, int h) {}

    /** Invisible drag handler (vanilla slider logic); the visible track is drawn by drawSizeSlider. */
    private static final class SizeSlider extends AbstractSliderButton {
        SizeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(),
                    (Crosshair.radius() - Crosshair.MIN_RADIUS) / (double) (Crosshair.MAX_RADIUS - Crosshair.MIN_RADIUS));
        }
        @Override protected void updateMessage() { setMessage(Component.empty()); }
        @Override protected void applyValue() {
            Config.I.crossRadius = Crosshair.MIN_RADIUS + (int) Math.round(this.value * (Crosshair.MAX_RADIUS - Crosshair.MIN_RADIUS));
        }
    }
    private Rect sizeSlider;

    private final List<Btn> btns = new ArrayList<>();
    private final List<Rect> cards = new ArrayList<>();
    private final List<Rect> fields = new ArrayList<>();
    private final List<Txt> texts = new ArrayList<>();
    private final List<Hint> hints = new ArrayList<>();
    private final List<int[]> cells = new ArrayList<>();       // {slot, x, y}
    private final List<ThumbCell> thumbCells = new ArrayList<>();
    private List<String> galleryNames = new ArrayList<>();

    private int px, py, pw, ph, cx, cy, cw, ch;

    public ChimiScreen() { super(Component.literal("Chimi's Menu")); }

    // ---------- helpers ----------
    private static Theme theme() {
        for (Theme t : THEMES) if (t.name().equals(Config.I.theme)) return t;
        return THEMES[0];
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static void rr(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x + 2, y, x + w - 2, y + h, c);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, c);
        g.fill(x, y + 2, x + w, y + h - 2, c);
    }

    private static void rrBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int border, int fill) {
        rr(g, x, y, w, h, border);
        rr(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    private static int parseInt(String s, int dflt) {
        try { return Math.max(0, Integer.parseInt(s.trim())); } catch (NumberFormatException e) { return dflt; }
    }

    static double parseMoney(String s) {
        Matcher m = MONEY.matcher(s == null ? "" : s);
        if (!m.find()) return 0;
        try { return EconomyCore.parseAmount(m.group(1), m.group(2)); } catch (Exception e) { return 0; }
    }

    private static String fmtTime(int s) {
        return (s >= 3600 ? (s / 3600) + "h " : "") + ((s % 3600) / 60) + "m " + (s % 60) + "s";
    }

    private static String fmtDur(int s) { return s < 60 ? s + "s" : s < 3600 ? (s / 60) + "m" : (s / 3600) + "h"; }

    private static String signed(double v) { return (v >= 0 ? "+" : "") + EconomyCore.format(v); }

    private void btn(String label, int x, int y, int w, int h, boolean active, Runnable r) {
        addRenderableWidget(Button.builder(Component.empty(), b -> r.run()).bounds(x, y, w, h).build());
        btns.add(new Btn(label, x, y, w, h, active, 0));
    }

    private void swatch(int color, int x, int y, int size, boolean active, Runnable r) {
        addRenderableWidget(Button.builder(Component.empty(), b -> r.run()).bounds(x, y, size, size).build());
        btns.add(new Btn("", x, y, size, size, active, color));
    }

    private void card(int x, int y, int w, int h) { cards.add(new Rect(x, y, w, h)); }

    private void text(String s, int x, int y, int color) { texts.add(new Txt(s, x, y, color)); }

    private EditBox field(int x, int y, int w, int h, String value, String hint, Consumer<String> onChange) {
        fields.add(new Rect(x, y, w, h));
        EditBox b = new EditBox(font, x + 5, y + (h - 8) / 2, w - 10, 10, Component.empty());
        b.setBordered(false);
        b.setMaxLength(64);
        b.setValue(value);
        b.setResponder(onChange);
        addRenderableWidget(b);
        if (hint != null) hints.add(new Hint(b, hint, x + 5, y + (h - 8) / 2));
        return b;
    }

    // ---------- build ----------
    @Override
    protected void init() {
        if (MenuScale.apply(this)) { rebuildWidgets(); return; }
        sizeSlider = null; btns.clear(); cards.clear(); fields.clear(); texts.clear(); hints.clear(); cells.clear(); thumbCells.clear();
        pw = Math.min(width - 12, 560);
        ph = Math.min(height - 12, 320);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + SB + 10;
        cy = py + TOP + 8;
        cw = pw - SB - 20;
        ch = ph - TOP - BOT - 16;

        for (int k = 0; k < ORDER.length; k++) {
            final int idx = ORDER[k];
            btn("# " + PAGES[idx], px + 8, py + TOP + 14 + k * 19, SB - 16, 16, page == idx, () -> { page = idx; notice = ""; rebuildWidgets(); });
        }
        btn("x", px + pw - 22, py + 6, 16, 14, false, this::onClose);

        switch (page) {
            case 0 -> buildOverview();
            case 1 -> buildAuction();
            case 2 -> buildProfits();
            case 3 -> buildCustomize();
            case 4 -> buildInventory();
            case 5 -> buildSky();
            case 6 -> buildSpotify();
            case 7 -> buildOutline();
            case 8 -> buildCrosshair();
            case 9 -> buildStyle();
        }
    }

    /** A row of colour swatches (wraps). Returns the height used. */
    private int paletteRow(int x, int y, int width, String[] names, String current, Consumer<String> set) {
        int sz = 12, gap = 3, per = Math.max(1, (width + gap) / (sz + gap));
        for (int i = 0; i < names.length; i++) {
            final String n = names[i];
            swatch(Palette.swatchColor(n), x + (i % per) * (sz + gap), y + (i / per) * (sz + gap), sz, n.equals(current),
                    () -> { set.accept(n); Config.save(); rebuildWidgets(); });
        }
        return ((names.length + per - 1) / per) * (sz + gap);
    }

    /** A row of equal-width option buttons. */
    private void optionRow(int x, int y, int width, String[] labels, String current, Consumer<String> set) {
        int gap = 4, bw = (width - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            final String l = labels[i];
            btn(l, x + i * (bw + gap), y, bw, 16, l.equals(current), () -> { set.accept(l); Config.save(); rebuildWidgets(); });
        }
    }

    private String fit(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private void buildSpotify() {
        int lw = (int) (cw * 0.6), rx = cx + lw + 8, rw = cw - lw - 8;
        card(cx, cy, lw, ch);
        card(rx, cy, rw, ch);
        int x = cx + 8;
        text("Now playing", x, cy + 6, TXT);
        btn("|<", x, cy + 62, 34, 16, false, () -> SpotifyBridge.send("prev"));
        btn("Play/Pause", x + 38, cy + 62, 70, 16, false, () -> SpotifyBridge.send("play"));
        btn("Skip >|", x + 112, cy + 62, 54, 16, true, () -> SpotifyBridge.send("next"));
        btn("Tracking: " + (Config.I.spotifyEnabled ? "On" : "Off"), x, cy + 84, 84, 16, Config.I.spotifyEnabled,
                () -> { Config.I.spotifyEnabled = !Config.I.spotifyEnabled; Config.save(); rebuildWidgets(); });
        btn("HUD: " + (Config.I.spotifyHud ? "On" : "Off"), x + 88, cy + 84, 56, 16, Config.I.spotifyHud,
                () -> { Config.I.spotifyHud = !Config.I.spotifyHud; Config.save(); rebuildWidgets(); });
        text("Text colour: " + Config.I.spotifyColor, x, cy + 110, DIM);
        paletteRow(x, cy + 122, lw - 16, Palette.NAMES, Config.I.spotifyColor, s -> Config.I.spotifyColor = s);

        int rx8 = rx + 8, hw = (rw - 16 - 4) / 2;
        text("HUD corner", rx8, cy + 6, TXT);
        for (int i = 0; i < CORNERS.length; i++) {
            final int c = i;
            btn(CORNERS[i], rx8 + (i % 2) * (hw + 4), cy + 18 + (i / 2) * 20, hw, 16, Config.I.spotifyCorner == c,
                    () -> { Config.I.spotifyCorner = c; Config.save(); rebuildWidgets(); });
        }
        text("Text when nothing plays", rx8, cy + 62, TXT);
        field(rx8, cy + 74, rw - 16, 16, Config.I.spotifyIdleText, null, s -> { Config.I.spotifyIdleText = s; Config.save(); });
        text("Windows desktop app only.", rx8, cy + 100, DIM);
        text("Skip uses your media keys.", rx8, cy + 111, DIM);
        text("Keys: ] next, [ previous", rx8, cy + 122, DIM);
        text("(change in Controls).", rx8, cy + 133, DIM);
        text("Song/paused text: Customize.", rx8, cy + 144, DIM);
    }

    private void buildOutline() {
        int lw = (int) (cw * 0.6), rx = cx + lw + 8, rw = cw - lw - 8;
        card(cx, cy, lw, ch);
        card(rx, cy, rw, ch);
        int x = cx + 8, w = lw - 16;
        btn("Block outline: " + (Config.I.outlineOn ? "On" : "Off"), x, cy + 6, 116, 16, Config.I.outlineOn,
                () -> { Config.I.outlineOn = !Config.I.outlineOn; Config.save(); rebuildWidgets(); });
        text("Style", x, cy + 28, DIM);
        optionRow(x, cy + 38, w, BlockOutline.STYLES, Config.I.outlineStyle, s -> Config.I.outlineStyle = s);
        text("Thickness", x, cy + 60, DIM);
        optionRow(x, cy + 70, w, BlockOutline.THICKNESS, Config.I.outlineThickness, s -> Config.I.outlineThickness = s);
        text("Fill strength (the inside)", x, cy + 92, DIM);
        optionRow(x, cy + 102, w, BlockOutline.FILL, Config.I.outlineFill, s -> Config.I.outlineFill = s);
        text("Outline colour: " + Config.I.outlineColor, x, cy + 124, DIM);
        int h1 = paletteRow(x, cy + 134, w, Palette.NAMES, Config.I.outlineColor, s -> Config.I.outlineColor = s);
        int y2 = cy + 134 + h1 + 6;
        text("Fill colour: " + Config.I.outlineFillColor, x, y2, DIM);
        paletteRow(x, y2 + 10, w, Palette.NAMES, Config.I.outlineFillColor, s -> Config.I.outlineFillColor = s);

        int rx8 = rx + 8;
        text("Preview", rx8, cy + 6, TXT);
        text("Drawn on the block you", rx8, cy + 118, DIM);
        text("look at. Minecraft's own", rx8, cy + 129, DIM);
        text("thin outline stays under", rx8, cy + 140, DIM);
        text("it.", rx8, cy + 151, DIM);
    }

    private void buildCrosshair() {
        int lw = (int) (cw * 0.6), rx = cx + lw + 8, rw = cw - lw - 8;
        card(cx, cy, lw, ch);
        card(rx, cy, rw, ch);
        int x = cx + 8, w = lw - 16;
        text("Style", x, cy + 6, TXT);
        int bw = (w - 8) / 3;
        for (int i = 0; i < Crosshair.STYLES.length; i++) {
            final String s = Crosshair.STYLES[i];
            btn(s, x + (i % 3) * (bw + 4), cy + 18 + (i / 3) * 19, bw, 16, s.equals(Config.I.crossStyle),
                    () -> { Config.I.crossStyle = s; Config.save(); rebuildWidgets(); });
        }
        sizeSlider = new Rect(x, cy + 128, w, 12);
        addRenderableWidget(new SizeSlider(x, cy + 128, w, 12));
        text("Colour: " + Config.I.crossColor, x, cy + 148, DIM);
        paletteRow(x, cy + 158, w, Palette.CROSSHAIR, Config.I.crossColor, s -> Config.I.crossColor = s);

        int rx8 = rx + 8;
        text("Preview", rx8, cy + 6, TXT);
        text("Vanilla = normal", rx8, cy + 118, DIM);
        text("Minecraft crosshair.", rx8, cy + 129, DIM);
    }

    private void sessionButtons(int x, int y) {
        boolean on = ChimiMenuClient.session.isActive();
        btn(on ? "Stop session" : "Start session", x, y, 92, 16, on, () -> {
            if (ChimiMenuClient.session.isActive()) ChimiMenuClient.stopSession(); else ChimiMenuClient.startSession();
            rebuildWidgets();
        });
        btn("HUD: " + (Config.I.hudEnabled ? "On" : "Off"), x + 98, y, 62, 16, Config.I.hudEnabled, () -> { Config.I.hudEnabled = !Config.I.hudEnabled; Config.save(); rebuildWidgets(); });
        btn("Send /bal", x + 166, y, 70, 16, false, () -> BalanceTracker.refresh(minecraft));
    }

    private int statW() { return (cw - 16) / 3; }

    private void buildOverview() {
        for (int i = 0; i < 3; i++) card(cx + i * (statW() + 8), cy + 28, statW(), 36);
        int listY = cy + 70, listH = ch - 70 - 24;
        card(cx, listY, cw, listH);
        text("Recent payments", cx + 8, listY + 6, TXT);
        sessionButtons(cx, cy + ch - 16);
    }

    private void buildAuction() {
        card(cx, cy, cw, 60);
        text("The auction now has its own menu.", cx + 8, cy + 8, TXT);
        text("Pick an item, set the timer and start. Shortcut: U to open, K to start/end.", cx + 8, cy + 21, DIM);
        btn("Open auction menu", cx + 8, cy + 36, 120, 16, true, () -> { if (minecraft != null) minecraft.gui.setScreen(new AuctionScreen()); });
    }

    private void startAuction() {
        if (minecraft == null || minecraft.player == null) return;
        ItemStack s = selectedSlot >= 0 ? minecraft.player.getInventory().getItem(selectedSlot) : ItemStack.EMPTY;
        String prize = !auctionName.isBlank() ? auctionName.trim()
                : !s.isEmpty() ? s.getCount() + "x " + s.getHoverName().getString() : "";
        if (prize.isEmpty()) { notice = "Pick an item or type what you're selling"; return; }
        ChimiMenuClient.auctionStack = s.isEmpty() ? ItemStack.EMPTY : s.copy();
        ChimiMenuClient.queueAuction(prize, DURS[durIdx], parseMoney(startBid), parseMoney(worth), parseInt(delaySec, 0));
        notice = "";
    }

    private void announce() {
        var a = ChimiMenuClient.auction;
        if (minecraft == null || minecraft.player == null || a == null) { notice = "Start an auction first"; return; }
        String msg = a.isOver()
                ? a.winner().map(w -> "Auction over: " + w.getKey() + " won " + a.prize).orElse("Auction over: no bids")
                : "Auction: " + a.prize + " | start " + EconomyCore.format(a.minBid) + " | " + fmtTime((int) a.secondsLeft()) + " left. Pay me to bid!";
        minecraft.player.connection.sendChat(msg); // one message per click, never automatic
    }

    private void buildProfits() {
        sessionButtons(cx, cy);
        card(cx, cy + 22, cw, 56);
        card(cx, cy + 84, cw, ch - 84);
        text("Profit history", cx + 8, cy + 90, TXT);
    }

    private void buildCustomize() {
        int lw = (int) (cw * 0.6), rx = cx + lw + 8, rw = cw - lw - 8;
        card(cx, cy, lw, ch);
        text("HUD theme", cx + 8, cy + 6, TXT);
        text("Theme every HUD at once.", cx + 8, cy + 18, DIM);
        int bw = (lw - 16 - 6) / 2;
        for (int i = 0; i < THEMES.length; i++) {
            final Theme t = THEMES[i];
            int x = cx + 8 + (i % 2) * (bw + 6), y = cy + 30 + (i / 2) * 22;
            btn("    " + t.name(), x, y, bw, 18, t.name().equals(Config.I.theme), () -> {
                Config.I.theme = t.name();
                Config.I.accent = t.accent();
                Config.save();
                rebuildWidgets();
            });
        }
        int dy = cy + 30 + 3 * 22 + 6;
        text("Theme colour", cx + 8, dy, TXT);
        int dsz = Math.max(8, Math.min(12, (lw - 16 - 11 * 3) / 12));
        for (int i = 0; i < DOTS.length; i++) {
            final int color = DOTS[i];
            swatch(color, cx + 8 + (i % 12) * (dsz + 3), dy + 12 + (i / 12) * (dsz + 3), dsz, color == Config.I.accent,
                    () -> { Config.I.accent = color; Config.save(); rebuildWidgets(); });
        }
        // Editable HUD texts
        int ty = dy + 12 + 2 * (dsz + 3) + 6;
        text("Profit text   {profit} {perhour}", cx + 8, ty, DIM);
        field(cx + 8, ty + 10, lw - 16, 16, Config.I.profitText, null, s -> { Config.I.profitText = s; Config.save(); });
        text("Now playing text   {title} {artist}", cx + 8, ty + 32, DIM);
        field(cx + 8, ty + 42, lw - 16, 16, Config.I.spotifyPlayingText, null, s -> { Config.I.spotifyPlayingText = s; Config.save(); });
        text("Paused text   {title} {artist}", cx + 8, ty + 64, DIM);
        field(cx + 8, ty + 74, lw - 16, 16, Config.I.spotifyPausedText, null, s -> { Config.I.spotifyPausedText = s; Config.save(); });

        card(rx, cy, rw, ch);
        text("Panel opacity", rx + 8, cy + 6, TXT);
        int hw = (rw - 16 - 4) / 2;
        for (int i = 0; i < ALPHAS.length; i++) {
            final int a = ALPHAS[i];
            btn((a * 100 / 255) + "%", rx + 8 + (i % 2) * (hw + 4), cy + 18 + (i / 2) * 20, hw, 16, Config.I.panelAlpha == a,
                    () -> { Config.I.panelAlpha = a; Config.save(); rebuildWidgets(); });
        }
        text("HUD corner", rx + 8, cy + 62, TXT);
        for (int i = 0; i < CORNERS.length; i++) {
            final int c = i;
            btn(CORNERS[i], rx + 8 + (i % 2) * (hw + 4), cy + 74 + (i / 2) * 20, hw, 16, Config.I.hudCorner == c,
                    () -> { Config.I.hudCorner = c; Config.save(); rebuildWidgets(); });
        }
        text("Menu size", rx + 8, cy + 120, TXT);
        for (int i = 0; i < MenuScale.OPTIONS.length; i++) {
            final int pct = MenuScale.OPTIONS[i];
            btn(pct + "%", rx + 8 + (i % 2) * (hw + 4), cy + 132 + (i / 2) * 20, hw, 16, Config.I.menuScale == pct,
                    () -> { Config.I.menuScale = pct; Config.save(); rebuildWidgets(); });
        }
        btn("Edit HUD positions", rx + 8, cy + 196, rw - 16, 16, true,
                () -> { if (minecraft != null) minecraft.gui.setScreen(new HudEditorScreen()); });
    }

    private void buildStyle() {
        int lw = (int) (cw * 0.55), rx = cx + lw + 8, rw = cw - lw - 8;
        card(cx, cy, lw, ch);
        card(rx, cy, rw, ch);
        text("HUD shape", cx + 8, cy + 6, TXT);
        int bw = (lw - 16 - 6) / 2;
        for (int i = 0; i < HudShape.SHAPES.length; i++) {
            final String n = HudShape.SHAPES[i];
            btn(n, cx + 8 + (i % 2) * (bw + 6), cy + 18 + (i / 2) * 20, bw, 16, n.equals(Config.I.hudShape),
                    () -> { Config.I.hudShape = n; Config.save(); rebuildWidgets(); });
        }
        int fy = cy + 18 + 3 * 20 + 8;
        text("HUD font", cx + 8, fy, TXT);
        for (int i = 0; i < HudText.FONTS.length; i++) {
            final String n = HudText.FONTS[i];
            btn(n, cx + 8 + (i % 2) * (bw + 6), fy + 12 + (i / 2) * 20, bw, 16, n.equals(Config.I.hudFont),
                    () -> { Config.I.hudFont = n; Config.save(); rebuildWidgets(); });
        }
        text("Applies to the profit, auction, Spotify and win HUDs.", cx + 8, fy + 12 + 4 * 20 + 2, DIM);
        text("Preview", rx + 8, cy + 6, TXT);
    }

    private void drawStyle(GuiGraphicsExtractor g, int accent, Theme th) {
        int lw = (int) (cw * 0.55), rx = cx + lw + 8, rw = cw - lw - 8;
        int x = rx + 8, y = cy + 22, w = rw - 16;
        g.fill(x - 1, y - 1, x + w + 1, y + 112, th.border());
        g.fill(x, y, x + w, y + 111, 0xFF1C2230);
        String[] lines = {"+12.4M profit", "#1 Alex   $750.00M", "* Alex  $150.00M *", "Cosmic Win"};
        int[] colors = {GOOD, 0xFFFFFFFF, 0xFFFFD84A, accent};
        for (int i = 0; i < lines.length; i++) {
            int bw = Math.min(w - 10, HudText.width(minecraft, lines[i]) + 12);
            int bx = x + 5, by = y + 6 + i * 26;
            HudShape.box(g, bx, by, bw, 18, accent, 0xDD0B1020);
            HudText.draw(g, minecraft, lines[i], bx + 6, by + 5, colors[i]);
        }
    }

    private void buildInventory() {
        int x = cx;
        btn("Add image", x, cy, 70, 16, true, () -> {
            try { FilePicker.pickImage().ifPresent(this::importFile); }
            catch (Throwable t) { notice = "File picker unavailable. Drag an image onto this window."; }
        });
        btn("Clear", x + 74, cy, 44, 16, false, () -> { BackgroundManager.select(""); rebuildWidgets(); });
        btn("Delete", x + 122, cy, 48, 16, false, () -> { BackgroundManager.delete(Config.I.bgImage); rebuildWidgets(); });
        btn("Enabled: " + (Config.I.bgEnabled ? "On" : "Off"), x + 174, cy, 74, 16, Config.I.bgEnabled,
                () -> { Config.I.bgEnabled = !Config.I.bgEnabled; Config.save(); rebuildWidgets(); });
        btn("Dark: " + (Config.I.bgDim * 100 / 255) + "%", x + 252, cy, 66, 16, false, () -> {
            Config.I.bgDim = Config.I.bgDim >= 200 ? 0 : Config.I.bgDim + 40; Config.save(); rebuildWidgets();
        });
        btn(Config.I.bgAllScreens ? "Scope: All screens" : "Scope: Inventory only", x, cy + 20, 130, 16, false,
                () -> { Config.I.bgAllScreens = !Config.I.bgAllScreens; Config.save(); rebuildWidgets(); });

        galleryNames = BackgroundManager.list();
        int cols = 4, gap = 6, tw = (cw - gap * (cols - 1)) / cols, th = 44, cellH = th + 14;
        int rows = Math.max(1, Math.min(2, (ch - 44 - 14) / cellH));
        int perPage = cols * rows;
        int pages = Math.max(1, (galleryNames.size() + perPage - 1) / perPage);
        galleryPage = Math.max(0, Math.min(galleryPage, pages - 1));
        btn("< Prev", cx + cw - 110, cy + 20, 52, 16, false, () -> { galleryPage = Math.max(0, galleryPage - 1); rebuildWidgets(); });
        btn("Next >", cx + cw - 54, cy + 20, 52, 16, false, () -> { galleryPage = Math.min(pages - 1, galleryPage + 1); rebuildWidgets(); });
        for (int i = 0; i < perPage; i++) {
            int idx = galleryPage * perPage + i;
            if (idx >= galleryNames.size()) break;
            final String name = galleryNames.get(idx);
            int x2 = cx + (i % cols) * (tw + gap), y2 = cy + 44 + (i / cols) * cellH;
            thumbCells.add(new ThumbCell(name, x2, y2, tw, th));
            addRenderableWidget(Button.builder(Component.empty(), b -> { BackgroundManager.select(name); rebuildWidgets(); })
                    .bounds(x2, y2, tw, th + 12).build());
        }
    }

    private void buildSky() {
        int lw = (int) (cw * 0.6), rx = cx + lw + 8, rw = cw - lw - 8;
        card(cx, cy, lw, ch);
        text("Sky preset", cx + 8, cy + 6, TXT);
        int bw = (lw - 16 - 12) / 3;
        for (int i = 0; i < SkyOverlay.PRESETS.length; i++) {
            final String name = SkyOverlay.PRESETS[i].name();
            int x = cx + 8 + (i % 3) * (bw + 6), y = cy + 20 + (i / 3) * 24;
            btn("    " + name, x, y, bw, 20, name.equals(Config.I.skyPreset), () -> { Config.I.skyPreset = name; Config.save(); rebuildWidgets(); });
        }
        btn("Custom sky: " + (Config.I.skyOverlay ? "On" : "Off"), cx + 8, cy + 96, 120, 16, Config.I.skyOverlay,
                () -> { Config.I.skyOverlay = !Config.I.skyOverlay; Config.save(); rebuildWidgets(); });
        card(rx, cy, rw, ch);
        text("About", rx + 8, cy + 6, TXT);
        text("Replaces the sky you see", rx + 8, cy + 20, DIM);
        text("in the overworld (first", rx + 8, cy + 31, DIM);
        text("person). Hills, trees and", rx + 8, cy + 42, DIM);
        text("roofs stay in front of it.", rx + 8, cy + 53, DIM);
        text("Uses your game FOV.", rx + 8, cy + 69, DIM);
    }

    private void importFile(java.nio.file.Path p) {
        try {
            String name = BackgroundManager.importImage(p);
            notice = "Added " + name;
        } catch (Exception e) {
            notice = "Couldn't import: " + e.getMessage();
        }
        rebuildWidgets();
    }

    @Override
    public void onFilesDrop(List<java.nio.file.Path> paths) {
        if (page == 4 && !paths.isEmpty()) importFile(paths.get(0));
    }

    // ---------- render ----------
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme th = theme();
        int accent = Config.I.accent;
        int panelFill = (Config.I.panelAlpha << 24) | (th.panel() & 0xFFFFFF);
        rrBorder(g, px - 1, py - 1, pw + 2, ph + 2, th.border(), th.bg());
        rr(g, px, py, pw, ph, panelFill);
        g.fill(px + 2, py + 2, px + pw - 2, py + 4, accent);
        g.fill(px + 2, py + TOP, px + SB, py + ph - 2, 0x33000000);
        g.fill(px + SB, py + TOP, px + SB + 1, py + ph - BOT, th.border());
        g.fill(px + 2, py + TOP, px + pw - 2, py + TOP + 1, th.border());
        g.fill(px + 2, py + ph - BOT, px + pw - 2, py + ph - BOT + 1, th.border());

        for (Rect r : cards) rrBorder(g, r.x(), r.y(), r.w(), r.h(), th.border(), th.card());
        for (Rect r : fields) rrBorder(g, r.x(), r.y(), r.w(), r.h(), th.border(), mix(th.bg(), 0xFF000000, 0.35f));

        super.extractRenderState(g, mouseX, mouseY, delta);

        g.text(font, "Chimi's Menu", px + 10, py + 9, accent);
        g.text(font, "CHANNELS", px + 10, py + TOP + 4, DIM);
        g.text(font, PAGES[page], cx, py + 9, TXT);
        g.text(font, "v1.3", px + pw - 48, py + 9, DIM);
        g.text(font, SUBTITLES[page], cx + font.width(PAGES[page]) + 10, py + 9, DIM);

        for (Btn b : btns) drawBtn(g, b, mouseX, mouseY, th, accent);
        for (Txt t : texts) g.text(font, t.s(), t.x(), t.y(), t.color());
        if (page == 8 && sizeSlider != null) drawSizeSlider(g, mouseX, mouseY, th, accent);
        for (Hint h : hints) if (h.box().getValue().isEmpty()) g.text(font, h.s(), h.x(), h.y(), 0xFF6A678F);

        switch (page) {
            case 0 -> drawOverview(g, accent);
            case 1 -> drawAuction(g, accent, mouseX, mouseY);
            case 2 -> drawProfits(g, accent);
            case 3 -> drawCustomize(g, accent, th);
            case 4 -> drawInventory(g, accent, mouseX, mouseY, th);
            case 5 -> drawSky(g, accent);
            case 6 -> drawSpotify(g, accent);
            case 7 -> drawOutline(g, accent);
            case 8 -> drawCrosshair(g, accent);
            case 9 -> drawStyle(g, accent, th);
        }
        drawStats(g, th);
    }

    private void drawBtn(GuiGraphicsExtractor g, Btn b, int mx, int my, Theme th, int accent) {
        boolean hover = mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h();
        if (b.color() != 0) {
            int swatchFill = b.color() == Palette.RAINBOW_SWATCH ? Palette.rainbow() : b.color();
            rrBorder(g, b.x(), b.y(), b.w(), b.h(), b.active() ? 0xFFFFFFFF : hover ? 0xFFCCCCCC : th.border(), swatchFill);
            return;
        }
        int base = b.active() ? mix(th.card(), accent, 0.30f) : th.card();
        int fill = hover ? mix(base, 0xFFFFFFFF, 0.10f) : base;
        rrBorder(g, b.x(), b.y(), b.w(), b.h(), b.active() ? accent : th.border(), fill);
        String label = b.label();
        int color = b.active() ? 0xFFFFFFFF : hover ? TXT : 0xFFC9C6EE;
        if (label.startsWith("# ")) {
            g.text(font, label, b.x() + 6, b.y() + (b.h() - 8) / 2, color);
        } else {
            g.text(font, label, b.x() + (b.w() - font.width(label)) / 2, b.y() + (b.h() - 8) / 2, color);
        }
        // color dot for theme / sky preset buttons ("    Name")
        if (label.startsWith("    ")) {
            int dot = dotColorFor(label.trim(), accent);
            g.fill(b.x() + 6, b.y() + b.h() / 2 - 3, b.x() + 12, b.y() + b.h() / 2 + 3, dot);
        }
    }

    private static int dotColorFor(String name, int fallback) {
        for (Theme t : THEMES) if (t.name().equals(name)) return t.accent();
        for (SkyOverlay.Preset p : SkyOverlay.PRESETS) if (p.name().equals(name)) return p.dot();
        return fallback;
    }

    private void drawStats(GuiGraphicsExtractor g, Theme th) {
        int y = py + ph - BOT, cellW = (pw - 4) / 5;
        double bal = BalanceTracker.balance, ph1 = ChimiMenuClient.perHour();
        String[] labels = {"Balance", "Session", "Per hour", "Today", "7 days"};
        double today = Config.I.dayTotal(1), week = Config.I.dayTotal(7);
        String[] vals = {
                Double.isNaN(bal) ? "-" : EconomyCore.format(bal),
                ChimiMenuClient.session.isActive() ? signed(ChimiMenuClient.profit()) : "-",
                Double.isNaN(ph1) ? "-" : signed(ph1),
                signed(today), signed(week)};
        int[] cols = {TXT,
                ChimiMenuClient.profit() >= 0 ? GOOD : BAD, TXT,
                today >= 0 ? GOOD : BAD, week >= 0 ? GOOD : BAD};
        for (int i = 0; i < 5; i++) {
            int x = px + 2 + i * cellW + 8;
            g.text(font, labels[i], x, y + 4, DIM);
            g.text(font, vals[i], x, y + 14, vals[i].equals("-") ? DIM : cols[i]);
            if (i > 0) g.fill(px + 2 + i * cellW, y + 4, px + 3 + i * cellW, y + BOT - 4, th.border());
        }
    }

    private void drawOverview(GuiGraphicsExtractor g, int accent) {
        int hour = LocalTime.now().getHour();
        String when = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        String name = minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : "";
        g.text(font, when + ", " + name, cx, cy + 2, TXT);
        g.text(font, "Your day at a glance - money in, money out, and what's going on.", cx, cy + 14, DIM);
        String[] labels = {"Today", "This week", "This month"};
        double[] vals = {Config.I.dayTotal(1), Config.I.dayTotal(7), Config.I.dayTotal(java.time.LocalDate.now().getDayOfMonth())};
        for (int i = 0; i < 3; i++) {
            int x = cx + i * (statW() + 8);
            g.text(font, labels[i], x + 8, cy + 34, DIM);
            g.text(font, signed(vals[i]), x + 8, cy + 48, vals[i] >= 0 ? GOOD : BAD);
            g.fill(x + 8, cy + 44, x + 8 + 16, cy + 45, accent);
        }
        int listY = cy + 70, listH = ch - 70 - 24;
        var recent = ChimiMenuClient.recent;
        if (recent.isEmpty()) {
            g.text(font, "No payments yet - they show up here as soon as one comes in.", cx + 8, listY + 20, DIM);
        }
        int rows = Math.max(0, (listH - 22) / 11);
        for (int i = 0; i < Math.min(rows, recent.size()); i++) {
            var p = recent.get(i);
            String line = (p.incoming() ? "+ " : "- ") + p.player() + "  " + EconomyCore.format(p.amount());
            g.text(font, line, cx + 8, listY + 20 + i * 11, p.incoming() ? GOOD : BAD);
        }
    }

    private void drawAuction(GuiGraphicsExtractor g, int accent, int mx, int my) {}

    private void drawProfits(GuiGraphicsExtractor g, int accent) {
        var s = ChimiMenuClient.session;
        int x = cx + 8, y = cy + 27;
        g.text(font, "Session: " + (s.isActive() ? "running" : "stopped") + "   " + BalanceTracker.status, x, y, TXT);
        g.text(font, "Balance: " + (Double.isNaN(BalanceTracker.balance) ? "-" : EconomyCore.format(BalanceTracker.balance))
                + "   Start: " + (Double.isNaN(BalanceTracker.startBalance) ? "-" : EconomyCore.format(BalanceTracker.startBalance)), x, y + 11, DIM);
        double p = ChimiMenuClient.profit(), ph1 = ChimiMenuClient.perHour();
        g.text(font, "Profit: " + EconomyCore.format(p) + (ChimiMenuClient.usingBalance() ? " (real balance)" : " (payments only)")
                + (Double.isNaN(ph1) ? "" : "   " + EconomyCore.format(ph1) + "/h"), x, y + 22, p >= 0 ? GOOD : BAD);
        g.text(font, "Payments in: " + EconomyCore.format(s.received()) + "   out: " + EconomyCore.format(s.sent()), x, y + 33, DIM);

        int gx = cx + 8, gy = cy + 104, gw = cw - 16, gh = ch - 84 - 30;
        if (gh < 20) return;
        g.fill(gx, gy, gx + gw, gy + gh, 0x55000000);
        var hist = ChimiMenuClient.history;
        if (hist.isEmpty()) {
            g.text(font, s.isActive() ? "Collecting data..." : "Start a session to see the graph.", gx + 6, gy + gh / 2 - 4, 0xFF777799);
            return;
        }
        int n = Math.min(hist.size(), gw / 2), from = hist.size() - n;
        double max = 0, min = 0;
        for (int i = from; i < hist.size(); i++) { max = Math.max(max, hist.get(i)); min = Math.min(min, hist.get(i)); }
        double range = Math.max(1e-9, max - min);
        int zeroY = gy + (int) Math.round(gh * (max / range));
        zeroY = Math.max(gy, Math.min(gy + gh - 1, zeroY));
        for (int i = 0; i < n; i++) {
            double v = hist.get(from + i);
            int vy = gy + (int) Math.round((gh - 1) * ((max - v) / range));
            int bx = gx + gw - n * 2 + i * 2;
            if (v >= 0) g.fill(bx, Math.min(vy, zeroY), bx + 2, zeroY + 1, GOOD);
            else g.fill(bx, zeroY, bx + 2, Math.max(vy, zeroY + 1), BAD);
        }
        g.fill(gx, zeroY, gx + gw, zeroY + 1, 0x66FFFFFF);
    }

    private void drawCustomize(GuiGraphicsExtractor g, int accent, Theme th) {
        int lw = (int) (cw * 0.6), rx = cx + lw + 8, rw = cw - lw - 8;
        int bx = rx + 8, by = cy + 226, bw = rw - 16, bh = Math.min(40, ch - 226 - 8);
        if (bh < 24) return;
        g.text(font, "HUD preview", bx, by - 10, DIM);
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, accent);
        g.fill(bx, by, bx + bw, by + bh, 0xFF1C2230);
        int cwid = 46, chh = 12, c = Config.I.hudCorner;
        int hx = (c == 1 || c == 3) ? bx + bw - cwid - 3 : bx + 3;
        int hy = (c == 2 || c == 3) ? by + bh - chh - 3 : by + 3;
        g.fill(hx, hy, hx + cwid, hy + chh, 0x99000000);
        g.text(font, "+12.4K", hx + 4, hy + 2, GOOD);
    }

    private void drawInventory(GuiGraphicsExtractor g, int accent, int mx, int my, Theme th) {
        String cur = Config.I.bgImage.isEmpty() ? "none" : Config.I.bgImage;
        g.text(font, "Current: " + cur, cx + 136, cy + 24, DIM);
        if (galleryNames.isEmpty()) {
            g.text(font, "No images yet. Press Add image or drag a file onto this window.", cx, cy + 56, DIM);
        }
        for (ThumbCell t : thumbCells) {
            boolean sel = t.name().equals(Config.I.bgImage);
            boolean hover = mx >= t.x() && mx < t.x() + t.w() && my >= t.y() && my < t.y() + t.h() + 12;
            rrBorder(g, t.x() - 1, t.y() - 1, t.w() + 2, t.h() + 14, sel ? accent : hover ? 0xFFCCCCCC : th.border(), th.card());
            drawThumb(g, t.name(), t.x(), t.y(), t.w(), t.h());
            String label = t.name().replaceAll("\\.png$", "");
            if (label.length() > 16) label = label.substring(0, 15) + "~";
            g.text(font, label, t.x() + 2, t.y() + t.h() + 3, sel ? 0xFFFFFFFF : DIM);
        }
        if (!notice.isEmpty()) g.text(font, notice, cx, cy + ch - 10, 0xFFFFD166);
        else g.text(font, "Reopen your inventory (E) to see it. Crop is automatic.", cx, cy + ch - 10, DIM);
    }

    private void drawThumb(GuiGraphicsExtractor g, String name, int x, int y, int w, int h) {
        var t = BackgroundManager.thumb(name);
        if (t == null) { g.fill(x, y, x + w, y + h, 0xFF000000); return; }
        float scale = Math.max((float) w / t.w(), (float) h / t.h());
        int rw = Math.min(t.w(), Math.round(w / scale)), rh = Math.min(t.h(), Math.round(h / scale));
        float u = (t.w() - rw) / 2f, v = (t.h() - rh) / 2f;
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, u, v, w, h, rw, rh, t.w(), t.h());
    }

    private void drawSky(GuiGraphicsExtractor g, int accent) {
        g.text(font, "Selected: " + Config.I.skyPreset, cx + 8, cy + ch - 14, TXT);
    }

    private void drawSpotify(GuiGraphicsExtractor g, int accent) {
        int lw = (int) (cw * 0.6), x = cx + 8, maxW = lw - 16;
        String st = SpotifyBridge.state;
        String line1, line2, line3;
        int c1 = TXT;
        switch (st) {
            case "playing" -> { line1 = SpotifyBridge.title; line2 = SpotifyBridge.artist; line3 = "Playing"; c1 = accent; }
            case "paused" -> { line1 = SpotifyBridge.title; line2 = SpotifyBridge.artist; line3 = "Paused"; }
            case "closed" -> { line1 = "Spotify isn't open"; line2 = "Open the Spotify app on your PC."; line3 = ""; }
            case "idle" -> { line1 = "Nothing played yet"; line2 = "Press play in Spotify."; line3 = ""; }
            case "unsupported" -> { line1 = "Windows only"; line2 = "This needs the Windows Spotify app."; line3 = ""; }
            case "error" -> { line1 = "Can't read Spotify"; line2 = "PowerShell didn't start."; line3 = ""; }
            default -> { line1 = "Looking for Spotify..."; line2 = ""; line3 = ""; }
        }
        g.text(font, fit(line1, maxW), x, cy + 20, c1);
        g.text(font, fit(line2, maxW), x, cy + 32, DIM);
        if (!line3.isEmpty()) g.text(font, line3, x, cy + 44, "playing".equals(st) ? GOOD : DIM);
        int rx = cx + lw + 8, rw = cw - lw - 8;
        // HUD text preview
        int bx = rx + 8, by = cy + 162, bw = rw - 16;
        if (ch - 162 >= 22) {
            g.text(font, "HUD text", bx, by - 10, DIM);
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 17, accent);
            g.fill(bx, by, bx + bw, by + 16, 0xFF1C2230);
            g.text(font, fit(SpotifyBridge.currentText(), bw - 8), bx + 4, by + 4, Palette.resolve(Config.I.spotifyColor));
        }
    }

    private void drawOutline(GuiGraphicsExtractor g, int accent) {
        int lw = (int) (cw * 0.6), rx = cx + lw + 8, rw = cw - lw - 8;
        int bx = rx + 8, by = cy + 18, bw = rw - 16, bh = 92;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, accent);
        g.fill(bx, by, bx + bw, by + bh, 0xFF1C2230);
        int s = Math.min(bw, bh) - 24;
        int x = bx + (bw - s) / 2, y = by + (bh - s) / 2;
        String style = Config.I.outlineStyle;
        if (!"Outline".equals(style)) {
            g.fill(x, y, x + s, y + s, Palette.withAlpha(Palette.resolve(Config.I.outlineFillColor), BlockOutline.fillAlpha()));
        }
        if (!"Fill only".equals(style)) {
            int t = new int[]{1, 2, 3, 5}[BlockOutline.thickIndex()];
            int col = Palette.resolve(Config.I.outlineColor);
            g.fill(x, y, x + s, y + t, col);
            g.fill(x, y + s - t, x + s, y + s, col);
            g.fill(x, y, x + t, y + s, col);
            g.fill(x + s - t, y, x + s, y + s, col);
        }
    }

    private void drawSizeSlider(GuiGraphicsExtractor g, int mx, int my, Theme th, int accent) {
        Rect r = sizeSlider;
        g.text(font, "Size: " + Crosshair.radius() + " px (drag)", r.x(), r.y() - 12, DIM);
        rrBorder(g, r.x(), r.y(), r.w(), r.h(), th.border(), mix(th.bg(), 0xFF000000, 0.35f));
        float t = (Crosshair.radius() - Crosshair.MIN_RADIUS) / (float) (Crosshair.MAX_RADIUS - Crosshair.MIN_RADIUS);
        int hx = r.x() + 3 + Math.round(t * (r.w() - 10));
        g.fill(r.x() + 3, r.y() + r.h() / 2 - 1, hx, r.y() + r.h() / 2 + 1, accent);
        boolean hover = mx >= r.x() && mx < r.x() + r.w() && my >= r.y() && my < r.y() + r.h();
        rrBorder(g, hx, r.y() + 1, 5, r.h() - 2, hover ? 0xFFFFFFFF : accent, mix(th.card(), accent, 0.45f));
    }

    private void drawCrosshair(GuiGraphicsExtractor g, int accent) {
        int lw = (int) (cw * 0.6), rx = cx + lw + 8, rw = cw - lw - 8;
        int bx = rx + 8, by = cy + 18, bw = rw - 16, bh = 92;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, accent);
        g.fill(bx, by, bx + bw, by + bh, 0xFF5A6B7F); // mid-tone so white and black both show
        if ("Vanilla".equals(Config.I.crossStyle)) {
            String t = "Vanilla";
            g.text(font, t, bx + (bw - font.width(t)) / 2, by + bh / 2 - 4, 0xFFFFFFFF);
        } else {
            Crosshair.draw(g, bx + bw / 2, by + bh / 2);
        }
    }

    @Override
    public void removed() { Config.save(); MenuScale.restore(); super.removed(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
