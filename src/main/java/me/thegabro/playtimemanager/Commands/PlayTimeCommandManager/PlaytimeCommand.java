package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms.LuckPermsManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class PlaytimeCommand {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    private LuckPermsManager luckPermsManager = null;

    public PlaytimeCommand(CommandSender sender, DBUser user) {
        if (plugin.isPermissionsManagerConfigured()) {
            try {
                this.luckPermsManager = LuckPermsManager.getInstance(plugin);
            } catch (NoClassDefFoundError e) {
                // LuckPerms is not loaded, leave luckPermsManager as null
            }
        }
        execute(sender, user);
    }

    public boolean execute(CommandSender sender, DBUser user) {
        if (user == null) {
            if (!sender.hasPermission("playtime")) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " You don't have permission to check playtime."));
                return false;
            }
            return handleSelf(sender);
        }else{
            if (!sender.hasPermission("playtime.others")) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " You don't have permission to check other players' playtime."));
                return false;
            }
            return handleOther(sender, user);
        }
    }

    private boolean handleSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " You must be a player to execute this command"));
            return false;
        }

        OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());

        // Check if prefix placeholder is used and LuckPerms is configured
        if (config.getString("playtime-self-message").contains("%PREFIX%") && plugin.isPermissionsManagerConfigured()) {
            luckPermsManager.getPrefixAsync(String.valueOf(player.getUniqueId()))
                    .thenAccept(prefix -> {
                        String message = createMessage(config.getString("playtime-self-message"),
                                player.getName(),
                                String.valueOf(onlineUser.getPlaytime()),
                                prefix);
                        sender.sendMessage(Utils.parseColors(message));
                    });
        } else {
            String message = createMessage(config.getString("playtime-self-message"),
                    player.getName(),
                    String.valueOf(onlineUser.getPlaytime()),
                    "");
            sender.sendMessage(Utils.parseColors(message));
        }

        return true;
    }

    private boolean handleOther(CommandSender sender, DBUser user) {
        if (config.getString("playtime-others-message").contains("%PREFIX%") && plugin.isPermissionsManagerConfigured()) {
            luckPermsManager.getPrefixAsync(user.getUuid()).thenAccept(prefix -> {
                String message = createMessage(config.getString("playtime-others-message"),
                        user.getNickname(),
                        String.valueOf(user.getPlaytime()),
                        prefix);
                sender.sendMessage(Utils.parseColors(message));
            });
        } else {
            String message = createMessage(config.getString("playtime-others-message"),
                        user.getNickname(),
                        String.valueOf(user.getPlaytime()),
                        "");
            sender.sendMessage(Utils.parseColors(message));
        }

        return true;
    }

    private String createMessage(String template, String playerName, String playtime, String prefix) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%PLAYER_NAME%", playerName);
        placeholders.put("%PLAYTIME%", playtime);

        if (prefix != null && !prefix.isEmpty()) {
            placeholders.put("%PREFIX%", prefix);  // No extra space added
        } else {
            placeholders.put("%PREFIX%", "");
        }

        return Utils.placeholdersReplacer(template, placeholders);
    }
}