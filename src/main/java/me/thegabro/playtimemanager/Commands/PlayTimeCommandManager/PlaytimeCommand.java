package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class PlaytimeCommand{

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    public PlaytimeCommand(CommandSender sender, String[] args){
        execute(sender, args);
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check base permissions first
        if (args.length == 0) {
            if (!sender.hasPermission("playtime")) {
                sender.sendMessage("§6[PlayTime§eManager§f]§7 You don't have permission to check playtime.");
                return false;
            }
            return handleSelf(sender);
        }

        // Check other player playtime permissions
        if (args.length == 1) {
            if (!sender.hasPermission("playtime.others")) {
                sender.sendMessage("§6[PlayTime§eManager§f]§7 You don't have permission to check other players' playtime.");
                return false;
            }
            return handleOther(sender, args[0]);
        }

        // Invalid command usage
        sender.sendMessage("§6[PlayTime§eManager§f]§7 Usage: /playtime [player]");
        return false;
    }

    private boolean handleSelf(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 You must be a player to execute this command");
            return false;
        }

        OnlineUser onlineUser = onlineUsersManager.getOnlineUser(sender.getName());

        String formattedPlaytime = Utils.ticksToFormattedPlaytime(onlineUser.getPlaytime());
        String message = replacePlaceholders(plugin.getConfiguration().getPlaytimeSelfMessage(), sender.getName(), formattedPlaytime);
        if(sender.hasPermission("playtime.others.modify"))
            message = message + " ("+ Utils.ticksToFormattedPlaytime(onlineUser.getArtificialPlaytime())+")";

        sender.sendMessage(message);
        return true;
    }

    private boolean handleOther(CommandSender sender, String playerName) {

        DBUser user = dbUsersManager.getUserFromNickname(playerName);

        String formattedPlaytime = Utils.ticksToFormattedPlaytime(user.getPlaytime());
        String message = replacePlaceholders(plugin.getConfiguration().getPlaytimeOthersMessage(), playerName, formattedPlaytime);
        if(sender.hasPermission("playtime.others.modify"))
            message = message + " ("+ Utils.ticksToFormattedPlaytime(user.getArtificialPlaytime())+")";
        sender.sendMessage(message);
        return true;
    }


    public String replacePlaceholders(String input, String playerName, String playtime) {
        // Create a map for the placeholders and their corresponding values
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%PLAYER_NAME%", playerName);
        placeholders.put("%PLAYTIME%", playtime);


        // Replace placeholders in the input string
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            input = input.replace(entry.getKey(), entry.getValue());
        }

        return input;
    }
}
