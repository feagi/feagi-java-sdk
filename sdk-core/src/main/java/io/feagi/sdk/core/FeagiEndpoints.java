/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.Objects;

/**
 * Explicit FEAGI ZMQ endpoints.
 *
 * <p>Guardrail: no defaults. Callers must provide all endpoints explicitly or via a deterministic
 * registration response.
 */
public final class FeagiEndpoints {
    private final String registrationEndpoint;
    private final String sensoryEndpoint;
    private final String motorEndpoint;
    private final String visualizationEndpoint;
    private final String controlEndpoint;

    public FeagiEndpoints(
            String registrationEndpoint,
            String sensoryEndpoint,
            String motorEndpoint,
            String visualizationEndpoint,
            String controlEndpoint
    ) {
        this.registrationEndpoint = requireTcpEndpoint(registrationEndpoint, "registrationEndpoint");
        this.sensoryEndpoint = requireTcpEndpoint(sensoryEndpoint, "sensoryEndpoint");
        this.motorEndpoint = requireTcpEndpoint(motorEndpoint, "motorEndpoint");
        this.visualizationEndpoint = requireTcpEndpoint(visualizationEndpoint, "visualizationEndpoint");
        this.controlEndpoint = requireTcpEndpoint(controlEndpoint, "controlEndpoint");
    }

    private static String requireTcpEndpoint(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        if (!value.startsWith("tcp://")) {
            throw new IllegalArgumentException(name + " must start with tcp://");
        }
        return value;
    }

    public String registrationEndpoint() {
        return registrationEndpoint;
    }

    public String sensoryEndpoint() {
        return sensoryEndpoint;
    }

    public String motorEndpoint() {
        return motorEndpoint;
    }

    public String visualizationEndpoint() {
        return visualizationEndpoint;
    }

    public String controlEndpoint() {
        return controlEndpoint;
    }
}

