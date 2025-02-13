package me.thegabro.playtimemanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    // Constants for tick conversions
    private static final long TICKS_PER_SECOND = 20L;
    private static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
    private static final long TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;
    private static final long TICKS_PER_DAY = TICKS_PER_HOUR * 24;
    private static final long TICKS_PER_YEAR = TICKS_PER_DAY * 365;
    // Previous constants remain the same...
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})([^&]*)");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fA-F])");

    public static Component parseColors(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        Component message = Component.empty();
        String remaining = input;

        // First handle hex colors
        Matcher hexMatcher = HEX_PATTERN.matcher(remaining);
        int lastEnd = 0;

        while (hexMatcher.find()) {
            // Add any text before the color code
            if (hexMatcher.start() > lastEnd) {
                String beforeText = remaining.substring(lastEnd, hexMatcher.start());
                message = message.append(parseLegacyColors(beforeText));
            }

            // Extract color and text
            String hex = hexMatcher.group(1);
            String text = hexMatcher.group(2);

            // Create colored component (only if there's text to color)
            if (!text.isEmpty()) {
                TextColor color = TextColor.fromHexString("#" + hex);
                message = message.append(
                        Component.text(text).color(color)
                );
            }

            lastEnd = hexMatcher.end();
        }

        // Add any remaining text and parse it for legacy colors
        if (lastEnd < remaining.length()) {
            String remainingText = remaining.substring(lastEnd);
            message = message.append(parseLegacyColors(remainingText));
        }

        return message;
    }

    private static Component parseLegacyColors(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        Component message = Component.empty();
        String[] parts = input.split("(?=&[0-9a-fA-F])");

        for (String part : parts) {
            if (part.startsWith("&") && part.length() >= 2) {
                String colorCode = part.substring(1, 2);
                String text = part.substring(2);
                TextColor color = getLegacyColor(colorCode);
                message = message.append(Component.text(text).color(color));
            } else {
                message = message.append(Component.text(part));
            }
        }

        return message;
    }

    private static TextColor getLegacyColor(String code) {
        return switch (code.toLowerCase()) {
            case "0" -> TextColor.color(0, 0, 0);         // Black
            case "1" -> TextColor.color(0, 0, 170);       // Dark Blue
            case "2" -> TextColor.color(0, 170, 0);       // Dark Green
            case "3" -> TextColor.color(0, 170, 170);     // Dark Aqua
            case "4" -> TextColor.color(170, 0, 0);       // Dark Red
            case "5" -> TextColor.color(170, 0, 170);     // Dark Purple
            case "6" -> TextColor.color(255, 170, 0);     // Gold
            case "7" -> TextColor.color(170, 170, 170);   // Gray
            case "8" -> TextColor.color(85, 85, 85);      // Dark Gray
            case "9" -> TextColor.color(85, 85, 255);     // Blue
            case "a" -> TextColor.color(85, 255, 85);     // Green
            case "b" -> TextColor.color(85, 255, 255);    // Aqua
            case "c" -> TextColor.color(255, 85, 85);     // Red
            case "d" -> TextColor.color(255, 85, 255);    // Light Purple
            case "e" -> TextColor.color(255, 255, 85);    // Yellow
            case "f" -> TextColor.color(255, 255, 255);   // White
            default -> TextColor.color(255, 255, 255);    // Default to white
        };
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