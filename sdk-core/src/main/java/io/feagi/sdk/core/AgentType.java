/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * FEAGI agent type.
 *
 * <p>Mirrors FEAGI Rust SDK agent types and is used to drive socket creation and capability validation.
 */
public enum AgentType {
    SENSORY,
    MOTOR,
    BOTH,
    VISUALIZATION,
    INFRASTRUCTURE
}

