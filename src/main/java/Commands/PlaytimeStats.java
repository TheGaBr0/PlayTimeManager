package Commands;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class PlaytimeStats implements CommandExecutor {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (sender.hasPermission("playtime.stats")) {
            if(args.length>0){
                long time;
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

                sender.sendMessage(String.valueOf(plugin.getDatabase().getPercentageOfPlayersWithPlaytimeGreaterThan(time)));

            }else{
                sender.sendMessage("[§6Play§eTime§f]§7 Missing arguments");
            }
        } else {
            sender.sendMessage("[§6Play§eTime§f]§7 You don't have the permission to execute this command");
        }
        return false;
    }

    public int convertTicksToDays(long seconds) {
        return (int) TimeUnit.SECONDS.toDays(seconds);
    }

    public int convertTicksToHours(long seconds) {

        int days = (int) TimeUnit.SECONDS.toDays(seconds);
        return (int) (TimeUnit.SECONDS.toHours(seconds) - TimeUnit.DAYS.toHours(days));
    }

    public int convertTicksToMinutes(long seconds) {

        int days = (int) TimeUnit.SECONDS.toDays(seconds);
        int hours = (int) (TimeUnit.SECONDS.toHours(seconds) - TimeUnit.DAYS.toHours(days));
        return (int) (TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(hours) - TimeUnit.DAYS.toMinutes(days));
    }


}

