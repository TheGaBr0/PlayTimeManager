package Commands.PlayTimeCommandManager;

import UsersDatabases.User;
import UsersDatabases.UsersManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class PlaytimeCommand{

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private User user;
    private final UsersManager usersManager;

    public PlaytimeCommand(CommandSender sender, String[] args){
        usersManager = plugin.getUsersManager();
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

        user = usersManager.getUserByNickname(sender.getName());

        long playtime = user.getPlayTime();
        String message = formatPlaytimeMessage(sender, user.getName(), playtime);
        sender.sendMessage(message);
        return true;
    }

    private boolean handleOther(CommandSender sender, String playerName) {

        long playtime = usersManager.getPlayTimeByNick(playerName);

        String message = formatPlaytimeMessage(sender, playerName, playtime);
        sender.sendMessage(message);
        return true;
    }

    private String formatPlaytimeMessage(CommandSender sender, String playerName, long playtime) {
        String formattedPlaytime = usersManager.convertTime(playtime / 20);
        long customPlayTime =  usersManager.getArtificialPlayTimeByNick(user.getUuid());
        if(customPlayTime != 0L && sender.hasPermission("playtime.others.modify"))
            return "[§6Play§eTime§f]§7 Il tempo di gioco di §e" + playerName + "§7 è §6" + formattedPlaytime +
                    " ("+usersManager.convertTime(customPlayTime / 20)+")";
        else
            return "[§6Play§eTime§f]§7 Il tempo di gioco di §e" + playerName + "§7 è §6" + formattedPlaytime;

    }
}
