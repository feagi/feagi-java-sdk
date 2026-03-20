/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.paths;

import io.feagi.sdk.core.FeagiSdkException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Cross-platform directory management for FEAGI.
 *
 * <p>Manages two categories of directories:
 * <ul>
 *   <li><b>Hidden system directory</b> (~/.feagi/) - config, logs, cache</li>
 *   <li><b>Visible user content directory</b> (platform-specific) - genomes, connectomes</li>
 * </ul>
 *
 * <p>Platform-specific user content root:
 * <ul>
 *   <li>Linux: ~/FEAGI/</li>
 *   <li>macOS: ~/Documents/FEAGI/</li>
 *   <li>Windows: %USERPROFILE%\Documents\FEAGI\</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * FeagiPaths paths = FeagiPaths.getInstance();
 * paths.ensureAll();
 * Path configDir = paths.getConfigDir();
 * }</pre>
 *
 * @see FeagiPaths#getInstance()
 */
public final class FeagiPaths {

    private static volatile FeagiPaths instance;

    private final Path homeDir;
    private final String osName;
    private final Path feagiHome;
    private final Path configDir;
    private final Path logsDir;
    private final Path cacheDir;
    private final Path userContentRoot;
    private final Path genomesDir;
    private final Path connectomesDir;

    private FeagiPaths() {
        this.homeDir = Path.of(System.getProperty("user.home"));
        this.osName = System.getProperty("os.name").toLowerCase();

        // Hidden system directory (~/.feagi/)
        this.feagiHome = homeDir.resolve(".feagi");
        this.configDir = feagiHome.resolve("config");
        this.logsDir = feagiHome.resolve("logs");
        this.cacheDir = feagiHome.resolve("cache");

        // Visible user content directory (platform-specific)
        if (isWindows() || isMacos()) {
            this.userContentRoot = homeDir.resolve("Documents").resolve("FEAGI");
        } else {
            // Linux and other Unix-like systems
            this.userContentRoot = homeDir.resolve("FEAGI");
        }

        this.genomesDir = userContentRoot.resolve("genomes");
        this.connectomesDir = userContentRoot.resolve("connectomes");
    }

    /**
     * Returns the singleton FeagiPaths instance.
     *
     * @return the singleton FeagiPaths instance
     */
    public static FeagiPaths getInstance() {
        FeagiPaths result = instance;
        if (result == null) {
            synchronized (FeagiPaths.class) {
                result = instance;
                if (result == null) {
                    instance = result = new FeagiPaths();
                }
            }
        }
        return result;
    }

    /**
     * Returns the hidden system directory root (~/.feagi/).
     *
     * @return the FEAGI home directory
     */
    public Path getFeagiHome() {
        return feagiHome;
    }

    /**
     * Returns the configuration directory.
     *
     * @return ~/.feagi/config/
     */
    public Path getConfigDir() {
        return configDir;
    }

    /**
     * Returns the logs directory.
     *
     * @return ~/.feagi/logs/
     */
    public Path getLogsDir() {
        return logsDir;
    }

    /**
     * Returns the cache directory.
     *
     * @return ~/.feagi/cache/
     */
    public Path getCacheDir() {
        return cacheDir;
    }

    /**
     * Returns the user content root directory (platform-specific).
     *
     * @return ~/FEAGI/ on Linux, ~/Documents/FEAGI/ on Windows/macOS
     */
    public Path getUserContentRoot() {
        return userContentRoot;
    }

    /**
     * Returns the genomes directory.
     *
     * @return user_content_root/genomes/
     */
    public Path getGenomesDir() {
        return genomesDir;
    }

    /**
     * Returns the connectomes directory.
     *
     * @return user_content_root/connectomes/
     */
    public Path getConnectomesDir() {
        return connectomesDir;
    }

    /**
     * Returns the default configuration file path.
     *
     * @return ~/.feagi/config/feagi_configuration.toml
     */
    public Path getDefaultConfigPath() {
        return configDir.resolve("feagi_configuration.toml");
    }

    /**
     * Ensures all FEAGI directories exist by creating them if necessary.
     *
     * @throws FeagiSdkException if directory creation fails
     */
    public void ensureAll() {
        ensureDir(feagiHome);
        ensureDir(configDir);
        ensureDir(logsDir);
        ensureDir(cacheDir);
        ensureDir(userContentRoot);
        ensureDir(genomesDir);
        ensureDir(connectomesDir);
    }

    /**
     * Ensures the configuration directory exists.
     *
     * @throws FeagiSdkException if creation fails
     */
    public void ensureConfigDir() {
        ensureDir(configDir);
    }

    /**
     * Ensures the logs directory exists.
     *
     * @throws FeagiSdkException if creation fails
     */
    public void ensureLogsDir() {
        ensureDir(logsDir);
    }

    /**
     * Ensures the cache directory exists.
     *
     * @throws FeagiSdkException if creation fails
     */
    public void ensureCacheDir() {
        ensureDir(cacheDir);
    }

    /**
     * Ensures the genomes directory exists.
     *
     * @throws FeagiSdkException if creation fails
     */
    public void ensureGenomesDir() {
        ensureDir(genomesDir);
    }

    /**
     * Ensures the connectomes directory exists.
     *
     * @throws FeagiSdkException if creation fails
     */
    public void ensureConnectomesDir() {
        ensureDir(connectomesDir);
    }

    private boolean isWindows() {
        return osName.contains("win") || osName.contains("windows");
    }

    private boolean isMacos() {
        return osName.contains("mac");
    }

    private void ensureDir(Path dir) {
        if (Files.isDirectory(dir)) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new FeagiSdkException("Failed to create directory: " + dir, e);
        }
    }

    /**
     * Checks if a directory is writable.
     *
     * @param dir the directory to check
     * @return true if writable, false otherwise
     */
    public boolean isWritable(Path dir) {
        return Files.isWritable(dir);
    }

    /**
     * Resets the singleton instance (useful for testing).
     */
    static void resetInstance() {
        synchronized (FeagiPaths.class) {
            instance = null;
        }
    }
}
