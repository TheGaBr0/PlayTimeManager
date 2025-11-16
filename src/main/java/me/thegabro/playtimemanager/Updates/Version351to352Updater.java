package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Database.SQLiteDatabase;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Version351to352Updater {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final GUIsConfiguration guIsConfiguration = GUIsConfiguration.getInstance();
    private final DatabaseHandler database = DatabaseHandler.getInstance();

    // Placeholder mapping from old format to new format
    private static final Map<String, String> PLACEHOLDER_MAPPINGS = new HashMap<>();

    static {
        PLACEHOLDER_MAPPINGS.put("{current_page}", "%CURRENT_PAGE%");
        PLACEHOLDER_MAPPINGS.put("{total_pages}", "%TOTAL_PAGES%");
        PLACEHOLDER_MAPPINGS.put("{required_joins}", "%REQUIRED_JOINS%");
        PLACEHOLDER_MAPPINGS.put("{current_streak}", "%CURRENT_STREAK%");
        PLACEHOLDER_MAPPINGS.put("{count}", "%COUNT%");
        PLACEHOLDER_MAPPINGS.put("{reward_description}", "%REWARD_DESCRIPTION%");
        PLACEHOLDER_MAPPINGS.put("{description}", "%DESCRIPTION%");
        PLACEHOLDER_MAPPINGS.put("{color}", "%JOIN_STREAK_COLOR%");
    }

    public Version351to352Updater(){}

    public void performUpgrade() {
        addAFKPlaytimeColumn();
        recreateConfigFile();
        updatePlaceholders();
    }

    public void addAFKPlaytimeColumn(){
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);

            try (Statement s = connection.createStatement()) {
                s.executeUpdate("ALTER TABLE play_time ADD COLUMN afk_playtime BIGINT NOT NULL DEFAULT 0;");

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to alter table: " + e.getMessage());
        }
    }

    public void recreateConfigFile(){
        guIsConfiguration.initialize(plugin);
        guIsConfiguration.updateConfig();
        plugin.getConfiguration().updateConfig(true);
    }

    /**
     * Updates all placeholders in the configuration from old format to new format
     */
    private void updatePlaceholders() {
        try {
            // Create a backup before making changes
            Map<String, Object> backup = guIsConfiguration.createConfigBackup();

            // Process all configuration keys
            for (String key : backup.keySet()) {
                Object value = backup.get(key);

                if (value instanceof String stringValue) {
                    String updatedValue = updatePlaceholdersInString(stringValue);

                    // Only update if the value actually changed
                    if (!stringValue.equals(updatedValue)) {
                        guIsConfiguration.set(key, updatedValue);
                    }

                } else if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> listValue = (List<Object>) value;
                    List<Object> updatedList = updatePlaceholdersInList(listValue);

                    // Only update if the list actually changed
                    if (!listValue.equals(updatedList)) {
                        guIsConfiguration.set(key, updatedList);
                    }
                }
            }

            plugin.getLogger().info("Successfully updated placeholders to new format");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update placeholders: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Updates placeholders in a single string
     */
    private String updatePlaceholdersInString(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = input;

        // Replace all mapped placeholders
        for (Map.Entry<String, String> mapping : PLACEHOLDER_MAPPINGS.entrySet()) {
            result = result.replace(mapping.getKey(), mapping.getValue());
        }

        return result;
    }

    /**
     * Updates placeholders in a list (handles string lists)
     */
    private List<Object> updatePlaceholdersInList(List<Object> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }

        List<Object> updatedList = new java.util.ArrayList<>();

        for (Object item : list) {
            if (item instanceof String) {
                updatedList.add(updatePlaceholdersInString((String) item));
            } else {
                updatedList.add(item);
            }
        }

        return updatedList;
    }


}