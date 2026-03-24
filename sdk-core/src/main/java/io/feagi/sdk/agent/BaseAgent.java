/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.agent;

import io.feagi.sdk.core.AgentCapabilities;
import io.feagi.sdk.core.AgentType;
import io.feagi.sdk.core.FeagiAgentClient;
import io.feagi.sdk.core.FeagiSdkException;
import io.feagi.sdk.registration.AgentRegistrationRequest;
import io.feagi.sdk.registration.AgentRegistrationResponse;
import io.feagi.sdk.registration.RegistrationManager;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Abstract base class for all FEAGI agents.
 *
 * <p>Provides a standard interface for hardware initialization, sensor/motor mapping,
 * and FEAGI communication. Subclass this to create agents for specific embodiments.</p>
 *
 * <p>The agent type is automatically derived from capabilities during {@link #connect()}:
 * <ul>
 *   <li>{@code vision_unit} present -&gt; SENSORY</li>
 *   <li>{@code motor_unit} or {@code motor_units} present -&gt; MOTOR</li>
 *   <li>Both present -&gt; BOTH</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * public class MyRobotAgent extends BaseAgent {
 *     {@literal @Override}
 *     public void initializeHardware() {
 *         // Connect to robot hardware
 *     }
 *
 *     {@literal @Override}
 *     public Map<String, byte[]> mapSensors(Object hardwareData) {
 *         // Convert hardware data -&gt; FEAGI format
 *         return Map.of("camera", imageBytes);
 *     }
 *
 *     {@literal @Override}
 *     public Object mapMotors(Map<String, Object> feagiOutput) {
 *         // Convert FEAGI commands -&gt; hardware format
 *         return motorCommands;
 *     }
 * }
 *
 * // Usage
 * AgentCapabilities capabilities = AgentCapabilities.builder()
 *     .vision(VisionCapability.builder()...)
 *     .build();
 *
 * BaseAgent agent = new MyRobotAgent("robot-001", capabilities);
 * agent.initializeHardware();
 * agent.connect();
 * agent.run();
 * }</pre>
 *
 * @see AgentCapabilities
 * @see FeagiAgentClient
 */
public abstract class BaseAgent {

    private static final Logger logger = Logger.getLogger(BaseAgent.class.getName());

    protected final String agentId;
    protected final AgentCapabilities capabilities;
    protected final String feagiHost;

    protected FeagiAgentClient client;
    protected RegistrationManager registrationManager;
    protected volatile boolean running;
    protected AgentType derivedAgentType;

    /**
     * Creates a new BaseAgent.
     *
     * @param agentId unique agent identifier
     * @param capabilities agent capabilities configuration
     * @param feagiHost FEAGI server hostname or IP (default: "localhost")
     * @throws IllegalArgumentException if agentId or capabilities is null
     */
    protected BaseAgent(String agentId, AgentCapabilities capabilities, String feagiHost) {
        this.agentId = Objects.requireNonNull(agentId, "agentId must not be null");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities must not be null");
        this.feagiHost = feagiHost != null ? feagiHost : "localhost";
        this.running = false;
    }

    /**
     * Creates a new BaseAgent with default host (localhost).
     *
     * @param agentId unique agent identifier
     * @param capabilities agent capabilities configuration
     */
    protected BaseAgent(String agentId, AgentCapabilities capabilities) {
        this(agentId, capabilities, "localhost");
    }

    /**
     * Initializes robot/simulator/device hardware.
     *
     * <p>Called once during startup before the main loop.
     * Override this method to connect to your hardware.</p>
     *
     * @throws FeagiSdkException if hardware initialization fails
     */
    public abstract void initializeHardware();

    /**
     * Converts hardware sensor data to FEAGI format.
     *
     * @param hardwareData raw sensor data from hardware
     * @return map of sensor names to byte arrays
     * @throws FeagiSdkException if mapping fails
     */
    public abstract Map<String, byte[]> mapSensors(Object hardwareData);

    /**
     * Converts FEAGI motor commands to hardware format.
     *
     * @param feagiOutput motor commands from FEAGI
     * @return hardware-specific motor commands
     * @throws FeagiSdkException if mapping fails
     */
    public abstract Object mapMotors(Map<String, Object> feagiOutput);

    /**
     * Derives the agent type from capabilities.
     *
     * <p>FEAGI 2.0 capability format:
     * <ul>
     *   <li>{@code vision_unit} present -&gt; SENSORY</li>
     *   <li>{@code motor_unit} or {@code motor_units} present -&gt; MOTOR</li>
     *   <li>Both present -&gt; BOTH</li>
     * </ul>
     *
     * @return derived AgentType
     * @throws FeagiSdkException if no valid capabilities found
     */
    protected AgentType deriveAgentType() {
        boolean hasVision = capabilities.vision() != null;
        boolean hasMotor = capabilities.motor() != null;

        if (hasVision && hasMotor) {
            logger.info("Derived agent type: BOTH (vision + motor)");
            return AgentType.BOTH;
        } else if (hasMotor) {
            logger.info("Derived agent type: MOTOR");
            return AgentType.MOTOR;
        } else if (hasVision) {
            logger.info("Derived agent type: SENSORY");
            return AgentType.SENSORY;
        } else {
            // Check for visualization capability as fallback
            if (capabilities.visualization() != null) {
                logger.info("Derived agent type: VISUALIZATION");
                return AgentType.VISUALIZATION;
            }
            throw new FeagiSdkException(
                "Agent must define vision_unit and/or motor_unit(s) capabilities. " +
                "Use AgentCapabilities.builder() to add vision() or motor()."
            );
        }
    }

    /**
     * Connects to the FEAGI server.
     *
     * <p>Creates the FEAGI client, derives agent type from capabilities,
     * and establishes connection. Call this after {@link #initializeHardware()}
     * and before {@link #run()}.</p>
     *
     * @throws FeagiSdkException if connection fails
     */
    public void connect() {
        logger.info("Connecting to FEAGI server at " + feagiHost);

        // Derive agent type from capabilities
        this.derivedAgentType = deriveAgentType();

        // TODO: Create and configure FeagiAgentClient when implementation is available
        // For now, this is a placeholder that will be implemented when the
        // FeagiAgentClient concrete implementation is created.
        //
        // The client configuration will use:
        // - agentId
        // - derivedAgentType
        // - capabilities (vision_unit, motor_unit, etc.)
        // - auth_token_b64 (from capabilities or environment)
        // - connection_timeout_ms
        // - registration_retries

        logger.info("FEAGI client created for agent: " + agentId + " (type: " + derivedAgentType + ")");
    }

    /**
     * Registers the agent with FEAGI.
     *
     * @param registrationManager the registration manager
     * @return registration response
     * @throws FeagiSdkException if registration fails
     */
    public AgentRegistrationResponse register(RegistrationManager registrationManager) {
        Objects.requireNonNull(registrationManager, "registrationManager must not be null");
        this.registrationManager = registrationManager;

        AgentRegistrationRequest request = new AgentRegistrationRequest.Builder()
            .agentId(agentId)
            .agentType(derivedAgentType)
            .agentIp(feagiHost)
            .capabilities(capabilities)
            .build();

        AgentRegistrationResponse response = registrationManager.registerAgent(request);

        if (response.isSuccess()) {
            logger.info("Agent registered successfully: " + agentId);
            logger.info("Cortical areas: " + response.getCorticalAreas());
        } else {
            logger.warning("Registration failed: " + response.getMessage() +
                          " (error: " + response.getErrorCode() + ")");
        }

        return response;
    }

    /**
     * Runs the agent main loop.
     *
     * <p>Override this method to implement the agent's main control loop.
     * The {@link #running} flag should be checked to support graceful shutdown.</p>
     *
     * @throws FeagiSdkException if run fails
     */
    public void run() {
        logger.info("Starting agent: " + agentId);
        running = true;

        try {
            runLoop();
        } finally {
            running = false;
            logger.info("Agent stopped: " + agentId);
        }
    }

    /**
     * The agent main control loop.
     *
     * <p>Override this to implement continuous agent behavior.
     * Check {@link #running} flag to support graceful shutdown.</p>
     *
     * @throws FeagiSdkException if loop fails
     */
    protected abstract void runLoop();

    /**
     * Stops the agent.
     *
     * <p>Sets {@link #running} to false to signal the run loop to exit.</p>
     */
    public void stop() {
        logger.info("Stopping agent: " + agentId);
        running = false;
    }

    /**
     * Checks if the agent is running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
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
     * Returns the agent capabilities.
     *
     * @return the capabilities configuration
     */
    public AgentCapabilities getCapabilities() {
        return capabilities;
    }

    /**
     * Returns the derived agent type.
     *
     * @return the agent type, or null if not yet connected
     */
    public AgentType getDerivedAgentType() {
        return derivedAgentType;
    }

    /**
     * Returns the FEAGI client.
     *
     * @return the client, or null if not yet connected
     */
    public FeagiAgentClient getClient() {
        return client;
    }

    /**
     * Returns the registration manager.
     *
     * @return the registration manager, or null if not yet registered
     */
    public RegistrationManager getRegistrationManager() {
        return registrationManager;
    }

    /**
     * Returns the FEAGI host.
     *
     * @return the FEAGI server hostname
     */
    public String getFeagiHost() {
        return feagiHost;
    }
}
