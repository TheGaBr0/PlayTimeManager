package me.thegabro.playtimemanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    // Constants for tick conversions
    private static final long TICKS_PER_SECOND = 20L;
    private static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
    private static final long TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;
    private static final long TICKS_PER_DAY = TICKS_PER_HOUR * 24;
    private static final long TICKS_PER_YEAR = TICKS_PER_DAY * 365;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})([^&]+)");

    public static Component parseComplexHex(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        Component message = Component.empty();
        Matcher matcher = HEX_PATTERN.matcher(input);
        int lastEnd = 0;

        while (matcher.find()) {
            // Add any text before the color code
            if (matcher.start() > lastEnd) {
                message = message.append(
                        Component.text(input.substring(lastEnd, matcher.start()))
                );
            }

            // Extract color and text
            String hex = matcher.group(1);
            String text = matcher.group(2);

            // Create colored component
            TextColor color = TextColor.fromHexString("#" + hex);
            message = message.append(
                    Component.text(text).color(color)
            );

            lastEnd = matcher.end();
        }

        // Add any remaining text
        if (lastEnd < input.length()) {
            message = message.append(
                    Component.text(input.substring(lastEnd))
            );
        }

        return message;
    }

    private static long safeAdd(long a, long b) {
        if (a > 0 && b > Long.MAX_VALUE - a) return -1L; // Positive overflow
        if (a < 0 && b < Long.MIN_VALUE - a) return -1L; // Negative overflow
        return a + b;
    }

    private static long safeMultiply(long a, long b) {
        if (a == 0 || b == 0) return 0;
        long result = a * b;
        if (a != result / b) return -1L; // Overflow check
        return result;
    }

    public static long formattedPlaytimeToTicks(String input) {
        if (input == null || input.trim().isEmpty()) {
            return -1L;
        }

        String[] timeParts = input.split(",");
        long timeToTicks = 0;
        boolean hasYear = false, hasDay = false, hasHour = false, hasMinute = false, hasSecond = false;

        for (String part : timeParts) {
            try {
                int time = Integer.parseInt(part.replaceAll("[^\\d.]", ""));
                if (time < 0) return -1L; // Prevent negative values

                String format = part.replaceAll("\\d", "");
                long partTicks;

                switch(format) {
                    case "y":
                        if (!hasYear) {
                            partTicks = safeMultiply(time, TICKS_PER_YEAR);
                            if (partTicks == -1L) return -1L;
                            timeToTicks = safeAdd(timeToTicks, partTicks);
                            if (timeToTicks == -1L) return -1L;
                            hasYear = true;
                        }
                        break;
                    case "d":
                        if (!hasDay) {
                            partTicks = safeMultiply(time, TICKS_PER_DAY);
                            if (partTicks == -1L) return -1L;
                            timeToTicks = safeAdd(timeToTicks, partTicks);
                            if (timeToTicks == -1L) return -1L;
                            hasDay = true;
                        }
                        break;
                    case "h":
                        if (!hasHour) {
                            partTicks = safeMultiply(time, TICKS_PER_HOUR);
                            if (partTicks == -1L) return -1L;
                            timeToTicks = safeAdd(timeToTicks, partTicks);
                            if (timeToTicks == -1L) return -1L;
                            hasHour = true;
                        }
                        break;
                    case "m":
                        if (!hasMinute) {
                            partTicks = safeMultiply(time, TICKS_PER_MINUTE);
                            if (partTicks == -1L) return -1L;
                            timeToTicks = safeAdd(timeToTicks, partTicks);
                            if (timeToTicks == -1L) return -1L;
                            hasMinute = true;
                        }
                        break;
                    case "s":
                        if (!hasSecond) {
                            partTicks = safeMultiply(time, TICKS_PER_SECOND);
                            if (partTicks == -1L) return -1L;
                            timeToTicks = safeAdd(timeToTicks, partTicks);
                            if (timeToTicks == -1L) return -1L;
                            hasSecond = true;
                        }
                        break;
                    default:
                        return -1L;
                }
            } catch(NumberFormatException e) {
                return -1L;
            }
        }
        return timeToTicks;
    }

    public static String ticksToFormattedPlaytime(long ticks) {
        if (ticks < 0) {
            return "0s";
        }

        long seconds = ticks / TICKS_PER_SECOND;

        final long SECONDS_PER_YEAR = 365 * 24 * 60 * 60;
        final long SECONDS_PER_DAY = 24 * 60 * 60;
        final long SECONDS_PER_HOUR = 60 * 60;
        final long SECONDS_PER_MINUTE = 60;

        // Calculate time units safely
        long years = seconds / SECONDS_PER_YEAR;
        seconds %= SECONDS_PER_YEAR;

        long days = seconds / SECONDS_PER_DAY;
        seconds %= SECONDS_PER_DAY;

        long hours = seconds / SECONDS_PER_HOUR;
        seconds %= SECONDS_PER_HOUR;

        long minutes = seconds / SECONDS_PER_MINUTE;
        seconds %= SECONDS_PER_MINUTE;

        StringBuilder result = new StringBuilder();

        if (years > 0) {
            result.append(years).append("y");
        }
        if (days > 0) {
            if (!result.isEmpty()) result.append(", ");
            result.append(days).append("d");
        }
        if (hours > 0) {
            if (!result.isEmpty()) result.append(", ");
            result.append(hours).append("h");
        }
        if (minutes > 0) {
            if (!result.isEmpty()) result.append(", ");
            result.append(minutes).append("m");
        }
        if (seconds > 0 || result.isEmpty()) {
            if (!result.isEmpty()) result.append(", ");
            result.append(seconds).append("s");
        }

        return result.toString();
    }
}