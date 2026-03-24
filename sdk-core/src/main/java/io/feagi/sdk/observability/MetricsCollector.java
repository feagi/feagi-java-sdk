/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.observability;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Metrics collector for PNS data flow.
 *
 * <p>Collects and aggregates statistics on data flow through brain_input
 * and brain_output, including:
 * <ul>
 *   <li>Packet counts and sizes</li>
 *   <li>Neuron counts</li>
 *   <li>Data rates (MB/s)</li>
 *   <li>Latencies</li>
 *   <li>Commands received</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * MetricsCollector metrics = new MetricsCollector();
 * brainInput.attachMonitor(metrics);
 * brainOutput.attachMonitor(metrics);
 *
 * // ... run agent ...
 *
 * InputStatistics inputStats = metrics.getInputStatistics();
 * System.out.println("Data rate: " + inputStats.getDataRateMbps() + " MB/s");
 *
 * OutputStatistics outputStats = metrics.getOutputStatistics();
 * System.out.println("Avg latency: " + outputStats.getAvgLatencyMs() + " ms");
 *
 * // Export
 * metrics.exportJson("metrics.json");
 * metrics.exportCsv("metrics.csv");
 * }</pre>
 *
 * @see Monitor
 * @see InputStatistics
 * @see OutputStatistics
 */
public class MetricsCollector implements Monitor {

    private final boolean enabled;
    private final Instant startTime;
    private volatile Instant endTime;

    // Input statistics (atomic for thread-safety)
    private final AtomicLong inputTotalPackets = new AtomicLong();
    private final AtomicLong inputTotalBytes = new AtomicLong();
    private final AtomicLong inputTotalNeurons = new AtomicLong();
    private final DoubleAdder inputTotalDurationMs = new DoubleAdder();

    // Output statistics
    private final AtomicLong outputTotalCommands = new AtomicLong();
    private final AtomicLong outputTotalReceives = new AtomicLong();
    private final DoubleAdder outputTotalDurationMs = new DoubleAdder();

    /**
     * Creates a new MetricsCollector.
     */
    public MetricsCollector() {
        this(true);
    }

    /**
     * Creates a new MetricsCollector.
     *
     * @param enabled whether collection is active
     */
    public MetricsCollector(boolean enabled) {
        this.enabled = enabled;
        this.startTime = Instant.now();
    }

    /**
     * Checks if this monitor is enabled.
     *
     * @return true if enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Called when sensory data transmission completes.
     *
     * @param data event data
     */
    @Override
    public void onSendComplete(Map<String, Object> data) {
        if (!enabled) {
            return;
        }

        Object packetSizeObj = data.get("packet_size_bytes");
        Object neuronCountObj = data.get("neuron_count");
        Object durationObj = data.get("duration_ms");

        inputTotalPackets.incrementAndGet();

        if (packetSizeObj instanceof Number) {
            inputTotalBytes.addAndGet(((Number) packetSizeObj).longValue());
        }

        if (neuronCountObj instanceof Number) {
            inputTotalNeurons.addAndGet(((Number) neuronCountObj).longValue());
        }

        if (durationObj instanceof Number) {
            inputTotalDurationMs.add(((Number) durationObj).doubleValue());
        }
    }

    /**
     * Called when motor command reception completes.
     *
     * @param data event data
     */
    @Override
    public void onReceiveComplete(Map<String, Object> data) {
        if (!enabled) {
            return;
        }

        Object commandCountObj = data.get("command_count");
        Object durationObj = data.get("duration_ms");

        outputTotalReceives.incrementAndGet();

        if (commandCountObj instanceof Number) {
            outputTotalCommands.addAndGet(((Number) commandCountObj).longValue());
        }

        if (durationObj instanceof Number) {
            outputTotalDurationMs.add(((Number) durationObj).doubleValue());
        }
    }

    /**
     * Returns input statistics.
     *
     * @return input statistics
     */
    public InputStatistics getInputStatistics() {
        return new InputStatistics(
            inputTotalPackets.get(),
            inputTotalBytes.get(),
            inputTotalNeurons.get(),
            inputTotalDurationMs.sum()
        );
    }

    /**
     * Returns output statistics.
     *
     * @return output statistics
     */
    public OutputStatistics getOutputStatistics() {
        return new OutputStatistics(
            outputTotalCommands.get(),
            outputTotalReceives.get(),
            outputTotalDurationMs.sum()
        );
    }

    /**
     * Returns combined statistics.
     *
     * @return combined statistics
     */
    public Statistics getStatistics() {
        return new Statistics(
            getInputStatistics(),
            getOutputStatistics(),
            startTime,
            endTime
        );
    }

    /**
     * Resets all collected metrics.
     */
    public void reset() {
        inputTotalPackets.set(0);
        inputTotalBytes.set(0);
        inputTotalNeurons.set(0);
        inputTotalDurationMs.reset();
        outputTotalCommands.set(0);
        outputTotalReceives.set(0);
        outputTotalDurationMs.reset();
        startTime.atZone(java.time.ZoneId.systemDefault());
        endTime = null;
    }

    /**
     * Marks the end of metrics collection.
     */
    public void stop() {
        endTime = Instant.now();
    }

    /**
     * Exports statistics to JSON file.
     *
     * @param outputPath path to output file
     * @throws IOException if write fails
     */
    public void exportJson(String outputPath) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        Statistics stats = getStatistics();
        String json = stats.toJson();

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println(json);
        }
    }

    /**
     * Exports statistics to CSV file.
     *
     * @param outputPath path to output file
     * @throws IOException if write fails
     */
    public void exportCsv(String outputPath) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        InputStatistics inputStats = getInputStatistics();
        OutputStatistics outputStats = getOutputStatistics();

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            // Header
            writer.println("metric,value");

            // Input metrics
            writer.println("input_total_packets," + inputStats.getTotalPackets());
            writer.println("input_total_bytes," + inputStats.getTotalBytes());
            writer.println("input_total_neurons," + inputStats.getTotalNeurons());
            writer.printf("input_total_duration_ms,%.2f%n", inputStats.getTotalDurationMs());
            writer.printf("input_avg_packet_size,%.2f%n", inputStats.getAvgPacketSize());
            writer.printf("input_data_rate_mbps,%.4f%n", inputStats.getDataRateMbps());

            // Output metrics
            writer.println("output_total_commands," + outputStats.getTotalCommands());
            writer.println("output_total_receives," + outputStats.getTotalReceives());
            writer.printf("output_total_duration_ms,%.2f%n", outputStats.getTotalDurationMs());
            writer.printf("output_avg_commands_per_receive,%.2f%n", outputStats.getAvgCommandsPerReceive());
            writer.printf("output_avg_latency_ms,%.2f%n", outputStats.getAvgLatencyMs());

            // Timing
            writer.println("start_time," + startTime.toString());
            if (endTime != null) {
                writer.println("end_time," + endTime.toString());
            }
        }
    }

    /**
     * Input statistics (immutable).
     */
    public static class InputStatistics {
        private final long totalPackets;
        private final long totalBytes;
        private final long totalNeurons;
        private final double totalDurationMs;

        public InputStatistics(long totalPackets, long totalBytes, long totalNeurons, double totalDurationMs) {
            this.totalPackets = totalPackets;
            this.totalBytes = totalBytes;
            this.totalNeurons = totalNeurons;
            this.totalDurationMs = totalDurationMs;
        }

        public long getTotalPackets() { return totalPackets; }
        public long getTotalBytes() { return totalBytes; }
        public long getTotalNeurons() { return totalNeurons; }
        public double getTotalDurationMs() { return totalDurationMs; }

        /**
         * Average packet size in bytes.
         */
        public double getAvgPacketSize() {
            return totalPackets > 0 ? (double) totalBytes / totalPackets : 0.0;
        }

        /**
         * Average neurons per packet.
         */
        public double getAvgNeuronsPerPacket() {
            return totalPackets > 0 ? (double) totalNeurons / totalPackets : 0.0;
        }

        /**
         * Average send duration in ms.
         */
        public double getAvgDurationMs() {
            return totalPackets > 0 ? totalDurationMs / totalPackets : 0.0;
        }

        /**
         * Data rate in MB/s.
         */
        public double getDataRateMbps() {
            if (totalDurationMs == 0) return 0.0;
            double totalSeconds = totalDurationMs / 1000.0;
            return (totalBytes / (1024.0 * 1024.0)) / totalSeconds;
        }

        /**
         * Packets per second.
         */
        public double getPacketsPerSec() {
            if (totalDurationMs == 0) return 0.0;
            double totalSeconds = totalDurationMs / 1000.0;
            return totalPackets / totalSeconds;
        }

        /**
         * Converts to map.
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("total_packets", totalPackets);
            map.put("total_bytes", totalBytes);
            map.put("total_neurons", totalNeurons);
            map.put("total_duration_ms", totalDurationMs);
            map.put("avg_packet_size", getAvgPacketSize());
            map.put("avg_neurons_per_packet", getAvgNeuronsPerPacket());
            map.put("avg_duration_ms", getAvgDurationMs());
            map.put("data_rate_mbps", getDataRateMbps());
            map.put("packets_per_sec", getPacketsPerSec());
            return Collections.unmodifiableMap(map);
        }
    }

    /**
     * Output statistics (immutable).
     */
    public static class OutputStatistics {
        private final long totalCommands;
        private final long totalReceives;
        private final double totalDurationMs;

        public OutputStatistics(long totalCommands, long totalReceives, double totalDurationMs) {
            this.totalCommands = totalCommands;
            this.totalReceives = totalReceives;
            this.totalDurationMs = totalDurationMs;
        }

        public long getTotalCommands() { return totalCommands; }
        public long getTotalReceives() { return totalReceives; }
        public double getTotalDurationMs() { return totalDurationMs; }

        /**
         * Average commands per receive.
         */
        public double getAvgCommandsPerReceive() {
            return totalReceives > 0 ? (double) totalCommands / totalReceives : 0.0;
        }

        /**
         * Average latency in ms.
         */
        public double getAvgLatencyMs() {
            return totalReceives > 0 ? totalDurationMs / totalReceives : 0.0;
        }

        /**
         * Commands per second.
         */
        public double getCommandsPerSec() {
            if (totalDurationMs == 0) return 0.0;
            double totalSeconds = totalDurationMs / 1000.0;
            return totalCommands / totalSeconds;
        }

        /**
         * Converts to map.
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("total_commands", totalCommands);
            map.put("total_receives", totalReceives);
            map.put("total_duration_ms", totalDurationMs);
            map.put("avg_commands_per_receive", getAvgCommandsPerReceive());
            map.put("avg_latency_ms", getAvgLatencyMs());
            map.put("commands_per_sec", getCommandsPerSec());
            return Collections.unmodifiableMap(map);
        }
    }

    /**
     * Combined statistics.
     */
    public static class Statistics {
        private final InputStatistics input;
        private final OutputStatistics output;
        private final Instant startTime;
        private final Instant endTime;

        public Statistics(InputStatistics input, OutputStatistics output,
                         Instant startTime, Instant endTime) {
            this.input = input;
            this.output = output;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public InputStatistics getInput() { return input; }
        public OutputStatistics getOutput() { return output; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }

        /**
         * Uptime in seconds.
         */
        public double getUptimeSeconds() {
            Instant end = endTime != null ? endTime : Instant.now();
            return (double) java.time.Duration.between(startTime, end).toMillis() / 1000.0;
        }

        /**
         * Converts to JSON string.
         */
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"input\": {\n");
            for (Map.Entry<String, Object> entry : input.toMap().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Double) {
                    sb.append(String.format("    \"%s\": %.4f,\n", entry.getKey(), value));
                } else {
                    sb.append("    \"").append(entry.getKey()).append("\": ").append(value).append(",\n");
                }
            }
            // Remove last comma
            sb.setLength(sb.length() - 2);
            sb.append("\n  },\n");

            sb.append("  \"output\": {\n");
            for (Map.Entry<String, Object> entry : output.toMap().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Double) {
                    sb.append(String.format("    \"%s\": %.4f,\n", entry.getKey(), value));
                } else {
                    sb.append("    \"").append(entry.getKey()).append("\": ").append(value).append(",\n");
                }
            }
            sb.setLength(sb.length() - 2);
            sb.append("\n  },\n");

            sb.append("  \"start_time\": \"").append(startTime).append("\",\n");
            if (endTime != null) {
                sb.append("  \"end_time\": \"").append(endTime).append("\",\n");
            }
            sb.append("  \"uptime_seconds\": ").append(String.format("%.2f", getUptimeSeconds())).append("\n");
            sb.append("}");

            return sb.toString();
        }

        /**
         * Converts to map.
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("input", input.toMap());
            map.put("output", output.toMap());
            map.put("start_time", startTime.toString());
            map.put("end_time", endTime != null ? endTime.toString() : null);
            map.put("uptime_seconds", getUptimeSeconds());
            return Collections.unmodifiableMap(map);
        }
    }
}
