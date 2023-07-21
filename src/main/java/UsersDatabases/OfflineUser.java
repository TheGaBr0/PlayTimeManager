package UsersDatabases;

public class OfflineUser extends User{
    public OfflineUser(String uuid){
        super(uuid);
    }

    public void manuallyUpdatePlayTime(long playtime){
        customPlayTimeDB.updatePlayerToDatabase(uuid, playtime);
    }

    public long getPlayTime(){
        actualPlayTime = DBPlayTime;
        actualPlayTime += customPlayTimeDB.getCustomPlayTime(uuid);
        return actualPlayTime;
    }

}
