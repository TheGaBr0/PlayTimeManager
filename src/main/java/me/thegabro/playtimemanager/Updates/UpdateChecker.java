package me.thegabro.playtimemanager.Updates;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class UpdateChecker implements Listener {

    private final Plugin plugin;
    private final String projectId;
    private final String currentVersion;

    private String latestVersion = null;
    private long lastCheckTime = 0;
    private final Set<String> notifiedPlayers = new HashSet<>();
    private static final long CHECK_INTERVAL = TimeUnit.DAYS.toMillis(1);

    /**
     * Creates a new update checker for Modrinth
     * @param plugin plugin instance
     * @param projectId Modrinth project ID
     * @param currentVersion current plugin version
     */
    public UpdateChecker(Plugin plugin, String projectId, String currentVersion) {
        this.plugin = plugin;
        this.projectId = projectId;
        this.currentVersion = currentVersion;
    }

    /**
     * Starts the update checker
     */
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        checkForUpdates();

        // Schedule daily checks
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkForUpdates,
                20L * 60 * 60, // 1 hour initial delay
                20L * 60 * 60 * 24); // 24 hours repeat
    }

    /**
     * Checks for updates on Modrinth
     */
    public void checkForUpdates() {
        long currentTime = System.currentTimeMillis();

        // Skip if checked recently (within the last day)
        if (currentTime - lastCheckTime < CHECK_INTERVAL && latestVersion != null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String apiUrl = "https://api.modrinth.com/v2/project/" + projectId + "/version";
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", plugin.getName() + "/" + currentVersion);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JsonArray versions = JsonParser.parseString(response.toString()).getAsJsonArray();

                    if (!versions.isEmpty()) {
                        JsonObject latestVersionObj = versions.get(0).getAsJsonObject();
                        latestVersion = latestVersionObj.get("version_number").getAsString();
                        lastCheckTime = currentTime;

                        // Log to console if update available
                        if (isNewerVersion(latestVersion, currentVersion)) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                logUpdateAvailable();
                                notifiedPlayers.clear(); // Reset notifications for new version
                            });
                        }
                    }
                }
                connection.disconnect();

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates");
            }
        });
    }

    /**
     * Compares two version strings
     * @param newVersion The version to check
     * @param oldVersion The current version
     * @return true if newVersion is newer than oldVersion
     */
    private boolean isNewerVersion(String newVersion, String oldVersion) {
        // Null check
        if (newVersion == null || oldVersion == null) {
            return false;
        }

        String[] newParts = newVersion.replaceAll("[^0-9.]", "").split("\\.");
        String[] oldParts = oldVersion.replaceAll("[^0-9.]", "").split("\\.");

        int maxLength = Math.max(newParts.length, oldParts.length);

        for (int i = 0; i < maxLength; i++) {
            int newPart = i < newParts.length ? parseVersionPart(newParts[i]) : 0;
            int oldPart = i < oldParts.length ? parseVersionPart(oldParts[i]) : 0;

            if (newPart > oldPart) {
                return true;
            } else if (newPart < oldPart) {
                return false;
            }
        }

        return false;
    }

    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Logs update notification to console
     */
    private void logUpdateAvailable() {
        plugin.getLogger().warning("========================================");
        plugin.getLogger().warning("A new PlayTimeManager update is available!");
        plugin.getLogger().warning("Current version: " + currentVersion);
        plugin.getLogger().warning("Latest version: " + latestVersion);
        plugin.getLogger().warning("Download: https://modrinth.com/plugin/" + projectId + "/version/" + latestVersion);
        plugin.getLogger().warning("========================================");
    }

    /**
     * Handles player join events to notify OPs about updates
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Only notify OPs
        if (!player.isOp()) {
            return;
        }

        // Only notify once per player per session
        if (notifiedPlayers.contains(player.getName())) {
            return;
        }

        // Check if update is available
        if (isNewerVersion(latestVersion, currentVersion)) {
            notifiedPlayers.add(player.getName());

            // Delay message slightly so it doesn't get lost in join spam
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(Utils.parseColors("&e&l[" + plugin.getName() + "]&r &aA new update is available!"));
                player.sendMessage(Utils.parseColors("&eCurrent: &f" + currentVersion + " &e-> Latest: &f" + latestVersion));
                player.sendMessage(Utils.parseColors("&eDownload: &bhttps://modrinth.com/plugin/" + projectId + "/version/" + latestVersion)
                        .color(NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/" + projectId + "/version/" + latestVersion))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to open!"))));
            }, 40L); // 2 second delay
        }
    }


}