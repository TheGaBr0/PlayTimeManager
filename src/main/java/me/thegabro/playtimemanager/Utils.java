package me.thegabro.playtimemanager;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Utils {
    // Constants for tick conversions
    private static final long TICKS_PER_SECOND = 20L;
    private static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
    private static final long TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;
    private static final long TICKS_PER_DAY = TICKS_PER_HOUR * 24;
    private static final long TICKS_PER_WEEK = TICKS_PER_DAY * 7;
    private static final long TICKS_PER_YEAR = TICKS_PER_DAY * 365;

    /**
     * Uses a regex to check if an input nickname is valid or not
     *
     * @param username The input string containing the nickname to check
     * @return whether it's valid or not
     */
    public static boolean isValidMinecraftUsername(String username) {
        return username != null && username.matches("^[a-zA-Z0-9_]{3,16}$");
    }

    /**
     * Parses color codes and formatting from a string and converts it to a Component
     * Supports both legacy color codes (&0-f, &k-o, &r) and hex colors (&#RRGGBB)
     *
     * @param input The input string containing color codes and text
     * @return Component with proper formatting and colors applied
     */
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
                if (!currentText.isEmpty()) {
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

    /**
     * Gets the TextColor for a legacy color code (0-9, a-f)
     *
     * @param code The single character color code
     * @return TextColor object for the code, or null if invalid
     */
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

    /**
     * Gets the TextDecoration for a legacy formatting code (k, l, m, n, o)
     *
     * @param code The single character formatting code
     * @return TextDecoration object for the code, or null if invalid
     */
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

    /**
     * Removes all color codes from a text string
     *
     * @param text The text containing § color codes
     * @return The text with all color codes removed
     */
    public static String stripColor(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
    }

    /**
     * Safely adds two long values, checking for overflow
     *
     * @param a First value
     * @param b Second value
     * @return Sum of a and b, or -1L if overflow occurs
     */
    private static long safeAdd(long a, long b) {
        long result = a + b;
        if (((a ^ result) & (b ^ result)) < 0) return -1L; // Overflow or underflow
        return result;
    }

    /**
     * Safely multiplies two long values, checking for overflow
     *
     * @param a First value
     * @param b Second value
     * @return Product of a and b, or -1L if overflow occurs
     */
    private static long safeMultiply(long a, long b) {
        if (a == 0 || b == 0) return 0;
        long result = a * b;
        if (a != result / b) return -1L; // Overflow or underflow
        return result;
    }

    /**
     * Converts a formatted playtime string to ticks
     * Accepts formats like "1y,2w,3d,4h,5m,6s" or combinations thereof
     * Each time unit can only appear once in the input
     *
     * @param input The formatted playtime string (e.g., "1y,2w,3d")
     * @return The equivalent time in ticks, or -1L if input is invalid
     */
    public static long formattedPlaytimeToTicks(String input) {
        if (input == null || input.trim().isEmpty()) {
            return -1L;
        }

        String[] timeParts = input.split("\\s*,\\s*");
        long timeToTicks = 0;
        boolean hasYear = false, hasWeek = false, hasDay = false, hasHour = false, hasMinute = false, hasSecond = false;

        for (String part : timeParts) {
            try {
                int time = Integer.parseInt(part.replaceAll("[^\\d-]", ""));
                if (time < 0) return -1L; // Prevent negative values

                String format = part.replaceAll("[\\d\\s]", "").toLowerCase();

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
                    case "w":
                        if (!hasWeek) {
                            partTicks = safeMultiply(time, TICKS_PER_WEEK);
                            if (partTicks == -1L) return -1L;
                            timeToTicks = safeAdd(timeToTicks, partTicks);
                            if (timeToTicks == -1L) return -1L;
                            hasWeek = true;
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


    /**
     * Converts ticks to a formatted playtime string using the default format
     *
     * @param ticks The time in ticks to convert
     * @return Formatted playtime string using default format configuration
     */
    public static String ticksToFormattedPlaytime(long ticks){
        return ticksToFormattedPlaytime(ticks, PlaytimeFormatsConfiguration.getInstance().getFormat("default"));
    }

    /**
     * Converts ticks to a formatted playtime string using a specific format
     * Handles negative values and formats time units according to the provided format configuration
     * Can redistribute time from omitted units to displayed units based on format settings
     *
     * @param ticks The time in ticks to convert
     * @param format The PlaytimeFormat to use for formatting
     * @return Formatted playtime string (e.g., "1 year, 2 weeks, 3 days, 4 hours")
     */
    public static String ticksToFormattedPlaytime(long ticks, PlaytimeFormat format) {

        boolean isNegative = ticks < 0;
        ticks = Math.abs(ticks);

        long seconds = ticks / TICKS_PER_SECOND;

        final long SECONDS_PER_YEAR = 365 * 24 * 60 * 60;
        final long SECONDS_PER_WEEK = 7 * 24 * 60 * 60;
        final long SECONDS_PER_DAY = 24 * 60 * 60;
        final long SECONDS_PER_HOUR = 60 * 60;
        final long SECONDS_PER_MINUTE = 60;

        // Get the formatting string to determine which units are included
        String formatting = format.getFormatting();
        boolean hasYears = formatting.contains("%y%");
        boolean hasWeeks = formatting.contains("%w%");
        boolean hasDays = formatting.contains("%d%");
        boolean hasHours = formatting.contains("%h%");
        boolean hasMinutes = formatting.contains("%m%");
        boolean hasSeconds = formatting.contains("%s%");

        long years, weeks, days, hours, minutes;

        // Calculate time units based on redistribution setting
        if (format.shouldDistributeRemovedTime()) {
            // Redistribution mode: only break down time for units that are in the format
            if (hasYears) {
                years = seconds / SECONDS_PER_YEAR;
                seconds %= SECONDS_PER_YEAR;
            } else {
                years = 0;
            }

            if (hasWeeks) {
                weeks = seconds / SECONDS_PER_WEEK;
                seconds %= SECONDS_PER_WEEK;
            } else {
                weeks = 0;
            }

            if (hasDays) {
                days = seconds / SECONDS_PER_DAY;
                seconds %= SECONDS_PER_DAY;
            } else {
                days = 0;
            }

            if (hasHours) {
                hours = seconds / SECONDS_PER_HOUR;
                seconds %= SECONDS_PER_HOUR;
            } else {
                hours = 0;
            }

            if (hasMinutes) {
                minutes = seconds / SECONDS_PER_MINUTE;
                seconds %= SECONDS_PER_MINUTE;
            } else {
                minutes = 0;
            }
        } else {
            // Standard mode: calculate all units normally
            years = seconds / SECONDS_PER_YEAR;
            seconds %= SECONDS_PER_YEAR;

            weeks = seconds / SECONDS_PER_WEEK;
            seconds %= SECONDS_PER_WEEK;

            days = seconds / SECONDS_PER_DAY;
            seconds %= SECONDS_PER_DAY;

            hours = seconds / SECONDS_PER_HOUR;
            seconds %= SECONDS_PER_HOUR;

            minutes = seconds / SECONDS_PER_MINUTE;
            seconds %= SECONDS_PER_MINUTE;
        }

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

        if (weeks > 0) {
            result = result.replace("%w%", String.valueOf(weeks));
            result = result.replace("{weeks}", format.getWeeksLabel((int) weeks));
        } else {
            // Remove weeks section if zero
            result = result.replaceAll("%w%\\{weeks\\}(?:,\\s*)?", "");
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
        if (seconds > 0 || (years == 0 && weeks == 0 && days == 0 && hours == 0 && minutes == 0)) {
            result = result.replace("%s%", String.valueOf(seconds));
            result = result.replace("{seconds}", format.getSecondsLabel((int) seconds));
        } else {
            // Remove seconds section if zero and other units exist
            result = result.replaceAll("%s%\\{seconds\\}(?:,\\s*)?", "");
        }

        // Special case: if everything is 0, ensure we show "0{seconds}" with plural form
        if (years == 0 && weeks == 0 && days == 0 && hours == 0 && minutes == 0 && seconds == 0) {
            result = "0" + format.getSecondsLabel(0); // Use plural form for 0
        }

        // Clean up any remaining placeholders and extra commas/spaces
        result = result.replaceAll("%[ywdhms]%", "");
        result = result.replaceAll("\\{\\w+\\}", "");
        result = result.replaceAll(",\\s*,", ",");
        result = result.replaceAll("^,\\s*|,\\s*$", "");
        result = result.trim();

        return isNegative ? "-" + result : result;
    }

    /**
     * Converts ticks to a specific time unit
     *
     * @param ticks The time in ticks to convert
     * @param unit The target time unit ("y", "w", "d", "h", "m", "s")
     * @return The equivalent time in the specified unit, or 0 if invalid
     */
    public static long ticksToTimeUnit(long ticks, String unit) {
        if (ticks < 0) {
            return 0;
        }

        long seconds = ticks / TICKS_PER_SECOND;

        return switch (unit.toLowerCase()) {
            case "y" -> seconds / (365 * 24 * 60 * 60);
            case "w" -> seconds / (7 * 24 * 60 * 60);
            case "d" -> seconds / (24 * 60 * 60);
            case "h" -> seconds / (60 * 60);
            case "m" -> seconds / 60;
            case "s" -> seconds;
            default -> 0;
        };
    }

    /**
     * Replaces playtime-specific placeholders in a message string with their corresponding values
     * Handles special playtime placeholders with optional custom formatting:
     * - %PLAYTIME% / %PLAYTIME:format%
     * - %ACTUAL_PLAYTIME% / %ACTUAL_PLAYTIME:format%
     * - %ARTIFICIAL_PLAYTIME% / %ARTIFICIAL_PLAYTIME:format%
     * - %AFK_PLAYTIME% / %AFK_PLAYTIME:format%
     *
     * @param message The message containing playtime placeholders to replace
     * @param combinations Map of placeholder-value pairs (should contain playtime values in ticks)
     * @return The message with playtime placeholders replaced
     */
    public static String playtimePlaceholdersReplacer(String message, Map<String, String> combinations){
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("%((?:AFK_|ACTUAL_|ARTIFICIAL_)?PLAYTIME)(?::(\\w+))?%");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String playtimeType = matcher.group(1);  // PLAYTIME, ACTUAL_PLAYTIME, ARTIFICIAL_PLAYTIME, or AFK_PLAYTIME
            String formatName = matcher.group(2);    // Optional format name after colon

            // Get the PlaytimeFormat for this placeholder, or default if not found
            PlaytimeFormatsConfiguration config = PlaytimeFormatsConfiguration.getInstance();
            String actualFormatName = (formatName == null) ? "default" : formatName;
            PlaytimeFormat format = config.getFormat(actualFormatName);

            format = (format == null) ? config.getFormat("default") : format;

            // Check if we have a playtime value in the combinations map for this specific type
            String playtimeValue;
            String placeholderKey = "%" + playtimeType + "%";

            if (combinations.containsKey(placeholderKey)) {
                // If we have ticks, convert them using the format
                try {
                    long ticks = Long.parseLong(combinations.get(placeholderKey));
                    playtimeValue = ticksToFormattedPlaytime(ticks, format);
                } catch (NumberFormatException e) {
                    playtimeValue = "0s"; // Default fallback
                }
            } else {
                playtimeValue = "0s"; // Default fallback if placeholder not found
            }

            // Replace the placeholder with the formatted playtime
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(playtimeValue));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Replaces standard placeholders in a message string with their corresponding values
     * Does NOT handle playtime placeholders - use playtimePlaceholdersReplacer for those
     *
     * @param message The message containing placeholders to replace
     * @param combinations Map of placeholder-value pairs for replacement
     * @return The message with all standard placeholders replaced and normalized spacing
     */
    public static String standardPlaceholdersReplacer(String message, Map<String, String> combinations){
        // Apply passed placeholders
        for (Map.Entry<String, String> entry : combinations.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        // Normalize multiple spaces to single space
        message = message.replaceAll("\\s+", " ");

        return message;
    }

    /**
     * Replaces all placeholders (both playtime and standard) in a message string
     * This is a convenience method that combines both playtimePlaceholdersReplacer and standardPlaceholdersReplacer
     *
     * @param message The message containing placeholders to replace
     * @param combinations Map of placeholder-value pairs for replacement
     * @return The message with all placeholders replaced and normalized spacing
     */
    public static String placeholdersReplacer(String message, Map<String, String> combinations){
        // First replace playtime placeholders (with custom formatting support)
        message = playtimePlaceholdersReplacer(message, combinations);

        // Then replace standard placeholders
        message = standardPlaceholdersReplacer(message, combinations);

        return message;
    }


    /**
     * Formats a raw lore string into a list of colored lines for Minecraft item lore.
     * Splits the input string by /n characters and applies color formatting to each line.
     *
     * @param rawLore The raw lore string containing /n separators and color codes
     * @return A list of formatted strings ready to be used with ItemMeta.setLore()
     */
    public static List<Component> formatLore(String rawLore) {
        if (rawLore == null || rawLore.isEmpty()) {
            return new ArrayList<>();
        }

        String[] lines = rawLore.split("/n");
        List<Component> formattedLore = new ArrayList<>();

        for (String line : lines) {
            formattedLore.add(parseColors(line));
        }

        return formattedLore;
    }


    /**
     * Creates a player head ItemStack from input format "PLAYER_HEAD:playername"
     * If no player name is specified or the format is invalid, defaults to Steve's head
     * Handles both Java and Bedrock Edition players (Bedrock players via Geyser/Floodgate)
     *
     * @param input The input string in format "PLAYER_HEAD:playername" or just "PLAYER_HEAD"
     * @return ItemStack of a player head with the specified player's skin, or Steve by default
     */
    public static ItemStack createPlayerHead(String input) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);

        if (input == null || input.trim().isEmpty()) {
            return skull;
        }

        String[] parts = input.split(":", 2);

        // Check if it's a player head request
        if (!parts[0].equalsIgnoreCase("PLAYER_HEAD")) {
            return skull;
        }

        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();

        if (skullMeta != null) {
            try {
                String playerName = (parts.length > 1 && !parts[1].trim().isEmpty()) ? parts[1].trim() : "Steve";

                // Try to get the player if they're online (works for both Java and Bedrock)
                Player onlinePlayer = Bukkit.getPlayerExact(playerName);
                if (onlinePlayer != null) {
                    skullMeta.setOwningPlayer(onlinePlayer);
                } else {
                    // For offline players, try to create profile
                    // This will work for Java players but may fail for Bedrock players
                    PlayerProfile profile = Bukkit.createProfile(playerName);
                    skullMeta.setPlayerProfile(profile);
                }
                skull.setItemMeta(skullMeta);
            } catch (Exception e) {
                // If profile creation fails (e.g., Bedrock player), skull remains as default (Steve)
                // This is intentional - we silently fall back to Steve's head
            }
        }

        return skull;
    }

    /**
     * Creates a player head ItemStack with proper handling for online/offline and Java/Bedrock players
     *
     * @param input The input string in format "PLAYER_HEAD:playername" or just "PLAYER_HEAD"
     * @param contextPlayerName The player name to use when no specific player is specified
     * @param offlinePlayer Optional OfflinePlayer instance (can be null)
     * @return ItemStack of a player head with the specified or context player's skin
     */
    public static ItemStack createPlayerHeadWithContext(String input, String contextPlayerName, @Nullable OfflinePlayer offlinePlayer) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);

        if (input == null || input.trim().isEmpty()) return skull;

        String[] parts = input.split(":", 2);
        if (!parts[0].equalsIgnoreCase("PLAYER_HEAD")) return skull;

        String playerName;
        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
            playerName = parts[1].trim();
        } else if (contextPlayerName != null && !contextPlayerName.trim().isEmpty()) {
            playerName = contextPlayerName.trim();
        } else {
            return skull;
        }

        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            try {
                // If we have an OfflinePlayer instance and it matches our target, use it directly
                if (offlinePlayer != null && offlinePlayer.getName() != null &&
                        offlinePlayer.getName().equalsIgnoreCase(playerName)) {
                    meta.setOwningPlayer(offlinePlayer);
                    skull.setItemMeta(meta);
                    return skull;
                }

                // For offline Java players only - Bedrock player names start with special chars
                if (isValidMinecraftUsername(playerName)) {
                    PlayerProfile profile = Bukkit.createProfile(playerName);
                    meta.setPlayerProfile(profile);
                    skull.setItemMeta(meta);
                }
                // For Bedrock players, skull remains as Steve (default)

            } catch (Exception e) {
                // Silently fall back to Steve's head
            }
        }

        return skull;
    }

    public static String formatInstant(Instant instant, String pattern) {
        if (instant == null) {
            return "Never";
        }

        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter
                    .ofPattern(pattern)
                    .withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException e) {
            PlayTimeManager.getInstance().getLogger().warning(
                    "Invalid datetime-format pattern '" + pattern + "': " + e.getMessage() +
                            ". Using default format 'MMM dd, yyyy HH:mm:ss'"
            );
            formatter = DateTimeFormatter
                    .ofPattern("MMM dd, yyyy HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
        }

        return formatter.format(instant);
    }

}