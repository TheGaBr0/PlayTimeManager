package me.thegabro.playtimemanager.Commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Updates.DatabaseBackupUtility;
import me.thegabro.playtimemanager.Utils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PlayTimeBackup implements CommandRegistrar {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public void registerCommands(){
        new CommandAPICommand("playtimebackup")
                .withAliases("ptbkp")
                .withPermission(CommandPermission.fromString("playtime.backup"))
                .executes((sender, args) -> {
                    DatabaseBackupUtility backupUtility = new DatabaseBackupUtility(plugin);
                    File success = backupUtility.createBackup("play_time", generateReadmeContent());
                    if (success != null) {
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                " &7Database backup created successfully!"));
                    } else {
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                " &7Failed to create database backup. Check console for details."));
                    }
                })
                .register();
    }

    private String generateReadmeContent() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());

        StringBuilder readme = new StringBuilder();
        readme.append("PlayTimeManager Database Backup\n");
        readme.append("==============================\n\n");
        readme.append("Backup created on: " + timestamp + "\n\n");
        readme.append("Plugin information:\n");
        readme.append("- Plugin name: PlayTimeManager\n");
        readme.append("- Version: " + plugin.getDescription().getVersion() + "\n");
        readme.append("- Author: TheGabro\n\n");
        readme.append("Database information:\n");
        readme.append("- Database name: play_time\n");
        readme.append("- Backup reason: Manual backup triggered by command\n");
        readme.append("Restore instructions:\n");
        readme.append("1. Stop your server\n");
        readme.append("2. Replace the play_time.db file in your plugins/PlayTimeManager folder with this backup\n");
        readme.append("3. Restart your server\n\n");
        readme.append("Note: This backup contains all player playtime data up to the moment of backup creation.\n");
        readme.append("If you need assistance, please contact me on discord or refer to the documentation.\n");

        return readme.toString();
    }
}