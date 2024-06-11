package UsersDatabases;

import me.thegabro.playtimemanager.PlayTimeManager;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DataCombiner {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private HashMap<String, Long> temp;
    private List<HashMap.Entry<String, Long>> ordered;
    private final List<String> topPlayers = new ArrayList();
    private UsersManager usersManager;

    public DataCombiner(){
        usersManager = plugin.getUsersManager();
    }


    //average playtime of all players
    public long getAveragePlayTime(){
        long average = 0;
        long sum = 0;
        long count = 0;

        for(String nickname : usersManager.getStoredPlayers()) {

            sum += usersManager.getPlayTimeByNick(nickname);
            count++;
        }

        if(count == 0)
            return 0;

        average = sum/count;

        return average;
    }

    //getPercentages will return a string containing the % of players who have overpassed a given playtime
    public String getPercentages(int time, String format) {

        float sum = 0, numberOfPlayers = 0;
        String result;

        DecimalFormat df = new DecimalFormat("#.##%");

        df.setRoundingMode(RoundingMode.CEILING);

        for (String nickname : usersManager.getStoredPlayers()) {

            if(usersManager.userExists(nickname)){
                long playtime = usersManager.getPlayTimeByNick(nickname);
                numberOfPlayers++;
                if (format.equals("d"))
                    if (convertTicksToDays(playtime / 20) >= time)
                        sum++;

                if (format.equals("h"))
                    if (convertTicksToHours(playtime / 20) >= time)
                        sum++;

                if (format.equals("m"))
                    if (convertTicksToMinutes(playtime / 20) >= time)
                        sum++;
            }

        }

        result = df.format(sum / numberOfPlayers);

        return "[§6Play§eTime§f]§7 The players with a playtime higher or equal to §6"+time+format+"§7 are " +
                "§e"+Math.round(sum)+"§7 accounting for §e"+result+"§7 of players.";
    }

    //-------------------------------------------------Rank system functions-------------------------------------------


    public List<String> FillTopPlayers(){

//        ArrayList<Map.Entry<String, Long>> ordered = plugin.getPlayTimeDB().getSortedPlayTimeMap();
//
//        topPlayers.clear();
//
//        for(int i = 0; i<100; i++){
//            String nickname = usersManager.getPlayerName(String.valueOf(ordered.get(i).getKey()));
//            if(nickname != null){
//                User user = usersManager.getUserByNickname(nickname);
//                if(user.getPlayTime() != 0L){
//                    topPlayers.add(user.getName());
//                }
//            }
//        }
//
//        return topPlayers;
        return null;

    }

    public User getPlayerAtPosition(int position){

        topPlayers.clear();
        FillTopPlayers();

        User user = usersManager.getUserByNickname(topPlayers.get(position-1));

        try{
            return user;
        }catch(IndexOutOfBoundsException e){
            return null;
        }

    }

    //-------------------------------------------------End of rank system functions-------------------------------------------

    //Time converters & checkers used by DataCombiner's functions
    public int convertTicksToDays(long seconds) {
        return (int) TimeUnit.SECONDS.toDays(seconds);
    }

    public int convertTicksToHours(long seconds) {

        int days = (int) TimeUnit.SECONDS.toDays(seconds);
        return (int) (TimeUnit.SECONDS.toHours(seconds) - TimeUnit.DAYS.toHours(days));
    }

    public int convertTicksToMinutes(long seconds) {

        int days = (int) TimeUnit.SECONDS.toDays(seconds);
        int hours = (int) (TimeUnit.SECONDS.toHours(seconds) - TimeUnit.DAYS.toHours(days));
        return (int) (TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(hours) - TimeUnit.DAYS.toMinutes(days));
    }
}
