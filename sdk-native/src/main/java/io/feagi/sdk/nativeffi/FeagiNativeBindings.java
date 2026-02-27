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

    /**
     * Mirrors FeagiStatus codes from the C ABI.
     */
    public enum FeagiStatus {
        OK(0),
        NULL_POINTER(1),
        INVALID_ARGUMENT(2),
        INVALID_UTF8(3),
        JSON_ERROR(4),
        SDK_ERROR(5),
        ALLOCATION_FAILED(6);

        private final int code;

        FeagiStatus(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    /**
     * Mirrors FeagiSensoryUnit from the C ABI.
     */
    public enum FeagiSensoryUnit {
        INFRARED(0),
        PROXIMITY(1),
        SHOCK(2),
        BATTERY(3),
        SERVO(4),
        ANALOG_GPIO(5),
        DIGITAL_GPIO(6),
        MISC_DATA(7),
        TEXT_ENGLISH_INPUT(8),
        COUNT_INPUT(9),
        VISION(10),
        SEGMENTED_VISION(11),
        ACCELEROMETER(12),
        GYROSCOPE(13);

        private final int code;

        FeagiSensoryUnit(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    /**
     * Mirrors FeagiMotorUnit from the C ABI.
     */
    public enum FeagiMotorUnit {
        ROTARY_MOTOR(0),
        POSITIONAL_SERVO(1),
        GAZE(2),
        MISC_DATA(3),
        TEXT_ENGLISH_OUTPUT(4),
        COUNT_OUTPUT(5),
        OBJECT_SEGMENTATION(6),
        SIMPLE_VISION_OUTPUT(7);

        private final int code;

        FeagiMotorUnit(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    // === ABI / version ===

    /**
     * Return ABI version from native library.
     */
    public static native int feagiAbiVersion();

    /**
     * Return native library version string.
     */
    public static native String feagiLibraryVersion();

    // === Error reporting ===

    /**
     * Return last error message for the calling thread, or null if none.
     */
    public static native String feagiLastErrorMessage();

    // === Config / client handles ===
    // Handles are represented as long (native pointer) in Java.

    /**
     * Allocate a new native config handle.
     */
    public static native long feagiConfigNew(String agentId, int agentType);

    /**
     * Free a native config handle.
     */
    public static native void feagiConfigFree(long cfgHandle);

    /**
     * Set registration endpoint.
     */
    public static native int feagiConfigSetRegistrationEndpoint(long cfgHandle, String endpoint);

    /**
     * Set sensory endpoint.
     */
    public static native int feagiConfigSetSensoryEndpoint(long cfgHandle, String endpoint);

    /**
     * Set motor endpoint.
     */
    public static native int feagiConfigSetMotorEndpoint(long cfgHandle, String endpoint);

    /**
     * Set visualization endpoint.
     */
    public static native int feagiConfigSetVisualizationEndpoint(long cfgHandle, String endpoint);

    /**
     * Set control endpoint.
     */
    public static native int feagiConfigSetControlEndpoint(long cfgHandle, String endpoint);

    /**
     * Set all FEAGI endpoints from host + explicit ports.
     */
    public static native int feagiConfigSetFeagiEndpoints(
            long cfgHandle,
            String host,
            int registrationPort,
            int sensoryPort,
            int motorPort,
            int visualizationPort,
            int controlPort
    );

    /**
     * Set heartbeat interval in seconds.
     */
    public static native int feagiConfigSetHeartbeatIntervalSeconds(long cfgHandle, double heartbeatIntervalSeconds);

    /**
     * Set connection timeout in milliseconds.
     */
    public static native int feagiConfigSetConnectionTimeoutMs(long cfgHandle, long connectionTimeoutMs);

    /**
     * Set registration retry count.
     */
    public static native int feagiConfigSetRegistrationRetries(long cfgHandle, int registrationRetries);

    /**
     * Set the required agent descriptor fields for registration.
     */
    public static native int feagiConfigSetAgentDescriptor(
            long cfgHandle,
            String manufacturer,
            String agentName,
            int agentVersion
    );

    /**
     * Set the required auth token as base64 (must decode to 32 bytes).
     */
    public static native int feagiConfigSetAuthTokenBase64(
            long cfgHandle,
            String authTokenBase64
    );

    /**
     * Set retry backoff in milliseconds.
     */
    public static native int feagiConfigSetRetryBackoffMs(long cfgHandle, long retryBackoffMs);

    /**
     * Set sensory socket configuration.
     */
    public static native int feagiConfigSetSensorySocketConfig(
            long cfgHandle,
            int sendHwm,
            int lingerMs,
            boolean immediate
    );

    /**
     * Set generic sensory capability.
     */
    public static native int feagiConfigSetSensoryCapability(
            long cfgHandle,
            double rateHz,
            String shmPathOrNull
    );

    /**
     * Set vision capability using target cortical area.
     */
    public static native int feagiConfigSetVisionCapability(
            long cfgHandle,
            String modality,
            long width,
            long height,
            long channels,
            String targetCorticalArea
    );

    /**
     * Set vision capability using semantic unit + group.
     */
    public static native int feagiConfigSetVisionUnit(
            long cfgHandle,
            String modality,
            long width,
            long height,
            long channels,
            int unit,
            int group
    );

    /**
     * Set motor capability using cortical area list JSON.
     */
    public static native int feagiConfigSetMotorCapability(
            long cfgHandle,
            String modality,
            long outputCount,
            String sourceCorticalAreasJson
    );

    /**
     * Set motor capability using semantic unit + group.
     */
    public static native int feagiConfigSetMotorUnit(
            long cfgHandle,
            String modality,
            long outputCount,
            int unit,
            int group
    );

    /**
     * Set motor capability using semantic units JSON.
     */
    public static native int feagiConfigSetMotorUnitsJson(
            long cfgHandle,
            String modality,
            long outputCount,
            String motorUnitsJson
    );

    /**
     * Set visualization capability.
     */
    public static native int feagiConfigSetVisualizationCapability(
            long cfgHandle,
            String visualizationType,
            boolean hasResolution,
            long resolutionWidth,
            long resolutionHeight,
            boolean hasRefreshRate,
            double refreshRateHz,
            boolean bridgeProxy
    );

    /**
     * Set custom capability JSON for a key.
     */
    public static native int feagiConfigSetCustomCapabilityJson(
            long cfgHandle,
            String key,
            String jsonValue
    );

    /**
     * Validate configuration and return status.
     */
    public static native int feagiConfigValidate(long cfgHandle);

    /**
     * Create a new client handle from a config handle.
     */
    public static native int feagiClientNew(long cfgHandle, long[] outClientHandle);

    /**
     * Free a client handle.
     */
    public static native void feagiClientFree(long clientHandle);

    /**
     * Connect and register the client with FEAGI.
     */
    public static native int feagiClientConnect(long clientHandle);

    /**
     * Return last registration response JSON string.
     */
    public static native String feagiClientRegistrationResponseJson(long clientHandle);

    /**
     * Return ZMQ port map from the last registration response.
     */
    public static native String feagiClientRegistrationZmqPortsJson(long clientHandle);

    /**
     * Return chosen transport JSON with an optional preference.
     */
    public static native String feagiClientRegistrationChosenTransportJson(
            long clientHandle,
            String preferenceOrNull
    );

    /**
     * Return recommended transport string from registration response.
     */
    public static native String feagiClientRegistrationRecommendedTransport(long clientHandle);

    /**
     * Send sensory bytes to FEAGI.
     */
    public static native int feagiClientSendSensoryBytes(long clientHandle, byte[] bytes);

    /**
     * Try send sensory bytes to FEAGI and return sent status.
     */
    public static native int feagiClientTrySendSensoryBytes(
            long clientHandle,
            byte[] bytes,
            boolean[] outSent
    );

    /**
     * Receive motor payload buffer handle if available.
     */
    public static native int feagiClientReceiveMotorBuffer(
            long clientHandle,
            long[] outBufferHandle,
            boolean[] outHasData
    );

    /**
     * Return pointer address for a buffer handle.
     */
    public static native long feagiBufferPtr(long bufferHandle);

    /**
     * Return length for a buffer handle.
     */
    public static native long feagiBufferLen(long bufferHandle);

    /**
     * Free a buffer handle.
     */
    public static native void feagiBufferFree(long bufferHandle);
}

