package com.chimi.menu;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Shows what the Spotify desktop app (Windows) is playing and lets you skip from inside Minecraft.
 *
 * Reading: a small background PowerShell loop reads the Spotify window title once a second. While a song plays the
 * title is "Artist - Song"; while paused it is just "Spotify".
 * Controlling: sends the Windows media keys (next / previous / play-pause).
 * No Spotify login, API key or network access is needed.
 */
public final class SpotifyBridge {
    private SpotifyBridge() {}

    /** playing, paused, idle (open, nothing played yet), closed, unsupported, error, starting */
    public static volatile String state = "starting";
    public static volatile String title = "";
    public static volatile String artist = "";

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static boolean started;
    private static Process poller, control;
    private static BufferedWriter controlIn;

    private static final String POLL_SCRIPT = """
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            while ($true) {
              $p = Get-Process -Name Spotify -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle } | Select-Object -First 1
              if ($p) { [Console]::Out.WriteLine('T|' + $p.MainWindowTitle) } else { [Console]::Out.WriteLine('N|') }
              [Console]::Out.Flush()
              Start-Sleep -Milliseconds 1000
            }
            """;

    private static final String CONTROL_SCRIPT = """
            Add-Type -Namespace Chimi -Name Native -MemberDefinition '[DllImport("user32.dll")] public static extern void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);'
            while (($line = [Console]::In.ReadLine()) -ne $null) {
              $vk = 0
              switch ($line.Trim()) { 'next' { $vk = 0xB0 } 'prev' { $vk = 0xB1 } 'play' { $vk = 0xB3 } }
              if ($vk -ne 0) {
                [Chimi.Native]::keybd_event([byte]$vk, 0, 0, 0)
                [Chimi.Native]::keybd_event([byte]$vk, 0, 2, 0)
              }
            }
            """;

    private static Process launch(String script) throws Exception {
        String enc = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    public static synchronized void start() {
        if (started) return;
        started = true;
        if (!WINDOWS) { state = "unsupported"; return; }
        Thread t = new Thread(SpotifyBridge::pollLoop, "chimi-spotify");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (poller != null) poller.destroy();
            if (control != null) control.destroy();
        }));
    }

    private static void pollLoop() {
        try {
            poller = launch(POLL_SCRIPT);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(poller.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (Config.I.spotifyEnabled) handle(line.replace("﻿", ""));
                }
            }
            state = "error";
        } catch (Exception e) {
            state = "error";
        }
    }

    private static void handle(String line) {
        if (line.startsWith("N|")) { state = "closed"; return; }
        if (!line.startsWith("T|")) return;
        String t = line.substring(2).trim();
        if (t.equals("Spotify") || t.equals("Spotify Free") || t.equals("Spotify Premium")) {
            state = title.isEmpty() ? "idle" : "paused";
            return;
        }
        int i = t.indexOf(" - ");
        if (i > 0) {
            artist = t.substring(0, i);
            title = t.substring(i + 3);
        } else {
            artist = "";
            title = t; // e.g. "Advertisement"
        }
        state = "playing";
    }

    /** cmd is "next", "prev" or "play". Runs off the game thread. */
    public static void send(String cmd) {
        if (!WINDOWS) return;
        Thread t = new Thread(() -> {
            synchronized (SpotifyBridge.class) {
                try {
                    if (control == null || !control.isAlive()) {
                        control = launch(CONTROL_SCRIPT);
                        controlIn = new BufferedWriter(new OutputStreamWriter(control.getOutputStream(), StandardCharsets.UTF_8));
                        // discard anything the control shell prints
                        Process c = control;
                        Thread drain = new Thread(() -> { try { c.getInputStream().transferTo(java.io.OutputStream.nullOutputStream()); } catch (Exception ignored) {} }, "chimi-spotify-drain");
                        drain.setDaemon(true);
                        drain.start();
                    }
                    controlIn.write(cmd + "\n");
                    controlIn.flush();
                } catch (Exception e) {
                    control = null;
                }
            }
        }, "chimi-spotify-send");
        t.setDaemon(true);
        t.start();
    }

    /** Text for the HUD, from the templates in Customize. {title} and {artist} are filled in. */
    public static String currentText() {
        Config c = Config.I;
        String f = switch (state) {
            case "playing" -> c.spotifyPlayingText;
            case "paused" -> c.spotifyPausedText;
            case "unsupported" -> "Spotify: Windows only";
            case "error" -> "Spotify: can't read the app";
            default -> c.spotifyIdleText;
        };
        return f.replace("{title}", title).replace("{artist}", artist);
    }

    public static void drawHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Config c = Config.I;
        if (!c.spotifyEnabled || !c.spotifyHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String text = currentText();
        if (text.isBlank()) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int max = sw / 2;
        if (HudText.width(mc, text) > max) {
            while (text.length() > 1 && HudText.width(mc, text + "..") > max) text = text.substring(0, text.length() - 1);
            text += "..";
        }
        int w = HudText.width(mc, text) + 8, h = 14;
        float sc = HudLayout.scale(1);
        int[] pos = HudLayout.pos(1, w, h, false);
        int x = Math.round(pos[0] / sc), y = Math.round(pos[1] / sc);
        g.pose().pushMatrix();
        g.pose().scale(sc, sc);
        HudShape.fill(g, x, y, w, h, 0x99000000);
        HudText.draw(g, mc, text, x + 4, y + 3, Palette.resolve(c.spotifyColor));
        g.pose().popMatrix();
    }
}
