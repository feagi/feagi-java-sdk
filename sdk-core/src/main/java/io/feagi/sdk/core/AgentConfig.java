/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.time.Duration;
import java.util.Objects;

/**
 * High-level configuration for a FEAGI agent.
 *
 * <p>Guardrails:
 * - No hidden defaults for endpoints or timeouts.
 * - Validate all values at construction time.
 */
public final class AgentConfig {
    private final String agentId;
    private final AgentType agentType;
    private final FeagiEndpoints endpoints;
    private final Duration heartbeatInterval;
    private final Duration connectionTimeout;
    private final int registrationRetries;

    public AgentConfig(
            String agentId,
            AgentType agentType,
            FeagiEndpoints endpoints,
            Duration heartbeatInterval,
            Duration connectionTimeout,
            int registrationRetries
    ) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        if (agentId.isEmpty()) {
            throw new IllegalArgumentException("agentId must not be empty");
        }
        this.agentId = agentId;
        this.agentType = Objects.requireNonNull(agentType, "agentType must not be null");
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints must not be null");

        this.heartbeatInterval = requireNonNegative(heartbeatInterval, "heartbeatInterval");
        this.connectionTimeout = requirePositive(connectionTimeout, "connectionTimeout");

        if (registrationRetries < 0) {
            throw new IllegalArgumentException("registrationRetries must be >= 0");
        }
        this.registrationRetries = registrationRetries;
    }

    private static Duration requirePositive(Duration v, String name) {
        Objects.requireNonNull(v, name + " must not be null");
        if (v.isZero() || v.isNegative()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return v;
    }

    private static Duration requireNonNegative(Duration v, String name) {
        Objects.requireNonNull(v, name + " must not be null");
        if (v.isNegative()) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return v;
    }

    public String agentId() {
        return agentId;
    }

    public AgentType agentType() {
        return agentType;
    }

    public FeagiEndpoints endpoints() {
        return endpoints;
    }

    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    public Duration connectionTimeout() {
        return connectionTimeout;
    }

    public int registrationRetries() {
        return registrationRetries;
    }
}

