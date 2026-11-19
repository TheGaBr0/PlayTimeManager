package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Database.DAOs.JournalDAO.Event;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Journal.JournalManager;
import me.thegabro.playtimemanager.Journal.JournalReport;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /playtimejournal <player> <from> [HH:mm] <to> [HH:mm] [pN]
 */
public class PlaytimeJournal implements CommandExecutor, TabCompleter {
    private static final int LINES_PER_PAGE = 12;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final ZoneId zone = ZoneId.systemDefault();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("playtime.journal")) {
            sender.sendMessage(Utils.parseColors(Utils.withPrefix(config.getString("no-permission"))));
            return false;
        }

        int page = 1;
        if (args.length > 3 && isPageArg(args[args.length - 1])) {
            page = Math.max(1, parsePage(args[args.length - 1]));
            args = Arrays.copyOf(args, args.length - 1);
        }
        final int requestedPage = page;
        final String commandBase = "/" + label + " " + String.join(" ", args);

        long[] range = args.length >= 3 ? parseRange(args) : null;
        if (range == null) {
            sender.sendMessage(Utils.parseColors(Utils.withPrefix(
                    "&cUsage: /" + label + " <player> <from: yyyy-MM-dd [HH:mm]> <to: yyyy-MM-dd [HH:mm]> [pN]")));
            return false;
        }
        long from = range[0];
        long to = range[1];
        if (to <= from) {
            sender.sendMessage(Utils.parseColors(Utils.withPrefix("&cThe end of the period must be after its start")));
            return false;
        }

        DBUsersManager.getInstance().getUserFromNicknameAsyncWithContext(args[0], "ptjournal command", user -> {
            if (user == null) {
                sendSync(sender, List.of("&cPlayer not found"));
                return;
            }

            // Make sure the sessions open right now are reported up to this very moment
            JournalManager.getInstance().flush().thenRun(() ->
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        JournalReport report = JournalReport.build(
                                DatabaseHandler.getInstance().getJournalDAO(), user.getUuid(), from, to);
                        sendPaged(sender, format(user.getNickname(), report, from, to), requestedPage, commandBase);
                    }));
        });

        return true;
    }

    private List<String> format(String nickname, JournalReport r, long from, long to) {
        List<String> lines = new ArrayList<>();
        lines.add("&7Journal of &e" + nickname + "&7 from &e" + formatInstant(from) + "&7 to &e" + formatInstant(to));
        lines.add("&7- Sessions: &e" + r.sessions());
        lines.add("&7- Online: &e" + duration(r.online())
                + "&7 (visible: &e" + duration(r.onlineVisible()) + "&7, vanished: &e" + duration(r.vanish()) + "&7)");
        lines.add("&7- AFK: &e" + duration(r.afk())
                + "&7 (visible: &e" + duration(r.afkVisible()) + "&7, vanished: &e" + duration(r.afkWhileVanished()) + "&7)");

        lines.add("&7- Goals completed: &e" + r.goals().size());
        addEvents(lines, r.goals(), false);

        lines.add("&7- Join streak rewards received: &e" + r.rewards().size());
        addEvents(lines, r.rewards(), true);
        return lines;
    }

    private void addEvents(List<String> lines, List<Event> events, boolean reward) {
        for (Event e : events) {
            String name = e.label();
            if (reward) {
                String[] parts = e.label().split(":");
                name = "Reward #" + parts[0] + " (" + parts[1] + " joins)";
            }
            lines.add("&7    * &f" + name + " &8" + formatInstant(e.at()));
        }
    }

    private String duration(long millis) {
        return Utils.ticksToFormattedPlaytime(millis / 50);
    }

    private String formatInstant(long epochMillis) {
        return DATE_TIME.format(java.time.Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    private void sendSync(CommandSender sender, List<String> lines) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String line : lines) {
                sender.sendMessage(Utils.parseColors(line.startsWith("&7Journal") || line.startsWith("&c")
                        ? Utils.withPrefix(line) : line));
            }
        });
    }

    /** Sends the title line, then one page of the body, followed by clickable navigation when there are several pages. */
    private void sendPaged(CommandSender sender, List<String> lines, int page, String commandBase) {
        String title = lines.get(0);
        List<String> body = lines.subList(1, lines.size());
        int totalPages = Math.max(1, (body.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
        int current = Math.min(page, totalPages);
        int start = (current - 1) * LINES_PER_PAGE;
        List<String> pageLines = new ArrayList<>();
        pageLines.add(title);
        pageLines.addAll(body.subList(start, Math.min(body.size(), start + LINES_PER_PAGE)));

        Component nav = null;
        if (totalPages > 1) {
            Component prev = current > 1
                    ? Utils.parseColors("&e[<<]")
                            .clickEvent(ClickEvent.runCommand(commandBase + " p" + (current - 1)))
                            .hoverEvent(HoverEvent.showText(Utils.parseColors("&7Previous page")))
                    : Utils.parseColors("&8[<<]");
            Component next = current < totalPages
                    ? Utils.parseColors("&e[>>]")
                            .clickEvent(ClickEvent.runCommand(commandBase + " p" + (current + 1)))
                            .hoverEvent(HoverEvent.showText(Utils.parseColors("&7Next page")))
                    : Utils.parseColors("&8[>>]");
            nav = prev.append(Utils.parseColors(" &7Page &e" + current + "&7/&e" + totalPages + " ")).append(next);
        }

        final Component footer = nav;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String line : pageLines) {
                sender.sendMessage(Utils.parseColors(line.startsWith("&7Journal") || line.startsWith("&c")
                        ? Utils.withPrefix(line) : line));
            }
            if (footer != null) sender.sendMessage(footer);
        });
    }

    private boolean isPageArg(String s) {
        return s.length() > 1 && (s.charAt(0) == 'p' || s.charAt(0) == 'P') && s.substring(1).chars().allMatch(Character::isDigit);
    }

    private int parsePage(String s) {
        try {
            return Integer.parseInt(s.substring(1));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Parses "from-date [time] to-date [time]" starting at args[1].
     */
    private long[] parseRange(String[] args) {
        try {
            int i = 1;
            LocalDate fromDate = LocalDate.parse(args[i++]);
            LocalTime fromTime = LocalTime.MIDNIGHT;
            if (i < args.length && isTime(args[i])) {
                fromTime = LocalTime.parse(args[i++]);
            }

            if (i >= args.length) return null;
            LocalDate toDate = LocalDate.parse(args[i++]);
            LocalDateTime toDateTime;
            if (i < args.length && isTime(args[i])) {
                toDateTime = toDate.atTime(LocalTime.parse(args[i++]));
            } else {
                toDateTime = toDate.plusDays(1).atStartOfDay();
            }

            if (i != args.length) return null;

            return new long[]{
                    fromDate.atTime(fromTime).atZone(zone).toInstant().toEpochMilli(),
                    toDateTime.atZone(zone).toInstant().toEpochMilli()
            };
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isTime(String s) {
        return s.indexOf(':') >= 0;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (!sender.hasPermission("playtime.journal")) return suggestions;

        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) suggestions.add(p.getName());
            }
        } else if (args.length == 2 || args.length == 3) {
            suggestions.add(LocalDate.now(zone).toString());
        }
        return suggestions;
    }
}
