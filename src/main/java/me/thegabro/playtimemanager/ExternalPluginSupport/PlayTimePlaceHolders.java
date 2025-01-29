package me.thegabro.playtimemanager.ExternalPluginSupport;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

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
            return Utils.ticksToFormattedPlaytime(plugin.getOnlineUsersManager().getOnlineUser(player.getName()).getPlaytime());
        }

        if(params.toLowerCase().contains("PlayTime_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(13))){
                position = Integer.parseInt(params.substring(13));
                DBUser user = plugin.getDbUsersManager().getTopPlayerAtPosition(position);

                if(user == null)
                    return "Error: wrong position?";
                else
                    return Utils.ticksToFormattedPlaytime(user.getPlaytime());
            }
        }

        if(params.toLowerCase().contains("Nickname_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(13))){
                position = Integer.parseInt(params.substring(13));
                DBUser user = plugin.getDbUsersManager().getTopPlayerAtPosition(position);

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

}
