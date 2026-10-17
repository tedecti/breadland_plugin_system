package me.tedecti.events.deathnote;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses times written in the note ("40с", "5 мин", "1ч 30м", "90") and formats countdowns. */
public final class TimeFormat {
    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*([a-zа-я]*)");

    private TimeFormat() {}

    /**
     * @return seconds, or -1 if the text is not a valid time. A bare number means seconds.
     */
    public static long parseSeconds(String text) {
        String normalized = text.toLowerCase(Locale.ROOT).replace("через", "").replace(",", " ").trim();
        if (normalized.isEmpty()) return -1;

        Matcher matcher = TOKEN.matcher(normalized);
        long total = 0;
        int end = 0;
        boolean found = false;
        while (matcher.find()) {
            // Anything other than spaces between tokens means the text is not a time
            if (!normalized.substring(end, matcher.start()).isBlank()) return -1;
            end = matcher.end();
            found = true;

            long value;
            try {
                value = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }

            long multiplier = unitMultiplier(matcher.group(2));
            if (multiplier < 0) return -1;
            total += value * multiplier;
        }

        if (!found || !normalized.substring(end).isBlank()) return -1;
        return total;
    }

    private static long unitMultiplier(String unit) {
        if (unit.isEmpty() || unit.startsWith("с") || unit.startsWith("s")) return 1;
        if (unit.startsWith("м") || unit.startsWith("m")) return 60;
        if (unit.startsWith("ч") || unit.startsWith("h")) return 3600;
        return -1;
    }

    /** "1 ч 5 мин", "5 мин 3 сек", "40 сек" */
    public static String formatLong(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder builder = new StringBuilder();
        if (hours > 0) builder.append(hours).append(" ч ");
        if (minutes > 0) builder.append(minutes).append(" мин ");
        if (secs > 0 || builder.isEmpty()) builder.append(secs).append(" сек");
        return builder.toString().trim();
    }

    /** "1:05:03", "2:31" */
    public static String formatClock(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, secs);
        return String.format("%d:%02d", minutes, secs);
    }
}
