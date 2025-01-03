package SQLiteDB;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import me.thegabro.playtimemanager.PlayTimeManager; // import your main class

public class SQLite extends PlayTimeDatabase {

    String dbname;
    PlayTimeManager plugin;
    public SQLite(PlayTimeManager instance){
        super(instance);
        this.plugin = instance;
        dbname = "play_time";
    }

    public String PlayTimeTable = "CREATE TABLE IF NOT EXISTS play_time (" +
            "uuid VARCHAR(32) NOT NULL," +
            "nickname VARCHAR(32) NOT NULL," +
            "playtime BIGINT NOT NULL," +
            "artificial_playtime BIGINT NOT NULL," +
            "completed_goals TEXT DEFAULT ''," +
            "PRIMARY KEY (uuid)" +
            ");";

    // SQL creation stuff, You can leave the blow stuff untouched.
    public Connection getSQLConnection() {
        if (dataSource == null) {
            throw new IllegalStateException("DataSource not initialized");
        }
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public void load() {
        initialize(dbname);
        connection = getSQLConnection();
        try {
            Statement s = connection.createStatement();

            //planned for removal, upgrade from 3.0.4 to 3.1 due to groups being transformed into goals
            // First check if the table exists
            boolean tableExists = false;
            try {
                ResultSet rs = s.executeQuery("SELECT 1 FROM play_time LIMIT 1");
                tableExists = true;
                rs.close();
            } catch (SQLException e) {
                // Table doesn't exist
            }

            if (tableExists) {
                // Only attempt column modification if the table exists
                try {
                    s.executeQuery("SELECT completed_goals FROM play_time LIMIT 1");
                } catch (SQLException e) {
                    createBackup();
                    // Add the column only if it doesn't exist
                    s.executeUpdate("ALTER TABLE play_time ADD COLUMN completed_goals TEXT DEFAULT ''");
                }
            }
            //----------------------------------------------------------

            // Create table if it doesn't exist
            s.executeUpdate(PlayTimeTable);
            s.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void createBackup() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());
        String backupFileName = "backup_" + timestamp + ".zip";

        // Get plugin's data folder
        File dataFolder = plugin.getDataFolder();
        File backupFolder = new File(dataFolder, "backups");
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }

        File backupFile = new File(backupFolder, backupFileName);
        File dbFile = new File(dataFolder, dbname + ".db");

        try (FileOutputStream fos = new FileOutputStream(backupFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Add database file to ZIP
            addToZip(dbFile, "database.db", zos);

            // Create and add README
            String readme = createReadmeContent(timestamp, dbFile);
            addTextToZip("README.txt", readme, zos);

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create backup: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private String createReadmeContent(String timestamp, File dbFile) {
        StringBuilder readme = new StringBuilder();
        readme.append("PlayTimeManager Database Backup\n");
        readme.append("============================\n\n");
        readme.append("!!! IMPORTANT VERSION UPGRADE NOTICE !!!\n");
        readme.append("=====================================\n");
        readme.append("This backup was automatically created during the upgrade from version 3.0.4 to 3.1.\n");
        readme.append("This is a critical backup as the upgrade transforms the groups system into goals.\n\n");

        readme.append("Backup Information:\n");
        readme.append("------------------\n");
        readme.append("Backup created: ").append(timestamp).append("\n");
        readme.append("Original database: ").append(dbFile.getName()).append("\n");
        readme.append("Database size: ").append(dbFile.length() / 1024).append(" KB\n\n");

        readme.append("Restore Instructions:\n");
        readme.append("-------------------\n");
        readme.append("!!! CRITICAL: The restored database file MUST be named 'play_time.db' !!!\n");
        readme.append("If the file is not named exactly 'play_time.db', the plugin will not load it.\n\n");
        readme.append("Steps to restore:\n");
        readme.append("1. Stop your server\n");
        readme.append("2. Delete the current 'play_time.db'\n");
        readme.append("3. Extract the database.db file from this backup zip\n");
        readme.append("4. Rename the extracted file to 'play_time.db'\n");
        readme.append("5. Place it in your plugin's data folder\n");
        readme.append("6. Start your server\n\n");

        readme.append("Warning: This backup contains data from before the groups-to-goals transformation.\n");
        readme.append("Restoring this backup will revert your data to the pre-3.1 format.\n");

        return readme.toString();
    }

    private void addToZip(File file, String entryName, ZipOutputStream zos) throws IOException {
        byte[] buffer = new byte[1024];

        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry ze = new ZipEntry(entryName);
            zos.putNextEntry(ze);

            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }

            zos.closeEntry();
        }
    }

    private void addTextToZip(String entryName, String content, ZipOutputStream zos) throws IOException {
        ZipEntry ze = new ZipEntry(entryName);
        zos.putNextEntry(ze);
        zos.write(content.getBytes());
        zos.closeEntry();
    }


}
