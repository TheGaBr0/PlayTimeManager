package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Version354to355Updater {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final GUIsConfiguration guIsConfiguration = GUIsConfiguration.getInstance();
    private final CommandsConfiguration commandsConfiguration = CommandsConfiguration.getInstance();

    public Version354to355Updater() {}

    public void performUpgrade() {
        recreateConfigFile();
        migrateRewardData();
        migrateGoalData();
        removeOldColumns();
        updateGoalData();
    }

    private void recreateConfigFile() {
        Configuration.getInstance().updateConfig(false);
        commandsConfiguration.initialize(plugin);
        commandsConfiguration.updateConfig();
        guIsConfiguration.initialize(plugin);
        guIsConfiguration.updateConfig();


        guIsConfiguration.set("player-stats-gui.goals-settings.list-format", "&7• &e%GOAL% &7(&e%GOAL_COMPLETED_TIMES%)");
    }

    private void migrateRewardData() {
        plugin.getLogger().info("Starting reward data migration from version 3.5.4 to 3.5.5...");

        try (Connection connection = database.getConnection()) {
            // First, check if the old columns exist
            if (!columnsExist(connection)) {
                plugin.getLogger().info("Old reward columns not found, migration not needed.");
                return;
            }

            // Get all players with reward data
            String selectQuery = "SELECT uuid, nickname, received_rewards, rewards_to_be_claimed FROM play_time " +
                    "WHERE (received_rewards IS NOT NULL AND received_rewards != '') " +
                    "OR (rewards_to_be_claimed IS NOT NULL AND rewards_to_be_claimed != '')";

            PreparedStatement selectStmt = connection.prepareStatement(selectQuery);
            ResultSet rs = selectStmt.executeQuery();

            int migratedPlayers = 0;
            int totalReceivedRewards = 0;
            int totalClaimableRewards = 0;

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String nickname = rs.getString("nickname");
                String receivedRewards = rs.getString("received_rewards");
                String rewardsToBeClaimedStr = rs.getString("rewards_to_be_claimed");

                // Migrate received rewards
                if (receivedRewards != null && !receivedRewards.trim().isEmpty()) {
                    List<String> rewardValues = parseRewardString(receivedRewards);
                    totalReceivedRewards += insertReceivedRewards(connection, uuid, nickname, rewardValues);
                }

                // Migrate rewards to be claimed
                if (rewardsToBeClaimedStr != null && !rewardsToBeClaimedStr.trim().isEmpty()) {
                    List<String> rewardValues = parseRewardString(rewardsToBeClaimedStr);
                    totalClaimableRewards += insertRewardsToBeClaimed(connection, uuid, nickname, rewardValues);
                }

                migratedPlayers++;
            }

            rs.close();
            selectStmt.close();

            if (migratedPlayers > 0) {
                plugin.getLogger().info(String.format(
                        "Migration completed successfully! Migrated %d players, %d received rewards, %d claimable rewards",
                        migratedPlayers, totalReceivedRewards, totalClaimableRewards
                ));
            } else {
                plugin.getLogger().info("No reward data found to migrate.");
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Error during reward data migration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void migrateGoalData() {
        plugin.getLogger().info("Starting goal data migration from version 3.5.4 to 3.5.5...");

        try (Connection connection = database.getConnection()) {
            // Get all players with completed goals
            String selectQuery = "SELECT uuid, nickname, completed_goals FROM play_time " +
                    "WHERE completed_goals IS NOT NULL AND completed_goals != ''";

            PreparedStatement selectStmt = connection.prepareStatement(selectQuery);
            ResultSet rs = selectStmt.executeQuery();

            int migratedPlayers = 0;
            int totalGoals = 0;

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String nickname = rs.getString("nickname");
                String completedGoals = rs.getString("completed_goals");

                if (completedGoals != null && !completedGoals.trim().isEmpty()) {
                    List<String> goalNames = parseGoalString(completedGoals);
                    int insertedGoals = insertCompletedGoals(connection, uuid, nickname, goalNames);
                    totalGoals += insertedGoals;

                    if (insertedGoals > 0) {
                        migratedPlayers++;
                    }
                }
            }

            rs.close();
            selectStmt.close();

            plugin.getLogger().info(String.format(
                    "Goal migration completed! Migrated %d players with %d total completed goals",
                    migratedPlayers, totalGoals
            ));

        } catch (SQLException e) {
            plugin.getLogger().severe("Error during goal data migration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean columnsExist(Connection connection) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "SELECT received_rewards, rewards_to_be_claimed FROM play_time LIMIT 1"
            );
            stmt.executeQuery();
            stmt.close();
            return true;
        } catch (SQLException e) {
            // Columns don't exist if query fails
            return false;
        }
    }

    private List<String> parseRewardString(String rewardStr) {
        if (rewardStr == null || rewardStr.trim().isEmpty()) {
            return List.of();
        }

        // Split by comma and clean up each reward value
        return Arrays.stream(rewardStr.split(","))
                .map(String::trim)
                .filter(reward -> !reward.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> parseGoalString(String goalStr) {
        if (goalStr == null || goalStr.trim().isEmpty()) {
            return List.of();
        }

        // Split by comma and clean up each goal name
        return Arrays.stream(goalStr.split(","))
                .map(String::trim)
                .filter(goal -> !goal.isEmpty())
                .collect(Collectors.toList());
    }

    private int insertReceivedRewards(Connection connection, String uuid, String nickname, List<String> rewardValues) {
        if (rewardValues.isEmpty()) {
            return 0;
        }

        try {
            String insertQuery = "INSERT INTO received_rewards (user_uuid, nickname, main_instance_ID, required_joins, received_at) " +
                    "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
            PreparedStatement stmt = connection.prepareStatement(insertQuery);

            for (String rewardValue : rewardValues) {
                // Parse reward string: format -> main_instance_ID.required_joins
                String[] parts = rewardValue.split("\\.");
                if (parts.length != 2) {
                    plugin.getLogger().warning("Invalid reward format for UUID " + uuid + ": " + rewardValue);
                    continue;
                }

                int mainInstanceId;
                int requiredJoins;
                try {
                    mainInstanceId = Integer.parseInt(parts[0]);
                    requiredJoins = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid numeric values in reward for UUID " + uuid + ": " + rewardValue);
                    continue;
                }

                stmt.setString(1, uuid);
                stmt.setString(2, nickname);
                stmt.setInt(3, mainInstanceId);
                stmt.setInt(4, requiredJoins);
                stmt.addBatch();
            }

            int[] results = stmt.executeBatch();
            stmt.close();

            return results.length;

        } catch (SQLException e) {
            plugin.getLogger().warning("Error inserting received rewards for UUID " + uuid + ": " + e.getMessage());
            return 0;
        }
    }

    private int insertRewardsToBeClaimed(Connection connection, String uuid, String nickname, List<String> rewardValues) {
        if (rewardValues.isEmpty()) {
            return 0;
        }

        try {
            String insertQuery = "INSERT INTO rewards_to_be_claimed (user_uuid, nickname, main_instance_ID, required_joins, created_at, expired) " +
                    "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertQuery);

            for (String rewardValue : rewardValues) {
                boolean notClaimedInTime = false;

                // Handle `.R` suffix
                if (rewardValue.endsWith(".R")) {
                    rewardValue = rewardValue.substring(0, rewardValue.length() - 2);
                    notClaimedInTime = true;
                }

                // Parse reward string
                String[] parts = rewardValue.split("\\.");
                if (parts.length != 2) {
                    plugin.getLogger().warning("Invalid reward format for UUID " + uuid + ": " + rewardValue);
                    continue;
                }

                int mainInstanceId;
                int requiredJoins;
                try {
                    mainInstanceId = Integer.parseInt(parts[0]);
                    requiredJoins = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid numeric values in reward for UUID " + uuid + ": " + rewardValue);
                    continue;
                }

                stmt.setString(1, uuid);
                stmt.setString(2, nickname);
                stmt.setInt(3, mainInstanceId);
                stmt.setInt(4, requiredJoins);
                stmt.setBoolean(5, notClaimedInTime);
                stmt.addBatch();
            }

            int[] results = stmt.executeBatch();
            stmt.close();

            return results.length;

        } catch (SQLException e) {
            plugin.getLogger().warning("Error inserting claimable rewards for UUID " + uuid + ": " + e.getMessage());
            return 0;
        }
    }

    private int insertCompletedGoals(Connection connection, String uuid, String nickname, List<String> goalNames) {
        if (goalNames.isEmpty()) {
            return 0;
        }

        try {
            String insertQuery = "INSERT INTO completed_goals (goal_name, user_uuid, nickname, received_at) " +
                    "VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
            PreparedStatement stmt = connection.prepareStatement(insertQuery);

            for (String goalName : goalNames) {
                stmt.setString(1, goalName);
                stmt.setString(2, uuid);
                stmt.setString(3, nickname);
                stmt.addBatch();
            }

            int[] results = stmt.executeBatch();
            stmt.close();

            return results.length;

        } catch (SQLException e) {
            plugin.getLogger().warning("Error inserting completed goals for UUID " + uuid + ": " + e.getMessage());
            return 0;
        }
    }

    private void removeOldColumns() {
        try {
            // Clean up in case a failed migration left a stale table
            Connection connection = database.getConnection();
            PreparedStatement dropNewIfExists = connection.prepareStatement("DROP TABLE IF EXISTS play_time_new");
            dropNewIfExists.executeUpdate();
            dropNewIfExists.close();

            String createNewTableQuery = "CREATE TABLE play_time_new (" +
                    "uuid VARCHAR(32) NOT NULL UNIQUE," +
                    "nickname VARCHAR(32) NOT NULL UNIQUE," +
                    "playtime BIGINT NOT NULL," +
                    "artificial_playtime BIGINT NOT NULL," +
                    "afk_playtime BIGINT NOT NULL," +
                    "last_seen DATETIME DEFAULT NULL," +
                    "first_join DATETIME DEFAULT NULL," +
                    "relative_join_streak INT DEFAULT 0," +
                    "absolute_join_streak INT DEFAULT 0," +
                    "PRIMARY KEY (uuid)" +
                    ");";

            PreparedStatement createStmt = connection.prepareStatement(createNewTableQuery);
            createStmt.executeUpdate();
            createStmt.close();

            String copyDataQuery = "INSERT INTO play_time_new " +
                    "(uuid, nickname, playtime, artificial_playtime, afk_playtime, " +
                    "last_seen, first_join, relative_join_streak, absolute_join_streak) " +
                    "SELECT uuid, nickname, playtime, artificial_playtime, afk_playtime, " +
                    "last_seen, first_join, relative_join_streak, absolute_join_streak " +
                    "FROM play_time";

            PreparedStatement copyStmt = connection.prepareStatement(copyDataQuery);
            copyStmt.executeUpdate();
            copyStmt.close();

            PreparedStatement dropStmt = connection.prepareStatement("DROP TABLE play_time");
            dropStmt.executeUpdate();
            dropStmt.close();

            PreparedStatement renameStmt = connection.prepareStatement("ALTER TABLE play_time_new RENAME TO play_time");
            renameStmt.executeUpdate();
            renameStmt.close();

        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing old columns / adding new column: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateGoalData(){

        PlaytimeFormatsConfiguration playtimeFormatsConfiguration = PlaytimeFormatsConfiguration.getInstance();
        playtimeFormatsConfiguration.initialize(plugin);

        GoalsManager goalsManager = GoalsManager.getInstance();
        goalsManager.initialize(plugin);
        goalsManager.goalsUpdater();
    }
}