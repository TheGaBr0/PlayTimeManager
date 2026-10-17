package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Database.Database;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Database.JournalTables;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class Version366to400Updater {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public Version366to400Updater() {}

    public void performUpgrade() {
        createJournalTables();
        recreateConfigFile();
    }

    /**
     * Adds the playtime journal tables (playtime_journal, afk_journal, vanish_journal).
     * Already-existing tables are left untouched, so this is safe to run on a database that
     * already has them.
     */
    private void createJournalTables() {
        Database.DBTYPES type = DatabaseHandler.getInstance().getDatabaseType();

        try (Connection conn = DatabaseHandler.getInstance().getConnection();
             Statement s = conn.createStatement()) {

            for (String statement : JournalTables.createStatements(type)) {
                s.executeUpdate(statement);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create the playtime journal tables", e);
            throw new RuntimeException("Failed to create the playtime journal tables", e);
        }
    }

    private void recreateConfigFile() {
        Configuration.getInstance().updateConfig(false);
    }
}
