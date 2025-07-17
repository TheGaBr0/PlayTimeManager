package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.SQLite;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;

import org.bukkit.configuration.file.YamlConfiguration;

public class Version304To31Updater {
    private final PlayTimeManager plugin;
    private final SQLite database;
    private GoalsManager goalsManager;

    public Version304To31Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.database = (SQLite) plugin.getDatabase();
        this.goalsManager = GoalsManager.getInstance();
        goalsManager.initialize(plugin);

    }

    public void performUpgrade() {
        performDatabaseMigration();
        recreateConfigFile();
        migrateUserGoalData();
    }

    private void performDatabaseMigration() {
        try (Connection connection = database.getSQLConnection()) {

            try (Statement s = connection.createStatement()) {
                s.executeUpdate("ALTER TABLE play_time ADD COLUMN completed_goals TEXT DEFAULT ''");
            } catch (SQLException e) {
                plugin.getLogger().severe("Database migration failed: " + e.getMessage());
            }

            // Migrate groups table data
            convertGroupsToGoals(connection);
        } catch (SQLException e) {
            plugin.getLogger().severe("Database migration failed: " + e.getMessage());
        }
    }

    private void convertGroupsToGoals(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM groups")) {

            while (rs.next()) {
                String groupName = rs.getString("group_name");
                long requiredTime = rs.getLong("playtime_required");

                // Create goals directory if it doesn't exist
                File goalsDir = new File(plugin.getDataFolder(), "goals");
                if (!goalsDir.exists()) {
                    goalsDir.mkdirs();
                }

                // Create config file
                File goalFile = new File(goalsDir, groupName + ".yml");

                // Create YAML configuration
                YamlConfiguration config = new YamlConfiguration();

                // Set header with comments
                String header =
                        "GUIDE OF AVAILABLE OPTIONS:\n" +
                                "---------------------------\n" +
                                "goal-sound is played to a player if it reaches the time specified in this config.\n" +
                                "A list of available sounds can be found here: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html\n" +
                                "---------------------------\n" +
                                "goal-message is showed to a player if it reaches the time specified in this config.\n" +
                                "Available placeholders: %TIME_REQUIRED%, %PLAYER_NAME%\n" +
                                "---------------------------\n" +
                                "active determines whether this goal is enabled and being checked by the plugin\n" +
                                "Set to 'true' to enable the goal and track player progress\n" +
                                "Set to 'false' (default option) to disable the goal without deleting it\n" +
                                "This is useful for:\n" +
                                "* Temporarily disabling goals without removing them\n" +
                                "* Testing new goals before making them live\n" +
                                "* Managing seasonal or event-specific goals\n" +
                                "---------------------------\n" +
                                "permissions defines what permissions will be granted to a player when they reach this goal\n" +
                                "You can specify multiple permissions and groups that will all be granted. The plugin will assume that\n" +
                                "the group has already been created using the permissions manager plugin specified in the main config.\n" +
                                "---------------------------\n" +
                                "commands defines a list of commands that will be executed when a player reaches this goal\n" +
                                "Available placeholders: %PLAYER_NAME%\n" +
                                "Example commands:\n" +
                                "- '/give %PLAYER_NAME% diamond 64'\n" +
                                "- '/broadcast %PLAYER_NAME% has reached an amazing milestone!'";

                config.options().header(header);

                // Set config values
                config.set("time", requiredTime);
                config.set("goal-sound", "ENTITY_PLAYER_LEVELUP");
                config.set("goal-message", "[§6PlayTime§eManager§f]§7 Congratulations §e%PLAYER_NAME%§7 you have reached §6%TIME_REQUIRED%§7 of playtime!");
                config.set("active", true);

                // Set permissions as a list
                List<String> permissions = new ArrayList<>();
                permissions.add("group." + groupName);
                config.set("permissions", permissions);

                // Empty commands list
                config.set("commands", new ArrayList<String>());

                // Save config to file
                try {
                    config.save(goalFile);
                    plugin.getLogger().info("Created goal config for former group: " + groupName);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to save goal config for " + groupName + ": " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().info("Error while migrating groups: " + e);
        }

        // Drop groups table
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS groups");
        }
    }

    private void recreateConfigFile() {
        Configuration.getInstance().updateConfig(true);
    }

    private void migrateUserGoalData() {
        // Map to store user data: UUID -> [playtime, completed goals list]
        Map<String, Object[]> userData = new HashMap<>();

        // Get all users from database
        String selectUsersQuery = "SELECT uuid, playtime, completed_goals FROM play_time";
        try (Connection conn = database.getSQLConnection();
             PreparedStatement stmt = conn.prepareStatement(selectUsersQuery);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                long userPlaytime = rs.getLong("playtime");
                String completedGoalsStr = rs.getString("completed_goals");

                // Parse existing completed goals
                ArrayList<String> completedGoals = new ArrayList<>();
                if (completedGoalsStr != null && !completedGoalsStr.isEmpty()) {
                    completedGoals.addAll(Arrays.asList(completedGoalsStr.split(",")));
                }

                // Store user data in the map
                userData.put(uuid, new Object[]{userPlaytime, completedGoals});
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error retrieving user data: " + e.getMessage());
            return;
        }

        // Process each goal file
        File goalsDirectory = new File(plugin.getDataFolder(), "goals");
        if (!goalsDirectory.exists() || !goalsDirectory.isDirectory()) {
            plugin.getLogger().warning("Goals directory not found, skipping user goal data migration");
            return;
        }

        File[] goalFiles = goalsDirectory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (goalFiles == null || goalFiles.length == 0) {
            plugin.getLogger().warning("No goal files found, skipping user goal data migration");
            return;
        }

        // For each goal, check all users
        for (File goalFile : goalFiles) {
            YamlConfiguration goalConfig = YamlConfiguration.loadConfiguration(goalFile);
            String goalName = goalFile.getName().replace(".yml", "");
            long goalTime = goalConfig.getLong("time", 0);

            // Check each user against this goal
            for (Map.Entry<String, Object[]> entry : userData.entrySet()) {
                String uuid = entry.getKey();
                long userPlaytime = (long) entry.getValue()[0];
                @SuppressWarnings("unchecked")
                ArrayList<String> completedGoals = (ArrayList<String>) entry.getValue()[1];

                // Add goal if user meets the time requirement and hasn't already completed it
                if (userPlaytime >= goalTime && !completedGoals.contains(goalName)) {
                    completedGoals.add(goalName);
                }
            }
        }

        // Update database for all users with modified goal lists
        for (Map.Entry<String, Object[]> entry : userData.entrySet()) {
            String uuid = entry.getKey();
            @SuppressWarnings("unchecked")
            ArrayList<String> completedGoals = (ArrayList<String>) entry.getValue()[1];

            database.updateCompletedGoals(uuid, completedGoals);
        }
    }

}