package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils;

import me.thegabro.playtimemanager.Configuration;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A period that always ends now and starts a fixed time back, written {@code last<N><unit>} in
 * placeholders: last30m, last24h, last7d, last2w, last1mo, last1y.
 */
public record RollingWindow(long millis) {

    private static final Pattern TOKEN = Pattern.compile("last(\\d{1,9})(mo|mc|s|m|h|d|w|y)");

    /** @return the window, or null when the token is not a rolling window */
    public static RollingWindow parse(String token) {
        Matcher m = TOKEN.matcher(token.toLowerCase(Locale.ROOT));
        if (!m.matches()) return null;

        long amount = Long.parseLong(m.group(1));
        if (amount == 0) return null;

        long day = 24L * 60 * 60 * 1000;
        long unit = switch (m.group(2)) {
            case "s" -> 1000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> day;
            case "w" -> 7 * day;
            case "mo" -> Configuration.getInstance().getInt("month-value-in-days", 30) * day;
            case "y" -> 365 * day;
            case "mc" -> 1_200_000L; // one Minecraft day, 24000 ticks
            default -> 0L;
        };
        return new RollingWindow(amount * unit);
    }
}
