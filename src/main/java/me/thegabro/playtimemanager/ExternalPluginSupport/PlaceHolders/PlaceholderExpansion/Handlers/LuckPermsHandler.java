package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Handlers;

import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms.LuckPermsManager;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.PlaceholderUtils;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import org.bukkit.OfflinePlayer;

import java.util.concurrent.ExecutionException;

public class LuckPermsHandler implements PlaceholderHandler {

    private final LuckPermsManager luckPermsManager;
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final PlaceholderUtils utils;

    public LuckPermsHandler(LuckPermsManager luckPermsManager,
                            PlaceholderUtils utils) {
        this.luckPermsManager = luckPermsManager;
        this.utils = utils;
    }

    @Override
    public boolean canHandle(String params) {
        return params.toLowerCase().startsWith("lp_prefix_top_");
    }

    @Override
    public String handle(String params, OfflinePlayer player, PlaytimeFormat format) {
        return handleLPPrefixTop(params.substring(14));
    }

    private String handleLPPrefixTop(String posStr) {
        if (!utils.isInt(posStr)) return utils.error("wrong top position?");

        DBUser user = dbUsersManager.getTopPlayerAtPosition(Integer.parseInt(posStr));
        if (user == null) return utils.error("wrong top position?");

        if (luckPermsManager == null) return utils.error("luckperms not loaded");

        try {
            return luckPermsManager.getPrefixAsync(user.getUuid()).get();
        } catch (InterruptedException | ExecutionException e) {
            return utils.error("luckperms retrieve unsuccessful");
        }
    }
}