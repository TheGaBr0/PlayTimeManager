package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Database.DatabaseBackupUtility;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PlayTimeBackup implements CommandExecutor {
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DatabaseBackupUtility backupUtility = DatabaseBackupUtility.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (sender.hasPermission("playtime.backup")){

            File success = backupUtility.createBackup(generateReadmeContent());
            if (success != null) {
                sender.sendMessage(Utils.parseColors(config.getString("prefix") +
                        " &7Database backup created successfully!"));
            } else {
                sender.sendMessage(Utils.parseColors(config.getString("prefix") +
                        " &7Failed to create database backup. Check console for details."));
            }
            return true;
        } else {
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("no-permission")));
        }
        return false;
    }

    private String generateReadmeContent() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());

        String readme = "PlayTimeManager Database Backup\n" +
                "==============================\n\n" +
                "Backup created on: " + timestamp + "\n\n" +
                "Plugin information:\n" +
                "- Plugin name: PlayTimeManager\n" +
                "- Version: " + plugin.getDescription().getVersion() + "\n" +
                "- Author: TheGabro\n\n" +
                "Database information:\n" +
                "- Database name: play_time\n" +
                "- Backup reason: Manual backup triggered by command\n" +
                "Restore instructions:\n" +
                "1. Stop your server\n" +
                "2. Replace the play_time.db file in your plugins/PlayTimeManager folder with this backup\n" +
                "3. Restart your server\n\n" +
                "Note: This backup contains all player playtime data up to the moment of backup creation.\n" +
                "If you need assistance, please contact me on discord or refer to the documentation.\n";

        return readme;
    }
}