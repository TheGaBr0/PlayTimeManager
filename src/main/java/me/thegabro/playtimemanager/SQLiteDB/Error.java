package me.thegabro.playtimemanager.SQLiteDB;

import me.thegabro.playtimemanager.PlayTimeManager;

import java.util.logging.Level;

public class Error {
    public static void execute(PlayTimeManager plugin, Exception ex){
        plugin.getLogger().log(Level.SEVERE, "Couldn't execute MySQL statement: ", ex);
    }
    public static void close(PlayTimeManager plugin, Exception ex){
        plugin.getLogger().log(Level.SEVERE, "Failed to close MySQL connection: ", ex);
    }
}
