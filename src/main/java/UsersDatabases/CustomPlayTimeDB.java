package UsersDatabases;

import Main.PlayTimeManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;

public class CustomPlayTimeDB {
    protected final boolean createIfNotExist, resource;
    protected final PlayTimeManager plugin;
    protected HashMap<String, Long> customPlayTimeMap = new HashMap();
    protected final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    protected File file, path;
    protected String name;

    public CustomPlayTimeDB(PlayTimeManager instance, File path, String name, boolean createIfNotExist, boolean resource) {
        this.plugin = instance;
        this.path = path;
        this.name = name + ".json";
        this.createIfNotExist = createIfNotExist;
        this.resource = resource;
        create();
        loadMap();
    }

    private File reloadFile() {
        file = new File(path, name);
        return file;
    }

    private void create(){
        if (file == null) {
            reloadFile();
        }
        if (!createIfNotExist || file.exists()) {
            return;
        }
        file.getParentFile().mkdirs();
        if (resource) {
            plugin.saveResource(name, false);
        } else {
            try {
                file.createNewFile();
            } catch (Exception exc) {
                exc.printStackTrace();
            }
        }
    }

    //Map utilities

    private void loadMap(){
        try {
            customPlayTimeMap = gson.fromJson(new FileReader(file), new TypeToken<HashMap<String, Long>>(){}.getType());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    private void save(){
        String json = gson.toJson(customPlayTimeMap);

        try {
            Files.write(file.toPath(), json.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE,  StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Other

    public Long getCustomPlayTime(String uuid){
        return customPlayTimeMap.getOrDefault(uuid, 0L);
    }

    public void updatePlayerToDatabase(String uuid, Long customPlayTime){

        if(getCustomPlayTime(uuid) + customPlayTime == 0L){
            customPlayTimeMap.remove(uuid);
        }
        else{
            customPlayTimeMap.put(uuid, getCustomPlayTime(uuid) + customPlayTime);
        }

        save();
    }
}
