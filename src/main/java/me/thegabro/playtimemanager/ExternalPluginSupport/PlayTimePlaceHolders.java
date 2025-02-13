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
import java.util.concurrent.ExecutionException;

public class PlayTimePlaceHolders extends PlaceholderExpansion{

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final LuckPermsManager luckPermsManager = LuckPermsManager.getInstance(plugin);

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
        return "3.1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        boolean showErrors = plugin.getConfiguration().isPlaceholdersEnableErrors();
        String defaultErrorMessage = plugin.getConfiguration().getPlaceholdersDefaultMessage();

        if(params.toLowerCase().contains("LP_prefix_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(14))) {
                position = Integer.parseInt(params.substring(14));
                DBUser user = dbUsersManager.getTopPlayerAtPosition(position);

                if(user == null)
                    return showErrors ? "Error: wrong top position?" : "";
                else {
                    if(plugin.isPermissionsManagerConfigured()) {
                        try {
                            return luckPermsManager.getPrefixAsync(user.getUuid()).get();
                        } catch (InterruptedException | ExecutionException e) {
                            return showErrors ? "Error: luckperms retrieve unsuccessful" : "";
                        }
                    } else {
                        return showErrors ? "Error: luckperms not loaded" : "";
                    }
                }
            }
        }

        if(params.toLowerCase().contains("Lastseen_Elapsed_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(21))){
                position = Integer.parseInt(params.substring(21));
                DBUser user = dbUsersManager.getTopPlayerAtPosition(position);

                if(user == null)
                    return showErrors ? "Error: wrong top position?" : defaultErrorMessage;
                else if(user.getLastSeen() == null)
                    return showErrors ? "Error: last seen data missing" : defaultErrorMessage;

                Duration duration = Duration.between(user.getLastSeen(), LocalDateTime.now());
                return Utils.ticksToFormattedPlaytime(duration.getSeconds() * 20);
            }
        }

        if(params.toLowerCase().contains("Lastseen_Elapsed_".toLowerCase())) {
            String nickname = params.substring(17);
            DBUser user = dbUsersManager.getUserFromNickname(nickname);

            if(user == null)
                return showErrors ? "Error: wrong nickname?" : defaultErrorMessage;
            else if(user.getLastSeen() == null)
                return showErrors ? "Error: last seen data missing" : defaultErrorMessage;

            Duration duration = Duration.between(user.getLastSeen(), LocalDateTime.now());
            return Utils.ticksToFormattedPlaytime(duration.getSeconds() * 20);
        }

        if(params.toLowerCase().contains("Nickname_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(13))){
                position = Integer.parseInt(params.substring(13));
                DBUser user = dbUsersManager.getTopPlayerAtPosition(position);

                return user != null ? user.getNickname() : showErrors ? "Error: wrong top position?" : defaultErrorMessage;
            }
        }

        if(params.toLowerCase().contains("PlayTime_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(13))){
                position = Integer.parseInt(params.substring(13));
                DBUser user = dbUsersManager.getTopPlayerAtPosition(position);

                return user != null ? Utils.ticksToFormattedPlaytime(user.getPlaytime()) : showErrors ? "Error: wrong top position?" : defaultErrorMessage;
            }
        }

        if(params.toLowerCase().contains("PlayTime_".toLowerCase())) {
            String nickname = params.substring(9);
            DBUser user = dbUsersManager.getUserFromNickname(nickname);

            return user != null ? Utils.ticksToFormattedPlaytime(user.getPlaytime()) : showErrors ? "Error: wrong nickname?" : defaultErrorMessage;
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
                    return showErrors ? "Error: wrong top position?" : defaultErrorMessage;
                else if(user.getLastSeen() == null)
                    return showErrors ? "Error: last seen data missing" : defaultErrorMessage;

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getDateTimeFormat());
                return user.getLastSeen().format(formatter);
            }
        }

        if(params.toLowerCase().contains("Lastseen_".toLowerCase())) {
            String nickname = params.substring(9);
            DBUser user = dbUsersManager.getUserFromNickname(nickname);

            if(user == null)
                return showErrors ? "Error: wrong nickname?" : defaultErrorMessage;
            else if(user.getLastSeen() == null)
                return showErrors ? "Error: last seen data missing" : defaultErrorMessage;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getDateTimeFormat());
            return user.getLastSeen().format(formatter);
        }

        return null;
    }

    public boolean isStringInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
