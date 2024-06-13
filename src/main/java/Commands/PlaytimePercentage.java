package Commands;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

public class PlaytimePercentage implements CommandExecutor {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (sender.hasPermission("playtime.percentage")) {
            if(args.length>0){
                long time, timeToTicks;
                //rimpiazza tutti i numeri con ""
                try{
                    time = Integer.parseInt(args[0].replaceAll("[^\\d.]", ""));
                }catch(NumberFormatException e){
                    sender.sendMessage("[§6Play§eTime§f]§7 Time is not specified correctly!");
                    return false;
                }
                String format = args[0].replaceAll("\\d", "");
                switch(format){
                    case "d": timeToTicks = time * 1728000L; break;
                    case "h": timeToTicks = time * 72000L; break;
                    case "m": timeToTicks = time * 1200L; break;
                    default: sender.sendMessage("[§6Play§eTime§f]§7 Time format must be specified! [d/h/m]"); return false;
                }
                DecimalFormat df = new DecimalFormat("#.##");
                df.setRoundingMode(RoundingMode.HALF_UP);
                Object[] result = plugin.getDatabase().getPercentageOfPlayers(timeToTicks);
                String formattedNumber = df.format(result[0]);
                sender.sendMessage("[§6Play§eTime§f]§7 The players with playtime greater than or equal to §6" + args[0] +
                        " §7are §6" + result[1] + " §7and represent §6"+ formattedNumber +"% §7of the §6"+ result[2] +" §7players stored");
            }else{
                sender.sendMessage("[§6Play§eTime§f]§7 Missing arguments");
            }
        } else {
            sender.sendMessage("[§6Play§eTime§f]§7 You don't have the permission to execute this command");
        }
        return false;
    }
}

