package com.chimi.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks your real balance.
 *  - DonutSMP: read from the official API (https://api.donutsmp.net/v1/stats/{user}).
 *  - Any other server (e.g. Prism SMP): read from chat whenever your balance message appears
 *    (run /bal, or press Refresh in the menu, which sends /bal once).
 */
public final class BalanceTracker {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern MONEY = Pattern.compile("\"money\"\\s*:\\s*\"?([0-9.]+)\"?");

    public static volatile double balance = Double.NaN;
    public static volatile double startBalance = Double.NaN;
    public static volatile String status = "Idle";
    /** True when the last balance reading was rounded (e.g. "1.2M" on the sidebar), so small changes can't be seen in it. */
    public static volatile boolean approx;
    private static volatile boolean inFlight;
    private static volatile long lastPoll;

    private BalanceTracker() {}

    public static boolean onDonut(Minecraft mc) {
        var server = mc.getCurrentServer();
        String match = Config.I.apiServerMatch == null ? "donut" : Config.I.apiServerMatch.toLowerCase();
        return server != null && server.ip != null && server.ip.toLowerCase().contains(match);
    }

    public static boolean useApiHere(Minecraft mc) { return Config.I.useApi && onDonut(mc); }

    public static void beginSession(Minecraft mc) {
        // API mode takes a fresh reading; otherwise start from the last balance seen (sidebar or /bal).
        startBalance = useApiHere(mc) ? Double.NaN : balance;
        lastPoll = 0;
        if (!useApiHere(mc) && Double.isNaN(balance)) status = "Waiting for your balance (sidebar or /bal)";
    }

    public static void resetAll() {
        balance = Double.NaN;
        startBalance = Double.NaN;
        approx = false;
        status = "Idle";
    }

    public static boolean hasBalance() { return !Double.isNaN(balance) && !Double.isNaN(startBalance); }
    public static double profit() { return balance - startBalance; }

    /** Menu "Refresh" button. */
    public static void refresh(Minecraft mc) {
        if (mc.player == null) return;
        if (useApiHere(mc)) { lastPoll = 0; return; }
        mc.player.connection.sendCommand("bal"); // one command per click, never automatic
        status = "Sent /bal, waiting for reply";
    }

    /** Chat mode: called for every system message. */
    public static void acceptChat(Minecraft mc, String text, boolean sessionActive) {
        if (useApiHere(mc)) return;
        Matcher m;
        try {
            m = Pattern.compile(Config.I.balanceRegex).matcher(text);
        } catch (Exception e) {
            status = "Bad balanceRegex in config";
            return;
        }
        if (!m.find()) return;
        try {
            String suffix = m.groupCount() >= 2 ? m.group(2) : null;
            balance = EconomyCore.parseAmount(m.group(1), suffix);
            approx = suffix != null;
        } catch (Exception e) {
            return;
        }
        if (sessionActive && Double.isNaN(startBalance)) startBalance = balance;
        status = "Balance from /bal";
    }

    /** Reads the balance line from the right-side scoreboard (local data only, sends nothing). */
    public static void readSidebar(Minecraft mc, boolean sessionActive) {
        if (mc.level == null || useApiHere(mc)) return;
        Pattern p;
        try {
            p = Pattern.compile(Config.I.sidebarRegex);
        } catch (Exception e) {
            status = "Bad sidebarRegex in config";
            return;
        }
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return;
        for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
            Component line = e.display() != null
                    ? e.display()
                    : PlayerTeam.formatNameForTeam(sb.getPlayersTeam(e.owner()), Component.literal(e.owner()));
            Matcher m = p.matcher(line.getString());
            if (!m.find()) continue;
            try {
                String suffix = m.groupCount() >= 2 ? m.group(2) : null;
                balance = EconomyCore.parseAmount(m.group(1), suffix);
                approx = suffix != null;
            } catch (Exception ex) {
                continue;
            }
            if (sessionActive && Double.isNaN(startBalance)) startBalance = balance;
            status = "Balance from sidebar";
            return;
        }
    }

    /** API mode: polls only while a session runs. */
    public static void tick(Minecraft mc, boolean sessionActive) {
        if (!sessionActive || inFlight || !useApiHere(mc)) return;
        long now = System.currentTimeMillis();
        if (now - lastPoll < Math.max(15, Config.I.pollSeconds) * 1000L) return;
        if (Config.I.apiKey == null || Config.I.apiKey.isBlank()) { status = "No API key (type /api in game)"; return; }

        lastPoll = now;
        inFlight = true;
        String user = mc.getUser().getName();
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.donutsmp.net/v1/stats/" + user))
                .header("Authorization", "Bearer " + Config.I.apiKey.trim())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET().build();

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, err) -> {
            inFlight = false;
            if (err != null) { status = "Request failed"; return; }
            if (resp.statusCode() == 401) { status = "API key rejected (run /api again)"; return; }
            if (resp.statusCode() == 429) { status = "Rate limited, slowing down"; lastPoll = System.currentTimeMillis() + 30_000; return; }
            if (resp.statusCode() != 200) { status = "API error " + resp.statusCode(); return; }
            Matcher m = MONEY.matcher(resp.body());
            if (!m.find()) { status = "Couldn't read balance from API"; return; }
            balance = Double.parseDouble(m.group(1));
            approx = false;
            if (Double.isNaN(startBalance)) startBalance = balance;
            status = "Connected (API)";
        });
    }
}
