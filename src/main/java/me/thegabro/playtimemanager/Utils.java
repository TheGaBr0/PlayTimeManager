package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Map;

public class Utils {
    // Constants for tick conversions
    private static final long TICKS_PER_SECOND = 20L;
    private static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
    private static final long TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;
    private static final long TICKS_PER_DAY = TICKS_PER_HOUR * 24;
    private static final long TICKS_PER_YEAR = TICKS_PER_DAY * 365;

    public static Component parseColors(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        Component message = Component.empty();
        Style currentStyle = Style.empty();
        StringBuilder currentText = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '&' && i + 1 < input.length()) {
                // If we have accumulated text, append it with current style
                if (currentText.length() > 0) {
                    message = message.append(Component.text(currentText.toString(), currentStyle));
                    currentText.setLength(0);
                }

                // Check for hex color
                if (i + 7 < input.length() && input.charAt(i + 1) == '#') {
                    String hexCode = input.substring(i + 2, i + 8);
                    try {
                        // Validate hex code
                        if (hexCode.matches("[0-9A-Fa-f]{6}")) {
                            currentStyle = currentStyle.color(TextColor.fromHexString("#" + hexCode));
                            i += 7;  // Skip the hex code
                            continue;
                        }
                    } catch (IllegalArgumentException e) {
                        // Invalid hex code, treat as normal text
                    }
                }

                // Handle legacy formatting
                char formatCode = Character.toLowerCase(input.charAt(i + 1));

                // Reset
                if (formatCode == 'r') {
                    currentStyle = Style.empty();
                }
                // Colors
                else if (getLegacyColor(String.valueOf(formatCode)) != null) {
                    currentStyle = currentStyle.color(getLegacyColor(String.valueOf(formatCode)));
                }
                // Formatting
                else if (getLegacyFormatting(String.valueOf(formatCode)) != null) {
                    currentStyle = currentStyle.decoration(getLegacyFormatting(String.valueOf(formatCode)), true);
                }

                i++; // Skip the format code
            } else {
                currentText.append(input.charAt(i));
            }
        }

        // Append any remaining text
        if (currentText.length() > 0) {
            message = message.append(Component.text(currentText.toString(), currentStyle));
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
            default -> null;                              // Not a color code
        };
    }

    private static TextDecoration getLegacyFormatting(String code) {
        return switch (code.toLowerCase()) {
            case "k" -> TextDecoration.OBFUSCATED;    // Obfuscated
            case "l" -> TextDecoration.BOLD;          // Bold
            case "m" -> TextDecoration.STRIKETHROUGH; // Strikethrough
            case "n" -> TextDecoration.UNDERLINED;    // Underline
            case "o" -> TextDecoration.ITALIC;        // Italic
            default -> null;                          // Not a formatting code
        };
    }

    public static String stripColor(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
    }


    private static long safeAdd(long a, long b) {
        long result = a + b;
        if (((a ^ result) & (b ^ result)) < 0) return -1L; // Overflow or underflow
        return result;
    }

    private static long safeMultiply(long a, long b) {
        if (a == 0 || b == 0) return 0;
        long result = a * b;
        if (a != result / b) return -1L; // Overflow or underflow
        return result;
    }

    public static long formattedPlaytimeToTicks(String input) {
        if (input == null || input.trim().isEmpty()) {
            return -1L;
        }

        String[] timeParts = input.split("\\s*,\\s*");
        long timeToTicks = 0;
        boolean hasYear = false, hasDay = false, hasHour = false, hasMinute = false, hasSecond = false;

        for (String part : timeParts) {
            try {
                int time = Integer.parseInt(part.replaceAll("[^\\d-]", ""));
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



    public static String ticksToFormattedPlaytime(long ticks){
        return ticksToFormattedPlaytime(ticks, PlaytimeFormatsConfiguration.getInstance().getFormat("default"));
    }

    public static String ticksToFormattedPlaytime(long ticks, PlaytimeFormat format) {

        boolean isNegative = ticks < 0;
        ticks = Math.abs(ticks);

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

        // Use the format's formatting string as template
        String result = format.getFormatting();

        // Replace time value placeholders and labels
        if (years > 0) {
            result = result.replace("%y%", String.valueOf(years));
            result = result.replace("{years}", format.getYearsLabel((int) years));
        } else {
            // Remove years section if zero - match pattern like "%y%{years}, " or "%y%{years}"
            result = result.replaceAll("%y%\\{years\\}(?:,\\s*)?", "");
        }

        if (days > 0) {
            result = result.replace("%d%", String.valueOf(days));
            result = result.replace("{days}", format.getDaysLabel((int) days));
        } else {
            // Remove days section if zero
            result = result.replaceAll("%d%\\{days\\}(?:,\\s*)?", "");
        }

        if (hours > 0) {
            result = result.replace("%h%", String.valueOf(hours));
            result = result.replace("{hours}", format.getHoursLabel((int) hours));
        } else {
            // Remove hours section if zero
            result = result.replaceAll("%h%\\{hours\\}(?:,\\s*)?", "");
        }

        if (minutes > 0) {
            result = result.replace("%m%", String.valueOf(minutes));
            result = result.replace("{minutes}", format.getMinutesLabel((int) minutes));
        } else {
            // Remove minutes section if zero
            result = result.replaceAll("%m%\\{minutes\\}(?:,\\s*)?", "");
        }

        // Always show seconds if it's > 0, OR if everything else is 0 (including when ticks was originally 0)
        if (seconds > 0 || (years == 0 && days == 0 && hours == 0 && minutes == 0)) {
            result = result.replace("%s%", String.valueOf(seconds));
            result = result.replace("{seconds}", format.getSecondsLabel((int) seconds));
        } else {
            // Remove seconds section if zero and other units exist
            result = result.replaceAll("%s%\\{seconds\\}(?:,\\s*)?", "");
        }

        // Special case: if everything is 0, ensure we show "0{seconds}" with plural form
        if (years == 0 && days == 0 && hours == 0 && minutes == 0 && seconds == 0) {
            result = "0" + format.getSecondsLabel(0); // Use plural form for 0
        }

        // Clean up any remaining placeholders and extra commas/spaces
        result = result.replaceAll("%[ydhms]%", "");
        result = result.replaceAll("\\{\\w+\\}", "");
        result = result.replaceAll(",\\s*,", ",");
        result = result.replaceAll("^,\\s*|,\\s*$", "");
        result = result.trim();

        return isNegative ? "-" + result : result;
    }

    public static long ticksToTimeUnit(long ticks, String unit) {
        if (ticks < 0) {
            return 0;
        }

        long seconds = ticks / TICKS_PER_SECOND;

        return switch (unit.toLowerCase()) {
            case "y" -> seconds / (365 * 24 * 60 * 60);
            case "d" -> seconds / (24 * 60 * 60);
            case "h" -> seconds / (60 * 60);
            case "m" -> seconds / 60;
            case "s" -> seconds;
            default -> 0;
        };
    }


    public static String placeholdersReplacer(String message, Map<String, String> combinations){

        //Apply %PLAYTIME% special placeholder with custom format
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("%PLAYTIME(?::(\\w+))?%");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String formatName = matcher.group(1);
            // Get the PlaytimeFormat for this placeholder, or default if not found
            PlaytimeFormatsConfiguration config = PlaytimeFormatsConfiguration.getInstance();
            String actualFormatName = (formatName == null) ? "default" : formatName;
            PlaytimeFormat format = config.getFormat(actualFormatName);

            format = (format == null) ? config.getFormat("default") : format;

            // Check if we have a playtime value in the combinations map
            String playtimeValue = null;
            if (combinations.containsKey("%PLAYTIME%")) {
                // If we have ticks, convert them using the format
                try {
                    long ticks = Long.parseLong(combinations.get("%PLAYTIME%"));
                    playtimeValue = ticksToFormattedPlaytime(ticks, format);
                } catch (NumberFormatException e) {
                    playtimeValue = "0s"; // Default fallback
                }
            }

            // Replace the placeholder with the formatted playtime
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(playtimeValue));
        }

        matcher.appendTail(result);
        message = result.toString();

        // Apply passed placeholders
        for (Map.Entry<String, String> entry : combinations.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        // Normalize multiple spaces to single space
        message = message.replaceAll("\\s+", " ");

        return message;
    }
}