package Commands;

import me.thegabro.playtimemanager.PlayTimeManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;

public class PlaytimeUuidAdd implements CommandExecutor {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private HashMap<String, String> map = new HashMap();
    private File inputFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();


    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        inputFile = new File(plugin.getDataFolder(), "inputUuid.json");
        if (sender.hasPermission("playtime.dbadd")){
            if(inputFile.exists()){

                sender.sendMessage("[§6Play§eTime§f]§7 Saving old database to another location..");

                saveOldDatabase();

                sender.sendMessage("[§6Play§eTime§f]§7 Loading input file..");

                loadMap();

                sender.sendMessage("[§6Play§eTime§f]§7 Adding new uuid entries..");

                plugin.getUuidDB().addUuidFromHashMap(map);

                sender.sendMessage("[§6Play§eTime§f]§7 Operation completed successfully!");

            }else{
                sender.sendMessage("[§6Play§eTime§f]§7 Input file is missing");
            }
        }else{
            sender.sendMessage("[§6Play§eTime§f]§7 You don't have the permission to execute this command");
        }


        return false;

    }

    private void loadMap(){
        try {
            map = gson.fromJson(new FileReader(inputFile), new TypeToken<HashMap<String, String>>(){}.getType());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void saveOldDatabase(){
        String fileSeparator = System.getProperty("file.separator");
        LocalDate now = LocalDate.now();

        File dest = new File(plugin.getDataFolder()+fileSeparator+"Outdated databases"+fileSeparator+"UUidDatabase-("+now+").json");

        try {
            dest.getParentFile().mkdirs();
            dest.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        File source = new File(plugin.getDataFolder()+fileSeparator+"UuidDatabase.json");
        try {
            com.google.common.io.Files.copy(source, dest);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
