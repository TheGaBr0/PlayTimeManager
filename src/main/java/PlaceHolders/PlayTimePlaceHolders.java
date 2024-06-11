package PlaceHolders;
import me.thegabro.playtimemanager.PlayTimeManager;import UsersDatabases.User;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
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
        return "TheGaBr0_";
    }

    @Override
    public @NotNull String getVersion() {
        return "2.0.0";
    }

    @Override
    public boolean persist() {
        return true; // This is required or else PlaceholderAPI will unregister the Expansion on reload
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if(params.equalsIgnoreCase("PlayTime")){
            return String.valueOf(plugin.getPlayTimeDB().convertTime(plugin.getUsersManager().getUserByNickname(player.getName()).getPlayTime()/20));
        }

        if(params.toLowerCase().contains("PlayTime_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(13))){
                position = Integer.parseInt(params.substring(13));
                User user = plugin.getDbDataCombiner().getPlayerAtPosition(position);
                return plugin.getPlayTimeDB().convertTime(user.getPlayTime()/20);
            }
        }

        if(params.toLowerCase().contains("Nickname_Top_".toLowerCase())) {
            int position;
            if(isStringInt(params.substring(13))){
                position = Integer.parseInt(params.substring(13));
                User user = plugin.getDbDataCombiner().getPlayerAtPosition(position);
                return user.getName();
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
