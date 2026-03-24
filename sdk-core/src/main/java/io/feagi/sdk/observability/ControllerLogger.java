/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.observability;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Standardized logger for embodiment controllers.
 *
 * <p>Provides timestamped console output with consistent formatting
 * across all FEAGI controllers. Integrates with the PNS observability
 * framework for automatic monitoring of sensory/motor data flow.</p>
 *
 * <p>Features:
 * <ul>
 *   <li>Configurable log levels (DEBUG, INFO, WARNING, ERROR)</li>
 *   <li>Optional timestamps with custom format</li>
 *   <li>Controller-specific log prefix</li>
 *   <li>Automatic monitoring hooks for brain_input/brain_output</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Create logger
 * ControllerLogger logger = new ControllerLogger.Builder()
 *     .controllerName("MuJoCo")
 *     .showTimestamps(true)
 *     .logLevel(LogLevel.INFO)
 *     .build();
 *
 * // Use for controller logging
 * logger.info("Model loaded");
 * logger.debug("Processing frame 120");
 * logger.warning("No motors registered");
 * logger.error("Connection failed");
 *
 * // Attach to brain_output for automatic monitoring
 * brainOutput.attachMonitor(logger);
 * }</pre>
 *
 * @see LogLevel
 * @see Monitor
 */
public class ControllerLogger implements Monitor {

    private static final DateTimeFormatter DEFAULT_TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String controllerName;
    private final boolean showTimestamps;
    private final DateTimeFormatter timestampFormat;
    private final LogLevel logLevel;
    private final PrintStream stream;

    private final Logger internalLogger;
    private final boolean enabled;

    /**
     * Creates a new ControllerLogger.
     *
     * @param builder the builder
     */
    private ControllerLogger(Builder builder) {
        this.controllerName = Objects.requireNonNull(builder.controllerName, "controllerName must not be null");
        this.showTimestamps = builder.showTimestamps;
        this.timestampFormat = builder.timestampFormat != null ? builder.timestampFormat : DEFAULT_TIMESTAMP_FORMAT;
        this.logLevel = builder.logLevel != null ? builder.logLevel : LogLevel.INFO;
        this.stream = builder.stream != null ? builder.stream : System.out;
        this.enabled = builder.enabled;

        // Set up internal Java logger for actual logging
        this.internalLogger = Logger.getLogger("feagi.controller." + controllerName);
        this.internalLogger.setUseParentHandlers(false);
        this.internalLogger.setLevel(toJavaLogLevel(this.logLevel));
    }

    /**
     * Logs an info message.
     *
     * @param message the message to log
     */
    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    /**
     * Logs a debug message.
     *
     * @param message the message to log
     */
    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    /**
     * Logs a warning message.
     *
     * @param message the message to log
     */
    public void warning(String message) {
        log(LogLevel.WARNING, message);
    }

    /**
     * Logs an error message.
     *
     * @param message the message to log
     */
    public void error(String message) {
        log(LogLevel.ERROR, message);
    }

    /**
     * Logs a critical message.
     *
     * @param message the message to log
     */
    public void critical(String message) {
        log(LogLevel.CRITICAL, message);
    }

    /**
     * Internal logging method with formatting.
     *
     * @param level the log level
     * @param message the message
     */
    private void log(LogLevel level, String message) {
        if (!enabled) {
            return;
        }

        if (level.ordinal() < this.logLevel.ordinal()) {
            return;
        }

        String formattedMessage;
        if (showTimestamps) {
            String timestamp = ZonedDateTime.now(ZoneId.systemDefault())
                .format(timestampFormat);
            formattedMessage = "[" + timestamp + "] [" + controllerName + "] " + message;
        } else {
            formattedMessage = "[" + controllerName + "] " + message;
        }

        // Use internal Java logger for actual output
        Level javaLevel = toJavaLogLevel(level);
        internalLogger.log(javaLevel, formattedMessage);

        // Also write to stream for immediate visibility
        stream.println(formattedMessage);
        stream.flush();
    }

    /**
     * Converts LogLevel to Java Level.
     *
     * @param logLevel the log level
     * @return corresponding Java Level
     */
    private Level toJavaLogLevel(LogLevel logLevel) {
        switch (logLevel) {
            case DEBUG: return Level.FINE;
            case INFO: return Level.INFO;
            case WARNING: return Level.WARNING;
            case ERROR: return Level.SEVERE;
            case CRITICAL: return Level.SEVERE;
            default: return Level.INFO;
        }
    }

    /**
     * Checks if monitoring is enabled.
     *
     * @return true if enabled, false otherwise
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Called when sensory data send starts.
     *
     * @param data event data
     */
    @Override
    public void onSendStart(Map<String, Object> data) {
        if (!enabled || logLevel != LogLevel.DEBUG) {
            return;
        }
        Integer inputCount = (Integer) data.get("input_count");
        Object areas = data.get("cortical_areas");
        debug("Sending sensory data: " + inputCount + " inputs, areas: " + areas);
    }

    /**
     * Called when sensory data send completes.
     *
     * @param data event data
     */
    @Override
    public void onSendComplete(Map<String, Object> data) {
        if (!enabled || logLevel != LogLevel.DEBUG) {
            return;
        }
        Integer neuronCount = (Integer) data.get("neuron_count");
        Integer packetSize = (Integer) data.get("packet_size_bytes");
        Double duration = (Double) data.get("duration_ms");
        debug("Sent " + neuronCount + " neurons (" + packetSize + " bytes) in " +
              String.format(Locale.US, "%.2f", duration) + " ms");
    }

    /**
     * Called when motor command receive starts.
     *
     * @param data event data
     */
    @Override
    public void onReceiveStart(Map<String, Object> data) {
        if (!enabled || logLevel != LogLevel.DEBUG) {
            return;
        }
        Integer outputCount = (Integer) data.get("output_count");
        debug("Receiving motor commands for " + outputCount + " outputs");
    }

    /**
     * Called when motor command receive completes.
     *
     * @param data event data
     */
    @Override
    public void onReceiveComplete(Map<String, Object> data) {
        if (!enabled || logLevel != LogLevel.DEBUG) {
            return;
        }
        Integer commandCount = (Integer) data.get("command_count");
        Double duration = (Double) data.get("duration_ms");
        debug("Received " + commandCount + " commands in " +
              String.format(Locale.US, "%.2f", duration) + " ms");
    }

    /**
     * Builder for ControllerLogger.
     */
    public static class Builder {
        private String controllerName = "Controller";
        private boolean showTimestamps = true;
        private DateTimeFormatter timestampFormat;
        private LogLevel logLevel = LogLevel.INFO;
        private PrintStream stream;
        private boolean enabled = true;

        /**
         * Sets the controller name.
         *
         * @param name the controller name
         * @return this builder
         */
        public Builder controllerName(String name) {
            this.controllerName = name;
            return this;
        }

        /**
         * Sets whether to show timestamps.
         *
         * @param show true to show timestamps
         * @return this builder
         */
        public Builder showTimestamps(boolean show) {
            this.showTimestamps = show;
            return this;
        }

        /**
         * Sets the timestamp format.
         *
         * @param format the DateTimeFormatter
         * @return this builder
         */
        public Builder timestampFormat(DateTimeFormatter format) {
            this.timestampFormat = format;
            return this;
        }

        /**
         * Sets the log level.
         *
         * @param level the log level
         * @return this builder
         */
        public Builder logLevel(LogLevel level) {
            this.logLevel = level;
            return this;
        }

        /**
         * Sets the output stream.
         *
         * @param stream the output stream
         * @return this builder
         */
        public Builder stream(PrintStream stream) {
            this.stream = stream;
            return this;
        }

        /**
         * Sets whether the logger is enabled.
         *
         * @param enabled true to enable
         * @return this builder
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Builds the ControllerLogger.
         *
         * @return new ControllerLogger instance
         */
        public ControllerLogger build() {
            return new ControllerLogger(this);
        }
    }

    /**
     * Log levels for ControllerLogger.
     */
    public enum LogLevel {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
}
