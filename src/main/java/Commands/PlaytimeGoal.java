package Commands;

import GUIs.AllGoalsGui;
import Goals.Goal;
import Goals.GoalManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;
public class PlaytimeGoal implements TabExecutor {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final String[] SUBCOMMANDS = {"set", "remove"};  // Removed "list" from subcommands
    private final String[] SUBSUBCOMMANDS = {"time:", "activate:"};

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (!sender.hasPermission("playtime.goal")) {
            sender.sendMessage("[§6PlayTime§eManager§f]§7 You don't have the permission to execute this command");
            return false;
        }

        // If no arguments provided and sender is a player, open GUI
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("[§6PlayTime§eManager§f]§7 Only players can use the GUI!");
                return false;
            }
            AllGoalsGui gui = new AllGoalsGui();
            gui.openInventory((Player) sender);
            return true;
        }

        String goalName;
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "set":
                if (args.length < 2) {
                    sender.sendMessage("[§6PlayTime§eManager§f]§7 Usage: /playtimegoal set <goalName> [time:<time>] [activate:true|false]");
                    return false;
                }

                goalName = args[1];
                String time = null;
                boolean activate = false;

                // Process optional arguments
                for (int i = 2; i < args.length; i++) {
                    if (args[i].startsWith("time:")) {
                        time = args[i].substring(5);
                        if (time.isEmpty()) {
                            sender.sendMessage("[§6PlayTime§eManager§f]§7 Missing time value!");
                            return false;
                        }
                        long timeToTicks = parseTime(time);
                        if (timeToTicks == -1) {
                            sender.sendMessage("[§6PlayTime§eManager§f]§7 Invalid time format!");
                            return false;
                        }
                    } else if (args[i].startsWith("activate:")) {
                        String activateValue = args[i].substring(9).toLowerCase();
                        if (activateValue.equals("true")) {
                            activate = true;
                        } else if (!activateValue.equals("false")) {
                            sender.sendMessage("[§6PlayTime§eManager§f]§7 Invalid activate value! Use true or false");
                            return false;
                        }
                    } else {
                        sender.sendMessage("[§6PlayTime§eManager§f]§7 Invalid argument: " + args[i]);
                        return false;
                    }
                }

                setGoal(sender, goalName, time != null ? parseTime(time) : null, activate);
                break;
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage("[§6PlayTime§eManager§f]§7 Usage: /playtimegoal remove <goalName>");
                    return false;
                }
                goalName = args[1];
                removeGoal(sender, goalName);
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

    private void setGoal(CommandSender sender, String goalName, Long time, boolean activate) {
        Goal g = GoalManager.getGoal(goalName);
        StringBuilder message = new StringBuilder();

        if (g != null) {
            message.append("[§6PlayTime§eManager§f]§7 Goal §e").append(goalName).append(" §7updated:\n");
            if(time != null)
                g.setTime(time);
        } else {
            g = new Goal(plugin, goalName, time, activate);
            message.append("[§6PlayTime§eManager§f]§7 Goal §e").append(goalName).append(" §7created:\n");
        }

        g.setActivation(activate);

        long gTime = g.getTime();
        if(gTime == Long.MAX_VALUE)
            message.append("§7- Required time to reach the goal: §6None\n");
        else
            message.append("§7- Required time to reach the goal: §6").append(convertTime(gTime / 20)).append("\n");
        message.append("§7- Active: ").append(activate ? "§a" : "§c").append(activate).append("\n");

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

    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, String[] args) {
        final List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList(SUBCOMMANDS), completions);
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set")) {
                StringUtil.copyPartialMatches(args[1], GoalManager.getGoalsNames(), completions);
            } else if (args[0].equalsIgnoreCase("remove")) {
                StringUtil.copyPartialMatches(args[1], GoalManager.getGoalsNames(), completions);
            }
            return completions;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("set")) {
            List<String> availableSubSubCommands = new ArrayList<>(Arrays.asList(SUBSUBCOMMANDS));

            // Remove already used arguments
            for (int i = 2; i < args.length - 1; i++) {
                for (String subSubCmd : SUBSUBCOMMANDS) {
                    if (args[i].startsWith(subSubCmd)) {
                        availableSubSubCommands.remove(subSubCmd);
                    }
                }
            }

            StringUtil.copyPartialMatches(args[args.length - 1], availableSubSubCommands, completions);
            return completions;
        }

        return null;
    }
}