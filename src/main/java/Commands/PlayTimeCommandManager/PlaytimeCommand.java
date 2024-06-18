package Commands.PlayTimeCommandManager;

import Users.DBUser;
import Users.OnlineUser;
import Users.OnlineUsersManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public class PlaytimeCommand{

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = plugin.getUsersManager();

    public PlaytimeCommand(CommandSender sender, String[] args){
        execute(sender, args);
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return handleSelf(sender);
        } else if (args.length == 1 && sender.hasPermission("playtime.others")) {
            return handleOther(sender, args[0]);
        } else {
            sender.sendMessage("[§6Play§eTime§f]§7 Usage: /playtime [player]");
            return false;
        }
    }

    private boolean handleSelf(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("[§6Play§eTime§f]§7 You must be a player to execute this command");
            return false;
        }

        OnlineUser onlineUser = onlineUsersManager.getOnlineUser(sender.getName());

        String message = formatPlaytimeMessage(sender, sender.getName(), onlineUser.getPlaytime(), onlineUser.getArtificialPlaytime());
        sender.sendMessage(message);
        return true;
    }

    private boolean handleOther(CommandSender sender, String playerName) {

        DBUser dbUser = DBUser.fromNickname(playerName);

        String message = formatPlaytimeMessage(sender, dbUser.getNickname(), dbUser.getPlaytime(), dbUser.getArtificialPlaytime());
        sender.sendMessage(message);
        return true;
    }

    private String formatPlaytimeMessage(CommandSender sender, String playerName, long playtime, long artificialPlaytime) {
        String formattedPlaytime = convertTime(playtime / 20);
        if(sender.hasPermission("playtime.others.modify"))
            return "[§6Play§eTime§f]§7 Il tempo di gioco di §e" + playerName + "§7 è §6" + formattedPlaytime +
                    " ("+ convertTime(artificialPlaytime / 20)+")";
        else
            return "[§6Play§eTime§f]§7 Il tempo di gioco di §e" + playerName + "§7 è §6" + formattedPlaytime;

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
}
