package me.thegabro.playtimemanager.Database;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.io.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DatabaseBackupUtility {
    private static DatabaseBackupUtility instance;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private static final int BUFFER_SIZE = 1024;
    private final Configuration config = Configuration.getInstance();
    private DatabaseBackupUtility() {}

    public static DatabaseBackupUtility getInstance() {
        if (instance == null) {
            instance = new DatabaseBackupUtility();
        }
        return instance;
    }

    public File createBackup(String reason) {
        Database.DBTYPES dbType = DatabaseHandler.getInstance().getDatabaseType();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());
        String backupFileName = "backup_" + timestamp + ".zip";

        File dataFolder = plugin.getDataFolder();
        File backupFolder = new File(dataFolder, "backups");
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }

        File backupFile = new File(backupFolder, backupFileName);

        try (FileOutputStream fos = new FileOutputStream(backupFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            addTextToZip(generateReadmeContent(reason), zos);

            addPlayTimeManagerFolder(dataFolder, zos);

            switch (dbType) {
                case SQLITE:
                    // SQLite database is already included in the folder backup
                    break;
                case MYSQL:
                    backupMySQL(zos);
                    break;
                case POSTGRESQL:
                    backupPostgreSQL(zos);
                    break;
                default:
                    plugin.getLogger().warning("Unknown database type: " + dbType);
                    return null;
            }

            return backupFile;
        } catch (IOException | SQLException e) {
            plugin.getLogger().severe("Backup creation failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void addPlayTimeManagerFolder(File dataFolder, ZipOutputStream zos) throws IOException {
        for (File file : dataFolder.listFiles()) {
            if (!file.getName().equalsIgnoreCase("backups")) {
                addToZipRecursively(file, dataFolder, "PlayTimeManager/", zos);
            }
        }
    }

    private void backupMySQL(ZipOutputStream zos) throws SQLException, IOException {

        String host = config.getString("mysql.host", "localhost");
        String port = String.valueOf(config.getInt("mysql.port", 3306));
        String database = config.getString("mysql.database", "playtime_manager");
        String username = config.getString("mysql.username", null);
        String password = config.getString("mysql.password", null);

        File tempDump = new File(plugin.getDataFolder(), "temp_mysql_dump.sql");

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "mysqldump",
                    "--host=" + host,
                    "--port=" + port,
                    "--user=" + username,
                    "--password=" + password,
                    "--single-transaction",
                    "--quick",
                    "--lock-tables=false",
                    "--add-drop-table",
                    "--routines",
                    "--triggers",
                    "--no-tablespaces",
                    database
            );

            pb.redirectOutput(tempDump);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder error = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
                throw new IOException("mysqldump failed with exit code " + exitCode + ": " + error.toString());
            }

            // Add the dump file to zip
            try (FileInputStream fis = new FileInputStream(tempDump)) {
                ZipEntry ze = new ZipEntry("mysql_dump.sql");
                zos.putNextEntry(ze);

                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }

                zos.closeEntry();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Backup interrupted", e);
        } finally {
            // Clean up temp files
            if (tempDump.exists()) {
                tempDump.delete();
            }
        }
    }

    private void backupPostgreSQL(ZipOutputStream zos) throws SQLException, IOException {

        String host = config.getString("postgresql.host", "localhost");
        String port = String.valueOf(config.getInt("postgresql.port", 5432));
        String database = config.getString("postgresql.database", "playtime_manager");
        String username = config.getString("postgresql.username", null);
        String password = config.getString("postgresql.password", null);

        File tempDump = new File(plugin.getDataFolder(), "temp_pg_dump.sql");

        try {
            // Set PGPASSWORD environment variable
            ProcessBuilder pb = new ProcessBuilder(
                    "pg_dump",
                    "--host=" + host,
                    "--port=" + port,
                    "--username=" + username,
                    "--format=plain",
                    "--no-owner",
                    "--no-acl",
                    "--clean",
                    "--if-exists",
                    database
            );

            // Add password via environment variable (safer than command line)
            pb.environment().put("PGPASSWORD", password);

            pb.redirectOutput(tempDump);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder error = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
                throw new IOException("pg_dump failed with exit code " + exitCode + ": " + error.toString());
            }

            // Add the dump file to zip
            try (FileInputStream fis = new FileInputStream(tempDump)) {
                ZipEntry ze = new ZipEntry("postgresql_dump.sql");
                zos.putNextEntry(ze);

                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }

                zos.closeEntry();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Backup interrupted", e);
        } finally {
            // Clean up temp files
            if (tempDump.exists()) {
                tempDump.delete();
            }
        }
    }

    private void addToZipRecursively(File file, File baseFolder, String prefix, ZipOutputStream zos) throws IOException {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                addToZipRecursively(child, baseFolder, prefix, zos);
            }
        } else {
            String name = file.getName();
            if (name.endsWith(".db-shm") || name.endsWith(".db-wal")) {
                return; // Skip unwanted files
            }

            String relativePath = baseFolder.toPath().relativize(file.toPath()).toString().replace("\\", "/");
            String entryName = prefix + relativePath;

            try (FileInputStream fis = new FileInputStream(file)) {
                ZipEntry ze = new ZipEntry(entryName);
                zos.putNextEntry(ze);

                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }

                zos.closeEntry();
            }
        }
    }

    private void addTextToZip(String content, ZipOutputStream zos) throws IOException {
        ZipEntry ze = new ZipEntry("README.txt");
        zos.putNextEntry(ze);
        zos.write(content.getBytes());
        zos.closeEntry();
    }

    private String generateReadmeContent(String reason) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = dateFormat.format(new Date());

        Database.DBTYPES dbType = DatabaseHandler.getInstance().getDatabaseType();

        StringBuilder readme = new StringBuilder();
        readme.append("PlayTimeManager Database Backup\n");
        readme.append("================================\n\n");
        readme.append("Backup created on: ").append(timestamp).append("\n");
        readme.append("Reason: ").append(reason).append("\n\n");

        readme.append("Plugin Information:\n");
        readme.append("-------------------\n");
        readme.append("- Plugin name: PlayTimeManager\n");
        readme.append("- Version: ").append(plugin.getDescription().getVersion()).append("\n");
        readme.append("- Author: TheGabro\n\n");

        readme.append("Database Information:\n");
        readme.append("---------------------\n");
        readme.append("- Database type: ").append(dbType.toString()).append("\n");

        switch (dbType) {
            case SQLITE:
                readme.append("- Database file: play_time.db\n");
                readme.append("- Location: plugins/PlayTimeManager/\n");
                break;
            case MYSQL:
                readme.append("- Host: ").append(config.getString("mysql.host", "localhost")).append("\n");
                readme.append("- Port: ").append(config.getInt("mysql.port", 3306)).append("\n");
                readme.append("- Database: ").append(config.getString("mysql.database", null)).append("\n");
                readme.append("- Username: ").append(config.getString("mysql.username", null)).append("\n");
                break;
            case POSTGRESQL:
                readme.append("- Host: ").append(config.getString("postgresql.host", "localhost")).append("\n");
                readme.append("- Port: ").append(config.getInt("postgresql.port", 5432)).append("\n");
                readme.append("- Database: ").append(config.getString("postgresql.database", null)).append("\n");
                readme.append("- Username: ").append(config.getString("postgresql.username", null)).append("\n");
                break;
        }

        readme.append("\nBackup Contents:\n");
        readme.append("----------------\n");
        readme.append("- README.txt (this file)\n");
        readme.append("- PlayTimeManager/ (complete plugin folder with configs)\n");

        switch (dbType) {
            case SQLITE:
                readme.append("  - Contains play_time.db (SQLite database file)\n");
                break;
            case MYSQL:
                readme.append("- mysql_dump.sql (MySQL database dump)\n");
                break;
            case POSTGRESQL:
                readme.append("- postgresql_dump.sql (PostgreSQL database dump)\n");
                break;
        }

        readme.append("\nRestore Instructions:\n");
        readme.append("---------------------\n");

        switch (dbType) {
            case SQLITE:
                readme.append("1. Stop your server\n");
                readme.append("2. Extract the 'PlayTimeManager' folder from this backup\n");
                readme.append("3. Replace your current plugins/PlayTimeManager folder with the extracted one\n");
                readme.append("   (or just replace the play_time.db file if you only want to restore data)\n");
                readme.append("4. Restart your server\n");
                break;

            case MYSQL:
                readme.append("MySQL/MariaDB Quick restore command:\n");
                readme.append("mysql -h ").append(config.getString("mysql.host", "localhost"))
                        .append(" -P ").append(config.getInt("mysql.port", 3306))
                        .append(" -u ").append(config.getString("mysql.username", null))
                        .append(" -p ").append(config.getString("mysql.database", null))
                        .append(" < mysql_dump.sql\n");
                break;

            case POSTGRESQL:
                readme.append("PostgreSQL Quick restore command:\n");
                readme.append("psql -h ").append(config.getString("postgresql.host", "localhost"))
                        .append(" -p ").append(config.getInt("postgresql.port", 5432))
                        .append(" -U ").append(config.getString("postgresql.username", null))
                        .append(" -d ").append(config.getString("postgresql.database", null))
                        .append(" -f postgresql_dump.sql\n");
                break;
        }

        readme.append("\nImportant Notes:\n");
        readme.append("----------------\n");
        readme.append("- This backup contains all player playtime data up to the moment of creation\n");
        readme.append("- Always test restores on a backup/test server first if possible\n");

        if (dbType == Database.DBTYPES.MYSQL || dbType == Database.DBTYPES.POSTGRESQL) {
            readme.append("- Restoring the database dump will REPLACE all existing data\n");
        }
        return readme.toString();
    }
}