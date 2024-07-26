package Commands.PlayTimeCommandManager;

import Users.*;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.command.CommandSender;

import java.util.concurrent.TimeUnit;

public class PlayTimeRemoveTime {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final OnlineUsersManager onlineUsersManager =  plugin.getUsersManager();

    public PlayTimeRemoveTime(CommandSender sender, String[] args){
        execute(sender, args);
    }
    public void execute(CommandSender sender, String[] args){

        if(args.length < 3){
            sender.sendMessage("[§6PlayTime§eManager§f]§7 Too few arguments!");
            return;
        }

        int time;
        long timeToTicks;

        try{
            time = Integer.parseInt(args[2].replaceAll("[^\\d.]", ""));
        }catch(NumberFormatException e){
            sender.sendMessage("[§6PlayTime§eManager§f]§7 Time is not specified correctly!");
            return;
        }

        String format = args[2].replaceAll("\\d", "");
        switch(format){
            case "d": timeToTicks = -1 * time * 1728000L; break;
            case "h": timeToTicks = -1 * time * 72000L; break;
            case "m": timeToTicks = -1 * time * 1200L; break;
            case "s": timeToTicks = -1 * time * 20L; break;
            default: sender.sendMessage("[§6PlayTime§eManager§f]§7 Time format must be specified! [d/h/m/s]"); return;
        }
        DBUser user = onlineUsersManager.getOnlineUser(args[0]);

        if(user == null)
            user = DBUser.fromNickname(args[0]);

        long oldPlaytime = user.getPlaytime() / 20;
        String formattedOldPlaytime = convertTime(oldPlaytime);
        user.setArtificialPlaytime(user.getArtificialPlaytime() + timeToTicks);
        String formattedNewPlaytime = convertTime(oldPlaytime + (timeToTicks /20));

        sender.sendMessage("[§6PlayTime§eManager§f]§7 PlayTime of §e" + args[0] +
                "§7 has been updated from §6" + formattedOldPlaytime + "§7 to §6" + formattedNewPlaytime +"!");
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
