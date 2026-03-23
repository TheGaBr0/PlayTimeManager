package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Handlers;

import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import org.bukkit.OfflinePlayer;

public interface PlaceholderHandler {
    boolean canHandle(String params);
    String handle(String cleanParams, OfflinePlayer player, PlaytimeFormat format);
}