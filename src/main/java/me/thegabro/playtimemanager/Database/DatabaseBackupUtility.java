package me.thegabro.playtimemanager.Database;

import me.thegabro.playtimemanager.PlayTimeManager;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DatabaseBackupUtility {
    private static DatabaseBackupUtility instance;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private static final int BUFFER_SIZE = 1024;

    private DatabaseBackupUtility() {}

    public static DatabaseBackupUtility getInstance() {
        if (instance == null) {
            instance = new DatabaseBackupUtility();
        }
        return instance;
    }

    public File createBackup(String readme) {
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

            // Add README
            addTextToZip(readme, zos);

            // Add plugin data folder as "PlayTimeManager/"
            for (File file : dataFolder.listFiles()) {
                if (!file.getName().equalsIgnoreCase("backups")) {
                    addToZipRecursively(file, dataFolder, "PlayTimeManager/", zos);
                }
            }

            return backupFile;
        } catch (IOException e) {
            plugin.getLogger().severe("Backup creation failed: " + e.getMessage());
            e.printStackTrace();
            return null;
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
}
