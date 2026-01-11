/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

/**
 * Native library loader for the FEAGI Java SDK.
 *
 * <p>This is intentionally minimal. The production SDK will likely load a platform-specific
 * classifier artifact (native .so/.dylib/.dll) and then call {@link FeagiNativeBindings#feagiAbiVersion()}
 * to verify compatibility.
 *
 * <p>Guardrail: no runtime defaults (host/ports/timeouts) belong here.
 */
public final class FeagiNativeLibrary {
    private FeagiNativeLibrary() {}

    /**
     * Load the FEAGI native library into the JVM.
     *
     * <p>Future work: implement a robust loader (classpath extraction + OS/arch detection).
     *
     * @param libraryName name passed to {@link System#loadLibrary(String)} (no "lib" prefix)
     */
    public static void load(String libraryName) {
        if (libraryName == null || libraryName.isEmpty()) {
            throw new IllegalArgumentException("libraryName must not be null/empty");
        }
        System.loadLibrary(libraryName);
    }
}

