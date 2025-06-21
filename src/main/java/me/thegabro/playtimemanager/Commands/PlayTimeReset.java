package me.thegabro.playtimemanager.Commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.CommandTree;
import dev.jorel.commandapi.arguments.*;
import me.thegabro.playtimemanager.Commands.PlayTimeCommandManager.PlaytimeCommand;
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
import java.util.stream.Collectors;

public class PlayTimeReset {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();

    // Map to store pending confirmations with a timestamp
    private static final Map<UUID, PendingReset> pendingResets = new HashMap<>();
    // Timeout for confirmation (60 seconds)
    private static final long CONFIRMATION_TIMEOUT_SECONDS = 60;
    private final JoinStreaksManager joinStreaksManager = JoinStreaksManager.getInstance();

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

    public Argument<String> customPlayerArgument(String nodeName) {
        return new CustomArgument<>(new StringArgument(nodeName), info -> {
            String input = info.input();

            if (input.equals("+")) {
                return input;
            }

            if (dbUsersManager.getUserFromNickname(input) == null) {
                throw CustomArgument.CustomArgumentException.fromMessageBuilder(
                        new CustomArgument.MessageBuilder("Player has never joined: ").appendArgInput()
                );
            }

            return input;
        }).replaceSuggestions(ArgumentSuggestions.strings(info -> {
            List<String> suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());

            if (info.sender().hasPermission("playtime.others.modify.all")) {
                suggestions.add("+");
            }

            return suggestions.toArray(new String[0]);
        }));
    }

    public void registerCommands() {
        new CommandTree("playtimereset")
                .withPermission(CommandPermission.fromString("playtime.others.modify"))
                .then(customPlayerArgument("target")
                        .then(new LiteralArgument("stats")
                                .executes((sender, args) -> {
                                    try {
                                        String target = (String) args.get("target");
                                        handleResetCommand(sender, target, "stats");
                                    } catch (Exception e) {
                                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                                " &cAn error occurred while executing the command."));
                                        plugin.getLogger().severe("Error executing playtime reset stats command: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                })
                        )
                        .then(new LiteralArgument("db")
                                .executes((sender, args) -> {
                                    try {
                                        String target = (String) args.get("target");
                                        handleResetCommand(sender, target, "db");
                                    } catch (Exception e) {
                                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                                " &cAn error occurred while executing the command."));
                                        plugin.getLogger().severe("Error executing playtime reset db command: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                })
                        )
                        .then(new LiteralArgument("joinstreak")
                                .executes((sender, args) -> {
                                    try {
                                        String target = (String) args.get("target");
                                        handleResetCommand(sender, target, "joinstreak");
                                    } catch (Exception e) {
                                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                                " &cAn error occurred while executing the command."));
                                        plugin.getLogger().severe("Error executing playtime reset joinstreak command: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                })
                        )
                        .then(new LiteralArgument("all")
                                .executes((sender, args) -> {
                                    try {
                                        String target = (String) args.get("target");
                                        handleResetCommand(sender, target, "all");
                                    } catch (Exception e) {
                                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                                " &cAn error occurred while executing the command."));
                                        plugin.getLogger().severe("Error executing playtime reset all command: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                })
                        )
                ).register();
    }

    private void handleResetCommand(CommandSender sender, String target, String resetType) {
        // Check if targeting all players ("+")
        if (target.equals("+")) {
            // Check permission for all players reset
            if (!sender.hasPermission("playtime.others.modify.all")) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " &cYou don't have permission to reset all players' data!"));
                return;
            }
            handleResetAllConfirmation(sender, resetType);
        } else {
            // Single player reset
            DBUser user = dbUsersManager.getUserFromNickname(target);
            if (user == null) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " The player &e" + target + "&7 has never joined the server!"));
                return;
            }

            switch (resetType) {
                case "stats":
                    resetPlayerStats(sender, target);
                    break;
                case "db":
                    resetPlayerDatabase(sender, target);
                    break;
                case "joinstreak":
                    resetPlayerJoinstreak(sender, target);
                    break;
                case "all":
                    // Reset both stats and database for single player
                    resetPlayerStats(sender, target);
                    resetPlayerDatabase(sender, target);
                    resetPlayerJoinstreak(sender, target);
                    break;
                default:
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                            " Unknown reset type: &e" + resetType + "&7. Valid types: stats, db, joinstreak, all"));
                    break;
            }
        }
    }

    public void resetPlayerStats(CommandSender sender, String playerName) {
        DBUser user = dbUsersManager.getUserFromNickname(playerName);

        if (user == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                    " The player &e" + playerName + "&7 has never joined the server!"));
            return;
        }

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
                        plugin.getLogger().warning("Failed to reset statistics for offline player: " + playerName);
                    }
                }
            }

            final long finalResetPlaytime = resetPlaytime;

            // Notify on main thread
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                    " Reset in-game statistics for player &e" + playerName +
                    "&7 (Removed &e" + Utils.ticksToFormattedPlaytime(finalResetPlaytime) + "&7 of playtime)"));
        });
    }

    public void resetAllPlayerStats(CommandSender sender) {
        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                " Starting reset of all players' in-game statistics, this will take some time..."));

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
                            plugin.getLogger().warning("Failed to reset statistics for offline player: " +
                                    offlinePlayer.getName());
                        }
                    }
                }

                // Final notification
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " All players' in-game statistics have been reset! Total: &e" + totalPlayersReset +
                        "&7 players with &e" + Utils.ticksToFormattedPlaytime(totalPlaytimeReset.get()) + "&7 of playtime"));
            });
        });
    }

    public void resetPlayerDatabase(CommandSender sender, String playerName) {
        DBUser user = dbUsersManager.getUserFromNickname(playerName);

        if (user == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                    " The player &e" + playerName + "&7 has never joined the server!"));
            return;
        }

        long playtimeBeforeReset = user.getPlaytime();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Reset database record
            user.reset();

            // Update top players list
            dbUsersManager.updateTopPlayersFromDB();

            // Notify on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " Reset database records for player &e" + playerName +
                        "&7 (Removed &e" + Utils.ticksToFormattedPlaytime(playtimeBeforeReset) + "&7 of playtime)"));
            });
        });
    }

    public void resetAllPlayerDatabase(CommandSender sender) {
        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                " Starting reset of all players' database records, this will take some time..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<DBUser> users = dbUsersManager.getAllDBUsers();
            AtomicInteger totalPlayersReset = new AtomicInteger();
            AtomicLong totalPlaytimeReset = new AtomicLong();

            // Process all users
            for (DBUser u : users) {
                totalPlaytimeReset.addAndGet(u.getPlaytime());
                u.reset(); // Make sure this method is thread-safe!
                totalPlayersReset.getAndIncrement();
            }

            // Clear and update top players list
            dbUsersManager.clearCache();
            dbUsersManager.updateTopPlayersFromDB();

            // Final notification on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " All players' database records have been reset! Total: &e" + totalPlayersReset +
                        "&7 players with &e" + Utils.ticksToFormattedPlaytime(totalPlaytimeReset.get()) + "&7 of playtime"));
            });
        });
    }

    public void resetPlayerJoinstreak(CommandSender sender, String playerName) {
        DBUser user = dbUsersManager.getUserFromNickname(playerName);

        if (user == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                    " The player &e" + playerName + "&7 has never joined the server!"));
            return;
        }

        int joinStreakBeforeReset = user.getRelativeJoinStreak();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Reset database records
            user.resetJoinStreaks();
            joinStreaksManager.getStreakTracker().restartUserJoinStreakRewards(user);

            // Notify on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " Reset join streak for player &e" + playerName +
                        "&7 (Removed &e" + joinStreakBeforeReset + "&7 joins)"));
            });
        });
    }

    public void resetAllPlayerJoinstreak(CommandSender sender) {
        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                " Starting reset of all players' join streaks, this will take some time..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<DBUser> users = dbUsersManager.getAllDBUsers();
            AtomicInteger totalPlayersReset = new AtomicInteger();
            AtomicLong totalJoinStreakReset = new AtomicLong();

            // Process all users
            for (DBUser u : users) {
                totalJoinStreakReset.addAndGet(u.getRelativeJoinStreak());
                u.resetJoinStreaks();
                joinStreaksManager.getStreakTracker().restartUserJoinStreakRewards(u);
                totalPlayersReset.getAndIncrement();
            }

            // Clear and update top players list
            dbUsersManager.clearCache();
            dbUsersManager.updateTopPlayersFromDB();

            // Final notification on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " All players' join streaks have been reset! Total: &e" + totalPlayersReset +
                        "&7 players with &e" + totalJoinStreakReset + "&7 of joins"));
            });
        });
    }

    public void handleResetAllConfirmation(CommandSender sender, String resetType) {
        UUID senderUUID = sender instanceof Player
                ? ((Player) sender).getUniqueId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");

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
            switch (resetType) {
                case "stats":
                    resetAllPlayerStats(sender);
                    break;
                case "db":
                    resetAllPlayerDatabase(sender);
                    break;
                case "joinstreak":
                    resetAllPlayerJoinstreak(sender);
                    break;
                case "all":
                    // Reset all three: stats, database AND joinstreak
                    resetAllPlayerStats(sender);
                    resetAllPlayerDatabase(sender);
                    resetAllPlayerJoinstreak(sender);
                    break;
                default:
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                            " Unknown reset type: &e" + resetType + "&7. Valid types: stats, db, joinstreak, all"));
                    break;
            }
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

    private String getResetTypeDescription(String resetType) {
        switch (resetType) {
            case "db":
                return "database records";
            case "stats":
                return "in-game statistics";
            case "joinstreak":
                return "join streaks data";
            case "all":
                return "database records, in-game statistics, and join streaks";
            default:
                return "unknown data";
        }
    }

    public static void cleanupExpiredConfirmations() {
        pendingResets.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}