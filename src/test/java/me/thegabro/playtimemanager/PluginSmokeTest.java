package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("smoke")
class PluginSmokeTest extends PluginTestBase {

    @Test
    void pluginEnables() {
        assertTrue(plugin.isEnabled());
    }
}
