package me.thegabro.playtimemanager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * Handles configuration for GUI elements in the PlayTimeManager plugin.
 * Provides methods to access GUI settings and manage the configuration file.
 */
public class RewardsGuiConfiguration {

    private final PlayTimeManager plugin;
    private File configFile;
    private FileConfiguration config;
    private static final String CONFIG_FILENAME = "rewardsGUI-config.yml";

    /**
     * Constructs a new GuiConfiguration instance.
     *
     * @param plugin The PlayTimeManager plugin instance
     */
    public RewardsGuiConfiguration(PlayTimeManager plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Loads the GUI configuration from file. If the file doesn't exist,
     * it will be created with default values.
     */
    private void loadConfig() {
        if (configFile == null) {
            // Create translations/gui folder path
            File guiFolder = new File(plugin.getDataFolder(), "Translations/Gui");
            if (!guiFolder.exists()) {
                guiFolder.mkdirs();
            }

            // Create the config file in the translations/gui folder
            configFile = new File(guiFolder, CONFIG_FILENAME);
        }

        if (!configFile.exists()) {
            createDefaultConfig();
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Creates the default configuration file if it doesn't exist.
     */
    private void createDefaultConfig() {
        try {
            configFile.getParentFile().mkdirs();
            InputStream defaultConfigStream = plugin.getResource(CONFIG_FILENAME);

            if (defaultConfigStream != null) {
                Files.copy(defaultConfigStream, configFile.toPath());
                defaultConfigStream.close();
            } else {
                configFile.createNewFile();
                FileConfiguration defaultConfig = new YamlConfiguration();

                // Set default values
                defaultConfig.set("gui.title", "&6Claim Your Rewards");
                defaultConfig.set("gui.border-item-name", "&f[&6P.T.M.&f]&7");

                // Pagination settings
                defaultConfig.set("pagination.page-indicator", "&e&lPage {current_page} of {total_pages}");
                defaultConfig.set("pagination.next-page.name", "&a&lNext Page →");
                defaultConfig.set("pagination.next-page.lore", "&7Click to view the next page");
                defaultConfig.set("pagination.prev-page.name", "&a&l← Previous Page");
                defaultConfig.set("pagination.prev-page.lore", "&7Click to view the previous page");
                defaultConfig.set("pagination.no-more-pages", "&c&lNo More Pages");
                defaultConfig.set("pagination.first-page", "&c&lFirst Page");

                // Filters settings
                defaultConfig.set("filters.claimed.enabled-name", "&8&l[CLAIMED] &7Rewards: &a&lON");
                defaultConfig.set("filters.claimed.disabled-name", "&8&l[CLAIMED] &7Rewards: &c&lOFF");
                defaultConfig.set("filters.claimed.lore-enabled", "&7Click to hide claimed rewards");
                defaultConfig.set("filters.claimed.lore-disabled", "&7Click to show claimed rewards");

                defaultConfig.set("filters.available.enabled-name", "&a&l[AVAILABLE] &7Rewards: &a&lON");
                defaultConfig.set("filters.available.disabled-name", "&a&l[AVAILABLE] &7Rewards: &c&lOFF");
                defaultConfig.set("filters.available.lore-enabled", "&7Click to hide available rewards");
                defaultConfig.set("filters.available.lore-disabled", "&7Click to show available rewards");

                defaultConfig.set("filters.locked.enabled-name", "&c&l[LOCKED] &7Rewards: &a&lON");
                defaultConfig.set("filters.locked.disabled-name", "&c&l[LOCKED] &7Rewards: &c&lOFF");
                defaultConfig.set("filters.locked.lore-enabled", "&7Click to hide locked rewards");
                defaultConfig.set("filters.locked.lore-disabled", "&7Click to show locked rewards");

                // Claim all button
                defaultConfig.set("claim-all.name", "&e&lClaim all");

                // No rewards message
                defaultConfig.set("no-rewards.name", "&l&cNo rewards to display!");
                defaultConfig.set("no-rewards.lore", "&7Try changing your filters");

                // Reward items
                defaultConfig.set("reward-items.available.prefix", "&a&l[CLICK TO CLAIM] ");
                defaultConfig.set("reward-items.available.lore", List.of(
                        "&aThis reward is available to claim!",
                        "&7Click to receive your reward"
                ));

                defaultConfig.set("reward-items.claimed.prefix", "&8&l[CLAIMED] ");
                defaultConfig.set("reward-items.claimed.lore", List.of(
                        "&8You've already claimed this reward"
                ));

                defaultConfig.set("reward-items.locked.prefix", "&c&l[LOCKED] ");
                defaultConfig.set("reward-items.locked.lore", List.of(
                        "&cYou haven't reached this join streak yet"
                ));

                // Info lore settings
                defaultConfig.set("reward-items.info-lore.required-joins", "&7Required Joins: &e{required_joins}");
                defaultConfig.set("reward-items.info-lore.join-streak", "&7Your current join streak: {color}{current_streak}");
                defaultConfig.set("reward-items.info-lore.join-streak-color.sufficient", "&a");
                defaultConfig.set("reward-items.info-lore.join-streak-color.insufficient", "&c");
                defaultConfig.set("reward-items.info-lore.description-separator", "");
                defaultConfig.set("reward-items.info-lore.description", "&7{description}");
                defaultConfig.set("reward-items.info-lore.reward-description-separator", "");
                defaultConfig.set("reward-items.info-lore.reward-description", "&7Reward: {reward_description}");
                defaultConfig.set("reward-items.info-lore.permissions-header-separator", "");
                defaultConfig.set("reward-items.info-lore.permissions-header", "&7&lPermissions:");
                defaultConfig.set("reward-items.info-lore.permission-format", "&7- &f{permission}");
                defaultConfig.set("reward-items.info-lore.commands-header-separator", "");
                defaultConfig.set("reward-items.info-lore.commands-header", "&7&lCommands:");
                defaultConfig.set("reward-items.info-lore.command-format", "&7- &f{command}");

                // Messages
                defaultConfig.set("messages.no-permission", "&cYou don't have permission to claim rewards!");
                defaultConfig.set("messages.not-available", "&cThis reward is not available to claim!");
                defaultConfig.set("messages.reward-not-found", "&cCouldn't find the reward details!");
                defaultConfig.set("messages.error-processing", "&cAn error occurred while processing your reward.");
                defaultConfig.set("messages.claimed-rewards", "&aClaimed {count} rewards!");
                defaultConfig.set("messages.no-available-rewards", "&cYou don't have any rewards to claim!");

                defaultConfig.save(configFile);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create default GUI configuration file: " + e.getMessage());
        }
    }

    /**
     * Reloads the configuration from file.
     */
    public void reload() {
        loadConfig();
    }

    /**
     * Saves the current configuration to file.
     */
    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save GUI configuration file: " + e.getMessage());
        }
    }

    /**
     * Sets a value in the configuration.
     *
     * @param path The configuration path
     * @param value The value to set
     */
    public void set(String path, Object value) {
        config.set(path, value);
        save();
    }

    /**
     * Returns the underlying FileConfiguration for direct manipulation.
     *
     * @return The FileConfiguration object
     */
    public FileConfiguration getConfig() {
        return config;
    }
}