package Commands.PlayTimeCommandManager;

import UsersDatabases.DataCombiner;
import UsersDatabases.User;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.command.CommandSender;

public class PlayTimeAddTime {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private User user;
    public PlayTimeAddTime(CommandSender sender, String[] args, User user){
        this.user = user;
        execute(sender, args);
    }

    public void execute(CommandSender sender, String[] args){

        if(args.length < 3){
            sender.sendMessage("[§6Play§eTime§f]§7 Too few arguments!");
            return;
        }

        int time;
        long timeToTicks;

        try{
            time = Integer.parseInt(args[2].replaceAll("[^\\d.]", ""));
        }catch(NumberFormatException e){
            sender.sendMessage("[§6Play§eTime§f]§7 Time is not specified correctly!");
            return;
        }
        String format = args[2].replaceAll("\\d", "");
        switch(format){
            case "d": timeToTicks = time * 1728000L; break;
            case "h": timeToTicks = time * 72000L; break;
            case "m": timeToTicks = time * 1200L; break;
            default: sender.sendMessage("[§6Play§eTime§f]§7 Time format must be specified! [d/h/m]"); return;
        }
        String formattedOldPlaytime = plugin.getPlayTimeDB().convertTime(user.getPlayTime() / 20);
        user.manuallyUpdatePlayTime(timeToTicks);
        String formattedNewPlaytime = plugin.getPlayTimeDB().convertTime(user.getPlayTime() / 20);

        sender.sendMessage("[§6Play§eTime§f]§7 PlayTime of §e" + args[0] +
                "§7 has been updated from §6" + formattedOldPlaytime + "§7 to §6" + formattedNewPlaytime +"!");

    }
}
