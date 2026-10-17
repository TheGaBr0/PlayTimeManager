package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every command declared in plugin.yml is registered with an executor and survives being run
 * without arguments, from the console.
 */
@Tag("commands")
@Tag("smoke")
class CommandsSmokeTest extends PluginTestBase {

    private List<String> declaredCommands() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(plugin.getResource("plugin.yml"), StandardCharsets.UTF_8));
        return List.copyOf(yaml.getConfigurationSection("commands").getKeys(false));
    }

    @Test
    void thereAreCommandsToTest() {
        assertTrue(declaredCommands().size() >= 13, "plugin.yml declares " + declaredCommands());
    }

    @TestFactory
    List<DynamicTest> everyCommandIsRegisteredWithAnExecutor() {
        return declaredCommands().stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            PluginCommand command = plugin.getCommand(name);
            assertNotNull(command, name + " is declared in plugin.yml but not registered");
            assertNotNull(command.getExecutor(), name + " has no executor");
        })).toList();
    }

    @TestFactory
    List<DynamicTest> everyCommandRunsFromTheConsoleWithoutArguments() {
        return declaredCommands().stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            ConsoleCommandSenderMock console = server.getConsoleSender();
            assertDoesNotThrow(() -> {
                server.execute(name, console);
                for (int i = 0; i < 5; i++) {
                    server.getScheduler().performTicks(1);
                    Thread.sleep(30);
                }
            });
        })).toList();
    }
}
