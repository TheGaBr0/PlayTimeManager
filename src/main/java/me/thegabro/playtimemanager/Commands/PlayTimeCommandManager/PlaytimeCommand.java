package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms.LuckPermsManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class PlaytimeCommand {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private LuckPermsManager luckPermsManager = null;

    public PlaytimeCommand(CommandSender sender, String[] args) {
        if (plugin.isPermissionsManagerConfigured()) {
            try {
                this.luckPermsManager = LuckPermsManager.getInstance(plugin);
            } catch (NoClassDefFoundError e) {
                // LuckPerms is not loaded, leave luckPermsManager as null
            }
        }
        execute(sender, args);
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check base permissions first
        if (args.length == 0) {
            if (!sender.hasPermission("playtime")) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have permission to check playtime."));
                return false;
            }
            return handleSelf(sender);
        }

        // Check other player playtime permissions
        if (args.length == 1) {
            if (!sender.hasPermission("playtime.others")) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have permission to check other players' playtime."));
                return false;
            }
            return handleOther(sender, args[0]);
        }

        // Invalid command usage
        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Usage: /playtime [player]"));
        return false;
    }

    private boolean handleSelf(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You must be a player to execute this command"));
            return false;
        }

        Player player = (Player) sender;
        OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());
        String formattedPlaytime = Utils.ticksToFormattedPlaytime(onlineUser.getPlaytime());

        // Check if prefix placeholder is used and LuckPerms is configured
        if (plugin.getConfiguration().getPlaytimeSelfMessage().contains("%PREFIX%") && plugin.isPermissionsManagerConfigured()) {
            luckPermsManager.getPrefixAsync(String.valueOf(player.getUniqueId()))
                    .thenAccept(prefix -> {
                        String message = createMessage(plugin.getConfiguration().getPlaytimeSelfMessage(),
                                player.getName(),
                                formattedPlaytime,
                                prefix,
                                sender.hasPermission("playtime.others.modify"),
                                onlineUser.getArtificialPlaytime());
                        sender.sendMessage(Utils.parseColors(message));
                    });
        } else {
            String message = createMessage(plugin.getConfiguration().getPlaytimeSelfMessage(),
                    player.getName(),
                    formattedPlaytime,
                    "",
                    sender.hasPermission("playtime.others.modify"),
                    onlineUser.getArtificialPlaytime());
            sender.sendMessage(Utils.parseColors(message));
        }

        return true;
    }

    private boolean handleOther(CommandSender sender, String playerName) {
        DBUser user = dbUsersManager.getUserFromNickname(playerName);
        String formattedPlaytime = Utils.ticksToFormattedPlaytime(user.getPlaytime());

        // Check if prefix placeholder is used and LuckPerms is configured
        if (plugin.getConfiguration().getPlaytimeOthersMessage().contains("%PREFIX%") && plugin.isPermissionsManagerConfigured()) {
            luckPermsManager.getPrefixAsync(user.getUuid())
                    .thenAccept(prefix -> {
                        String message = createMessage(plugin.getConfiguration().getPlaytimeOthersMessage(),
                                playerName,
                                formattedPlaytime,
                                prefix,
                                sender.hasPermission("playtime.others.modify"),
                                user.getArtificialPlaytime());
                        sender.sendMessage(Utils.parseColors(message));
                    });
        } else {
            String message = createMessage(plugin.getConfiguration().getPlaytimeOthersMessage(),
                    playerName,
                    formattedPlaytime,
                    "",
                    sender.hasPermission("playtime.others.modify"),
                    user.getArtificialPlaytime());
            sender.sendMessage(Utils.parseColors(message));
        }

        return true;
    }

    private String createMessage(String template, String playerName, String playtime, String prefix, boolean showArtificial, long artificialPlaytime) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%PLAYER_NAME%", playerName);
        placeholders.put("%PLAYTIME%", playtime);

        String message = template;
        if (prefix != null && !prefix.isEmpty()) {
            placeholders.put("%PREFIX%", prefix);  // No extra space added
        } else {
            message = message.replace("%PREFIX%", "");
        }

        // Apply remaining placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        // Normalize multiple spaces to single space
        message = message.replaceAll("\\s+", " ");

        if (showArtificial) {
            message = message + " (" + Utils.ticksToFormattedPlaytime(artificialPlaytime) + ")";
        }

        return message;
    }
}