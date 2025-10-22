package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.GUIs.Player.PlayerStatsGui;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class PlayTimeStats implements CommandExecutor {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private static final Map<UUID, Long> lastGuiOpenTime = new HashMap<>();
    private static final long GUI_OPEN_COOLDOWN = 1000;
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("playtime.stats")) {
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + " " +
                    GUIsConfiguration.getInstance().getString("player-stats-gui.messages.no-permission")));
            return false;
        }

        String targetPlayerName;

        if (args.length == 0) {
            if (sender instanceof Player) {
                targetPlayerName = sender.getName();
            } else {
                sender.sendMessage("§cOnly players can use this command.");
                return false;
            }
        } else {
            if (!sender.hasPermission("playtime.others.stats")) {
                sender.sendMessage(Utils.parseColors(config.getString("prefix") + " " +
                        GUIsConfiguration.getInstance().getString("player-stats-gui.messages.no-permission-others")));
                return false;
            }
            targetPlayerName = args[0];
        }

        dbUsersManager.getUserFromNicknameAsyncWithContext(targetPlayerName, "ptstats command", user -> {
            if (user == null) {
                sender.sendMessage(Utils.parseColors(config.getString("prefix") + " " +
                        GUIsConfiguration.getInstance().getString("player-stats-gui.messages.player-not-found")));
                return;
            }

            if (sender instanceof ConsoleCommandSender) {
                sendTextStats(sender, user);
            } else if (sender instanceof Player player) {
                // Schedule GUI opening on main thread
                Bukkit.getScheduler().runTask(plugin, () -> openStatsGui(player, user));
            } else {
                sendTextStats(sender, user);
            }
        });

        return true;
    }

    /**
     * Common method to display stats with consistent formatting
     */
    private void sendTextStats(CommandSender sender, DBUser user) {
        long playtimeSnapshot;
        if (user.isOnline()) {
            OnlineUser onlineUser = (OnlineUser) user;
            playtimeSnapshot = onlineUser.getPlayerInstance().getStatistic(Statistic.PLAY_ONE_MINUTE);
        }else{
            // For offline users, snapshot is not needed (methods ignore it)
            // So let's use 0 as placeholder since it's ignored in DBUser base implementation
            playtimeSnapshot = 0L;
        }

        Instant lastSeen = user.getLastSeen();
        Instant firstJoin = user.getFirstJoin();
        long totalPlaytime = user.getPlaytimeWithSnapshot(playtimeSnapshot);
        long afkPlaytime = user.getAFKPlaytimeWithSnapshot(playtimeSnapshot);
        long artificialPlaytime = user.getArtificialPlaytime();
        int relativeJoinStreak = user.getRelativeJoinStreak();
        int absoluteJoinStreak = user.getAbsoluteJoinStreak();

        long realPlaytime;
        if (plugin.getConfiguration().getBoolean("ignore-afk-time"))
            realPlaytime = totalPlaytime - artificialPlaytime + afkPlaytime;
        else
            realPlaytime = totalPlaytime - artificialPlaytime;

        sender.sendMessage(Utils.parseColors("&8&l===============[ &6&lPlayer Stats &8&l]==============="));
        sender.sendMessage(Utils.parseColors("&7Player: &e" + user.getNickname()));

        sender.sendMessage(Utils.parseColors("\n&6&lPlaytime Information:"));
        sender.sendMessage(Utils.parseColors("&7Total Playtime: &e" + Utils.ticksToFormattedPlaytime(totalPlaytime)));
        sender.sendMessage(Utils.parseColors("&7- Real Playtime: &e" + Utils.ticksToFormattedPlaytime(realPlaytime)));
        sender.sendMessage(Utils.parseColors("&7- Artificial Playtime: &e" + Utils.ticksToFormattedPlaytime(artificialPlaytime)));
        sender.sendMessage(Utils.parseColors("&7- AFK Playtime: &e" + Utils.ticksToFormattedPlaytime(afkPlaytime)));

        if (firstJoin != null) {
            sender.sendMessage(Utils.parseColors("\n&6&lFirst Join:"));
            sender.sendMessage(Utils.parseColors("&7Date: &e" + Utils.formatInstant(firstJoin, plugin.getConfiguration().getString("datetime-format"))));
            Duration accountAge = Duration.between(firstJoin, Instant.now());
            sender.sendMessage(Utils.parseColors("&7Account Age: &e" + Utils.ticksToFormattedPlaytime(accountAge.getSeconds() * 20)));
        }

        if (lastSeen != null) {
            sender.sendMessage(Utils.parseColors("\n&6&lLast Seen:"));
            sender.sendMessage(Utils.parseColors("&7Date: &e" + Utils.formatInstant(lastSeen, plugin.getConfiguration().getString("datetime-format"))));
            Duration timeSinceLastSeen = Duration.between(lastSeen, Instant.now());
            sender.sendMessage(Utils.parseColors("&7Time Elapsed: &e" + Utils.ticksToFormattedPlaytime(timeSinceLastSeen.getSeconds() * 20)));
        }

        sender.sendMessage(Utils.parseColors("\n&6&lJoin Streak:"));
        sender.sendMessage(Utils.parseColors("&7Streak for the current cycle: &e" + relativeJoinStreak));
        sender.sendMessage(Utils.parseColors("&7Total Streak: &e" + absoluteJoinStreak));

        sender.sendMessage(Utils.parseColors("\n&6&lCompleted Goals:"));
        if (user.getCompletedGoals().isEmpty()) {
            sender.sendMessage(Utils.parseColors("&7No goals completed yet"));
        } else {

            ArrayList<String> completedGoals = user.getCompletedGoals();
            Map<String, Integer> goalsCount = new LinkedHashMap<>();
            for (String name : completedGoals) {
                goalsCount.put(name, goalsCount.getOrDefault(name, 0) + 1);
            }

            for (Map.Entry<String, Integer> entry : goalsCount.entrySet()) {
                sender.sendMessage(Utils.parseColors("&7- &e" + entry.getKey() + "&7(&e" + entry.getValue() + "&7)"));
            }
        }

        sender.sendMessage(Utils.parseColors("&8&l============================================"));
    }

    private void openStatsGui(Player player, DBUser user) {
        // Check for rapid GUI opening (potential exploit)
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        if (lastGuiOpenTime.containsKey(playerId)) {
            long lastTime = lastGuiOpenTime.get(playerId);
            if (currentTime - lastTime < GUI_OPEN_COOLDOWN) {
                player.sendMessage(Utils.parseColors(config.getString("prefix") + " "+
                        GUIsConfiguration.getInstance().getString("player-stats-gui.messages.command-spam")));
                return;
            }
        }
        lastGuiOpenTime.put(playerId, currentTime);

        // Create a session token for this GUI interaction
        String sessionToken = java.util.UUID.randomUUID().toString();
        plugin.getSessionManager().createSession(player.getUniqueId(), sessionToken);

        // Create and open the GUI with session validation
        PlayerStatsGui gui = new PlayerStatsGui(player, user, sessionToken);
        gui.openInventory();
    }
}