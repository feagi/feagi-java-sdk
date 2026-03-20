/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for FeagiConfig.
 */
public class FeagiConfigTest {

    @TempDir
    Path tempDir;

    @Test
    public void testGenerateDefaultConfig() throws Exception {
        Path configPath = FeagiConfig.generateDefaultConfig(tempDir.resolve("test.toml"), false);
        assertTrue(Files.exists(configPath), "Config file should be created");

        String content = Files.readString(configPath);
        assertTrue(content.contains("[api]"), "Config should contain [api] section");
        assertTrue(content.contains("[websocket]"), "Config should contain [websocket] section");
        assertTrue(content.contains("[burst_engine]"), "Config should contain [burst_engine] section");
    }

    @Test
    public void testGenerateDefaultConfigFailsOnExistingFile() throws Exception {
        Path configPath = tempDir.resolve("test.toml");
        FeagiConfig.generateDefaultConfig(configPath, false);

        assertThrows(
            io.feagi.sdk.core.FeagiSdkException.class,
            () -> FeagiConfig.generateDefaultConfig(configPath, false)
        );
    }

    @Test
    public void testGenerateDefaultConfigOverwritesWithForce() throws Exception {
        Path configPath = tempDir.resolve("test.toml");
        FeagiConfig.generateDefaultConfig(configPath, false);

        assertDoesNotThrow(
            () -> FeagiConfig.generateDefaultConfig(configPath, true)
        );
    }

    @Test
    public void testLoadFromToml() throws Exception {
        Path configPath = FeagiConfig.generateDefaultConfig(tempDir.resolve("test.toml"), false);

        FeagiConfig config = FeagiConfig.loadFromToml(configPath);

        assertEquals("127.0.0.1", config.getApiHost());
        assertEquals(8000, config.getApiPort());
        assertEquals(5558, config.getSensoryPort());
        assertEquals(5564, config.getMotorPort());
        assertEquals(8080, config.getVisualizationPort());
        assertTrue(config.isWebsocketEnabled());
    }

    @Test
    public void testValidatePassesForDefaultConfig() throws Exception {
        Path configPath = FeagiConfig.generateDefaultConfig(tempDir.resolve("test.toml"), false);
        FeagiConfig config = FeagiConfig.loadFromToml(configPath);

        assertDoesNotThrow(() -> config.validate("test.toml"));
    }

    @Test
    public void testLoadFromNonExistentFile() {
        Path nonExistent = tempDir.resolve("nonexistent.toml");

        assertThrows(
            io.feagi.sdk.core.FeagiSdkException.class,
            () -> FeagiConfig.loadFromToml(nonExistent)
        );
    }
}
