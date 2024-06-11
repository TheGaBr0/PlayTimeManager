package Commands;

import me.thegabro.playtimemanager.PlayTimeManager;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlaytimeLuckPermsGroup implements TabExecutor {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private static final String[] SUBCOMMANDS = {"addGroup", "removeGroup"};
    private static final String[] TIME = {"setTime:"};

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (sender.hasPermission("playtime.group")){

            if(args.length >= 2){
                String extractedTime;
                if (args[2].contains("setTime:") && args[2].startsWith("setTime:")) {
                    extractedTime = args[2].substring(args[2].indexOf(":")+1);
                }else{
                    sender.sendMessage("[§6Play§eTime§f]§7 Time is not specified correctly!");
                    return false;
                }
                long time;
                try{
                    time = Integer.parseInt(extractedTime.replaceAll("[^\\d.]", ""));
                }catch(NumberFormatException e){
                    sender.sendMessage("[§6Play§eTime§f]§7 Time is not specified correctly!");
                    return false;
                }

                String format = extractedTime.replaceAll("\\d", "");
                switch(format){
                    case "d": time = time * 1728000L; break;
                    case "h": time = time * 72000L; break;
                    case "m": time = time * 1200L; break;
                    default: sender.sendMessage("[§6Play§eTime§f]§7 Time format must be specified! [d/h/m]"); return false;
                }

                if(args[0].equals("addGroup"))
                    addGroup(sender, args, time);
                else
                    removeGroup(sender, args);
            }else{
                sender.sendMessage("[§6Play§eTime§f]§7 Too few arguments!");
            }
        }else{
            sender.sendMessage("[§6Play§eTime§f]§7 You don't have the permission to execute this command");
        }
        return false;
    }

    private void addGroup(CommandSender sender, String[] args, long time){
        String groupName = args[1];
        Group group = plugin.luckPermsApi.getGroupManager().getGroup(groupName);
        if (group != null){
            plugin.getConfiguration().addGroup(groupName, time);
            sender.sendMessage("[§6Play§eTime§f]§7 The group §e"+groupName+"§7 will be aumomatically set to a player " +
                    "whenever he reaches §6"+ plugin.getUsersManager().convertTime(time/20));
        }else{
            sender.sendMessage("[§6Play§eTime§f]§7 The group §e"+groupName+"§7 doesn't exists!");
        }

    }

    private void removeGroup(CommandSender sender, String[] args){
        String groupName = args[1];
        Group group = plugin.luckPermsApi.getGroupManager().getGroup(groupName);

        if (group != null){
            plugin.getConfiguration().removeGroup(groupName);
            sender.sendMessage("[§6Play§eTime§f]§7 The group §e"+groupName+" §7has been removed!");
        }else{
            sender.sendMessage("[§6Play§eTime§f]§7 The group §e"+groupName+" §7doesn't exists!");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] args) {
        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0],  Arrays.asList(SUBCOMMANDS), completions);
            return completions;
        }

        if(args.length == 3){
            StringUtil.copyPartialMatches(args[2],  Arrays.asList(TIME), completions);
            return completions;
        }

        return null;
    }
}


