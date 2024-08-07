package Commands;

import Users.OnlineUsersManagerLuckPerms;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class PlaytimeReload implements CommandExecutor {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (sender.hasPermission("playtime.reload")){
            plugin.getConfiguration().reload();
            sender.sendMessage("[§6PlayTime§eManager§f]§7 The configuration file has been reloaded");

            if(plugin.isLuckPermsLoaded()) {
                OnlineUsersManagerLuckPerms onlineUsersManager = (OnlineUsersManagerLuckPerms) plugin.getUsersManager();
                onlineUsersManager.restartSchedule();
                sender.sendMessage("[§6PlayTime§eManager§f]§7 LuckPerms check schedule has been restarted");
            }
            return true;
        }else{
            sender.sendMessage("[§6PlayTime§eManager§f]§7 You don't have the permission to execute this command");
        }
        return false;
    }
}
