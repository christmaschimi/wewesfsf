package com.chimi.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core logic with zero Minecraft dependencies, so it works on any version.
 * Feed it plain-text chat/system messages; it tracks payments, profit and auctions.
 */
public final class EconomyCore {
    private EconomyCore() {}

    // Amount like: 1000, 1,000, $1.5k, $ 150M, 2m, 3b. Any money symbol / glyph / spaces may sit in front of the number.
    private static final String AMOUNT = "[^\\p{L}\\p{N}]{0,12}?(\\d+(?:,\\d+)*(?:\\.\\d+)?)\\s*([kKmMbBtT](?![A-Za-z]))?";

    // "X paid you $Y" can sit anywhere in the line (ranks, [tags], arrows before it are fine), but not after a chat separator like
    // "Bob: " or "<Bob> ", so a player typing a fake payment in chat doesn't count.
    private static final Pattern RECEIVED =
            Pattern.compile("([A-Za-z0-9_.]{1,20})\\s+paid\\s+you\\b" + AMOUNT, Pattern.CASE_INSENSITIVE);
    private static final Pattern SENT =
            Pattern.compile("\\byou\\s+(?:paid|sent)\\s+([A-Za-z0-9_.]{1,20})\\s+" + AMOUNT, Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAT_SEPARATOR = Pattern.compile("[\\p{L}\\p{N}_\\]]\\s*[:\u00BB>]");

    private static boolean fromChat(String text, int matchStart) {
        return CHAT_SEPARATOR.matcher(text.substring(0, matchStart)).find();
    }

    public record Payment(String player, double amount, boolean incoming) {}

    private static final String SMALL_CAPS_FROM =
            "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡʏᴢ";
    private static final String SMALL_CAPS_TO = "abcdefghijklmnopqrstuvwyz";

    /** Removes colour codes, fixes fancy fonts (small caps, full-width) and trims, so servers' styling can't hide a payment. */
    public static String clean(String message) {
        if (message == null) return "";
        String s = message.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int k = SMALL_CAPS_FROM.indexOf(c);
            sb.append(k >= 0 ? SMALL_CAPS_TO.charAt(k) : c);
        }
        return sb.toString().strip();
    }

    public static double parseAmount(String number, String suffix) {
        double v = Double.parseDouble(number.replace(",", ""));
        if (suffix == null) return v;
        return switch (Character.toLowerCase(suffix.charAt(0))) {
            case 'k' -> v * 1_000;
            case 'm' -> v * 1_000_000;
            case 'b' -> v * 1_000_000_000;
            case 't' -> v * 1_000_000_000_000.0;
            default -> v;
        };
    }

    public static Optional<Payment> parse(String message) {
        String text = clean(message);
        try {
            Matcher in = RECEIVED.matcher(text);
            if (in.find() && !fromChat(text, in.start())) {
                return Optional.of(new Payment(in.group(1), parseAmount(in.group(2), in.group(3)), true));
            }
            Matcher out = SENT.matcher(text);
            if (out.find() && !fromChat(text, out.start())) {
                return Optional.of(new Payment(out.group(1), parseAmount(out.group(2), out.group(3)), false));
            }
        } catch (NumberFormatException ignored) {
            // "paid you" followed by something that isn't a number: not a payment
        }
        return Optional.empty();
    }

    public static String format(double v) {
        double a = Math.abs(v);
        String[] suffix = {"", "K", "M", "B", "T"};
        double[] unit = {1, 1e3, 1e6, 1e9, 1e12};
        int i = 0;
        while (i < 4 && a >= unit[i + 1]) i++;
        // a value like 999,999.9 rounds up to "1000.00K": move it to the next unit instead
        if (i > 0 && i < 4 && Math.round(a / unit[i] * 100) >= 100000) i++;
        if (i == 0) return String.format("%.0f", v);
        return String.format("%.2f%s", v / unit[i], suffix[i]);
    }

    /** Live profit tracking for the HUD. Toggle with start()/stop(). */
    public static final class Session {
        private boolean active;
        private double received;
        private double sent;

        public void start() { active = true; received = 0; sent = 0; }
        public boolean hasPayments() { return received != 0 || sent != 0; }
        public void stop() { active = false; }
        public boolean isActive() { return active; }

        public void accept(Payment p) {
            if (!active) return;
            if (p.incoming()) received += p.amount(); else sent += p.amount();
        }

        public double profit() { return received - sent; }
        public double received() { return received; }
        public double sent() { return sent; }
    }

    /** One auction: highest total paid by a single player wins. */
    public static final class Auction {
        public final String prize;
        public final long durationSeconds;
        public final double minBid;
        public final double worth;
        private long endMillis;
        public volatile String lastBidPlayer = "";
        public volatile double lastBidAmount;
        public volatile long lastBidAt;
        private final Map<String, Double> totals = new HashMap<>();

        public Auction(String prize, long durationSeconds) { this(prize, durationSeconds, 0, 0); }

        public Auction(String prize, long durationSeconds, double minBid, double worth) {
            this.minBid = minBid;
            this.worth = worth;
            this.prize = prize;
            this.durationSeconds = durationSeconds;
            this.endMillis = System.currentTimeMillis() + durationSeconds * 1000;
        }

        public boolean isOver() { return System.currentTimeMillis() >= endMillis; }
        public void extend(long seconds) { if (!isOver()) endMillis += seconds * 1000; }
        public void endNow() { endMillis = System.currentTimeMillis(); }
        public long secondsLeft() { return Math.max(0, (endMillis - System.currentTimeMillis()) / 1000); }

        public double pot() { return leaderboard().stream().mapToDouble(Map.Entry::getValue).sum(); }
        public int bidders() { return leaderboard().size(); }

        public void accept(Payment p) {
            if (p.incoming() && !isOver()) {
                if (p.amount() < minBid) return; // under the minimum: skipped, not listed, no alert
                totals.merge(p.player(), p.amount(), Double::sum);
                lastBidPlayer = p.player();
                lastBidAmount = p.amount();
                lastBidAt = System.currentTimeMillis();
            }
        }

        public List<Map.Entry<String, Double>> leaderboard() {
            List<Map.Entry<String, Double>> list = new ArrayList<>();
            for (Map.Entry<String, Double> e : totals.entrySet()) if (e.getValue() >= minBid) list.add(e);
            list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            return list;
        }

        public Optional<Map.Entry<String, Double>> winner() {
            List<Map.Entry<String, Double>> lb = leaderboard();
            return lb.isEmpty() ? Optional.empty() : Optional.of(lb.get(0));
        }
    }
}
