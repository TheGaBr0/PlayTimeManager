package UsersDatabases;

import me.thegabro.playtimemanager.PlayTimeManager;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PlayTimeDB {

    protected final boolean createIfNotExist, resource;
    protected final PlayTimeManager plugin;
    protected HashMap<String, Long> playTimeMap = new HashMap();
    protected final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    protected File file, path;
    protected String name;


    public PlayTimeDB(PlayTimeManager instance, File path, String name, boolean createIfNotExist, boolean resource) {
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

    //Map Utilities

    private void loadMap(){
        file = reloadFile();
        try {
            playTimeMap = gson.fromJson(new FileReader(file), new TypeToken<HashMap<String, Long>>(){}.getType());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void save(){
        String json = gson.toJson(playTimeMap);

        try {
            Files.write(file.toPath(), json.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean checkUUID(String uuid) {
        return playTimeMap.containsKey(uuid);
    }

    protected Long getPlaytimeForUUID(String uuid) {
        // or you can throw an exception or return a default value
        return playTimeMap.getOrDefault(uuid, 0L);
    }

    protected Set<String> getKeySet(){
        return playTimeMap.keySet();
    }

    protected Set<Map.Entry<String, Long>> getEntrySet(){
        return playTimeMap.entrySet();
    }

    protected int getSize(){
        return playTimeMap.size();
    }

    //Others

    Ordering<HashMap.Entry<String, Long>> byMapValues = new Ordering<HashMap.Entry<String, Long>>() {
        @Override
        public int compare(HashMap.Entry<String, Long> left, HashMap.Entry<String, Long> right) {
            return left.getValue().compareTo(right.getValue());
        }
    };

    public ArrayList<Map.Entry<String, Long>> getSortedPlayTimeMap() {
        HashMap<String, Long> temp = playTimeMap;
        ArrayList<Map.Entry<String, Long>> ordered = Lists.newArrayList(temp.entrySet());
        Collections.sort(ordered, byMapValues.reverse());

        return ordered;
    }

    public String convertTime(long secondsx) {
        int days = (int) TimeUnit.SECONDS.toDays(secondsx);
        int hours = (int) (TimeUnit.SECONDS.toHours(secondsx) - TimeUnit.DAYS.toHours(days));
        int minutes = (int) (TimeUnit.SECONDS.toMinutes(secondsx) - TimeUnit.HOURS.toMinutes(hours)
                - TimeUnit.DAYS.toMinutes(days));
        int seconds = (int) (TimeUnit.SECONDS.toSeconds(secondsx) - TimeUnit.MINUTES.toSeconds(minutes)
                - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.DAYS.toSeconds(days));

        if (days != 0) {
            return days + "d, " + hours + "h, " + minutes + "m, " + seconds + "s";
        } else {
            if (hours != 0) {
                return hours + "h, " + minutes + "m, " + seconds + "s";
            } else {
                if (minutes != 0) {
                    return minutes + "m, " + seconds + "s";
                } else {
                    return seconds + "s";
                }
            }

        }
    }

    protected void updatePlayerToDatabase(String uuid, long playtime){
        playTimeMap.put(uuid, playtime);
        save();
    }


    public void addTimeFromHashMap(HashMap<String, Long> map){
        for(Map.Entry<String, Long> entry : map.entrySet()) {
            String key = entry.getKey();

            if(playTimeMap.containsKey(key)){
                User user = plugin.getUsersManager().getUserByUuid(key);
                long sum = user.getPlayTime()+map.get(key);

                playTimeMap.put(key, sum);
                user.updateDBPlayTime(sum);

                if(Bukkit.getOfflinePlayer(UUID.fromString(key)).isOnline()){
                    plugin.getUsersManager().addOnlineUser(plugin.getUsersManager().getUserByUuid(key));
                }

            }else{
                playTimeMap.put(key, map.get(key));
            }
        }
        save();
    }


}
