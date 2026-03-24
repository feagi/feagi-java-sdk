/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.registration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Agent registration response from FEAGI server.
 *
 * <p>Returned after successful agent registration. Contains cortical area assignments,
 * FQ sampler configuration, and transport information.</p>
 *
 * <p>FEAGI 2.0 requires {@code corticalAreas} to be present in all responses.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * AgentRegistrationResponse response = registrationManager.register(request);
 * if (response.isSuccess()) {
 *     Map<String, Object> areas = response.getCorticalAreas();
 *     System.out.println("Assigned areas: " + areas);
 * } else {
 *     System.err.println("Registration failed: " + response.getErrorCode());
 * }
 * }</pre>
 */
public final class AgentRegistrationResponse {

    private final boolean success;
    private final String message;
    private final String agentId;
    private final Map<String, Object> corticalAreas;
    private final Map<String, Boolean> fqSamplersEnabled;
    private final Map<String, Object> transportInfo;
    private final String errorCode;

    /**
     * Creates a new AgentRegistrationResponse.
     *
     * @param success whether registration was successful
     * @param message human-readable message
     * @param agentId the registered agent ID
     * @param corticalAreas cortical area assignments (required in FEAGI 2.0)
     * @param fqSamplersEnabled FQ sampler enabled status by type (optional)
     * @param transportInfo transport configuration info (optional)
     * @param errorCode error code if registration failed (optional)
     */
    public AgentRegistrationResponse(
            boolean success,
            String message,
            String agentId,
            Map<String, Object> corticalAreas,
            Map<String, Boolean> fqSamplersEnabled,
            Map<String, Object> transportInfo,
            String errorCode
    ) {
        this.success = success;
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.agentId = Objects.requireNonNull(agentId, "agentId must not be null");
        this.corticalAreas = Objects.requireNonNull(corticalAreas, "corticalAreas must not be null (FEAGI 2.0 requirement)");
        this.fqSamplersEnabled = fqSamplersEnabled != null ? fqSamplersEnabled : Collections.emptyMap();
        this.transportInfo = transportInfo != null ? transportInfo : Collections.emptyMap();
        this.errorCode = errorCode;
    }

    /**
     * Returns whether registration was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the human-readable message.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the registered agent ID.
     *
     * @return the agent ID
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Returns the cortical area assignments.
     *
     * <p>FEAGI 2.0 requires this to be present in all responses.</p>
     *
     * @return cortical area assignments (e.g., {"required_ipu_areas": [], "required_opu_areas": []})
     */
    public Map<String, Object> getCorticalAreas() {
        return Collections.unmodifiableMap(corticalAreas);
    }

    /**
     * Returns FQ sampler enabled status by type.
     *
     * @return map of FQ sampler type to enabled status
     */
    public Map<String, Boolean> getFqSamplersEnabled() {
        return Collections.unmodifiableMap(fqSamplersEnabled);
    }

    /**
     * Returns transport configuration information.
     *
     * @return transport info (e.g., ZMQ endpoints, WebSocket URLs)
     */
    public Map<String, Object> getTransportInfo() {
        return Collections.unmodifiableMap(transportInfo);
    }

    /**
     * Returns the error code if registration failed.
     *
     * @return error code, or null if registration was successful
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Creates a successful registration response.
     *
     * @param agentId the agent ID
     * @param message the success message
     * @param corticalAreas the cortical areas
     * @return successful AgentRegistrationResponse
     */
    public static AgentRegistrationResponse success(
            String agentId,
            String message,
            Map<String, Object> corticalAreas
    ) {
        return new AgentRegistrationResponse(
            true,
            message,
            agentId,
            corticalAreas,
            Collections.emptyMap(),
            Collections.emptyMap(),
            null
        );
    }

    /**
     * Creates a successful registration response with transport info.
     *
     * @param agentId the agent ID
     * @param message the success message
     * @param corticalAreas the cortical areas
     * @param transportInfo the transport information
     * @return successful AgentRegistrationResponse
     */
    public static AgentRegistrationResponse success(
            String agentId,
            String message,
            Map<String, Object> corticalAreas,
            Map<String, Object> transportInfo
    ) {
        return new AgentRegistrationResponse(
            true,
            message,
            agentId,
            corticalAreas,
            Collections.emptyMap(),
            transportInfo,
            null
        );
    }

    /**
     * Creates a failed registration response.
     *
     * @param agentId the agent ID
     * @param message the error message
     * @param errorCode the error code
     * @return failed AgentRegistrationResponse
     */
    public static AgentRegistrationResponse failure(
            String agentId,
            String message,
            String errorCode
    ) {
        return new AgentRegistrationResponse(
            false,
            message,
            agentId,
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            errorCode
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentRegistrationResponse that = (AgentRegistrationResponse) o;
        return success == that.success &&
               Objects.equals(agentId, that.agentId) &&
               Objects.equals(message, that.message) &&
               Objects.equals(corticalAreas, that.corticalAreas) &&
               Objects.equals(fqSamplersEnabled, that.fqSamplersEnabled) &&
               Objects.equals(errorCode, that.errorCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, agentId, corticalAreas, fqSamplersEnabled, errorCode);
    }

    @Override
    public String toString() {
        return "AgentRegistrationResponse{" +
               "success=" + success +
               ", message='" + message + '\'' +
               ", agentId='" + agentId + '\'' +
               ", corticalAreas=" + corticalAreas +
               ", fqSamplersEnabled=" + fqSamplersEnabled +
               ", transportInfo=" + transportInfo +
               ", errorCode='" + errorCode + '\'' +
               '}';
    }
}
