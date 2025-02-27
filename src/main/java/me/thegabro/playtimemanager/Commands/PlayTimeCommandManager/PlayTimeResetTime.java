package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PlayTimeResetTime {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();

    // Map to store pending confirmations with a timestamp
    private static final Map<UUID, PendingReset> pendingResets = new HashMap<>();
    // Timeout for confirmation (60 seconds)
    private static final long CONFIRMATION_TIMEOUT_SECONDS = 60;

    // Class to store pending reset information
    private static class PendingReset {
        final String resetType;
        final long timestamp;

        PendingReset(String resetType) {
            this.resetType = resetType;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TimeUnit.SECONDS.toMillis(CONFIRMATION_TIMEOUT_SECONDS);
        }
    }

    public PlayTimeResetTime(CommandSender sender, String[] args) {
        execute(sender, args);
    }

    public void execute(CommandSender sender, String[] args) {
        // Default to "all" if no reset type is specified
        String resetType = args.length >= 3 ? args[2].toLowerCase() : "all";

        if (!resetType.equals("db") && !resetType.equals("stats") && !resetType.equals("all")) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                    " Invalid reset type. Use 'db', 'stats', or 'all'"));
            return;
        }

        if (args[0].equals("*")) {
            // Handle reset all players with confirmation
            handleResetAllConfirmation(sender, resetType);
            return;
        }

        resetSinglePlayer(sender, args[0], resetType);
    }

    private void handleResetAllConfirmation(CommandSender sender, String resetType) {
        UUID senderUUID;

        // Get UUID based on sender type
        if (sender instanceof Player) {
            senderUUID = ((Player) sender).getUniqueId();
        } else {
            // For console commands, use a fixed UUID
            senderUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
        }

        // Check if there's a pending confirmation
        if (pendingResets.containsKey(senderUUID)) {
            PendingReset pendingReset = pendingResets.get(senderUUID);

            // Check if the confirmation has expired
            if (pendingReset.isExpired()) {
                pendingResets.remove(senderUUID);
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " Your previous reset confirmation has expired. Please try again."));
                requestConfirmation(sender, senderUUID, resetType);
                return;
            }

            // If reset type doesn't match, require a new confirmation
            if (!pendingReset.resetType.equals(resetType)) {
                pendingResets.remove(senderUUID);
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " Reset type has changed. Please confirm again."));
                requestConfirmation(sender, senderUUID, resetType);
                return;
            }

            // Confirmed, proceed with reset
            pendingResets.remove(senderUUID);
            resetAllPlayers(sender, resetType);
        } else {
            // No pending confirmation, request one
            requestConfirmation(sender, senderUUID, resetType);
        }
    }

    private void requestConfirmation(CommandSender sender, UUID senderUUID, String resetType) {
        pendingResets.put(senderUUID, new PendingReset(resetType));

        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                " &c&lWARNING&r&7: You are about to reset " + getResetTypeDescription(resetType) +
                " for &e&lALL players&7!"));
        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                " &7This action cannot be undone. Run the command again within " +
                CONFIRMATION_TIMEOUT_SECONDS + " seconds to confirm."));
    }

    private void resetAllPlayers(CommandSender sender, String resetType) {
        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                " Starting reset of all players' " + getResetTypeDescription(resetType) + ", this will take some time..."));

        // Run the reset process async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Get all users first
            List<DBUser> users = dbUsersManager.getAllDBUsers();
            AtomicInteger totalPlayersReset = new AtomicInteger();
            AtomicLong totalPlaytimeReset = new AtomicLong();

            // Keep track of processed player UUIDs to avoid double-counting
            Set<UUID> processedPlayerUUIDs = new HashSet<>();

            // Process resets based on type
            CompletableFuture<Void> resetFuture = CompletableFuture.completedFuture(null);

            // Reset in-game statistics if needed
            if (resetType.equals("stats") || resetType.equals("all")) {
                resetFuture = resetFuture.thenCompose(v -> {
                    CompletableFuture<Void> statsFuture = new CompletableFuture<>();

                    // Reset stats on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // Handle online players
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            UUID playerUUID = player.getUniqueId();
                            long currentPlaytime = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
                            totalPlaytimeReset.addAndGet(currentPlaytime);

                            if (!processedPlayerUUIDs.contains(playerUUID)) {
                                totalPlayersReset.getAndIncrement();
                                processedPlayerUUIDs.add(playerUUID);
                            }

                            player.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);

                            OnlineUser onlineUser = onlineUsersManager.getOnlineUserByUUID(playerUUID.toString());
                            if (onlineUser != null) {
                                onlineUser.refreshFromServerOnJoinPlayTime();
                            }
                        }

                        // Handle offline players
                        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                            if (offlinePlayer.hasPlayedBefore()) {
                                UUID playerUUID = offlinePlayer.getUniqueId();

                                try {
                                    if (!processedPlayerUUIDs.contains(playerUUID)) {
                                        totalPlayersReset.getAndIncrement();
                                        processedPlayerUUIDs.add(playerUUID);
                                    }

                                    long currentPlaytime = offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE);
                                    totalPlaytimeReset.addAndGet(currentPlaytime);
                                    offlinePlayer.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Failed to reset statistics for offline player: " +
                                            offlinePlayer.getName());
                                }
                            }
                        }

                        statsFuture.complete(null);
                    });

                    return statsFuture;
                });
            }

            // Reset database records if needed
            if (resetType.equals("db") || resetType.equals("all")) {
                resetFuture = resetFuture.thenCompose(v -> {
                    CompletableFuture<Void> dbFuture = new CompletableFuture<>();

                    // Process database updates completely asynchronously
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        // Process all users at once since we're already running asynchronously
                        for (DBUser u : users) {
                            UUID playerUUID = UUID.fromString(u.getUuid());

                            if (!processedPlayerUUIDs.contains(playerUUID)) {
                                totalPlayersReset.getAndIncrement();
                                processedPlayerUUIDs.add(playerUUID);
                            }

                            totalPlaytimeReset.addAndGet(u.getPlaytime());
                            u.reset(); // Make sure this method is thread-safe!
                        }

                        dbFuture.complete(null);
                    });

                    return dbFuture;
                });
            }

            // After all resets, update online players' data and then update top players list
            resetFuture.thenCompose(v -> {
                CompletableFuture<Void> updateFuture = new CompletableFuture<>();

                // Run a task to update online player data after both stats and DB are reset
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    // Update current playtime of online players to the database
                    for (OnlineUser user : onlineUsersManager.getOnlineUsersByUUID().values()) {
                        try {
                            // Update to DB
                            user.updateDB();
                        } catch (Exception e) {
                            plugin.getLogger().severe(String.format("Failed to update playtime for user %s: %s",
                                    user.getNickname(), e.getMessage()));
                        }
                    }

                    // Now we can update the top players list
                    dbUsersManager.clearCache();

                    // We need to handle this differently since updateTopPlayersFromDB runs its own async task
                    // Instead, we'll use our own implementation that waits for completion
                    Map<String, String> dbTopPlayers = plugin.getDatabase().getTopPlayersByPlaytime(100);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        List<DBUser> topPlayers = dbUsersManager.getTopPlayers();
                        synchronized (topPlayers) {
                            topPlayers.clear();
                            for (Map.Entry<String, String> entry : dbTopPlayers.entrySet()) {
                                DBUser user = dbUsersManager.getUserFromUUID(entry.getKey());
                                if (user != null) {
                                    topPlayers.add(user);
                                }
                            }
                        }
                        updateFuture.complete(null);
                    });
                });

                return updateFuture;
            }).thenRun(() -> {
                dbUsersManager.updateTopPlayersFromDB();
                // Final notification
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                            " All players' " + getResetTypeDescription(resetType) +
                            " have been reset! Total: &e" + totalPlayersReset +
                            "&7 players with &e" + Utils.ticksToFormattedPlaytime(totalPlaytimeReset.get()) + "&7 of playtime"));
                });
            });
        });
    }

    private void resetSinglePlayer(CommandSender sender, String playerName, String resetType) {
        DBUser user = dbUsersManager.getUserFromNickname(playerName);

        if (user == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                    " The player &e" + playerName + "&7 has never joined the server!"));
            return;
        }

        long playtimeBeforeReset = user.getPlaytime();
        AtomicLong totalPlaytimeReset = new AtomicLong(0);

        // Create a CompletableFuture chain similar to resetAllPlayers
        CompletableFuture<Void> resetFuture = CompletableFuture.completedFuture(null);

        // Reset in-game statistics if needed
        if (resetType.equals("stats") || resetType.equals("all")) {
            resetFuture = resetFuture.thenCompose(v -> {
                CompletableFuture<Void> statsFuture = new CompletableFuture<>();

                // Reset stats on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (user instanceof OnlineUser) {
                        Player p = Bukkit.getPlayerExact(playerName);
                        if (p != null) {
                            totalPlaytimeReset.addAndGet(p.getStatistic(Statistic.PLAY_ONE_MINUTE));
                            p.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
                            // Add this line to refresh the fromServerOnJoinPlayTime
                            ((OnlineUser) user).refreshFromServerOnJoinPlayTime();
                        }
                    } else {
                        OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
                        if (p.hasPlayedBefore()) {
                            try {
                                totalPlaytimeReset.addAndGet(p.getStatistic(Statistic.PLAY_ONE_MINUTE));
                                p.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to reset statistics for offline player: " + playerName);
                            }
                        }
                    }
                    statsFuture.complete(null);
                });

                return statsFuture;
            });
        }

        // Reset database data if needed
        if (resetType.equals("db") || resetType.equals("all")) {
            resetFuture = resetFuture.thenCompose(v -> {
                CompletableFuture<Void> dbFuture = new CompletableFuture<>();

                // Process database updates asynchronously
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    totalPlaytimeReset.addAndGet(playtimeBeforeReset);
                    user.reset();
                    dbFuture.complete(null);
                });

                return dbFuture;
            });
        }

        // After all resets, update online player's data first (if applicable) and then update top players list
        resetFuture.thenRun(() -> {
            // Update the player's DB entry first if they're online
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // If the user is online, update their DB entry first
                if (user instanceof OnlineUser) {
                    try {
                        ((OnlineUser) user).updateDB();
                    } catch (Exception e) {
                        plugin.getLogger().severe(String.format("Failed to update playtime for user %s: %s",
                                user.getNickname(), e.getMessage()));
                    }
                }

                // Then update top players list
                dbUsersManager.updateTopPlayersFromDB();

                // Final notification on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                            " Reset " + getResetTypeDescription(resetType) + " for player &e" + playerName +
                            "&7 (Removed &e" + Utils.ticksToFormattedPlaytime(totalPlaytimeReset.get()) + "&7 of playtime)"));
                });
            });
        });
    }

    private String getResetTypeDescription(String resetType) {
        switch (resetType) {
            case "db":
                return "database records";
            case "stats":
                return "in-game statistics";
            case "all":
            default:
                return "database records and in-game statistics";
        }
    }

    // Method to clean up expired confirmations (call this periodically from your plugin)
    public static void cleanupExpiredConfirmations() {
        pendingResets.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}