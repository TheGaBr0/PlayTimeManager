package Commands;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlaytimeTop implements TabExecutor {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    public final int TOP_MAX = 100;
    int page;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (sender.hasPermission("playtime.top")){
            if(args.length>=1){
                if (isStringInt(args[0]))
                {
                    if(Integer.parseInt(args[0])<=TOP_MAX){

                        if(args.length>1){
                            if(isStringInt(args[1].substring(1))){
                                if(getPages(args[0]).contains(args[1])){
                                    page = Integer.parseInt(args[1].substring(1));
                                }else{
                                    sender.sendMessage("[§6Play§eTime§f]§7 Page "+args[1].substring(1)+" doesn't exist!");
                                    return false;
                                }
                            }else{
                                sender.sendMessage("[§6Play§eTime§f]§7 The argument is not valid!");
                                return false;
                            }
                        }else{
                            page = 1;
                        }
                        List<String> topPlayers = plugin.getDbDataCombiner().FillTopPlayers();

                        if(topPlayers.size()>0){
                            // Ottenere il numero totale degli utenti dal parametro args[0]
                            int numeroUtentiTotali = Integer.parseInt(args[0]);

                            if(numeroUtentiTotali > topPlayers.size())
                                numeroUtentiTotali = topPlayers.size();

                            // Calcolare il numero di pagine
                            int numeroPagine = (int) Math.ceil((double) (numeroUtentiTotali + 1) / 10);

                            if (page > 0 && page <= numeroPagine) {
                                // Calcolare l'indice di partenza e l'indice di fine per la pagina specificata
                                int indiceInizio = (page - 1) * 10;
                                int indiceFine = Math.min(page * 10, numeroUtentiTotali);

                                // Stampa i giocatori della pagina specificata
                                sender.sendMessage("[§6Play§eTime§f]§7 Top "+numeroUtentiTotali+" players - page: "+page);
                                for (int i = indiceInizio; i < indiceFine; i++) {
                                    sender.sendMessage("§7§l#"+(i+1)+" §e"+ topPlayers.get(i)+" §7- §d"+
                                            plugin.getPlayTimeDB().convertTime(plugin.getUsersManager().getUserByNickname(topPlayers.get(i)).getPlayTime()/20));
                                }
                            } else if (page == 0) {
                                // Mostra tutti i giocatori
                                sender.sendMessage("[§6Play§eTime§f]§7 Top "+numeroUtentiTotali+" players - page: 1");
                                for (int i = 0; i <= numeroUtentiTotali; i++) {
                                    sender.sendMessage("§7§l#"+(i+1)+" §e"+ topPlayers.get(i)+" §7- §d"+
                                            plugin.getPlayTimeDB().convertTime(plugin.getUsersManager().getUserByNickname(topPlayers.get(i)).getPlayTime()/20));
                                }
                            } else {
                                // Pagina non valida
                                sender.sendMessage("[§6Play§eTime§f]§7 Invalid page!");
                            }
                        }else{
                            sender.sendMessage("[§6Play§eTime§f]§7 No players joined!");
                        }
                    }else{
                        sender.sendMessage("[§6Play§eTime§f]§7 Number of players is limited to 100!");
                    }
                }
                else{
                    sender.sendMessage("[§6Play§eTime§f]§7 The argument is not valid!");
                }
            }else{
                sender.sendMessage("[§6Play§eTime§f]§7 You must specify the number of players you'd like to rank!");
            }
        }else{
            sender.sendMessage("[§6Play§eTime§f]§7 You don't have the permission to execute this command");
        }
        return false;
    }



    public boolean isStringInt(String s)
    {
        try
        {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException ex)
        {
            return false;
        }
    }

    public List<String> getPages(String players){
        List<String> result = new ArrayList<String>();

        for(int i = 0; i<Math.ceil(Float.parseFloat(players)/10); i++){
            result.add("p"+(i+1));
        }

        return result;
    }


    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {

        final List<String> completions = new ArrayList<>();

        if(args.length>1 && isStringInt(args[0])){
            List<String> pages = getPages(args[0]);
            StringUtil.copyPartialMatches(args[1], pages, completions);

            return completions;
        }

        return null;
    }
}
