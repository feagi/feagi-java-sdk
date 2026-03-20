/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.config;

import io.feagi.sdk.core.FeagiSdkException;
import io.feagi.sdk.paths.FeagiPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * FEAGI configuration management.
 *
 * <p>Handles loading, validating, and generating FEAGI configuration files.
 * Configuration is stored in TOML format with the following sections:
 * <ul>
 *   <li>{@code [api]} - API server host and port</li>
 *   <li>{@code [websocket]} - WebSocket settings (enabled, ports)</li>
 *   <li>{@code [burst_engine]} - Neural engine burst settings</li>
 *   <li>{@code [performance]} - Worker threads and profiling</li>
 *   <li>{@code [logging]} - Log level and file logging</li>
 *   <li>{@code [timeouts]} - Service startup timeout</li>
 *   <li>{@code [connectome]} - Neuron and synapse space allocation</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Generate default config
 * Path configPath = FeagiConfig.generateDefaultConfig();
 *
 * // Load and validate config
 * FeagiConfig config = FeagiConfig.loadFromToml(configPath);
 * String host = config.getApiHost();  // "127.0.0.1"
 * int port = config.getApiPort();     // 8000
 * }</pre>
 *
 * @see FeagiConfig#generateDefaultConfig()
 * @see FeagiConfig#loadFromToml(Path)
 */
public final class FeagiConfig {

    private static final Logger logger = Logger.getLogger(FeagiConfig.class.getName());

    // Default configuration content
    private static final String DEFAULT_CONFIG_CONTENT = """
        # FEAGI Configuration File
        # This file is auto-generated. Modify as needed for your deployment.

        [api]
        host = "127.0.0.1"
        port = 8000

        [websocket]
        host = "127.0.0.1"
        enabled = true
        visualization_port = 8080
        sensory_port = 5558
        motor_port = 5564

        [burst_engine]
        burst_duration = 0.01
        max_bursts = 0
        gpu_enabled = false

        [performance]
        worker_threads = 4
        profiling_enabled = false

        [logging]
        level = "info"
        file_logging = true

        [timeouts]
        service_startup = 3.0

        [connectome]
        neuron_space = 1000000
        synapse_space = 10000000
        """;

    // API settings
    private final String apiHost;
    private final int apiPort;

    // WebSocket settings
    private final String websocketHost;
    private final boolean websocketEnabled;
    private final int visualizationPort;
    private final int sensoryPort;
    private final int motorPort;

    // Burst engine settings
    private final double burstDuration;
    private final int maxBursts;
    private final boolean gpuEnabled;

    // Performance settings
    private final int workerThreads;
    private final boolean profilingEnabled;

    // Logging settings
    private final String logLevel;
    private final boolean fileLogging;

    // Timeouts
    private final Duration serviceStartupTimeout;

    // Connectome settings
    private final int neuronSpace;
    private final int synapseSpace;

    private FeagiConfig(Builder builder) {
        this.apiHost = builder.apiHost;
        this.apiPort = builder.apiPort;
        this.websocketHost = builder.websocketHost;
        this.websocketEnabled = builder.websocketEnabled;
        this.visualizationPort = builder.visualizationPort;
        this.sensoryPort = builder.sensoryPort;
        this.motorPort = builder.motorPort;
        this.burstDuration = builder.burstDuration;
        this.maxBursts = builder.maxBursts;
        this.gpuEnabled = builder.gpuEnabled;
        this.workerThreads = builder.workerThreads;
        this.profilingEnabled = builder.profilingEnabled;
        this.logLevel = builder.logLevel;
        this.fileLogging = builder.fileLogging;
        this.serviceStartupTimeout = builder.serviceStartupTimeout;
        this.neuronSpace = builder.neuronSpace;
        this.synapseSpace = builder.synapseSpace;
    }

    // Getters
    public String getApiHost() { return apiHost; }
    public int getApiPort() { return apiPort; }
    public String getWebsocketHost() { return websocketHost; }
    public boolean isWebsocketEnabled() { return websocketEnabled; }
    public int getVisualizationPort() { return visualizationPort; }
    public int getSensoryPort() { return sensoryPort; }
    public int getMotorPort() { return motorPort; }
    public double getBurstDuration() { return burstDuration; }
    public int getMaxBursts() { return maxBursts; }
    public boolean isGpuEnabled() { return gpuEnabled; }
    public int getWorkerThreads() { return workerThreads; }
    public boolean isProfilingEnabled() { return profilingEnabled; }
    public String getLogLevel() { return logLevel; }
    public boolean isFileLogging() { return fileLogging; }
    public Duration getServiceStartupTimeout() { return serviceStartupTimeout; }
    public int getNeuronSpace() { return neuronSpace; }
    public int getSynapseSpace() { return synapseSpace; }

    /**
     * Generates a default configuration file in the default location.
     *
     * @return path to the created configuration file
     * @throws FeagiSdkException if generation fails
     */
    public static Path generateDefaultConfig() {
        return generateDefaultConfig(null, false);
    }

    /**
     * Generates a default configuration file.
     *
     * @param outputPath target path (null for default location)
     * @param forceOverwrite if true, overwrite existing file
     * @return path to the created configuration file
     * @throws FeagiSdkException if file exists and forceOverwrite=false, or if generation fails
     */
    public static Path generateDefaultConfig(Path outputPath, boolean forceOverwrite) {
        Path targetPath = outputPath;
        if (targetPath == null) {
            FeagiPaths paths = FeagiPaths.getInstance();
            paths.ensureConfigDir();
            targetPath = paths.getDefaultConfigPath();
        }

        if (Files.exists(targetPath) && !forceOverwrite) {
            throw new FeagiSdkException(
                "Configuration file already exists: " + targetPath +
                ". Use forceOverwrite=true to overwrite."
            );
        }

        try {
            Path parent = targetPath.getParent();
            if (parent != null && !Files.isDirectory(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(targetPath, DEFAULT_CONFIG_CONTENT);
            logger.info("Generated default config: " + targetPath);
            return targetPath;
        } catch (IOException e) {
            throw new FeagiSdkException("Failed to generate config: " + targetPath, e);
        }
    }

    /**
     * Ensures the default configuration file exists, creating it if necessary.
     *
     * @return path to the configuration file
     * @throws FeagiSdkException if creation fails
     */
    public static Path ensureDefaultConfig() {
        FeagiPaths paths = FeagiPaths.getInstance();
        Path configPath = paths.getDefaultConfigPath();

        if (!Files.exists(configPath)) {
            paths.ensureConfigDir();
            return generateDefaultConfig(configPath, false);
        }

        return configPath;
    }

    /**
     * Loads and validates a FEAGI configuration from a TOML file.
     *
     * @param tomlPath path to the TOML configuration file
     * @return validated FeagiConfig instance
     * @throws FeagiSdkException if file not found, parsing fails, or validation fails
     */
    public static FeagiConfig loadFromToml(Path tomlPath) {
        Objects.requireNonNull(tomlPath, "tomlPath must not be null");

        if (!Files.exists(tomlPath)) {
            throw new FeagiSdkException("Configuration file not found: " + tomlPath);
        }

        try {
            String content = Files.readString(tomlPath);
            TomlParser parser = new TomlParser(content);
            Map<String, Object> configMap = parser.parse();
            return fromMap(configMap, tomlPath);
        } catch (IOException e) {
            throw new FeagiSdkException("Failed to read config file: " + tomlPath, e);
        } catch (TomlParseException e) {
            throw new FeagiSdkException("Failed to parse TOML: " + tomlPath + " - " + e.getMessage(), e);
        }
    }

    /**
     * Loads configuration from a map (for advanced usage).
     *
     * @param configMap configuration map
     * @param sourcePath source path for error messages
     * @return validated FeagiConfig instance
     * @throws FeagiSdkException if validation fails
     */
    private static FeagiConfig fromMap(Map<String, Object> configMap, Path sourcePath) {
        String source = sourcePath != null ? sourcePath.toString() : "in-memory";

        // Parse API section
        Map<String, Object> api = requireSection(configMap, "api", source);
        String apiHost = getString(api, "host", source);
        int apiPort = getInt(api, "port", source);

        // Parse WebSocket section
        Map<String, Object> websocket = requireSection(configMap, "websocket", source);
        String websocketHost = getString(websocket, "host", source);
        boolean websocketEnabled = getBool(websocket, "enabled", source);
        int visualizationPort = getInt(websocket, "visualization_port", source);
        int sensoryPort = getInt(websocket, "sensory_port", source);
        int motorPort = getInt(websocket, "motor_port", source);

        // Parse Burst Engine section
        Map<String, Object> burstEngine = requireSection(configMap, "burst_engine", source);
        double burstDuration = getDouble(burstEngine, "burst_duration", source);
        int maxBursts = getInt(burstEngine, "max_bursts", source);
        boolean gpuEnabled = getBool(burstEngine, "gpu_enabled", source);

        // Parse Performance section
        Map<String, Object> performance = requireSection(configMap, "performance", source);
        int workerThreads = getInt(performance, "worker_threads", source);
        boolean profilingEnabled = getBool(performance, "profiling_enabled", source);

        // Parse Logging section
        Map<String, Object> logging = requireSection(configMap, "logging", source);
        String logLevel = getString(logging, "level", source);
        boolean fileLogging = getBool(logging, "file_logging", source);

        // Parse Timeouts section
        Map<String, Object> timeouts = requireSection(configMap, "timeouts", source);
        double serviceStartupSeconds = getDouble(timeouts, "service_startup", source);
        Duration serviceStartupTimeout = Duration.ofMillis((long) (serviceStartupSeconds * 1000));

        // Parse Connectome section
        Map<String, Object> connectome = requireSection(configMap, "connectome", source);
        int neuronSpace = getInt(connectome, "neuron_space", source);
        int synapseSpace = getInt(connectome, "synapse_space", source);

        // Build and validate
        FeagiConfig config = new Builder()
            .apiHost(apiHost)
            .apiPort(apiPort)
            .websocketHost(websocketHost)
            .websocketEnabled(websocketEnabled)
            .visualizationPort(visualizationPort)
            .sensoryPort(sensoryPort)
            .motorPort(motorPort)
            .burstDuration(burstDuration)
            .maxBursts(maxBursts)
            .gpuEnabled(gpuEnabled)
            .workerThreads(workerThreads)
            .profilingEnabled(profilingEnabled)
            .logLevel(logLevel)
            .fileLogging(fileLogging)
            .serviceStartupTimeout(serviceStartupTimeout)
            .neuronSpace(neuronSpace)
            .synapseSpace(synapseSpace)
            .build();

        config.validate(source);
        return config;
    }

    /**
     * Validates the configuration.
     *
     * @param source description for error messages
     * @throws FeagiSdkException if validation fails
     */
    public void validate(String source) {
        // Validate API
        if (apiHost == null || apiHost.isBlank()) {
            throw new FeagiSdkException("[" + source + "] api.host must not be empty");
        }
        validatePort(apiPort, "api.port", source);

        // Validate WebSocket
        if (websocketHost == null || websocketHost.isBlank()) {
            throw new FeagiSdkException("[" + source + "] websocket.host must not be empty");
        }
        if (websocketEnabled) {
            validatePort(visualizationPort, "websocket.visualization_port", source);
            validatePort(sensoryPort, "websocket.sensory_port", source);
            validatePort(motorPort, "websocket.motor_port", source);
        }

        // Validate Burst Engine
        if (burstDuration <= 0) {
            throw new FeagiSdkException("[" + source + "] burst_engine.burst_duration must be > 0");
        }
        if (maxBursts < 0) {
            throw new FeagiSdkException("[" + source + "] burst_engine.max_bursts must be >= 0");
        }

        // Validate Performance
        if (workerThreads <= 0) {
            throw new FeagiSdkException("[" + source + "] performance.worker_threads must be > 0");
        }

        // Validate Logging
        if (logLevel == null || logLevel.isBlank()) {
            throw new FeagiSdkException("[" + source + "] logging.level must not be empty");
        }
        if (!logLevel.matches("(?i)(debug|info|warning|error|trace)")) {
            throw new FeagiSdkException("[" + source + "] logging.level must be one of: debug, info, warning, error, trace");
        }

        // Validate Timeouts
        if (serviceStartupTimeout.isNegative() || serviceStartupTimeout.isZero()) {
            throw new FeagiSdkException("[" + source + "] timeouts.service_startup must be > 0");
        }

        // Validate Connectome
        if (neuronSpace <= 0) {
            throw new FeagiSdkException("[" + source + "] connectome.neuron_space must be > 0");
        }
        if (synapseSpace <= 0) {
            throw new FeagiSdkException("[" + source + "] connectome.synapse_space must be > 0");
        }
    }

    private static void validatePort(int port, String name, String source) {
        if (port <= 0 || port > 65535) {
            throw new FeagiSdkException("[" + source + "] " + name + " must be a valid port (1-65535)");
        }
    }

    private static Map<String, Object> requireSection(Map<String, Object> map, String section, String source) {
        Object value = map.get(section);
        if (value == null) {
            throw new FeagiSdkException("[" + source + "] Missing required section: [" + section + "]");
        }
        if (!(value instanceof Map)) {
            throw new FeagiSdkException("[" + source + "] Section [" + section + "] must be a table");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> sectionMap = (Map<String, Object>) value;
        return sectionMap;
    }

    private static String getString(Map<String, Object> map, String key, String source) {
        Object value = map.get(key);
        if (value == null) {
            throw new FeagiSdkException("[" + source + "] Missing required key: " + key);
        }
        return value.toString();
    }

    private static int getInt(Map<String, Object> map, String key, String source) {
        Object value = map.get(key);
        if (value == null) {
            throw new FeagiSdkException("[" + source + "] Missing required key: " + key);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new FeagiSdkException("[" + source + "] Key " + key + " must be an integer");
    }

    private static double getDouble(Map<String, Object> map, String key, String source) {
        Object value = map.get(key);
        if (value == null) {
            throw new FeagiSdkException("[" + source + "] Missing required key: " + key);
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new FeagiSdkException("[" + source + "] Key " + key + " must be a number");
    }

    private static boolean getBool(Map<String, Object> map, String key, String source) {
        Object value = map.get(key);
        if (value == null) {
            throw new FeagiSdkException("[" + source + "] Missing required key: " + key);
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new FeagiSdkException("[" + source + "] Key " + key + " must be a boolean");
    }

    /**
     * Builder for FeagiConfig.
     */
    public static class Builder {
        private String apiHost = "127.0.0.1";
        private int apiPort = 8000;
        private String websocketHost = "127.0.0.1";
        private boolean websocketEnabled = true;
        private int visualizationPort = 8080;
        private int sensoryPort = 5558;
        private int motorPort = 5564;
        private double burstDuration = 0.01;
        private int maxBursts = 0;
        private boolean gpuEnabled = false;
        private int workerThreads = 4;
        private boolean profilingEnabled = false;
        private String logLevel = "info";
        private boolean fileLogging = true;
        private Duration serviceStartupTimeout = Duration.ofMillis(3000);
        private int neuronSpace = 1000000;
        private int synapseSpace = 10000000;

        public Builder apiHost(String host) { this.apiHost = host; return this; }
        public Builder apiPort(int port) { this.apiPort = port; return this; }
        public Builder websocketHost(String host) { this.websocketHost = host; return this; }
        public Builder websocketEnabled(boolean enabled) { this.websocketEnabled = enabled; return this; }
        public Builder visualizationPort(int port) { this.visualizationPort = port; return this; }
        public Builder sensoryPort(int port) { this.sensoryPort = port; return this; }
        public Builder motorPort(int port) { this.motorPort = port; return this; }
        public Builder burstDuration(double duration) { this.burstDuration = duration; return this; }
        public Builder maxBursts(int max) { this.maxBursts = max; return this; }
        public Builder gpuEnabled(boolean enabled) { this.gpuEnabled = enabled; return this; }
        public Builder workerThreads(int threads) { this.workerThreads = threads; return this; }
        public Builder profilingEnabled(boolean enabled) { this.profilingEnabled = enabled; return this; }
        public Builder logLevel(String level) { this.logLevel = level; return this; }
        public Builder fileLogging(boolean enabled) { this.fileLogging = enabled; return this; }
        public Builder serviceStartupTimeout(Duration timeout) { this.serviceStartupTimeout = timeout; return this; }
        public Builder neuronSpace(int space) { this.neuronSpace = space; return this; }
        public Builder synapseSpace(int space) { this.synapseSpace = space; return this; }

        public FeagiConfig build() {
            return new FeagiConfig(this);
        }
    }

    /**
     * Simple TOML parser for FEAGI config files.
     * Supports basic TOML: strings, integers, floats, booleans, tables.
     */
    static class TomlParser {
        private final String content;
        private int pos = 0;
        private int line = 1;
        private int col = 1;

        TomlParser(String content) {
            this.content = content;
        }

        Map<String, Object> parse() throws TomlParseException {
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> currentTable = result;

            while (pos < content.length()) {
                skipWhitespaceAndComments();
                if (pos >= content.length()) break;

                char c = content.charAt(pos);

                if (c == '[') {
                    // Table header
                    String tableName = parseTableHeader();
                    currentTable = getOrCreateTable(result, tableName);
                } else if (isKeyStart(c)) {
                    // Key-value pair
                    parseKeyValue(currentTable);
                } else {
                    pos++;
                    col++;
                }
            }

            return result;
        }

        private String parseTableHeader() throws TomlParseException {
            pos++; // skip '['
            col++;

            StringBuilder name = new StringBuilder();
            while (pos < content.length() && content.charAt(pos) != ']') {
                name.append(content.charAt(pos));
                pos++;
                col++;
            }
            pos++; // skip ']'
            col++;

            return name.toString().trim();
        }

        private Map<String, Object> getOrCreateTable(Map<String, Object> result, String tableName) {
            String[] parts = tableName.split("\\.");
            Map<String, Object> current = result;

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                Object existing = current.get(part);
                if (existing == null) {
                    Map<String, Object> newTable = new HashMap<>();
                    current.put(part, newTable);
                    current = newTable;
                } else if (existing instanceof Map) {
                    current = (Map<String, Object>) existing;
                }
            }

            return current;
        }

        private void parseKeyValue(Map<String, Object> table) throws TomlParseException {
            StringBuilder key = new StringBuilder();
            while (pos < content.length()) {
                char c = content.charAt(pos);
                if (c == '=' || isWhitespace(c)) {
                    break;
                }
                key.append(c);
                pos++;
                col++;
            }

            skipWhitespaceAndComments();

            if (pos >= content.length() || content.charAt(pos) != '=') {
                throw new TomlParseException("Expected '=' at line " + line);
            }
            pos++; // skip '='
            col++;

            skipWhitespaceAndComments();

            Object value = parseValue();
            table.put(key.toString().trim(), value);
        }

        private Object parseValue() throws TomlParseException {
            if (pos >= content.length()) {
                throw new TomlParseException("Unexpected end of input");
            }

            char c = content.charAt(pos);

            if (c == '"') {
                return parseString();
            } else if (c == 't' || c == 'f') {
                return parseBoolean();
            } else if (c == '-' || Character.isDigit(c)) {
                return parseNumber();
            } else {
                throw new TomlParseException("Unexpected character '" + c + "' at line " + line);
            }
        }

        private String parseString() throws TomlParseException {
            pos++; // skip opening quote
            col++;
            StringBuilder sb = new StringBuilder();

            while (pos < content.length() && content.charAt(pos) != '"') {
                sb.append(content.charAt(pos));
                pos++;
                col++;
            }
            pos++; // skip closing quote
            col++;

            return sb.toString();
        }

        private Boolean parseBoolean() throws TomlParseException {
            if (content.startsWith("true", pos)) {
                pos += 4;
                col += 4;
                return true;
            } else if (content.startsWith("false", pos)) {
                pos += 5;
                col += 5;
                return false;
            }
            throw new TomlParseException("Invalid boolean at line " + line);
        }

        private Number parseNumber() throws TomlParseException {
            StringBuilder sb = new StringBuilder();
            boolean isFloat = false;

            if (pos < content.length() && content.charAt(pos) == '-') {
                sb.append('-');
                pos++;
                col++;
            }

            while (pos < content.length()) {
                char c = content.charAt(pos);
                if (c == '.' || c == 'e' || c == 'E') {
                    isFloat = true;
                    sb.append(c);
                } else if (Character.isDigit(c)) {
                    sb.append(c);
                } else {
                    break;
                }
                pos++;
                col++;
            }

            String numStr = sb.toString();
            if (isFloat) {
                return Double.parseDouble(numStr);
            } else {
                return Integer.parseInt(numStr);
            }
        }

        private void skipWhitespaceAndComments() {
            while (pos < content.length()) {
                char c = content.charAt(pos);
                if (c == '\n') {
                    line++;
                    col = 1;
                    pos++;
                } else if (c == '#') {
                    while (pos < content.length() && content.charAt(pos) != '\n') {
                        pos++;
                    }
                } else if (isWhitespace(c)) {
                    pos++;
                    col++;
                } else {
                    break;
                }
            }
        }

        private boolean isWhitespace(char c) {
            return c == ' ' || c == '\t' || c == '\r';
        }

        private boolean isKeyStart(char c) {
            return Character.isLetter(c) || c == '_';
        }
    }

    static class TomlParseException extends Exception {
        TomlParseException(String message) {
            super(message);
        }
    }
}
