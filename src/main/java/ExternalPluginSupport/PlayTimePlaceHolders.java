package ExternalPluginSupport;
import Users.DBUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class PlayTimePlaceHolders extends PlaceholderExpansion{

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

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
        if(params.equalsIgnoreCase("PlayTime")){
            return convertTime(plugin.getUsersManager().getOnlineUser(player.getName()).getPlaytime() / 20);
        }

        if(params.toLowerCase().contains("PlayTime_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(13))){
                position = Integer.parseInt(params.substring(13));
                DBUser user = plugin.getDatabase().getTopPlayerAtPosition(position);

                if(user == null)
                    return "Error: wrong position?";
                else
                    return convertTime(user.getPlaytime()/20);
            }
        }

        if(params.toLowerCase().contains("Nickname_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(13))){
                position = Integer.parseInt(params.substring(13));
                DBUser user = plugin.getDatabase().getTopPlayerAtPosition(position);

                if(user == null)
                    return "Error: wrong position?";
                else
                    return user.getNickname();
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
