package Main;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.UUID;

public class TimePlayedExtractor extends JavaPlugin {
    private final String fileSeparator = System.getProperty("file.separator");

    public HashMap map = new HashMap<String, String>();
    public final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private FileConfiguration userdataYml;

    @Override
    public void onEnable() {

        getLogger().info("has been enabled!");

        getLogger().info("Gathering data of each player...");

        File outputFile = new File(getDataFolder(), "outputUuidFile.json");
        File folder = new File(Bukkit.getPluginManager().getPlugin("Essentials").getDataFolder()+fileSeparator+"userdata");


        if (!outputFile.exists()) saveResource(outputFile.getName(), false);

        try {
            map = gson.fromJson(new FileReader(outputFile), HashMap.class);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        String uuid, nickname;
        int numberOfPlayers = 0;

        for (final File fileEntry : folder.listFiles()) {
            if (!fileEntry.isDirectory()) {
                userdataYml = YamlConfiguration.loadConfiguration(fileEntry);

                uuid = fileEntry.getName().substring(0, fileEntry.getName().length() - 4);
                nickname = userdataYml.getString("last-account-name");

                map.put(uuid, nickname);

                numberOfPlayers++;


            }
        }

        getLogger().info("Gathering completed, "+numberOfPlayers+" player(s) processed");


        String json = gson.toJson(map);
        outputFile.delete();

        try {
            Files.write(outputFile.toPath(), json.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Bukkit.getPluginManager().disablePlugin(this);
    }

    @Override
    public void onDisable(){


        getLogger().info("has been disabled!");
    }
}
