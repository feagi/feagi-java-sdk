/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.paths;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for FeagiPaths.
 */
public class FeagiPathsTest {

    @Test
    public void testSingletonInstance() {
        FeagiPaths paths1 = FeagiPaths.getInstance();
        FeagiPaths paths2 = FeagiPaths.getInstance();
        assertSame(paths1, paths2, "FeagiPaths should be a singleton");
    }

    @Test
    public void testConfigDirNotNull() {
        FeagiPaths paths = FeagiPaths.getInstance();
        assertNotNull(paths.getConfigDir(), "Config dir should not be null");
    }

    @Test
    public void testLogsDirNotNull() {
        FeagiPaths paths = FeagiPaths.getInstance();
        assertNotNull(paths.getLogsDir(), "Logs dir should not be null");
    }

    @Test
    public void testCacheDirNotNull() {
        FeagiPaths paths = FeagiPaths.getInstance();
        assertNotNull(paths.getCacheDir(), "Cache dir should not be null");
    }

    @Test
    public void testGenomesDirNotNull() {
        FeagiPaths paths = FeagiPaths.getInstance();
        assertNotNull(paths.getGenomesDir(), "Genomes dir should not be null");
    }

    @Test
    public void testConnectomesDirNotNull() {
        FeagiPaths paths = FeagiPaths.getInstance();
        assertNotNull(paths.getConnectomesDir(), "Connectomes dir should not be null");
    }

    @Test
    public void testDefaultConfigPath() {
        FeagiPaths paths = FeagiPaths.getInstance();
        Path configPath = paths.getDefaultConfigPath();
        assertNotNull(configPath, "Default config path should not be null");
        assertTrue(configPath.getFileName().toString().equals("feagi_configuration.toml"),
                   "Default config should be feagi_configuration.toml");
    }

    @Test
    public void testEnsureAllCreatesDirectories() {
        FeagiPaths paths = FeagiPaths.getInstance();
        // This should not throw any exceptions
        assertDoesNotThrow(() -> paths.ensureAll(), "ensureAll() should create directories");
    }
}
