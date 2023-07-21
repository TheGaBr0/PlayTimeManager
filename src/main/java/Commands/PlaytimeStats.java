package Commands;

import Main.PlayTimeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class PlaytimeStats implements CommandExecutor {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (sender.hasPermission("playtime.stats")) {
            if(args.length>0){
                int time;
                //rimpiazza tutti i numeri con ""
                String format = args[0].replaceAll("\\d", "");
                switch(format){
                    case "d": break;
                    case "h": break;
                    case "m": break;
                    default: sender.sendMessage("[§6Play§eTime§f]§7 Time format must be specified! [d/h/m]"); return false;
                }
                try{
                    time = Integer.parseInt(args[0].replaceAll("[^\\d.]", ""));
                }catch(NumberFormatException e){
                    sender.sendMessage("[§6Play§eTime§f]§7 Time is not specified correctly!");
                    return false;
                }
                sender.sendMessage(plugin.getDbDataCombiner().getPercentages(time, format));

            }else{
                sender.sendMessage("[§6Play§eTime§f]§7 Missing arguments");
            }
        } else {
            sender.sendMessage("[§6Play§eTime§f]§7 You don't have the permission to execute this command");
        }
        return false;
    }


}

