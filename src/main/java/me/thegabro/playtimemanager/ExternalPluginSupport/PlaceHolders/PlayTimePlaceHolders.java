package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;
import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms.LuckPermsManager;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Handlers.*;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.ParamParser;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.PlaceholderUtils;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.UserResolver;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PlayTimePlaceHolders extends PlaceholderExpansion {

    private final List<PlaceholderHandler> handlers;
    private final ParamParser parser;

    public PlayTimePlaceHolders() {
        PlayTimeManager plugin = PlayTimeManager.getInstance();
        DBUsersManager dbUsersManager = DBUsersManager.getInstance();
        PlaceholderUtils utils = new PlaceholderUtils(plugin);
        UserResolver resolver = new UserResolver(dbUsersManager);
        this.parser = new ParamParser(PlaytimeFormatsConfiguration.getInstance());

        LuckPermsManager luckPermsManager = null;
        if (plugin.isPermissionsManagerConfigured()) {
            try {
                luckPermsManager = LuckPermsManager.getInstance(plugin);
            } catch (NoClassDefFoundError e) {
                // LuckPerms not on the classpath — leave null
            }
        }

        this.handlers = List.of(
                new PlaytimeHandler(resolver, utils),
                new PlayerInfoHandler(resolver, utils),
                new LuckPermsHandler(luckPermsManager, utils),
                new GoalsHandler(resolver, utils),
                new JoinStreakRewardHandler(resolver, utils)
        );
    }

    @Override
    public @NotNull String getIdentifier() { return "PTM"; }

    @Override
    public @NotNull String getAuthor() { return "TheGabro"; }

    @Override
    public @NotNull String getVersion() { return "3.3.1"; }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) return null;

        ParamParser.ParseResult result = parser.parse(params);

        return handlers.stream()
                .filter(h -> h.canHandle(result.params()))
                .findFirst()
                .map(h -> h.handle(result.params(), player, result.format()))
                .orElse(null);
    }
}