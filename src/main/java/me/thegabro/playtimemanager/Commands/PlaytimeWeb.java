package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.Web.WebAuth;
import me.thegabro.playtimemanager.Web.WebServer;
import me.thegabro.playtimemanager.Web.WebUsers;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** /playtimeweb adduser|removeuser|users|sessions|logout - manages the web dashboard accounts. */
public class PlaytimeWeb implements TabExecutor {

    private static final String USAGE = "<adduser <name> [password] | removeuser <name> | users | sessions | logout [name]>";

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    private void msg(CommandSender s, String text) {
        s.sendMessage(Utils.parseColors(Utils.withPrefix(text)));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("playtime.web")) {
            msg(sender, "You don't have the permission to execute this command!");
            return false;
        }
        WebServer web = plugin.getWebServer();
        if (web == null) {
            msg(sender, "The web dashboard is disabled. Set &eweb.enabled: true&7 in config.yml and restart.");
            return true;
        }
        WebAuth auth = web.getAuth();
        WebUsers users = auth.getUsers();

        if (args.length == 0) {
            msg(sender, "Usage: &e/" + label + " " + USAGE);
            return true;
        }

        try {
            switch (args[0].toLowerCase()) {
                case "adduser" -> {
                    if (args.length < 2) { msg(sender, "Usage: &e/" + label + " adduser <name> [password]"); return true; }
                    String name = args[1];
                    if (!WebUsers.validName(name)) {
                        msg(sender, "&cInvalid name: use 1-32 letters, digits, &e_ . -");
                        return true;
                    }
                    boolean generated = args.length < 3;
                    String password = generated ? users.generatePassword() : args[2];
                    boolean existed = users.exists(name);
                    if (!users.set(name, password)) {
                        msg(sender, "&cThe password must be at least " + WebUsers.MIN_PASSWORD_LENGTH + " characters.");
                        return true;
                    }
                    auth.logoutUser(name); // a changed password closes the sessions opened with the old one
                    msg(sender, (existed ? "&aPassword changed" : "&aAccount created") + "&7 for &e" + name
                            + (generated ? "&7. Password: &e" + password + "&7 (shown only now)" : "&7."));
                    plugin.getLogger().info("[Web] " + sender.getName() + (existed ? " changed the password of " : " created the account ") + name);
                }
                case "removeuser" -> {
                    if (args.length < 2) { msg(sender, "Usage: &e/" + label + " removeuser <name>"); return true; }
                    if (users.remove(args[1])) {
                        auth.logoutUser(args[1]);
                        msg(sender, "&aAccount removed&7: &e" + args[1]);
                        plugin.getLogger().info("[Web] " + sender.getName() + " removed the account " + args[1]);
                    } else {
                        msg(sender, "&cNo such account.");
                    }
                }
                case "users" -> {
                    List<String> names = users.names();
                    msg(sender, names.isEmpty() ? "No dashboard accounts. Create one with &e/" + label + " adduser <name>"
                            : "Dashboard accounts (&e" + names.size() + "&7): &e" + String.join("&7, &e", names));
                }
                case "sessions" -> {
                    List<WebAuth.Session> list = auth.activeSessions();
                    if (list.isEmpty()) { msg(sender, "No active web sessions."); return true; }
                    msg(sender, "Active web sessions: &e" + list.size());
                    long now = System.currentTimeMillis();
                    for (WebAuth.Session s : list) {
                        msg(sender, "&7- &e" + s.user() + "&7 from &e" + s.ip() + "&7, opened "
                                + Duration.ofMillis(now - s.created()).toMinutes() + " min ago");
                    }
                }
                case "logout" -> {
                    if (args.length >= 2) {
                        auth.logoutUser(args[1]);
                        msg(sender, "Sessions of &e" + args[1] + "&7 revoked.");
                    } else {
                        auth.logoutAll();
                        msg(sender, "All web sessions have been revoked.");
                    }
                }
                default -> msg(sender, "Usage: &e/" + label + " " + USAGE);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[Web] Could not save the accounts file: " + e.getMessage());
            msg(sender, "&cCould not save the accounts file, see the console.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("playtime.web")) return List.of();
        if (args.length == 1) return List.of("adduser", "removeuser", "users", "sessions", "logout");
        WebServer web = plugin.getWebServer();
        if (args.length == 2 && web != null && List.of("removeuser", "logout").contains(args[0].toLowerCase())) {
            return web.getAuth().getUsers().names();
        }
        return List.of();
    }
}
