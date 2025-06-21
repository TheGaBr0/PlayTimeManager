package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.Commands.PlayTimeCommandManager.PlayTimeCommandManager;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.util.ArrayList;
import java.util.List;

public class CommandsManager {
    private final PlayTimeManager plugin;
    private final List<CommandRegistrar> commandRegistrars;

    public CommandsManager(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.commandRegistrars = new ArrayList<>();
        initializeCommands();
    }

    private void initializeCommands() {
        commandRegistrars.add(new PlayTimeCommandManager());
        commandRegistrars.add(new PlayTimeReset());
        commandRegistrars.add(new PlaytimeTop());
        commandRegistrars.add(new PlaytimePercentage());
        commandRegistrars.add(new ClaimRewards());
        commandRegistrars.add(new PlaytimeAverage());
    }

    public void registerAllCommands() {
        for (CommandRegistrar registrar : commandRegistrars) {
            try {
                registrar.registerCommands();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to register commands for " +
                        registrar.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }


}
