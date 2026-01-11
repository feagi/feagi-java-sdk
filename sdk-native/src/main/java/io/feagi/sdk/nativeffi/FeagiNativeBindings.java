/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

/**
 * JNI bindings surface (skeleton).
 *
 * <p>Important: {@code feagi-java-ffi} exposes a plain C ABI (not JNI symbols). This Java class is
 * a placeholder for the eventual JNI bridge library that will call into the C ABI.
 *
 * <p>Future work:
 * - A JNI C/C++ layer that exports JNI functions and calls `feagi_java_ffi.h` functions.
 * - A matching set of native method declarations here.
 *
 * <p>Guardrail: Java must always verify the native ABI version matches expectations.
 */
public final class FeagiNativeBindings {
    private FeagiNativeBindings() {}

    /**
     * Expected ABI version from the native library (`feagi-java-ffi`).
     *
     * <p>Keep this in sync with `FEAGI_JAVA_FFI_ABI_VERSION` in Rust.
     */
    public static final int EXPECTED_ABI_VERSION = 1;

    // === ABI / version ===

    public static native int feagiAbiVersion();

    public static native String feagiLibraryVersion();

    // === Error reporting ===

    public static native String feagiLastErrorMessage();

    // === Config / client handles ===
    // Handles are represented as long (native pointer) in Java.

    public static native long feagiConfigNew(String agentId, int agentType);

    public static native void feagiConfigFree(long cfgHandle);

    public static native int feagiConfigSetFeagiEndpoints(
            long cfgHandle,
            String host,
            int registrationPort,
            int sensoryPort,
            int motorPort,
            int visualizationPort,
            int controlPort
    );

    public static native int feagiClientNew(long cfgHandle);

    public static native void feagiClientFree(long clientHandle);
}

