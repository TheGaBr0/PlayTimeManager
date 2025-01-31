package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class PlayTimeOffline {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public PlayTimeOffline(CommandSender sender, String[] args) {
        DBUser user = plugin.getDbUsersManager().getUserFromNickname(args[0]);

        if (user == null) {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 Player not found!");
            return;
        }

        LocalDateTime lastSeen = user.getLastSeen();

        if (lastSeen == null || lastSeen.equals(LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0))) {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 No last seen data available for this player!");
            return;
        }

        // Format the last seen date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String formattedDate = lastSeen.format(formatter);

        Duration duration = Duration.between(lastSeen, LocalDateTime.now());
        long seconds = duration.getSeconds(); // Total seconds

        // Send messages
        sender.sendMessage("[§6PlayTime§eManager§f]§7 Player §e" + user.getNickname() + "§7 was last seen on: \n §e" + formattedDate +
                "§7, time elapsed: §e" + Utils.ticksToFormattedPlaytime(seconds * 20));
    }
}