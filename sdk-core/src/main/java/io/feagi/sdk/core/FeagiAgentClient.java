/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Minimal client contract for FEAGI agents.
 *
 * <p>This is a skeleton interface. Implementations are expected to be Rust-backed via JNI.
 */
public interface FeagiAgentClient extends AutoCloseable {
    /**
     * Connect and register the agent with FEAGI.
     *
     * <p>Implementations must fail fast on configuration issues and must not assume defaults.
     */
    void connect();

    /**
     * Send already-serialized FEAGI byte-container sensory payload (real-time semantics).
     *
     * <p>No implicit buffering: underlying implementation may drop on backpressure.
     */
    void sendSensoryBytes(byte[] payload);

    /**
     * Non-blocking receive of FEAGI motor payload as byte-container bytes.
     *
     * @return payload bytes if available, otherwise {@code null}
     */
    byte[] pollMotorBytes();

    /**
     * Close and release native resources.
     */
    @Override
    void close();
}

