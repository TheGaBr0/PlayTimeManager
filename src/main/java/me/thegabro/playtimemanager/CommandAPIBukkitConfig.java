package me.thegabro.playtimemanager;

import dev.jorel.commandapi.CommandAPIConfig;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandAPIBukkitConfig extends CommandAPIConfig {
    CommandAPIBukkitConfig(JavaPlugin plugin) {

    }

    CommandAPIBukkitConfig shouldHookPaperReload(boolean hooked) // Whether the CommandAPI should hook into the Paper-exclusive ServerResourcesReloadedEvent
    {
        return null;
    }

    CommandAPIBukkitConfig skipReloadDatapacks(boolean skip) // Whether the CommandAPI should reload datapacks on server load
    {
        return null;
    }

    @Override
    public CommandAPIConfig usePluginNamespace() {
        return null;
    }

    @Override
    public Object instance() {
        return null;
    }
}
