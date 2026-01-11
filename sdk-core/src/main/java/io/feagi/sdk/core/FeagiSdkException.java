/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Base exception type for the FEAGI Java SDK.
 *
 * <p>Design note: keep exceptions typed and explicit to support deterministic behavior and
 * future Rust/RTOS migration (clear failure modes, no hidden fallbacks).
 */
public class FeagiSdkException extends RuntimeException {
    public FeagiSdkException(String message) {
        super(message);
    }

    public FeagiSdkException(String message, Throwable cause) {
        super(message, cause);
    }
}

