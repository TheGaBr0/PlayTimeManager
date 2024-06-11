package UsersDatabases;

import me.thegabro.playtimemanager.PlayTimeManager;
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
import java.util.Map;
import java.util.Objects;

public class UuidDB {
    protected final boolean createIfNotExist, resource;
    protected final PlayTimeManager plugin;
    protected HashMap<String, String> uuidMap = new HashMap();
    protected final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    protected File file, path;
    protected String name;


    public UuidDB(PlayTimeManager instance, File path, String name, boolean createIfNotExist, boolean resource) {
        this.plugin = instance;
        this.path = path;
        this.name = name + ".json";
        this.createIfNotExist = createIfNotExist;
        this.resource = resource;
        create();
        loadMap();
    }

    //File Management

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
        reloadFile();
        try {
            uuidMap = gson.fromJson(new FileReader(file), new TypeToken<HashMap<String, String>>(){}.getType());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    private void save(){
        String json = gson.toJson(uuidMap);

        try {
            Files.write(file.toPath(), json.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected String getPlayerName(String uuid){
        return uuidMap.get(uuid);
    }
    @SuppressWarnings("unchecked")
    protected <T, E> T getUuid(E value) {

        Map<T, E> map = (Map<T, E>) uuidMap;

        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (Objects.equals(value, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    protected boolean checkUUID(String uuid) {
        loadMap();
        return uuidMap.containsKey(uuid);
    }

    protected boolean checkForUuidNicknamePairings(String uuid, String nickname){
        return Objects.equals(getPlayerName(uuid), nickname);
    }

    protected void addPlayerToDatabase(String uuid, String name){
        uuidMap.put(uuid, name);

        save();

    }

    protected void replaceUuidData(String uuid, String nickname){
        uuidMap.replace(uuid, nickname);

        save();
    }


    public void addUuidFromHashMap(HashMap<String, String> map){
        for(Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();

            uuidMap.put(key, map.get(key));

        }

        save();
    }
}
