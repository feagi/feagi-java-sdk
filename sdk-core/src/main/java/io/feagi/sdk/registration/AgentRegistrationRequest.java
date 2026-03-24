/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.registration;

import io.feagi.sdk.core.AgentCapabilities;
import io.feagi.sdk.core.AgentType;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Agent registration request sent to FEAGI server.
 *
 * <p>Contains all information required to register an agent with the FEAGI system.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * AgentRegistrationRequest request = new AgentRegistrationRequest.Builder()
 *     .agentId("my-robot-001")
 *     .agentType(AgentType.BOTH)
 *     .agentIp("192.168.1.100")
 *     .capabilities(capabilities)
 *     .agentDataPort(8080)
 *     .agentVersion("1.0.0")
 *     .build();
 * }</pre>
 */
public final class AgentRegistrationRequest {

    private final String agentId;
    private final AgentType agentType;
    private final String agentIp;
    private final AgentCapabilities capabilities;
    private final Integer agentDataPort;
    private final String agentVersion;
    private final String controllerVersion;
    private final Map<String, Object> metadata;
    private final Instant registrationTimestamp;

    private AgentRegistrationRequest(Builder builder) {
        this.agentId = Objects.requireNonNull(builder.agentId, "agentId must not be null");
        this.agentType = Objects.requireNonNull(builder.agentType, "agentType must not be null");
        this.agentIp = Objects.requireNonNull(builder.agentIp, "agentIp must not be null");
        this.capabilities = Objects.requireNonNull(builder.capabilities, "capabilities must not be null");
        this.agentDataPort = builder.agentDataPort;
        this.agentVersion = builder.agentVersion;
        this.controllerVersion = builder.controllerVersion;
        this.metadata = builder.metadata != null ? new HashMap<>(builder.metadata) : new HashMap<>();
        this.registrationTimestamp = Instant.now();
    }

    /**
     * Returns the agent ID.
     *
     * @return the agent ID
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Returns the agent type.
     *
     * @return the agent type (SENSORY, MOTOR, BOTH, VISUALIZATION, or INFRASTRUCTURE)
     */
    public AgentType getAgentType() {
        return agentType;
    }

    /**
     * Returns the agent IP address.
     *
     * @return the agent IP address
     */
    public String getAgentIp() {
        return agentIp;
    }

    /**
     * Returns the agent capabilities.
     *
     * @return the capabilities configuration
     */
    public AgentCapabilities getCapabilities() {
        return capabilities;
    }

    /**
     * Returns the agent data port, if specified.
     *
     * @return the agent data port, or null if not specified
     */
    public Integer getAgentDataPort() {
        return agentDataPort;
    }

    /**
     * Returns the agent version, if specified.
     *
     * @return the agent version, or null if not specified
     */
    public String getAgentVersion() {
        return agentVersion;
    }

    /**
     * Returns the controller version, if specified.
     *
     * @return the controller version, or null if not specified
     */
    public String getControllerVersion() {
        return controllerVersion;
    }

    /**
     * Returns additional metadata.
     *
     * @return unmodifiable map of metadata
     */
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Returns the registration timestamp.
     *
     * @return the instant when this request was created
     */
    public Instant getRegistrationTimestamp() {
        return registrationTimestamp;
    }

    /**
     * Builder for AgentRegistrationRequest.
     */
    public static class Builder {
        private String agentId;
        private AgentType agentType;
        private String agentIp;
        private AgentCapabilities capabilities;
        private Integer agentDataPort;
        private String agentVersion;
        private String controllerVersion;
        private Map<String, Object> metadata;

        /**
         * Sets the agent ID.
         *
         * @param agentId unique agent identifier
         * @return this builder
         */
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        /**
         * Sets the agent type.
         *
         * @param agentType the agent type
         * @return this builder
         */
        public Builder agentType(AgentType agentType) {
            this.agentType = agentType;
            return this;
        }

        /**
         * Sets the agent IP address.
         *
         * @param agentIp the agent IP address
         * @return this builder
         */
        public Builder agentIp(String agentIp) {
            this.agentIp = agentIp;
            return this;
        }

        /**
         * Sets the agent capabilities.
         *
         * @param capabilities the capabilities configuration
         * @return this builder
         */
        public Builder capabilities(AgentCapabilities capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        /**
         * Sets the agent data port (optional).
         *
         * @param agentDataPort the agent data port
         * @return this builder
         */
        public Builder agentDataPort(Integer agentDataPort) {
            this.agentDataPort = agentDataPort;
            return this;
        }

        /**
         * Sets the agent version (optional).
         *
         * @param agentVersion the agent version
         * @return this builder
         */
        public Builder agentVersion(String agentVersion) {
            this.agentVersion = agentVersion;
            return this;
        }

        /**
         * Sets the controller version (optional).
         *
         * @param controllerVersion the controller version
         * @return this builder
         */
        public Builder controllerVersion(String controllerVersion) {
            this.controllerVersion = controllerVersion;
            return this;
        }

        /**
         * Adds metadata entry (optional).
         *
         * @param key metadata key
         * @param value metadata value
         * @return this builder
         */
        public Builder addMetadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Builds the AgentRegistrationRequest.
         *
         * @return new AgentRegistrationRequest instance
         * @throws IllegalArgumentException if required fields are missing
         */
        public AgentRegistrationRequest build() {
            return new AgentRegistrationRequest(this);
        }
    }
}
