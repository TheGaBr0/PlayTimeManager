package me.clip.placeholderapi.expansion;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double that shadows the real PlaceholderExpansion: registering just records the expansion,
 * so tests can call {@link #onRequest} on it as PlaceholderAPI would.
 */
public abstract class PlaceholderExpansion {
    public static final List<PlaceholderExpansion> REGISTERED = new CopyOnWriteArrayList<>();

    public abstract @NotNull String getIdentifier();

    public abstract @NotNull String getAuthor();

    public abstract @NotNull String getVersion();

    public boolean persist() {
        return false;
    }

    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return null;
    }

    public boolean register() {
        REGISTERED.add(this);
        return true;
    }
}
