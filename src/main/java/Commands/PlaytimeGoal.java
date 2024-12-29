package Commands;

import GUIs.AllGoalsGui;
import Goals.Goal;
import Goals.GoalManager;
import Users.OnlineUsersManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PlaytimeGoal implements TabExecutor {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final String[] SUBCOMMANDS = {"set", "remove", "list", "gui"};
    private final String[] SUBSUBCOMMANDS = {"setTime:"};

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (!sender.hasPermission("playtime.goal")) {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 You don't have the permission to execute this command");
            return false;
        }

        if (args.length < 1) {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 Too few arguments!");
            return false;
        }
        String goalName;
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "gui":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("[§6PlayTime§eManager§f]§7 Only players can use the GUI!");
                    return false;
                }
                AllGoalsGui gui = new AllGoalsGui();
                gui.openInventory((Player) sender);
                return true;
            case "set":
                if (args.length < 2) {
                    sender.sendMessage("[§6PlayTime§eManager§f]§7 Usage: /playtimegoal set <goalName> setTime:<time>]");
                    return false;
                }

                goalName = args[1];
                String setTime = null;

                // Process optional arguments: setTime
                for (int i = 2; i < args.length; i++) {
                    if (args[i].startsWith("setTime:")) {
                        if (setTime != null) {
                            sender.sendMessage("[§6PlayTime§eManager§f]§7 Duplicate setTime argument!");
                            return false;
                        }
                        setTime = args[i].substring(8); // Extract time value after "setTime:"
                        if (setTime.isEmpty()) {
                            sender.sendMessage("[§6PlayTime§eManager§f]§7 Missing time value for setTime!");
                            return false;
                        }
                        long timeToTicks = parseTime(setTime);
                        if (timeToTicks == -1) {
                            sender.sendMessage("[§6PlayTime§eManager§f]§7 Invalid time format!");
                            return false;
                        }
                    } else {
                        sender.sendMessage("[§6PlayTime§eManager§f]§7 Invalid argument: " + args[i]);
                        return false;
                    }
                }

                setGoal(sender, goalName, setTime != null ? parseTime(setTime) : null);
                break;
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage("[§6PlayTime§eManager§f]§7 Usage: /playtimegoal remove <goalName>");
                    return false;
                }
                goalName = args[1];
                removeGoal(sender, goalName);
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
                    case "d":
                        timeToTicks += time * 1728000L;
                        break;
                    case "h":
                        timeToTicks += time * 72000L;
                        break;
                    case "m":
                        timeToTicks += time * 1200L;
                        break;
                    case "s":
                        timeToTicks += time * 20L;
                        break;
                    default:
                        return -1; // Invalid format
                }
            } catch (NumberFormatException e) {
                return -1; // Invalid number
            }
        }
        return timeToTicks;
    }

    private void setGoal(CommandSender sender, String goalName, Long time) {
        Goal g = GoalManager.getGoal(goalName);
        StringBuilder message = new StringBuilder();

        if (g != null) {
            message.append("[§6PlayTime§eManager§f]§7 Goal §e").append(goalName).append(" §7updated:\n");
            if(time != null)
                g.setTime(time);
        } else {
            g = new Goal(plugin, goalName, time);
            message.append("[§6PlayTime§eManager§f]§7 Goal §e").append(goalName).append(" §7created:\n");
        }

        long gTime = g.getTime();
        if(gTime == Long.MAX_VALUE)
            message.append("§7- Required time to reach the goal: §6None\n");
        else
            message.append("§7- Required time to reach the goal: §6").append(convertTime(gTime / 20)).append("\n");

        sender.sendMessage(message.toString());

        plugin.getUsersManager().restartSchedule();
    }

    private void removeGoal(CommandSender sender, String goalName) {
        Goal g = GoalManager.getGoal(goalName);
        if (g != null) {
            g.kill();
            plugin.getUsersManager().restartSchedule();
            sender.sendMessage("[§6PlayTime§eManager§f]§7 The goal §e" + goalName + " §7has been removed!");
        } else {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 The goal §e" + goalName + " §7doesn't exist!");
        }
    }

    private void list(CommandSender sender) {
        Set<Goal> goals = GoalManager.getGoals();

        if (sender instanceof Player) {
            Book.Builder book = Book.builder();
            Component bookAuthor = Component.text("TheGabro");
            Component bookTitle = Component.text("PlayTimeManager Goals");
            book.author(bookAuthor);
            book.title(bookTitle);

            List<Component> pages = convertToComponents(goals);
            book.pages(pages);

            Player p = (Player) sender;
            p.openBook(book.build());
        } else {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 Goals stored:");
            for (Goal goal : goals) {
                String goalName = goal.getName();
                Long timeRequired = goal.getTime();
                sender.sendMessage("[§6PlayTime§eManager§f]§7 " + goalName + " - " + convertTime(timeRequired / 20));
            }
        }
    }

    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, String[] args) {
        final List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList(SUBCOMMANDS), completions);
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set")) {
                // Suggest goal names for the "set" subcommand
                StringUtil.copyPartialMatches(args[1], GoalManager.getGoalsNames(), completions);
            } else if (args[0].equalsIgnoreCase("remove")) {
                // Suggest existing goal names for the "remove" subcommand
                StringUtil.copyPartialMatches(args[1], GoalManager.getGoalsNames(), completions);
            }
            return completions;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("set")) {
            List<String> availableSubSubCommands = new ArrayList<>(Arrays.asList(SUBSUBCOMMANDS));

            // Check if any of the subsubcommands are already present
            for (int i = 2; i < args.length - 1; i++) {
                for (String subSubCmd : SUBSUBCOMMANDS) {
                    if (args[i].startsWith(subSubCmd)) {
                        availableSubSubCommands.remove(subSubCmd);
                    }
                }
            }

            // Suggest remaining subsubcommands
            StringUtil.copyPartialMatches(args[args.length - 1], availableSubSubCommands, completions);

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

    public List<Component> convertToComponents(Set<Goal> goals) {
        List<Component> pages = new ArrayList<>();
        List<Goal> goalList = new ArrayList<>(goals);

        for (int i = goalList.size() - 1; i >= 0; i--) {
            Goal goal = goalList.get(i);
            String goalName = goal.getName();
            long timeRequired = goal.getTime();

            Component pageContent = Component.text()
                    .append(Component.text("Name: ").color(TextColor.color(0xFFAA00)))
                    .append(Component.text(goalName + "\n\n"))
                    .append(Component.text("Time: ").color(TextColor.color(0xFFAA00)))
                    .append(Component.text(timeRequired == Long.MAX_VALUE ? "None\n\n" : convertTime(timeRequired / 20) + "\n\n"))
                    .build();

            pages.add(pageContent);
        }

        return pages;
    }
}