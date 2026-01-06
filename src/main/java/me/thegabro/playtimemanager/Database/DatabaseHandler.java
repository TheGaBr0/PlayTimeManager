package me.thegabro.playtimemanager.Database;

import me.thegabro.playtimemanager.Database.DAOs.GoalsDAO;
import me.thegabro.playtimemanager.Database.DAOs.JoinstreakDAO;
import me.thegabro.playtimemanager.Database.DAOs.PlayerDAO;
import me.thegabro.playtimemanager.Database.DAOs.StatisticsDAO;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseHandler {
    private static DatabaseHandler instance;
    private final Database database;

    public static synchronized DatabaseHandler getInstance() {
        if (instance == null) {
            instance = new DatabaseHandler();
        }
        return instance;
    }

    private final PlayerDAO playerDAO;
    private final GoalsDAO goalsDAO;
    private final JoinstreakDAO streakDAO;
    private final StatisticsDAO statisticsDAO;

    private DatabaseHandler() {

        this.database = DatabaseFactory.createDatabase();

        // If we reach here, database was created successfully
        // Now initialize it (this will create tables, etc.)
        this.database.initialize();

        this.playerDAO = new PlayerDAO(this);
        this.goalsDAO = new GoalsDAO(this);
        this.streakDAO = new JoinstreakDAO(this);
        this.statisticsDAO = new StatisticsDAO(this);
    }

    public Connection getConnection() throws SQLException {
        return database.getConnection();
    }

    public Database.DBTYPES getDatabaseType(){
        return database.getDatabaseType();
    }

    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    public void close() {
        database.close();
    }

    public PlayerDAO getPlayerDAO() {
        return playerDAO;
    }

    public GoalsDAO getGoalsDAO() {
        return goalsDAO;
    }

    public JoinstreakDAO getStreakDAO() {
        return streakDAO;
    }

    public StatisticsDAO getStatisticsDAO() {
        return statisticsDAO;
    }
}