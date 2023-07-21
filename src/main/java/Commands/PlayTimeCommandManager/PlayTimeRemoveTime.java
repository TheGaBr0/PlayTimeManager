package Commands.PlayTimeCommandManager;

import UsersDatabases.*;
import Main.PlayTimeManager;
import org.bukkit.command.CommandSender;

public class PlayTimeRemoveTime {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private User user;
    private UsersManager usersManager;
    protected UuidDB uuidDB;

    public PlayTimeRemoveTime(CommandSender sender, String[] args, User user){

        this.user = user;
        usersManager = plugin.getUsersManager();
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
        DataCombiner timeConverter = new DataCombiner();
        String format = args[2].replaceAll("\\d", "");
        switch(format){
            case "d": timeToTicks = -1 * time * 1728000L; break;
            case "h": timeToTicks = -1 * time * 72000L; break;
            case "m": timeToTicks = -1 * time * 1200L; break;
            default: sender.sendMessage("[§6Play§eTime§f]§7 Time format must be specified! [d/h/m]"); return;
        }
        String formattedOldPlaytime = plugin.getPlayTimeDB().convertTime(user.getPlayTime() / 20);
        user.manuallyUpdatePlayTime(timeToTicks);
        String formattedNewPlaytime = plugin.getPlayTimeDB().convertTime(user.getPlayTime() / 20);

        sender.sendMessage("[§6Play§eTime§f]§7 PlayTime of §e" + args[0] +
                "§7 has been updated from §6" + formattedOldPlaytime + "§7 to §6" + formattedNewPlaytime +"!");
    }
}
