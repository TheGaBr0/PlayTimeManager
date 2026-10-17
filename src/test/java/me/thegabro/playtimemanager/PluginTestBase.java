package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Journal.JournalManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.mockbukkit.mockbukkit.command.MessageTarget;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Starts a simulated server with the plugin enabled once per test class.
 * <p>
 * The plugin keeps singletons and {@code static final} references to the plugin instance, which
 * cannot be reset between tests. For this reason surefire runs every test class in its own JVM
 * (see {@code reuseForks} in the pom) and the tests of a class share one server: give each test its
 * own player name so they do not interfere.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PluginTestBase {
    protected ServerMock server;
    protected PlayTimeManager plugin;
    private File dataFolder;

    @BeforeAll
    void startServer() throws Exception {
        server = MockBukkit.mock();
        seedConfig();
        // PlaceholderAPI is emulated: this makes the plugin believe it is installed, the behaviour
        // comes from the PlaceholderAPI/PlaceholderExpansion test doubles in src/test/java
        MockBukkit.createMockPlugin("PlaceholderAPI");
        plugin = MockBukkit.load(PlayTimeManager.class);
        dataFolder = plugin.getDataFolder();
        // Keep the test output readable: the plugin's warnings and info lines are expected noise here,
        // while real errors (SEVERE) stay visible
        plugin.getLogger().setLevel(Level.SEVERE);
        // What the plugin queued while starting (e.g. the leaderboard) only runs when the simulated server
        // ticks: let it finish now, before a test changes the database under it
        drain();
    }

    /**
     * The default config expects LuckPerms, which the tests do not have, and the plugin would warn
     * about it on every start: write the default config with that option turned off before loading.
     */
    private void seedConfig() {
        try (InputStream defaults = PlayTimeManager.class.getResourceAsStream("/config.yml")) {
            String text = new String(defaults.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("permissions-manager-plugin: \"luckperms\"", "permissions-manager-plugin: \"none\"");
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(text);
            customizeConfig(config);

            // MockBukkit names the data folder "<name>-<version>"
            YamlConfiguration description = YamlConfiguration.loadConfiguration(new InputStreamReader(
                    PlayTimeManager.class.getResourceAsStream("/plugin.yml"), StandardCharsets.UTF_8));
            File folder = new File(server.getPluginsFolder(),
                    description.getString("name") + "-" + description.getString("version"));
            Files.createDirectories(folder.toPath());
            Files.writeString(folder.toPath().resolve("config.yml"), config.saveToString());
        } catch (Exception e) {
            throw new IllegalStateException("Could not prepare the test config", e);
        }
    }

    /** Lets a test class change the config the plugin starts with (e.g. to use another database). */
    protected void customizeConfig(YamlConfiguration config) {
    }

    @AfterAll
    void stopServer() throws Exception {
        // unmock() waits for the running async tasks (and gives up after a minute): let them finish first
        drain();
        MockBukkit.unmock();
        if (dataFolder != null && dataFolder.exists()) {
            try (Stream<Path> files = Files.walk(dataFolder.toPath())) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        afterServerStopped();
    }

    /**
     * Called once the plugin is disabled. Whatever the plugin talks to (e.g. a database container)
     * has to be stopped here and not in a subclass @AfterAll, which JUnit runs BEFORE this class'
     * one: the plugin would then spend its shutdown waiting for a database that is already gone.
     */
    protected void afterServerStopped() {
    }

    /**
     * Waits for the command output of an async command and returns it as plain text lines.
     * Everything the plugin sends in one go arrives in the same tick, so it stops after a quiet poll.
     */
    protected List<String> awaitMessages(MessageTarget sender) throws Exception {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            server.getScheduler().performTicks(1);
            Thread.sleep(30);
            Component message;
            boolean got = false;
            while ((message = sender.nextComponentMessage()) != null) {
                lines.add(PlainTextComponentSerializer.plainText().serialize(message));
                got = true;
            }
            if (!got && !lines.isEmpty()) return lines;
        }
        return lines;
    }

    /** Runs something that is expected to log errors (e.g. a refused upgrade) without printing them. */
    protected <T> T quietly(Supplier<T> action) {
        Level previous = plugin.getLogger().getLevel();
        plugin.getLogger().setLevel(Level.OFF);
        try {
            return action.get();
        } finally {
            plugin.getLogger().setLevel(previous);
        }
    }

    /** Runs a command as {@code sender} and returns everything it answered, joined into one string. */
    protected String run(CommandSender sender, String command, String... args) throws Exception {
        // The console also receives the plugin's own log lines: drop whatever is already queued
        while (((MessageTarget) sender).nextComponentMessage() != null) { /* discard */ }
        server.execute(command, sender, args);
        String output = String.join("\n", awaitMessages((MessageTarget) sender));
        drain(); // what the command started in the background (database writes, leaderboard)
        return output;
    }

    /**
     * Advances the simulated server until no async task of the plugin is running anymore.
     * MockBukkit runs an async task only when its scheduler ticks, so a task that waits for another
     * one (e.g. the leaderboard update) would otherwise stay blocked, and results would not be ready.
     */
    protected void drain() throws Exception {
        int idle = 0;
        for (int i = 0; i < 200 && idle < 3; i++) {
            server.getScheduler().performOneTick();
            Thread.sleep(15);
            idle = server.getScheduler().getActiveWorkers().isEmpty() ? idle + 1 : 0;
        }
    }

    /** Lets the async join/quit handlers and the journal writer thread finish. */
    protected void settle() throws Exception {
        // Not waitAsyncTasksFinished(): the plugin owns repeating async tasks, so it would never return
        for (int i = 0; i < 10; i++) {
            server.getScheduler().performTicks(1);
            Thread.sleep(30);
        }
        JournalManager.getInstance().flush().get(10, TimeUnit.SECONDS);
        drain();
    }
}
