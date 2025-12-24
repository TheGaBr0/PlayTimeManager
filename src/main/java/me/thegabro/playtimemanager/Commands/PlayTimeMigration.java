package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PlayTimeMigration implements CommandExecutor, TabCompleter {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final Configuration config = Configuration.getInstance();
    private final CommandsConfiguration cmdConfig = CommandsConfiguration.getInstance();

    // Supported database types
    private static final List<String> VALID_DB_TYPES = Arrays.asList(
            "mysql",
            "mariadb",
            "postgresql",
            "sqlite"
    );

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        // Only allow console to use this command
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Utils.parseColors(cmdConfig.getString("prefix") + cmdConfig.getString("console-only-command")));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start":
                if (args.length < 2) {
                    sender.sendMessage(Utils.parseColors("&cUsage: /playtimemigration start <database_type>"));
                    sender.sendMessage(Utils.parseColors("&cAvailable types: " + String.join(", ", VALID_DB_TYPES)));
                    return true;
                }
                return handleMigrationStart(sender, args[1]);

            case "cancel":
                return handleMigrationCancel(sender);

            case "status":
                return handleMigrationStatus(sender);

            default:
                sendUsage(sender);
                return true;
        }
    }

    /**
     * Starts the migration process by setting the target database type
     */
    private boolean handleMigrationStart(CommandSender sender, String targetDbType) {
        targetDbType = targetDbType.toLowerCase();

        if (!VALID_DB_TYPES.contains(targetDbType)) {
            sender.sendMessage(Utils.parseColors("&cInvalid database type: &e" + targetDbType));
            sender.sendMessage(Utils.parseColors("&cAvailable types: &e" + String.join(", ", VALID_DB_TYPES)));
            return true;
        }

        String currentDbType = config.getString("database-type");
        if (currentDbType == null) {
            currentDbType = "sqlite";
        }

        if (currentDbType.equalsIgnoreCase(targetDbType)) {
            sender.sendMessage(Utils.parseColors("&cYou are already using &e" + targetDbType + "&c as your database type!"));
            sender.sendMessage(Utils.parseColors("&cNo migration needed."));
            return true;
        }

        String pendingMigration = config.getString("migrating-to");
        if (pendingMigration != null && !pendingMigration.equalsIgnoreCase("none")) {
            sender.sendMessage(Utils.parseColors("&cThere is already a pending migration to &e" + pendingMigration));
            sender.sendMessage(Utils.parseColors("&cPlease cancel it first or wait for the server to restart."));
            return true;
        }

        config.set("migrating-to", targetDbType);

        sender.sendMessage(Utils.parseColors("&a&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        sender.sendMessage(Utils.parseColors("&a&lMigration Scheduled"));
        sender.sendMessage(Utils.parseColors(""));
        sender.sendMessage(Utils.parseColors("&7Current database: &e" + currentDbType));
        sender.sendMessage(Utils.parseColors("&7Target database: &e" + targetDbType));
        sender.sendMessage(Utils.parseColors(""));
        sender.sendMessage(Utils.parseColors("&6⚠ &eThe migration will start on the next server reload/restart"));
        sender.sendMessage(Utils.parseColors("&6⚠ &ePlease make sure to correctly setup "+targetDbType));
        sender.sendMessage(Utils.parseColors("&6⚠ &edata fields in the config.yml before booting the server."));
        sender.sendMessage(Utils.parseColors(""));
        sender.sendMessage(Utils.parseColors("&7To cancel this migration, use:"));
        sender.sendMessage(Utils.parseColors("&f/playtimemigration cancel"));
        sender.sendMessage(Utils.parseColors("&a&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

        plugin.getLogger().info("Migration scheduled from " + currentDbType + " to " + targetDbType + " by console");

        return true;
    }

    /**
     * Cancels a pending migration
     */
    private boolean handleMigrationCancel(CommandSender sender) {
        String pendingMigration = config.getString("migrating-to");

        if (pendingMigration == null || pendingMigration.equalsIgnoreCase("none")) {
            sender.sendMessage(Utils.parseColors("&cThere is no pending migration to cancel."));
            return true;
        }

        config.set("migrating-to", "none");

        sender.sendMessage(Utils.parseColors("&a&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        sender.sendMessage(Utils.parseColors("&a&lMigration Cancelled"));
        sender.sendMessage(Utils.parseColors(""));
        sender.sendMessage(Utils.parseColors("&7The scheduled migration to &e" + pendingMigration + " &7has been cancelled."));
        sender.sendMessage(Utils.parseColors("&a&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

        plugin.getLogger().info("Migration cancelled by console");

        return true;
    }

    /**
     * Shows the current migration status
     */
    private boolean handleMigrationStatus(CommandSender sender) {
        String currentDbType = config.getString("database-type");
        if (currentDbType == null) {
            currentDbType = "sqlite";
        }

        String pendingMigration = config.getString("migrating-to");
        if (pendingMigration == null) {
            pendingMigration = "none";
        }

        sender.sendMessage(Utils.parseColors("&a&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        sender.sendMessage(Utils.parseColors("&a&lMigration Status"));
        sender.sendMessage(Utils.parseColors(""));
        sender.sendMessage(Utils.parseColors("&7Current database type: &e" + currentDbType));
        sender.sendMessage(Utils.parseColors("&7Pending migration: &e" + pendingMigration));
        sender.sendMessage(Utils.parseColors(""));

        if (!pendingMigration.equalsIgnoreCase("none")) {
            sender.sendMessage(Utils.parseColors("&6⚠ &eA migration is scheduled for the next reload/restart"));
            sender.sendMessage(Utils.parseColors("&7To cancel: &f/playtimemigration cancel"));
        } else {
            sender.sendMessage(Utils.parseColors("&aNo pending migrations"));
        }

        sender.sendMessage(Utils.parseColors("&a&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

        return true;
    }

    /**
     * Sends the command usage information
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Utils.parseColors("&a&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        sender.sendMessage(Utils.parseColors("&a&lPlayTime Migration"));
        sender.sendMessage(Utils.parseColors(""));
        sender.sendMessage(Utils.parseColors("&f/playtimemigration start <type> &7- Schedule a migration"));
        sender.sendMessage(Utils.parseColors("&f/playtimemigration cancel &7- Cancel pending migration"));
        sender.sendMessage(Utils.parseColors("&f/playtimemigration status &7- Check migration status"));
        sender.sendMessage(Utils.parseColors(""));
        sender.sendMessage(Utils.parseColors("&7Available database types:"));
        sender.sendMessage(Utils.parseColors("&e" + String.join(", ", VALID_DB_TYPES)));
        sender.sendMessage(Utils.parseColors("&a&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (!(sender instanceof ConsoleCommandSender)) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("start");
            completions.add("cancel");
            completions.add("status");

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return VALID_DB_TYPES.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}