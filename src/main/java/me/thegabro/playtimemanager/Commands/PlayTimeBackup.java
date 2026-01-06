package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Database.Database;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
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
    private final CommandsConfiguration commandsConfig = CommandsConfiguration.getInstance();
    private final Configuration config = Configuration.getInstance();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DatabaseBackupUtility backupUtility = DatabaseBackupUtility.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (sender.hasPermission("playtime.backup")){

            File success = backupUtility.createBackup("Manual backup");
            if (success != null) {
                sender.sendMessage(Utils.parseColors(commandsConfig.getString("prefix") +
                        " &7Database backup created successfully!"));
            } else {
                sender.sendMessage(Utils.parseColors(commandsConfig.getString("prefix") +
                        " &7Failed to create database backup. Check console for details."));
            }
            return true;
        } else {
            sender.sendMessage(Utils.parseColors(commandsConfig.getString("prefix") + commandsConfig.getString("no-permission")));
        }
        return false;
    }
}