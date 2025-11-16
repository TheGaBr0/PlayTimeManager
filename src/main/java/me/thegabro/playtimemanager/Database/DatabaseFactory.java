package me.thegabro.playtimemanager.Database;

import me.thegabro.playtimemanager.PlayTimeManager;

public class DatabaseFactory {
    private static final PlayTimeManager plugin = PlayTimeManager.getInstance();

    /**
     * Create a database instance based on configuration
     * @return Database implementation
     */
    public static Database createDatabase() {
        //to extend with more different databases support in the feature...
        return new SQLiteDatabase();

    }
}
