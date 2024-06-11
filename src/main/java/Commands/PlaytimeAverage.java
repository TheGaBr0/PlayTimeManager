package Commands;

import UsersDatabases.UsersManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PlaytimeAverage implements CommandExecutor {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final UsersManager usersManager = plugin.getUsersManager();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (sender.hasPermission("playtime.average")){
            sender.sendMessage("[§6Play§eTime§f]§7 The average play time is:§6 " + usersManager.convertTime(plugin.getDbDataCombiner().getAveragePlayTime()/20));
        }else{
            sender.sendMessage("[§6Play§eTime§f]§7 You don't have the permission to execute this command");
        }
        return false;
    }

}
