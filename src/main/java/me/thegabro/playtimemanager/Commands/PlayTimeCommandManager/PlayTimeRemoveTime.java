package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.command.CommandSender;

import java.util.concurrent.TimeUnit;

public class PlayTimeRemoveTime {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public PlayTimeRemoveTime(CommandSender sender, String[] args){
        execute(sender, args);
    }
    public void execute(CommandSender sender, String[] args){

        if(args.length < 3){
            sender.sendMessage("[§6PlayTime§eManager§f]§7 Too few arguments!");
            return;
        }

        String input = args[2];
        String[] timeParts = input.split(",");
        long timeToTicks = 0;

        int dcount = 0, hcount = 0, mcount = 0, scount = 0;

        for (String part : timeParts) {
            try {
                int time = Integer.parseInt(part.replaceAll("[^\\d.]", ""));
                String format = part.replaceAll("\\d", "");

                switch(format) {
                    case "d":
                        if(dcount == 0) {
                            timeToTicks += -1 * time * 1728000L;
                            dcount++;
                        }break;
                    case "h":
                        if(hcount == 0) {
                            timeToTicks += -1 * time * 72000L;
                            hcount++;
                        }break;
                    case "m":
                        if(mcount == 0) {
                            timeToTicks += -1 * time * 1200L;
                            mcount++;
                        }break;
                    case "s":
                        if(scount == 0) {
                            timeToTicks += -1 * time * 20L;
                            scount++;
                        }break;
                    default:
                        sender.sendMessage("[§6PlayTime§eManager§f]§7 Invalid time format: " + format);
                        return;
                }
            } catch(NumberFormatException e) {
                sender.sendMessage("[§6PlayTime§eManager§f]§7 Invalid time format: " + part);
                return;
            }
        }
        DBUser user = plugin.getDbUsersManager().getUserFromNickname(args[0]);

        long oldPlaytime = user.getPlaytime() / 20;
        String formattedOldPlaytime = convertTime(oldPlaytime);
        user.setArtificialPlaytime(user.getArtificialPlaytime() + timeToTicks);
        String formattedNewPlaytime = convertTime(oldPlaytime + (timeToTicks /20));

        sender.sendMessage("[§6PlayTime§eManager§f]§7 PlayTime of §e" + args[0] +
                "§7 has been updated from §6" + formattedOldPlaytime + "§7 to §6" + formattedNewPlaytime +"!");
        plugin.getDbUsersManager().updateTopPlayersFromDB();
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
