package Commands.PlayTimeCommandManager;

import Users.DBUser;
import Users.OnlineUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PlaytimeCommand{

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

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

        OnlineUser onlineUser = plugin.getOnlineUsersManager().getOnlineUser(sender.getName());

        String formattedPlaytime = convertTime(onlineUser.getPlaytime() / 20);
        String message = replacePlaceholders(plugin.getConfiguration().getPlaytimeSelfMessage(), sender.getName(), formattedPlaytime);
        if(sender.hasPermission("playtime.others.modify"))
            message = message + " ("+ convertTime(onlineUser.getArtificialPlaytime() / 20)+")";

        sender.sendMessage(message);
        return true;
    }

    private boolean handleOther(CommandSender sender, String playerName) {

        DBUser user = plugin.getDbUsersManager().getUserFromNickname(playerName);

        String formattedPlaytime = convertTime(user.getPlaytime() / 20);
        String message = replacePlaceholders(plugin.getConfiguration().getPlaytimeOthersMessage(), playerName, formattedPlaytime);
        if(sender.hasPermission("playtime.others.modify"))
            message = message + " ("+ convertTime(user.getArtificialPlaytime() / 20)+")";
        sender.sendMessage(message);
        return true;
    }


    private String convertTime(long secondsx) {
        int days = (int) TimeUnit.SECONDS.toDays(secondsx);
        int hours = (int) (TimeUnit.SECONDS.toHours(secondsx) - TimeUnit.DAYS.toHours(days));
        int minutes = (int) (TimeUnit.SECONDS.toMinutes(secondsx) - TimeUnit.HOURS.toMinutes(hours)
                - TimeUnit.DAYS.toMinutes(days));
        int seconds = (int) (TimeUnit.SECONDS.toSeconds(secondsx) - TimeUnit.MINUTES.toSeconds(minutes)
                - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.DAYS.toSeconds(days));

        if (days != 0) {
            return days + "d, " + hours + "h, " + minutes + "m, " + seconds + "s";
        } else {
            if (hours != 0) {
                return hours + "h, " + minutes + "m, " + seconds + "s";
            } else {
                if (minutes != 0) {
                    return minutes + "m, " + seconds + "s";
                } else {
                    return seconds + "s";
                }
            }

        }
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
