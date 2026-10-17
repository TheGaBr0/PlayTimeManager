package me.clip.placeholderapi;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test double that shadows the real PlaceholderAPI class (test classes come first on the classpath).
 * Placeholders resolve from values the test sets per player; unknown ones are left untouched, like
 * the real one does.
 */
public final class PlaceholderAPI {
    private static final Map<UUID, Map<String, String>> VALUES = new ConcurrentHashMap<>();

    private PlaceholderAPI() {}

    /** Test hook: makes {@code placeholder} (e.g. "%vanish_placeholder%") resolve to {@code value} for the player. */
    public static void setValue(UUID player, String placeholder, String value) {
        VALUES.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(placeholder, value);
    }

    public static String setPlaceholders(OfflinePlayer player, String text) {
        if (player == null || text == null) return text;
        String result = text;
        for (Map.Entry<String, String> e : VALUES.getOrDefault(player.getUniqueId(), Map.of()).entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    public static String setPlaceholders(Player player, String text) {
        return setPlaceholders((OfflinePlayer) player, text);
    }
}
