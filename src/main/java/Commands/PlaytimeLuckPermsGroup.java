package Commands;

import Users.OnlineUsersManagerLuckPerms;
import me.thegabro.playtimemanager.PlayTimeManager;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.luckperms.api.model.group.Group;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class PlaytimeLuckPermsGroup implements TabExecutor {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final String[] SUBCOMMANDS = {"add", "remove", "list"};
    private final String[] TIME = {"setTime:"};

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (!sender.hasPermission("playtime.group")) {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 You don't have the permission to execute this command");
            return false;
        }

        if (args.length < 1) {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 Too few arguments!");
            return false;
        }

        String subCommand = args[0].toLowerCase();
        String groupName;
        switch (subCommand) {
            case "add":
                if (args.length < 3) {
                    sender.sendMessage("[§6PlayTime§eManager§f]§7 Usage: /playtime add <groupName> setTime:<time>");
                    return false;
                }
                if (!args[2].startsWith("setTime:")) {
                    sender.sendMessage("[§6PlayTime§eManager§f]§7 Time is not specified correctly!");
                    return false;
                }
                String timeArg = args[2].substring("setTime:".length());
                long timeToTicks = parseTime(timeArg);
                if (timeToTicks == -1) {
                    sender.sendMessage("[§6PlayTime§eManager§f]§7 Invalid time format!");
                    return false;
                }
                groupName = args[1];
                addGroup(sender, groupName, timeToTicks);
                break;
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage("[§6PlayTime§eManager§f]§7 Usage: /playtime remove <groupName>");
                    return false;
                }
                groupName = args[1];
                removeGroup(sender, groupName);
                break;
            case "list":
                list(sender);
                break;
            default:
                sender.sendMessage("[§6PlayTime§eManager§f]§7 Subcommand " + subCommand + " is not valid.");
                return false;
        }

        return true;
    }

    private long parseTime(String timeString) {
        String[] timeParts = timeString.split(",");
        long timeToTicks = 0;
        for (String part : timeParts) {
            try {
                int time = Integer.parseInt(part.replaceAll("[^\\d.]", ""));
                String format = part.replaceAll("\\d", "");
                switch (format) {
                    case "d": timeToTicks += time * 1728000L; break;
                    case "h": timeToTicks += time * 72000L; break;
                    case "m": timeToTicks += time * 1200L; break;
                    case "s": timeToTicks += time * 20L; break;
                    default: return -1; // Invalid format
                }
            } catch (NumberFormatException e) {
                return -1; // Invalid number
            }
        }
        return timeToTicks;
    }

    private void addGroup(CommandSender sender, String groupName, long time) {
        Group group = plugin.luckPermsApi.getGroupManager().getGroup(groupName);
        if (group != null) {
            OnlineUsersManagerLuckPerms onlineUsersManager = (OnlineUsersManagerLuckPerms) plugin.getUsersManager();
            plugin.getConfiguration().addGroup(groupName, time);
            onlineUsersManager.restartSchedule();
            sender.sendMessage("[§6PlayTime§eManager§f]§7 The group §e" + groupName + "§7 will be automatically set to a player " +
                    "whenever it reaches §6" + convertTime(time / 20));
        } else {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 The group §e" + groupName + "§7 doesn't exist in LuckPerms configuration!");
        }
    }

    private void removeGroup(CommandSender sender, String groupName) {
        HashMap<String, Long> groups = plugin.getConfiguration().getGroups();

        if (groups.containsKey(groupName)) {
            OnlineUsersManagerLuckPerms onlineUsersManager = (OnlineUsersManagerLuckPerms) plugin.getUsersManager();
            plugin.getConfiguration().removeGroup(groupName);
            onlineUsersManager.restartSchedule();
            sender.sendMessage("[§6PlayTime§eManager§f]§7 The group §e" + groupName + " §7has been removed!");
        } else {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 The group §e" + groupName + " §7doesn't exist!");
        }
    }

    private void list(CommandSender sender){
        HashMap<String, Long> groups = plugin.getConfiguration().getGroups();

        if (sender instanceof Player) {
            Book.Builder book = Book.builder();
            Component bookAuthor = Component.text("TheGabro");
            Component bookTitle = Component.text("PlayTimeManager Groups");
            book.author(bookAuthor);
            book.title(bookTitle);

            List<Component> pages = convertToComponents(groups);

            book.pages(pages);

            Player p = (Player) sender;
            p.openBook(book);
        }else{
            sender.sendMessage("[§6PlayTime§eManager§f]§7 Groups stored:");
            for (Map.Entry<String, Long> entry : groups.entrySet()) {
                String groupName = entry.getKey();
                Long timeRequired = entry.getValue();
                sender.sendMessage("[§6PlayTime§eManager§f]§7 "+groupName+" - "+convertTime(timeRequired/20));
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, String[] args) {
        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0],  Arrays.asList(SUBCOMMANDS), completions);
            return completions;
        }

        if (args.length == 2 && args[0].equals(SUBCOMMANDS[1])){
            StringUtil.copyPartialMatches(args[1],  plugin.getConfiguration().getGroups().keySet(), completions);
            return completions;
        }

        if(args.length == 3 && args[0].equals(SUBCOMMANDS[0])){
            StringUtil.copyPartialMatches(args[2],  Arrays.asList(TIME), completions);
            return completions;
        }

        return null;
    }

    private String convertTime(long secondsx) {
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

    public List<Component> convertToComponents(Map<String, Long> map) {
        List<Component> componentList = new ArrayList<>();
        StringBuilder groupsBuilder = new StringBuilder();
        int groupCount = 0;

        for (Map.Entry<String, Long> entry : map.entrySet()) {
            String groupName = entry.getKey();
            Long timeRequired = entry.getValue();
            groupsBuilder.append("- ").append(groupName).append("\n").append(convertTime(timeRequired/20)).append("\n");
            groupCount++;

            if(groupCount == 7){
                componentList.add(Component.text(groupsBuilder.toString()));
                groupsBuilder.setLength(0);
                groupCount = 0;
            }
        }

        // Add any remaining nicknames that didn't make up a full set of 7
        if (groupCount > 0) {
            componentList.add(Component.text(groupsBuilder.toString()));
        }

        return componentList;
    }
}


