package Commands.PlayTimeCommandManager;

import SQLiteDB.Database;
import Users.OnlineUsersManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PlayTimeCommandManager implements CommandExecutor, TabCompleter {
    private final List<String> subCommands = new ArrayList<>();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final OnlineUsersManager onlineUsersManager;
    private final Database db = plugin.getDatabase();

    public PlayTimeCommandManager() {
        subCommands.add("add");
        subCommands.add("remove");
        onlineUsersManager = plugin.getUsersManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {

        if (!command.getName().equalsIgnoreCase("playtime")) {
            return false;
        }
        if (!(sender instanceof Player) || sender.hasPermission("playtime")) {
            if(args.length <= 1){
                if(args.length == 1){
                    if(!onlineUsersManager.userExists(args[0])){
                        sender.sendMessage("[§6Play§eTime§f]§7 The player §e" + args[0] + "§7 has never joined the server!");
                        return false;
                    }
                }

                new PlaytimeCommand(sender, args);
                return true;
            }

            if (sender.hasPermission("playtime.others.modify")){
                String subCommand = args[1];

                if (!subCommands.contains(subCommand)) {
                    sender.sendMessage("[§6Play§eTime§f]§7 Unknown subcommand: " + subCommand);
                    return false;
                }

                if (subCommand.equals("add")) {
                    new PlayTimeAddTime(sender, args);
                    return true;
                } else if (subCommand.equals("remove")) {
                    new PlayTimeRemoveTime(sender, args);
                    return true;
                }
            }


        } else {
            sender.sendMessage("[§6Play§eTime§f]§7 You don't have permission to use this command");
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        final List<String> completions = new ArrayList<>();

//The following code causes "java.sql.SQLException: stmt pointer is closed" when a PAPI leaderboard is active. Should probably cache the nicknames
//        if (args.length == 1) {
//
//            StringUtil.copyPartialMatches(args[0], db.getAllNicknames(), completions);
//
//            Collections.sort(completions);
//
//            return completions;
//        }

        if (args.length == 2 && sender.hasPermission("playtime.others.modify")) {
            StringUtil.copyPartialMatches(args[1], subCommands, completions);
            return completions;
        }
        return null;
    }

}
