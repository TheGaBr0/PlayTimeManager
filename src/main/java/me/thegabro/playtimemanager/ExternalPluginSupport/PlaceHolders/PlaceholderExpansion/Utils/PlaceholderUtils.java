package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils;

import me.thegabro.playtimemanager.PlayTimeManager;

public class PlaceholderUtils {

    private final PlayTimeManager plugin;

    public PlaceholderUtils(PlayTimeManager plugin) {
        this.plugin = plugin;
    }

    public String error(String error) {
        return plugin.getConfiguration().getBoolean("placeholders.enable-errors", false)
                ? "Error: " + error
                : plugin.getConfiguration().getString("placeholders.default-message", "No data");
    }

    public boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}