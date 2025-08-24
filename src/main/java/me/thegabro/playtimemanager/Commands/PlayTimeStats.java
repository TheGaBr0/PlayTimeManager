package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.GUIs.Player.PlayerStatsGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayTimeStats implements CommandExecutor {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private static final Map<UUID, Long> lastGuiOpenTime = new HashMap<>();
    private static final long GUI_OPEN_COOLDOWN = 1000;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Check permission
        if (!sender.hasPermission("playtime.stats")) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " You don't have permission to use this command!"));
            return true;
        }

        String targetPlayerName;

        // If no argument is provided, use the sender's name (if sender is a player)
        if (args.length == 0) {
            if (sender instanceof Player) {
                targetPlayerName = sender.getName();
            } else {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " You must specify a player name!"));
                return true;
            }
        } else {
            targetPlayerName = args[0];
        }

        // Get user from database
        DBUser user = dbUsersManager.getUserFromNickname(targetPlayerName);

        if (user == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " &cPlayer not found!"));
            return true;
        }

        // Check if sender is console or player
        if (sender instanceof ConsoleCommandSender) {
            // Send text-based stats to console
            sendTextStats(sender, user);
        } else if (sender instanceof Player player) {
            // Open GUI for player
            openStatsGui(player, user);
        } else {
            // Fallback to text stats for other command sender types
            sendTextStats(sender, user);
        }

        return true;
    }

    private void sendTextStats(CommandSender sender, DBUser user) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getString("datetime-format"));

        // Get all necessary data
        LocalDateTime lastSeen = user.getLastSeen();
        LocalDateTime firstJoin = user.getFirstJoin();
        long totalPlaytime = user.getPlaytime();
        long artificialPlaytime = user.getArtificialPlaytime();
        long afkPlaytime = user.getAFKPlaytime();
        int relativeJoinStreak = user.getRelativeJoinStreak();
        int absoluteJoinStreak = user.getAbsoluteJoinStreak();

        // Calculate real playtime
        long realPlaytime;
        if (plugin.getConfiguration().getBoolean("ignore-afk-time"))
            realPlaytime = totalPlaytime - artificialPlaytime - afkPlaytime;
        else
            realPlaytime = totalPlaytime - artificialPlaytime;

        // Header
        sender.sendMessage(Utils.parseColors("&8&l===============[ &6&lPlayer Stats &8&l]==============="));
        sender.sendMessage(Utils.parseColors("&7Player: &e" + user.getNickname()));

        // Playtime Information
        sender.sendMessage(Utils.parseColors("\n&6&lPlaytime Information:"));
        sender.sendMessage(Utils.parseColors("&7Total Playtime: &e" + Utils.ticksToFormattedPlaytime(totalPlaytime)));
        sender.sendMessage(Utils.parseColors("&7- Real Playtime: &e" + Utils.ticksToFormattedPlaytime(realPlaytime)));
        sender.sendMessage(Utils.parseColors("&7- Artificial Playtime: &e" + Utils.ticksToFormattedPlaytime(artificialPlaytime)));
        sender.sendMessage(Utils.parseColors("&7- AFK Playtime: &e" + Utils.ticksToFormattedPlaytime(afkPlaytime)));


        // First Join Information
        if (firstJoin != null) {
            sender.sendMessage(Utils.parseColors("\n&6&lFirst Join:"));
            sender.sendMessage(Utils.parseColors("&7Date: &e" + firstJoin.format(formatter)));
            Duration accountAge = Duration.between(firstJoin, LocalDateTime.now());
            sender.sendMessage(Utils.parseColors("&7Account Age: &e" + Utils.ticksToFormattedPlaytime(accountAge.getSeconds() * 20)));
        }

        // Last Seen Information
        if (lastSeen != null && !lastSeen.equals(LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0))) {
            sender.sendMessage(Utils.parseColors("\n&6&lLast Seen:"));
            sender.sendMessage(Utils.parseColors("&7Date: &e" + lastSeen.format(formatter)));
            Duration timeSinceLastSeen = Duration.between(lastSeen, LocalDateTime.now());
            sender.sendMessage(Utils.parseColors("&7Time Elapsed: &e" + Utils.ticksToFormattedPlaytime(timeSinceLastSeen.getSeconds() * 20)));
        }

        // Join Streak Information
        sender.sendMessage(Utils.parseColors("\n&6&lJoin Streak:"));
        sender.sendMessage(Utils.parseColors("&7Streak for the current cycle: &e" + relativeJoinStreak));
        sender.sendMessage(Utils.parseColors("&7Total Streak: &e" + absoluteJoinStreak));

        // Goals Information
        sender.sendMessage(Utils.parseColors("\n&6&lCompleted Goals:"));
        if (user.getCompletedGoals().isEmpty()) {
            sender.sendMessage(Utils.parseColors("&7No goals completed yet"));
        } else {
            for (String goal : user.getCompletedGoals()) {
                sender.sendMessage(Utils.parseColors("&7- &e" + goal));
            }
        }

        // Footer
        sender.sendMessage(Utils.parseColors("&8&l============================================"));
    }

    private void openStatsGui(Player player, DBUser user) {
        // Check for rapid GUI opening (potential exploit)
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        if (lastGuiOpenTime.containsKey(playerId)) {
            long lastTime = lastGuiOpenTime.get(playerId);
            if (currentTime - lastTime < GUI_OPEN_COOLDOWN) {
                player.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " &cPlease wait before using this command again."));
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