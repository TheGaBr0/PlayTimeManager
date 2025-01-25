package me.thegabro.playtimemanager.updaters;

import me.thegabro.playtimemanager.PlayTimeManager;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DatabaseBackupUtility {
    private final PlayTimeManager plugin;
    private static final int BUFFER_SIZE = 1024;

    public DatabaseBackupUtility(PlayTimeManager plugin) {
        this.plugin = plugin;
    }

    public File createBackup(String dbName, String readme) {
        // Generate timestamp for unique backup filename
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());
        String backupFileName = "backup_" + timestamp + ".zip";

        // Create backup directories
        File dataFolder = plugin.getDataFolder();
        File backupFolder = new File(dataFolder, "backups");
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }

        File backupFile = new File(backupFolder, backupFileName);
        File dbFile = new File(dataFolder, dbName + ".db");

        try (FileOutputStream fos = new FileOutputStream(backupFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Add database file to ZIP
            addFileToZip(dbFile, dbName+".db", zos);

            // Create and add README
            addTextToZip(readme, zos);

            return backupFile;
        } catch (IOException e) {
            plugin.getLogger().severe("Backup creation failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void addFileToZip(File file, String entryName, ZipOutputStream zos) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];

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

    private void addTextToZip(String content, ZipOutputStream zos) throws IOException {
        ZipEntry ze = new ZipEntry("README.txt");
        zos.putNextEntry(ze);
        zos.write(content.getBytes());
        zos.closeEntry();
    }


}