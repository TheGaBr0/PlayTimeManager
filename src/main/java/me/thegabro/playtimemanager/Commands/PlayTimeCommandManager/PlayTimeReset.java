package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PlayTimeReset {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final JoinStreaksManager joinStreaksManager = JoinStreaksManager.getInstance();

    // Map to store pending confirmations with a timestamp
    private static final Map<UUID, PendingReset> pendingResets = new HashMap<>();
    // Timeout for confirmation (60 seconds)
    private static final long CONFIRMATION_TIMEOUT_SECONDS = 60;

    /**
     * Class to store pending reset information
     */
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

    /**
     * Main constructor that handles the reset command parsing and execution
     */
    public PlayTimeReset(CommandSender sender, String[] args) {
        String targetPlayer = args[0];
        String resetType = "everything"; // default reset type

        // Check if reset type is specified
        if (args.length > 2) {
            resetType = args[2];
        }

        // Handle wildcard reset
        if (targetPlayer.equals("*")) {
            handleResetAllConfirmation(sender, resetType);
            return;
        }

        // Single player reset
        executeResetForPlayer(sender, targetPlayer, resetType);
    }

    /**
     * Execute reset for a single player based on reset type
     */
    private void executeResetForPlayer(CommandSender sender, String playerName, String resetType) {
        switch (resetType) {
            case "server_playtime":
                resetPlayerServerPlaytime(sender, playerName);
                dbUsersManager.updateTopPlayersFromDB();
                break;
            case "playtime":
                resetPlayerPlaytime(sender, playerName);
                dbUsersManager.updateTopPlayersFromDB();
                break;
            case "last_seen":
                resetPlayerLastSeen(sender, playerName);
                break;
            case "first_join":
                resetPlayerFirstJoin(sender, playerName);
                break;
            case "joinstreak":
                resetPlayerJoinstreak(sender, playerName);
                break;
            case "joinstreak_rewards":
                resetPlayerJoinstreakRewards(sender, playerName);
                break;
            case "goals":
                resetPlayerGoals(sender, playerName);
                break;
            case "everything":
                resetPlayerEverything(sender, playerName);
                dbUsersManager.updateTopPlayersFromDB();
                break;
            default:
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Unknown reset type: &e" + resetType + "&7. Valid types: server_playtime, playtime, last_seen, first_join, joinstreak, joinstreak_rewards, goals, everything"));
                break;
        }
    }

    /**
     * Reset server playtime statistics for a player
     */
    public void resetPlayerServerPlaytime(CommandSender sender, String playerName) {
        DBUser user = getValidatedUser(sender, playerName);
        if (user == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            long resetPlaytime = 0;

            // Reset for online player
            if (user instanceof OnlineUser) {
                Player p = Bukkit.getPlayerExact(playerName);
                if (p != null) {
                    resetPlaytime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
                    p.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
                    ((OnlineUser) user).refreshFromServerOnJoinPlayTime();
                }
            }
            // Reset for offline player
            else {
                OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
                if (p.hasPlayedBefore()) {
                    try {
                        resetPlaytime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
                        p.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to reset server playtime for offline player: " + playerName);
                    }
                }
            }

            final long finalResetPlaytime = resetPlaytime;
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                    " Reset server playtime for player &e" + playerName +
                    "&7 (Removed &e" + Utils.ticksToFormattedPlaytime(finalResetPlaytime) + "&7 of playtime)"));
        });
    }

    /**
     * Reset database playtime (both regular and artificial) for a player
     */
    public void resetPlayerPlaytime(CommandSender sender, String playerName) {
        DBUser user = getValidatedUser(sender, playerName);
        if (user == null) return;

        long playtimeBeforeReset = user.getPlaytime();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            user.resetPlaytime();

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Reset playtime for player &e" + playerName +
                        "&7 (Removed &e" + Utils.ticksToFormattedPlaytime(playtimeBeforeReset) + "&7 of playtime)"));
            });
        });
    }

    /**
     * Reset last seen data for a player
     */
    public void resetPlayerLastSeen(CommandSender sender, String playerName) {
        DBUser user = getValidatedUser(sender, playerName);
        if (user == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            user.resetLastSeen();

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Reset last seen data for player &e" + playerName + "&7"));
            });
        });
    }

    /**
     * Reset first join data for a player
     */
    public void resetPlayerFirstJoin(CommandSender sender, String playerName) {
        DBUser user = getValidatedUser(sender, playerName);
        if (user == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            user.resetFirstJoin();

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Reset first join data for player &e" + playerName + "&7"));
            });
        });
    }

    /**
     * Reset join streak data for a player
     */
    public void resetPlayerJoinstreak(CommandSender sender, String playerName) {
        DBUser user = getValidatedUser(sender, playerName);
        if (user == null) return;

        int joinStreakBeforeReset = user.getRelativeJoinStreak();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            user.resetJoinStreaks();
            joinStreaksManager.getStreakTracker().restartUserJoinStreakRewards(user);

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Reset join streak for player &e" + playerName +
                        "&7 (Removed &e" + joinStreakBeforeReset + "&7 joins)"));
            });
        });
    }

    /**
     * Reset join streak rewards for a player
     */
    public void resetPlayerJoinstreakRewards(CommandSender sender, String playerName) {
        DBUser user = getValidatedUser(sender, playerName);
        if (user == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            user.resetJoinStreakRewards();
            joinStreaksManager.getStreakTracker().restartUserJoinStreakRewards(user);

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Reset join streak rewards for player &e" + playerName + "&7"));
            });
        });
    }

    /**
     * Reset goals data for a player
     */
    public void resetPlayerGoals(CommandSender sender, String playerName) {
        DBUser user = getValidatedUser(sender, playerName);
        if (user == null) return;

        int completedGoalsCount = user.getCompletedGoals().size();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            user.resetGoals();

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Reset goals for player &e" + playerName +
                        "&7 (Removed &e" + completedGoalsCount + "&7 completed goals)"));
            });
        });
    }

    /**
     * Reset everything for a player (equivalent to old "all" reset)
     */
    public void resetPlayerEverything(CommandSender sender, String playerName) {
        DBUser user = getValidatedUser(sender, playerName);
        if (user == null) return;

        long playtimeBeforeReset = user.getPlaytime();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            user.reset();

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Reset everything for player &e" + playerName +
                        "&7 (Removed &e" + Utils.ticksToFormattedPlaytime(playtimeBeforeReset) + "&7 of playtime)"));
            });
        });
    }

    /**
     * Reset server playtime statistics for all players
     */
    public void resetAllPlayerServerPlaytime(CommandSender sender) {
        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                " Starting reset of all players' server playtime, this will take some time..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AtomicInteger totalPlayersReset = new AtomicInteger();
            AtomicLong totalPlaytimeReset = new AtomicLong();
            Set<UUID> processedPlayerUUIDs = new HashSet<>();

            Bukkit.getScheduler().runTask(plugin, () -> {
                // Reset online players' stats
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

                // Reset offline players' stats
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
                            plugin.getLogger().warning("Failed to reset server playtime for offline player: " +
                                    offlinePlayer.getName());
                        }
                    }
                }

                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " All players' server playtime has been reset! Total: &e" + totalPlayersReset +
                        "&7 players with &e" + Utils.ticksToFormattedPlaytime(totalPlaytimeReset.get()) + "&7 of playtime"));
            });
        });
    }

    /**
     * Generic method to reset specific data for all players
     */
    private void resetAllPlayersGeneric(CommandSender sender, String resetType, String displayName) {
        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                " Starting reset of all players' " + displayName + ", this will take some time..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<DBUser> users = dbUsersManager.getAllDBUsers();
            AtomicInteger totalPlayersReset = new AtomicInteger();
            AtomicLong totalDataReset = new AtomicLong();

            for (DBUser user : users) {
                switch (resetType) {
                    case "playtime":
                        totalDataReset.addAndGet(user.getPlaytime());
                        user.resetPlaytime();
                        dbUsersManager.updateTopPlayersFromDB();
                        break;
                    case "last_seen":
                        user.resetLastSeen();
                        dbUsersManager.updateTopPlayersFromDB();
                        break;
                    case "first_join":
                        user.resetFirstJoin();
                        break;
                    case "joinstreak":
                        totalDataReset.addAndGet(user.getRelativeJoinStreak());
                        user.resetJoinStreaks();
                        joinStreaksManager.getStreakTracker().restartUserJoinStreakRewards(user);
                        break;
                    case "joinstreak_rewards":
                        user.resetJoinStreakRewards();
                        joinStreaksManager.getStreakTracker().restartUserJoinStreakRewards(user);
                        break;
                    case "goals":
                        totalDataReset.addAndGet(user.getCompletedGoals().size());
                        user.resetGoals();
                        break;
                    case "everything":
                        totalDataReset.addAndGet(user.getPlaytime());
                        user.reset();
                        dbUsersManager.updateTopPlayersFromDB();
                        break;
                }
                totalPlayersReset.getAndIncrement();
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                String message = " All players' " + displayName + " has been reset! Total: &e" + totalPlayersReset + "&7 players";

                if (resetType.equals("playtime") || resetType.equals("everything")) {
                    message += " with &e" + Utils.ticksToFormattedPlaytime(totalDataReset.get()) + "&7 of playtime";
                } else if (resetType.equals("joinstreak")) {
                    message += " with &e" + totalDataReset + "&7 joins";
                } else if (resetType.equals("goals")) {
                    message += " with &e" + totalDataReset + "&7 completed goals";
                }

                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + message));
            });
        });
    }

    /**
     * Handle confirmation for reset all operations
     */
    public void handleResetAllConfirmation(CommandSender sender, String resetType) {
        UUID senderUUID = sender instanceof Player
                ? ((Player) sender).getUniqueId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");

        if (pendingResets.containsKey(senderUUID)) {
            PendingReset pendingReset = pendingResets.get(senderUUID);

            if (pendingReset.isExpired()) {
                pendingResets.remove(senderUUID);
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Your previous reset confirmation has expired. Please try again."));
                requestConfirmation(sender, senderUUID, resetType);
                return;
            }

            if (!pendingReset.resetType.equals(resetType)) {
                pendingResets.remove(senderUUID);
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Reset type has changed. Please confirm again."));
                requestConfirmation(sender, senderUUID, resetType);
                return;
            }

            pendingResets.remove(senderUUID);
            executeResetAllForType(sender, resetType);
        } else {
            requestConfirmation(sender, senderUUID, resetType);
        }
    }

    /**
     * Execute reset all for the specified type
     */
    private void executeResetAllForType(CommandSender sender, String resetType) {
        switch (resetType) {
            case "server_playtime":
                resetAllPlayerServerPlaytime(sender);
                break;
            case "playtime":
                resetAllPlayersGeneric(sender, "playtime", "playtime");
                break;
            case "last_seen":
                resetAllPlayersGeneric(sender, "last_seen", "last seen data");
                break;
            case "first_join":
                resetAllPlayersGeneric(sender, "first_join", "first join data");
                break;
            case "joinstreak":
                resetAllPlayersGeneric(sender, "joinstreak", "join streaks");
                break;
            case "joinstreak_rewards":
                resetAllPlayersGeneric(sender, "joinstreak_rewards", "join streak rewards");
                break;
            case "goals":
                resetAllPlayersGeneric(sender, "goals", "goals");
                break;
            case "everything":
                resetAllPlayersGeneric(sender, "everything", "data");
                break;
            default:
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Unknown reset type: &e" + resetType + "&7. Valid types: server_playtime, playtime, last_seen, first_join, joinstreak, joinstreak_rewards, goals, everything"));
                break;
        }
    }

    /**
     * Request confirmation for reset all operations
     */
    private void requestConfirmation(CommandSender sender, UUID senderUUID, String resetType) {
        pendingResets.put(senderUUID, new PendingReset(resetType));

        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                " &c&lWARNING&r&7: You are about to reset " + getResetTypeDescription(resetType) +
                " for &e&lALL players&7!"));
        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                " &7This action cannot be undone. Run the command again within " +
                CONFIRMATION_TIMEOUT_SECONDS + " seconds to confirm."));
    }

    /**
     * Get human-readable description for reset type
     */
    private String getResetTypeDescription(String resetType) {
        switch (resetType) {
            case "server_playtime":
                return "server playtime statistics";
            case "playtime":
                return "playtime data";
            case "last_seen":
                return "last seen data";
            case "first_join":
                return "first join data";
            case "joinstreak":
                return "join streaks";
            case "joinstreak_rewards":
                return "join streak rewards";
            case "goals":
                return "goals data";
            case "everything":
                return "all data";
            default:
                return "unknown data";
        }
    }

    /**
     * Validate user exists and return user object
     */
    private DBUser getValidatedUser(CommandSender sender, String playerName) {
        DBUser user = dbUsersManager.getUserFromNickname(playerName);
        if (user == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                    " The player &e" + playerName + "&7 has never joined the server!"));
        }
        return user;
    }

    /**
     * Clean up expired confirmations
     */
    public static void cleanupExpiredConfirmations() {
        pendingResets.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}