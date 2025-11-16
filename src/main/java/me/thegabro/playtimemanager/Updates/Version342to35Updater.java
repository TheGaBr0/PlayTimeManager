package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

public class Version342to35Updater {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final CommandsConfiguration commandsConfiguration = CommandsConfiguration.getInstance();
    public Version342to35Updater() {}

    public void performUpgrade() {
        renameCustomizationsFolder();
        recreateConfigFile();
    }


    public void renameCustomizationsFolder(){

        try {

            Path sourcePath = Paths.get(String.valueOf(plugin.getDataFolder()), "Translations");
            Path targetPath = Paths.get(String.valueOf(plugin.getDataFolder()), "Customizations");

            if (Files.exists(sourcePath)) {
                if (Files.exists(targetPath)) {
                    plugin.getLogger().warning("Customizations folder already exists. Deleting it");
                    deleteDirectory(targetPath);
                }

                // Rename the folder
                copyDirectory(sourcePath, targetPath);
                deleteDirectory(sourcePath);
                plugin.getLogger().info("Successfully created 'Customizations' folder from 'Translations'");

            } else {
                try {
                    Files.createDirectories(targetPath);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to create 'Customizations' folder: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to rename Translations folder: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error while renaming folder: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> pathStream = Files.walk(path)) {
                pathStream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> pathStream = Files.walk(source)) {
            pathStream.forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy: " + sourcePath, e);
                }
            });
        }
    }

    private void recreateConfigFile() {

        String playtimeSelfMessage = Configuration.getInstance().getConfig().getString("playtime-self-message");
        String playtimeOthersMessage = Configuration.getInstance().getConfig().getString("playtime-others-message");
        Configuration.getInstance().updateConfig(true);
        recreateCommandsConfigurationFile(playtimeSelfMessage, playtimeOthersMessage);
    }

    public void recreateCommandsConfigurationFile(String playtimeSelfMessage, String playtimeOthersMessage){

        commandsConfiguration.initialize(plugin);
        commandsConfiguration.updateConfig();

        commandsConfiguration.set("playtime-self-message", playtimeSelfMessage);
        commandsConfiguration.set("playtime-others-message", playtimeOthersMessage);

        commandsConfiguration.reload();
    }

}