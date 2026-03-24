/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.observability;

import java.util.Map;

/**
 * Monitor interface for PNS observability.
 *
 * <p>Implement this interface to create components that observe
 * data flow through brain_input and brain_output.</p>
 *
 * <p>Monitors are notified at key points in the data flow:
 * <ul>
 *   <li>{@link #onSendStart(Map)} - Before sensory data is sent</li>
 *   <li>{@link #onSendComplete(Map)} - After sensory data is sent</li>
 *   <li>{@link #onReceiveStart(Map)} - Before receiving motor commands</li>
 *   <li>{@link #onReceiveComplete(Map)} - After receiving motor commands</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * public class MyMonitor implements Monitor {
 *     {@literal @Override}
 *     public boolean isEnabled() { return true; }
 *
 *     {@literal @Override}
 *     public void onSendComplete(Map<String, Object> data) {
 *         System.out.println("Sent " + data.get("neuron_count") + " neurons");
 *     }
 *
 *     // Other methods...
 * }
 *
 * // Attach to brain input/output
 * brainInput.attachMonitor(new MyMonitor());
 * }</pre>
 *
 * @see ControllerLogger
 * @see MetricsCollector
 */
public interface Monitor {

    /**
     * Checks if this monitor is enabled.
     *
     * @return true if the monitor should receive events, false otherwise
     */
    boolean isEnabled();

    /**
     * Called when sensory data transmission starts.
     *
     * @param data event data containing:
     *             - input_count: number of inputs being sent
     *             - cortical_areas: list of target cortical areas
     *             - timestamp: start timestamp
     */
    default void onSendStart(Map<String, Object> data) {
        // Default no-op implementation
    }

    /**
     * Called when sensory data transmission completes.
     *
     * @param data event data containing:
     *             - neuron_count: number of neurons sent
     *             - packet_size_bytes: size of the packet
     *             - duration_ms: transmission duration
     *             - timestamp: completion timestamp
     */
    default void onSendComplete(Map<String, Object> data) {
        // Default no-op implementation
    }

    /**
     * Called when motor command reception starts.
     *
     * @param data event data containing:
     *             - output_count: number of outputs receiving commands
     *             - timestamp: start timestamp
     */
    default void onReceiveStart(Map<String, Object> data) {
        // Default no-op implementation
    }

    /**
     * Called when motor command reception completes.
     *
     * @param data event data containing:
     *             - command_count: number of commands received
     *             - duration_ms: reception duration
     *             - timestamp: completion timestamp
     */
    default void onReceiveComplete(Map<String, Object> data) {
        // Default no-op implementation
    }
}
