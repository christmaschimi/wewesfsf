package com.chimi.menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Saved to config/chimismenu.json. Everything the Customize tab changes lives here. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("chimismenu.json");
    public static Config I = new Config();

    public int accent = 0xFFB48CFF;
    public String theme = "Amethyst";
    public int panelAlpha = 200;
    public boolean hudEnabled = true;
    public int hudCorner = 0; // 0 TL, 1 TR, 2 BL, 3 BR
    public String skyPreset = "Default";
    public String apiKey = "";      // DonutSMP API key (get it with /api in game)
    public String apiServerMatch = "donut"; // servers whose address contains this use the DonutSMP API
    // Used on every other server (e.g. Prism SMP): reads your balance from chat when /bal is shown.
    // Group 1 = number, group 2 = optional k/m/b suffix. Edit in config/chimismenu.json if the wording differs.
    public String balanceRegex = "(?i)^\\s*(?:your\\s+)?(?:balance|bal|money)\\s*(?:is|:|-)?\\s*\\$?([\\d,]*\\.?\\d+)\\s*([kKmMbB])?";
    public boolean bgEnabled = true;
    public String bgImage = "";        // file name inside config/chimismenu/backgrounds
    public int bgDim = 80;             // 0-255 darkness over the image
    public boolean bgAllContainers = false; // old setting, no longer used
    public boolean bgAllScreens = true;     // true = every inventory-style screen (crafting, chests, furnace, creative...)
    public boolean useApi = false; // optional DonutSMP API; off by default (sidebar + /bal are used instead)
    // Reads the balance line shown on the right-side scoreboard. Group 1 = number, group 2 = optional k/m/b.
    public String sidebarRegex = "(?i)(?:money|balance|bal|cash|coins?)[^\\d$]{0,12}\\$?\\s*([\\d,]*\\.?\\d+)\\s*([kKmMbB])?";
    public int pollSeconds = 20;    // balance refresh rate (API limit is 250 requests/min)

    // ---- v1.3: texts you can edit in Customize ({profit} {perhour} / {title} {artist}) ----
    public String profitText = "Profit: {profit}";

    // ---- Spotify (Windows desktop app) ----
    public boolean spotifyEnabled = true;
    public boolean spotifyHud = true;
    public String spotifyPlayingText = "Now playing: {title} - {artist}";
    public String spotifyPausedText = "Paused: {title}";
    public String spotifyIdleText = "Spotify: nothing playing";
    public String spotifyColor = "White";
    public int spotifyCorner = 1; // 0 TL, 1 TR, 2 BL, 3 BR

    // ---- Block outline ----
    public boolean outlineEnabled = false;          // old setting (was off by default), no longer used
    public boolean outlineOn = true;                // block highlight, on by default
    public String outlineStyle = "Outline+Fill";   // Outline+Fill, Fill only, Outline
    public String outlineThickness = "Normal";      // Thin, Normal, Thick, Chunky
    public String outlineFill = "Strong";           // Light, Medium, Strong (how strong the inside fill is)
    public String outlineColor = "Magenta";
    public String outlineFillColor = "Magenta";

    // ---- Crosshair ----
    public String crossStyle = "Vanilla";
    public String crossSize = "Medium";   // old Small/Medium/Big setting, only read to carry over to crossRadius
    public int crossRadius = -1;          // crosshair size in pixels from the centre (2-20); -1 = not set yet, use crossSize
    public String crossColor = "White";

    // ---- Auction (v1.4) ----
    public String aucMin = "0";       // minimum payment for an auction (e.g. 500K, 2M); smaller payments are ignored
    public int aucTheme = 0;          // index into AuctionHud.THEMES
    public int aucHudScale = 10;      // tenths: 10 = 1.0x
    public int aucBoard = 3;          // how many bidders the HUD shows
    public boolean aucAlerts = true;  // pop-up when someone bids / takes #1
    public int aucTimerIdx = 1;       // index into AuctionScreen.TIMERS
    public String hudShape = "Straight";   // Straight, Pixel, Rounded, Bubble, Pill, Chamfer
    public String hudFont = "Minecraft";   // Minecraft, Pixelify, Silkscreen, Tektur, Big Shoulders, Erica One, Outfit
    public double aucCosmicAt = 500_000_000;  // winning bid at or above this gets the COSMIC WIN show
    public String aucItem = "";       // item picked in the Auction menu (registry id, e.g. "diamond_sword")
    public int aucHudX = -1;          // -1 = centred at the top
    public int aucHudY = 6;

    // ---- HUD layout (v1.4): x/y are screen pixels of the box's top-left, -1 = default corner. Scale is in tenths (10 = 1.0x) ----
    public int profitX = -1, profitY = -1, profitScale = 10;
    public int spotX = -1, spotY = -1, spotScale = 10;
    public int menuScale = 100;       // menu size in percent (70 / 85 / 100 / 125 / 150)

    public boolean skyOverlay = true;
    public int skyCells = 9000;                   // sky detail: fewer cells = higher FPS (try 5000 for max speed, 20000 for finer)
    public int skyFov = 70;                       // vertical FOV used to place sky stars; set to match your game FOV
    public java.util.Map<String, Double> daily = new java.util.TreeMap<>(); // yyyy-MM-dd -> profit earned that day

    public static String today() { return java.time.LocalDate.now().toString(); }

    public double dayTotal(int daysBack) {
        double sum = 0;
        java.time.LocalDate d = java.time.LocalDate.now();
        for (int i = 0; i < daysBack; i++) sum += daily.getOrDefault(d.minusDays(i).toString(), 0.0);
        return sum;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) I = GSON.fromJson(Files.readString(FILE), Config.class);
        } catch (Exception e) {
            I = new Config();
        }
        if (I == null) I = new Config();
        // the old default also showed profit per hour; it now shows just the +/- profit
        if ("Profit: {profit}  ({perhour}/h)".equals(I.profitText)) I.profitText = "Profit: {profit}";
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(I));
        } catch (Exception ignored) {}
    }
}
