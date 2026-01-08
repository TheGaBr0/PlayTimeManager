package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.sql.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Version354to36Updater {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final GUIsConfiguration guIsConfiguration = GUIsConfiguration.getInstance();
    private final CommandsConfiguration commandsConfiguration = CommandsConfiguration.getInstance();
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    private long goals_old_check_schedule;
    public Version354to36Updater() {}

    public void performUpgrade() {
        recreateConfigFile();
        migrateRewardData();
        migrateGoalData();
        removeOldColumns();
        updateGoalData();
        migrateTimestampsToInstant();
    }


    private void recreateConfigFile() {
        Configuration config = Configuration.getInstance();

        goals_old_check_schedule = config.getLong("goal-check-rate", 900L);

        String prefix = config.getString("prefix", "[&6PlayTime&eManager&f]&7 ");

        config.updateConfig(true);

        commandsConfiguration.initialize(plugin);

        String playtime_self = commandsConfiguration.getString("playtime-self-message");
        String playtime_others = commandsConfiguration.getString("playtime-others-message");

        commandsConfiguration.updateConfig();

        guIsConfiguration.initialize(plugin);
        guIsConfiguration.updateConfig();

        commandsConfiguration.set("prefix", prefix);
        commandsConfiguration.set("playtime.self-message", playtime_self.replace("[§6PlayTime§eManager§f]§7 ", "&7").replace("§", "&"));
        commandsConfiguration.set("playtime.others-message", playtime_others.replace("[§6PlayTime§eManager§f]§7 ", "&7").replace("§", "&"));
        guIsConfiguration.set("player-stats-gui.goals-settings.list-format", "&7• &e%GOAL% &7(&e%GOAL_COMPLETED_TIMES%)");
    }

    private void migrateRewardData() {
        plugin.getLogger().info("Starting reward data migration from version 3.5.4 to 3.6...");

        try (Connection connection = DatabaseHandler.getInstance().getConnection()) {
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
        plugin.getLogger().info("Starting goal data migration from version 3.5.4 to 3.6...");

        try (Connection connection = DatabaseHandler.getInstance().getConnection()) {
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
            String insertQuery = "INSERT INTO completed_goals (goal_name, user_uuid, nickname, completed_at, received, received_at) " +
                    "VALUES (?, ?, ?, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP)";
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
        try (Connection connection = DatabaseHandler.getInstance().getConnection()) {
            // Clean up in case a failed migration left a stale table
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

        updatePlaytimeFormatsData(playtimeFormatsConfiguration);

        goalsManager.initialize(plugin);
        goalsManager.goalsUpdater();

        goalsManager.clearGoals();
        goalsManager.loadGoals();
        for(Goal g : goalsManager.getGoals()){
            g.setCheckTime(String.valueOf(goals_old_check_schedule));
        }

        GoalsManager.resetInstance();
    }

    public void updatePlaytimeFormatsData(PlaytimeFormatsConfiguration playtimeFormatsConfiguration){
        Map<String, Object> newFields = new HashMap<>();
        newFields.put("distribute-removed-time", false);

        playtimeFormatsConfiguration.formatsUpdater(newFields);
    }

    private void migrateTimestampsToInstant() {
        plugin.getLogger().info("Starting timestamp migration to Instant format (DATETIME -> BIGINT)...");

        try (Connection connection = DatabaseHandler.getInstance().getConnection()) {

            connection.setAutoCommit(false);

            try {
                plugin.getLogger().info("Creating new table with BIGINT timestamp columns...");

                // Step 1: Create new table with BIGINT columns
                PreparedStatement createNewTableStmt = connection.prepareStatement(
                        "CREATE TABLE play_time_migrated (" +
                                "uuid VARCHAR(32) NOT NULL UNIQUE, " +
                                "nickname VARCHAR(32) NOT NULL UNIQUE, " +
                                "playtime BIGINT NOT NULL, " +
                                "artificial_playtime BIGINT NOT NULL, " +
                                "afk_playtime BIGINT NOT NULL, " +
                                "last_seen BIGINT DEFAULT NULL, " +
                                "first_join BIGINT DEFAULT NULL, " +
                                "relative_join_streak INT DEFAULT 0, " +
                                "absolute_join_streak INT DEFAULT 0, " +
                                "PRIMARY KEY (uuid)" +
                                ")"
                );
                createNewTableStmt.executeUpdate();
                createNewTableStmt.close();

                // Step 2: Read old data and convert with proper timezone handling
                plugin.getLogger().info("Converting and copying timestamp data with timezone consideration...");

                PreparedStatement selectStmt = connection.prepareStatement(
                        "SELECT uuid, nickname, playtime, artificial_playtime, afk_playtime, " +
                                "last_seen, first_join, relative_join_streak, absolute_join_streak " +
                                "FROM play_time"
                );

                PreparedStatement insertStmt = connection.prepareStatement(
                        "INSERT INTO play_time_migrated " +
                                "(uuid, nickname, playtime, artificial_playtime, afk_playtime, " +
                                "last_seen, first_join, relative_join_streak, absolute_join_streak) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                );

                ResultSet rs = selectStmt.executeQuery();
                int rowsCopied = 0;

                while (rs.next()) {
                    insertStmt.setString(1, rs.getString("uuid"));
                    insertStmt.setString(2, rs.getString("nickname"));
                    insertStmt.setLong(3, rs.getLong("playtime"));
                    insertStmt.setLong(4, rs.getLong("artificial_playtime"));
                    insertStmt.setLong(5, rs.getLong("afk_playtime"));

                    // Convert timestamps: read as Timestamp, convert to Instant epoch millis
                    Timestamp lastSeenTs = rs.getTimestamp("last_seen");
                    if (lastSeenTs != null && !rs.wasNull()) {
                        insertStmt.setLong(6, lastSeenTs.getTime()); // getTime() returns epoch millis
                    } else {
                        insertStmt.setNull(6, Types.BIGINT);
                    }

                    Timestamp firstJoinTs = rs.getTimestamp("first_join");
                    if (firstJoinTs != null && !rs.wasNull()) {
                        insertStmt.setLong(7, firstJoinTs.getTime());
                    } else {
                        insertStmt.setNull(7, Types.BIGINT);
                    }

                    insertStmt.setInt(8, rs.getInt("relative_join_streak"));
                    insertStmt.setInt(9, rs.getInt("absolute_join_streak"));

                    insertStmt.executeUpdate();
                    rowsCopied++;
                }

                rs.close();
                selectStmt.close();
                insertStmt.close();

                // Step 3: Drop old table
                plugin.getLogger().info("Removing old table...");
                PreparedStatement dropOldTableStmt = connection.prepareStatement("DROP TABLE play_time");
                dropOldTableStmt.executeUpdate();
                dropOldTableStmt.close();

                // Step 4: Rename new table
                plugin.getLogger().info("Finalizing migration...");
                PreparedStatement renameTableStmt = connection.prepareStatement(
                        "ALTER TABLE play_time_migrated RENAME TO play_time"
                );
                renameTableStmt.executeUpdate();
                renameTableStmt.close();

                // Commit transaction
                connection.commit();

                plugin.getLogger().info(String.format(
                        "Timestamp migration completed successfully! Migrated %d player records to BIGINT format",
                        rowsCopied
                ));

            } catch (SQLException e) {
                connection.rollback();
                plugin.getLogger().severe("Error during timestamp migration, rolled back changes: " + e.getMessage());
                e.printStackTrace();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Fatal error during timestamp migration: " + e.getMessage());
            e.printStackTrace();
        }
    }

}