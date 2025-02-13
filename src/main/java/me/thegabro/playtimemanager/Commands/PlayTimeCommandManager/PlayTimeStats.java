package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PlayTimeStats {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();

    public PlayTimeStats(CommandSender sender, String[] args) {

        DBUser user = dbUsersManager.getUserFromNickname(args[0]);

        if (user == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Player not found!"));
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getDateTimeFormat());

        // Get all necessary data
        LocalDateTime lastSeen = user.getLastSeen();
        LocalDateTime firstJoin = user.getFirstJoin();
        long totalPlaytime = user.getPlaytime();
        long artificialPlaytime = user.getArtificialPlaytime();

        // Header
        sender.sendMessage(Utils.parseColors("§8§l===============[ §6§lPlayer Stats §8§l]==============="));
        sender.sendMessage(Utils.parseColors("§7Player: §e" + user.getNickname()));

        // Playtime Information
        sender.sendMessage(Utils.parseColors("\n§6§lPlaytime Information:"));
        sender.sendMessage(Utils.parseColors("§7Total Playtime: §e" + Utils.ticksToFormattedPlaytime(totalPlaytime)));
        if (artificialPlaytime > 0) {
            sender.sendMessage(Utils.parseColors("§7- Playtime: §e" + Utils.ticksToFormattedPlaytime(totalPlaytime - artificialPlaytime)));
            sender.sendMessage(Utils.parseColors("§7- Artificial Playtime: §e" + Utils.ticksToFormattedPlaytime(artificialPlaytime)));
        }

        // First Join Information
        if (firstJoin != null) {
            sender.sendMessage(Utils.parseColors("\n§6§lFirst Join:"));
            sender.sendMessage(Utils.parseColors("§7Date: §e" + firstJoin.format(formatter)));
            Duration accountAge = Duration.between(firstJoin, LocalDateTime.now());
            sender.sendMessage(Utils.parseColors("§7Account Age: §e" + Utils.ticksToFormattedPlaytime(accountAge.getSeconds() * 20)));
        }

        // Last Seen Information
        if (lastSeen != null && !lastSeen.equals(LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0))) {
            sender.sendMessage(Utils.parseColors("\n§6§lLast Seen:"));
            sender.sendMessage(Utils.parseColors("§7Date: §e" + lastSeen.format(formatter)));
            Duration timeSinceLastSeen = Duration.between(lastSeen, LocalDateTime.now());
            sender.sendMessage(Utils.parseColors("§7Time Elapsed: §e" + Utils.ticksToFormattedPlaytime(timeSinceLastSeen.getSeconds() * 20)));
        }

        // Goals Information
        sender.sendMessage(Utils.parseColors("\n§6§lCompleted Goals:"));
        if (user.getCompletedGoals().isEmpty()) {
            sender.sendMessage(Utils.parseColors("§7No goals completed yet"));
        } else {
            for (String goal : user.getCompletedGoals()) {
                sender.sendMessage(Utils.parseColors("§7- §e" + goal));
            }
        }

        // Footer
        sender.sendMessage(Utils.parseColors("§8§l============================================"));
    }
}