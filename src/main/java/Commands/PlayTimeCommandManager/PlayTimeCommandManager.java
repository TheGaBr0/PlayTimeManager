package Commands.PlayTimeCommandManager;

import UsersDatabases.User;
import UsersDatabases.UsersManager;
import Main.PlayTimeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlayTimeCommandManager implements CommandExecutor, TabCompleter {
    private final List<String> subCommands = new ArrayList<>();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private UsersManager usersManager;

    public PlayTimeCommandManager() {
        subCommands.add("add");
        subCommands.add("remove");
        usersManager = plugin.getUsersManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!command.getName().equalsIgnoreCase("playtime")) {
            return false;
        }
        if (!(sender instanceof Player) || sender.hasPermission("playtime")) {
            if(args.length <= 1){
                if(args.length == 1){
                    User user = usersManager.getUserByNickname(args[0]);
                    if(user.getPlayTime() == 0L){
                        sender.sendMessage("[§6Play§eTime§f]§7 The player §e" + args[0] + "§7 has never joined the server!");
                        return false;
                    }
                }

                new PlaytimeCommand(sender, args);
                return true;
            }

            if (sender.hasPermission("playtime.others.modify")){
                String subCommand = args[1];

                User user = usersManager.getUserByNickname(args[0]);

                if (!subCommands.contains(subCommand)) {
                    sender.sendMessage("[§6Play§eTime§f]§7 Unknown subcommand: " + subCommand);
                    return false;
                }

                if (subCommand.equals("add")) {
                    new PlayTimeAddTime(sender, args, user);
                    return true;
                } else if (subCommand.equals("remove")) {
                    new PlayTimeRemoveTime(sender, args, user);
                    return true;
                }
            }


        } else {
            sender.sendMessage("[§6Play§eTime§f]§7 You don't have permission to use this command");
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();

        if (args.length == 1) {

            StringUtil.copyPartialMatches(args[0], getPlayersFromDB(), completions);

            Collections.sort(completions);

            return completions;
        }

        if (args.length == 2 && sender.hasPermission("playtime.others.modify")) {
            StringUtil.copyPartialMatches(args[1], subCommands, completions);
            return completions;
        }
        return null;
    }

    private List<String>getPlayersFromDB(){

        List<String> players = new ArrayList<String>();

        for (User user : usersManager.getStoredPlayers() ) {
            if(user.getPlayTime() != 0L)
                players.add(user.getName());
        }

        return players;

    }

}
