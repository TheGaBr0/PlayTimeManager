package me.thegabro.playtimemanager.ExternalPluginSupport;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PlayTimePlaceHolders extends PlaceholderExpansion{

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    @Override
    public @NotNull String getIdentifier() {
        return "PTM";
    }

    @Override
    public @NotNull String getAuthor() {
        return "TheGabro";
    }

    @Override
    public @NotNull String getVersion() {
        return "3.0.0";
    }

    @Override
    public boolean persist() {
        return true; // This is required or else PlaceholderAPI will unregister the Expansion on reload
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {

        if(params.toLowerCase().contains("Lastseen_Elapsed_Top_".toLowerCase())) {
            int position;
            long seconds;
            Duration duration;
            if(isStringInt(params.substring(21))){
                position = Integer.parseInt(params.substring(21));
                DBUser user = dbUsersManager.getTopPlayerAtPosition(position);

                if(user == null)
                    return "Error: wrong top position?";
                else {
                    if(user.getLastSeen() == null)
                        return "Error: last seen data missing";

                    duration = Duration.between(user.getLastSeen(), LocalDateTime.now());
                    seconds = duration.getSeconds();
                    return Utils.ticksToFormattedPlaytime(seconds * 20);
                }
            }
        }

        if(params.toLowerCase().contains("Lastseen_Elapsed_".toLowerCase())) {
            String nickname;
            Duration duration;
            long seconds;
            nickname = params.substring(17);
            DBUser user = dbUsersManager.getUserFromNickname(nickname);

            if(user == null)
                return "Error: wrong nickname?";
            else{

                if(user.getLastSeen() == null)
                    return "Error: last seen data missing";

                duration = Duration.between(user.getLastSeen(), LocalDateTime.now());
                seconds = duration.getSeconds();
                return Utils.ticksToFormattedPlaytime(seconds * 20);
            }
        }

        if(params.toLowerCase().contains("Nickname_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(13))){
                position = Integer.parseInt(params.substring(13));
                DBUser user = dbUsersManager.getTopPlayerAtPosition(position);

                if(user == null)
                    return "Error: wrong top position?";
                else
                    return user.getNickname();
            }
        }

        if(params.toLowerCase().contains("PlayTime_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(13))){
                position = Integer.parseInt(params.substring(13));
                DBUser user = dbUsersManager.getTopPlayerAtPosition(position);

                if(user == null)
                    return "Error: wrong top position?";
                else
                    return Utils.ticksToFormattedPlaytime(user.getPlaytime());
            }
        }

        if(params.toLowerCase().contains("PlayTime_".toLowerCase())) {
            String nickname;
            nickname = params.substring(9);
            DBUser user = dbUsersManager.getUserFromNickname(nickname);

            if(user == null)
                return "Error: wrong nickname?";
            else
                return Utils.ticksToFormattedPlaytime(user.getPlaytime());
        }

        if(params.equalsIgnoreCase("PlayTime")){
            return Utils.ticksToFormattedPlaytime(onlineUsersManager.getOnlineUser(player.getName()).getPlaytime());
        }

        if(params.toLowerCase().contains("Lastseen_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(13))){
                position = Integer.parseInt(params.substring(13));
                DBUser user = dbUsersManager.getTopPlayerAtPosition(position);
                if(user == null)
                    return "Error: wrong top position?";
                else{
                    LocalDateTime lastSeen = user.getLastSeen();
                    if(user.getLastSeen() == null)
                        return "Error: last seen data missing";
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getDateTimeFormat());
                    return lastSeen.format(formatter);
                }
            }
        }

        if(params.toLowerCase().contains("Lastseen_".toLowerCase())) {
            String nickname = params.substring(9);
            DBUser user = dbUsersManager.getUserFromNickname(nickname);

            if(user == null)
                return "Error: wrong nickname?";
            else {
                LocalDateTime lastSeen = user.getLastSeen();
                if(lastSeen == null)
                    return "Error: last seen data missing";

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getDateTimeFormat());
                return lastSeen.format(formatter);
            }
        }

        return null;
    }

    public boolean isStringInt(String s)
    {
        try
        {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException ex)
        {
            return false;
        }
    }

}
