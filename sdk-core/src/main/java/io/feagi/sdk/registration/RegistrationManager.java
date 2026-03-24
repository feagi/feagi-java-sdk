/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.registration;

import io.feagi.sdk.core.AgentCapabilities;
import io.feagi.sdk.core.AgentType;
import io.feagi.sdk.core.FeagiSdkException;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registration Manager for FEAGI agents.
 *
 * <p>Handles agent registration with the FEAGI server, manages heartbeat threads
 * to keep agents registered, and coordinates FQ sampler states.</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>Agent registration with capability tracking</li>
 *   <li>Heartbeat management to maintain registration state</li>
 *   <li>FQ sampler coordination for visualization and motor outputs</li>
 *   <li>Event listeners for registration state changes</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * RegistrationManager manager = new RegistrationManager();
 *
 * // Configure heartbeat (required - safety mode, no defaults)
 * manager.configureHeartbeat(intervalS: 5.0, joinTimeoutS: 10.0);
 *
 * // Register agent
 * AgentRegistrationRequest request = new AgentRegistrationRequest.Builder()
 *     .agentId("my-robot-001")
 *     .agentType(AgentType.BOTH)
 *     .agentIp("192.168.1.100")
 *     .capabilities(capabilities)
 *     .build();
 *
 * AgentRegistrationResponse response = manager.registerAgent(request);
 * if (response.isSuccess()) {
 *     System.out.println("Registered: " + response.getCorticalAreas());
 * }
 * }</pre>
 *
 * @see AgentRegistrationRequest
 * @see AgentRegistrationResponse
 */
public final class RegistrationManager {

    private static final Logger logger = Logger.getLogger(RegistrationManager.class.getName());

    private final Object lock = new Object();
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    // Heartbeat management
    private final Map<String, Thread> heartbeatThreads;
    private final Map<String, AtomicBoolean> heartbeatRunning;
    private final Map<String, String> feagiApiUrls;
    private final Map<String, Map<String, Object>> agentMetadata;

    // Capability tracking
    private final Map<String, Integer> capabilityCounts;

    // FQ sampler states
    private final Map<String, Boolean> fqSamplerStates;

    // Event listeners
    private final Set<Consumer<RegistrationEvent>> stateChangeListeners;

    // FEAGI readiness
    private volatile boolean feagiReady;

    // Heartbeat configuration (safety mode: no defaults, must be explicitly configured)
    private Double heartbeatInterval;
    private Double heartbeatJoinTimeoutS;

    // Shutdown flag
    private volatile boolean shuttingDown;

    /**
     * Creates a new RegistrationManager.
     */
    public RegistrationManager() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "RegistrationManager-Scheduler");
            t.setDaemon(true);
            return t;
        });

        this.heartbeatThreads = new ConcurrentHashMap<>();
        this.heartbeatRunning = new ConcurrentHashMap<>();
        this.feagiApiUrls = new ConcurrentHashMap<>();
        this.agentMetadata = new ConcurrentHashMap<>();

        this.capabilityCounts = new HashMap<>();
        capabilityCounts.put("visualization", 0);
        capabilityCounts.put("motor", 0);
        capabilityCounts.put("sensory", 0);
        capabilityCounts.put("video", 0);
        capabilityCounts.put("feagi", 0);

        this.fqSamplerStates = new HashMap<>();
        fqSamplerStates.put("visualization_enabled", false);
        fqSamplerStates.put("motor_enabled", false);

        this.stateChangeListeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

        this.feagiReady = true;
        this.shuttingDown = false;

        logger.info("Registration Manager initialized");
    }

    /**
     * Configures heartbeat timings.
     *
     * <p>Safety mode: callers must explicitly set these values (no defaults).</p>
     *
     * @param intervalS heartbeat interval in seconds (must be > 0)
     * @param joinTimeoutS join timeout in seconds (must be > 0)
     * @throws IllegalArgumentException if intervalS or joinTimeoutS <= 0
     */
    public void configureHeartbeat(double intervalS, double joinTimeoutS) {
        if (intervalS <= 0) {
            throw new IllegalArgumentException("intervalS must be > 0 (no defaults in safety mode)");
        }
        if (joinTimeoutS <= 0) {
            throw new IllegalArgumentException("joinTimeoutS must be > 0 (no defaults in safety mode)");
        }
        this.heartbeatInterval = intervalS;
        this.heartbeatJoinTimeoutS = joinTimeoutS;
        logger.info("Heartbeat configured: interval=" + intervalS + "s, joinTimeout=" + joinTimeoutS + "s");
    }

    /**
     * Registers an agent with the FEAGI server.
     *
     * @param request the registration request
     * @return the registration response
     * @throws FeagiSdkException if registration fails
     */
    public AgentRegistrationResponse registerAgent(AgentRegistrationRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        synchronized (lock) {
            logger.info("Registration request: " + request.getAgentId() + " (type: " + request.getAgentType() + ")");

            // 1. Validate request
            ValidationResult validation = validateAgentRequest(request);
            if (!validation.isValid()) {
                return AgentRegistrationResponse.failure(
                    request.getAgentId(),
                    validation.getError(),
                    "VALIDATION_ERROR"
                );
            }

            // 2. Check FEAGI readiness
            if (!feagiReady) {
                return AgentRegistrationResponse.failure(
                    request.getAgentId(),
                    "FEAGI system not ready",
                    "FEAGI_NOT_READY"
                );
            }

            // 3. Check heartbeat configuration
            if (heartbeatInterval == null || heartbeatJoinTimeoutS == null) {
                logger.warning("Heartbeat not configured. Call configureHeartbeat() before registering agents.");
            }

            // 4. Build cortical areas from capabilities
            Map<String, Object> corticalAreas = buildCorticalAreas(request.getCapabilities());

            // 5. Update capability counts
            updateCapabilityCounts(request.getCapabilities(), true);

            // 6. Coordinate FQ samplers
            coordinateFqSamplers(request.getCapabilities());

            // 7. Build transport info
            Map<String, Object> transportInfo = buildTransportInfo(request);

            // 8. Store agent metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("registration_time", System.currentTimeMillis());
            metadata.put("agent_type", request.getAgentType().name());
            metadata.put("endpoints", transportInfo);
            agentMetadata.put(request.getAgentId(), metadata);

            // 9. Store API URL for heartbeat
            String apiHost = request.getAgentIp();
            String apiUrl = "http://" + apiHost + ":8000";
            feagiApiUrls.put(request.getAgentId(), apiUrl);

            // 10. Start heartbeat if configured
            if (heartbeatInterval != null) {
                startHeartbeat(request.getAgentId(), apiUrl);
            }

            logger.info("Agent registered successfully: " + request.getAgentId());

            // 11. Fire event
            fireRegistrationEvent(new RegistrationEvent(
                request.getAgentId(),
                true,
                "Agent registered successfully"
            ));

            return AgentRegistrationResponse.success(
                request.getAgentId(),
                "Agent registered successfully",
                corticalAreas,
                transportInfo
            );
        }
    }

    /**
     * Unregisters an agent.
     *
     * @param agentId the agent ID to unregister
     * @return true if successfully unregistered, false if agent was not registered
     */
    public boolean unregisterAgent(String agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");

        synchronized (lock) {
            logger.info("Unregistering agent: " + agentId);

            // Stop heartbeat
            stopHeartbeat(agentId);

            // Update capability counts
            Map<String, Object> metadata = agentMetadata.remove(agentId);
            if (metadata != null) {
                AgentType agentType = AgentType.valueOf((String) metadata.get("agent_type"));
                // Decrement counts based on agent type
                switch (agentType) {
                    case SENSORY:
                        capabilityCounts.merge("sensory", -1, Integer::sum);
                        break;
                    case MOTOR:
                        capabilityCounts.merge("motor", -1, Integer::sum);
                        break;
                    case BOTH:
                        capabilityCounts.merge("sensory", -1, Integer::sum);
                        capabilityCounts.merge("motor", -1, Integer::sum);
                        break;
                    case VISUALIZATION:
                        capabilityCounts.merge("visualization", -1, Integer::sum);
                        break;
                    default:
                        break;
                }
            }

            feagiApiUrls.remove(agentId);
            agentMetadata.remove(agentId);

            // Fire event
            fireRegistrationEvent(new RegistrationEvent(
                agentId,
                false,
                "Agent unregistered"
            ));

            return metadata != null;
        }
    }

    /**
     * Starts heartbeat for an agent.
     *
     * @param agentId the agent ID
     * @param apiUrl the FEAGI API URL
     */
    private void startHeartbeat(String agentId, String apiUrl) {
        if (heartbeatInterval == null) {
            logger.warning("Cannot start heartbeat: not configured");
            return;
        }

        AtomicBoolean running = new AtomicBoolean(true);
        heartbeatRunning.put(agentId, running);

        Thread heartbeatThread = new Thread(() -> {
            logger.info("Heartbeat started for agent: " + agentId);

            while (running.get() && !shuttingDown) {
                try {
                    Thread.sleep((long) (heartbeatInterval * 1000));

                    if (running.get() && !shuttingDown) {
                        sendHeartbeat(agentId, apiUrl);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warning("Heartbeat interrupted for agent: " + agentId);
                    break;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Heartbeat failed for agent: " + agentId, e);
                }
            }

            logger.info("Heartbeat stopped for agent: " + agentId);
        }, "Heartbeat-" + agentId);
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
        heartbeatThreads.put(agentId, heartbeatThread);
    }

    /**
     * Stops heartbeat for an agent.
     *
     * @param agentId the agent ID
     */
    public void stopHeartbeat(String agentId) {
        AtomicBoolean running = heartbeatRunning.remove(agentId);
        if (running != null) {
            running.set(false);
        }

        Thread thread = heartbeatThreads.remove(agentId);
        if (thread != null) {
            try {
                if (heartbeatJoinTimeoutS != null) {
                    thread.join((long) (heartbeatJoinTimeoutS * 1000));
                } else {
                    thread.join(5000); // Default 5s timeout
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Interrupted while waiting for heartbeat to stop: " + agentId);
            }
        }
    }

    /**
     * Sends a heartbeat to the FEAGI API.
     *
     * @param agentId the agent ID
     * @param apiUrl the FEAGI API URL
     */
    private void sendHeartbeat(String agentId, String apiUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/v1/agents/" + agentId + "/heartbeat"))
                .timeout(Duration.ofSeconds(2))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warning("Heartbeat failed for " + agentId + ": HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Heartbeat exception for " + agentId, e);
        }
    }

    /**
     * Validates an agent registration request.
     *
     * @param request the request to validate
     * @return validation result
     */
    private ValidationResult validateAgentRequest(AgentRegistrationRequest request) {
        if (request.getAgentId() == null || request.getAgentId().isBlank()) {
            return ValidationResult.invalid("agentId must not be empty");
        }

        if (request.getAgentIp() == null || request.getAgentIp().isBlank()) {
            return ValidationResult.invalid("agentIp must not be empty");
        }

        if (request.getCapabilities() == null) {
            return ValidationResult.invalid("capabilities must not be null");
        }

        return ValidationResult.valid();
    }

    /**
     * Builds cortical areas from capabilities.
     *
     * @param capabilities the agent capabilities
     * @return cortical areas map
     */
    private Map<String, Object> buildCorticalAreas(AgentCapabilities capabilities) {
        Map<String, Object> corticalAreas = new HashMap<>();
        corticalAreas.put("required_ipu_areas", Collections.emptyList());
        corticalAreas.put("required_opu_areas", Collections.emptyList());

        // TODO: Extract actual cortical areas from capabilities based on vision_unit, motor_unit, etc.
        // For now, return empty areas - this should be populated based on the specific
        // capability configuration and the FEAGI genome being used.

        return corticalAreas;
    }

    /**
     * Updates capability counts.
     *
     * @param capabilities the capabilities
     * @param increment true to increment, false to decrement
     */
    private void updateCapabilityCounts(AgentCapabilities capabilities, boolean increment) {
        int delta = increment ? 1 : -1;

        // Check for visualization capability
        if (capabilities.visualization() != null) {
            capabilityCounts.merge("visualization", delta, Integer::sum);
        }

        // Check for motor capability
        if (capabilities.motor() != null) {
            capabilityCounts.merge("motor", delta, Integer::sum);
        }

        // Check for sensory capability
        if (capabilities.sensory() != null) {
            capabilityCounts.merge("sensory", delta, Integer::sum);
        }
    }

    /**
     * Coordinates FQ samplers based on capabilities.
     *
     * @param capabilities the capabilities
     */
    private void coordinateFqSamplers(AgentCapabilities capabilities) {
        // Enable visualization FQ sampler if visualization capability present
        if (capabilities.visualization() != null) {
            fqSamplerStates.put("visualization_enabled", true);
        }

        // Enable motor FQ sampler if motor capability present
        if (capabilities.motor() != null) {
            fqSamplerStates.put("motor_enabled", true);
        }
    }

    /**
     * Builds transport info from registration request.
     *
     * @param request the registration request
     * @return transport info map
     */
    private Map<String, Object> buildTransportInfo(AgentRegistrationRequest request) {
        Map<String, Object> transportInfo = new HashMap<>();
        transportInfo.put("agent_ip", request.getAgentIp());
        transportInfo.put("agent_data_port", request.getAgentDataPort());
        transportInfo.put("agent_type", request.getAgentType().name());
        return transportInfo;
    }

    /**
     * Checks if FEAGI is ready.
     *
     * @return true if FEAGI is ready, false otherwise
     */
    private boolean checkFeagiReadiness() {
        // TODO: Implement actual FEAGI readiness check via health endpoint
        return feagiReady;
    }

    /**
     * Sets FEAGI readiness status.
     *
     * @param ready the new readiness status
     */
    public void setFeagiReady(boolean ready) {
        this.feagiReady = ready;
    }

    /**
     * Adds a registration event listener.
     *
     * @param listener the listener to add
     */
    public void addRegistrationListener(Consumer<RegistrationEvent> listener) {
        stateChangeListeners.add(listener);
    }

    /**
     * Removes a registration event listener.
     *
     * @param listener the listener to remove
     */
    public void removeRegistrationListener(Consumer<RegistrationEvent> listener) {
        stateChangeListeners.remove(listener);
    }

    /**
     * Fires a registration event to all listeners.
     *
     * @param event the event to fire
     */
    private void fireRegistrationEvent(RegistrationEvent event) {
        for (Consumer<RegistrationEvent> listener : stateChangeListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Event listener threw exception", e);
            }
        }
    }

    /**
     * Returns the capability counts.
     *
     * @return unmodifiable map of capability counts
     */
    public Map<String, Integer> getCapabilityCounts() {
        return Collections.unmodifiableMap(capabilityCounts);
    }

    /**
     * Returns the FQ sampler states.
     *
     * @return unmodifiable map of FQ sampler states
     */
    public Map<String, Boolean> getFqSamplerStates() {
        return Collections.unmodifiableMap(fqSamplerStates);
    }

    /**
     * Returns agent metadata.
     *
     * @param agentId the agent ID
     * @return agent metadata, or null if not registered
     */
    public Map<String, Object> getAgentMetadata(String agentId) {
        Map<String, Object> metadata = agentMetadata.get(agentId);
        return metadata != null ? Collections.unmodifiableMap(metadata) : null;
    }

    /**
     * Checks if an agent is registered.
     *
     * @param agentId the agent ID
     * @return true if registered, false otherwise
     */
    public boolean isAgentRegistered(String agentId) {
        return agentMetadata.containsKey(agentId);
    }

    /**
     * Shuts down the registration manager.
     *
     * <p>Stops all heartbeat threads and shuts down the scheduler.</p>
     */
    public void shutdown() {
        logger.info("Shutting down Registration Manager");
        shuttingDown = true;

        // Stop all heartbeats
        for (String agentId : heartbeatRunning.keySet()) {
            stopHeartbeat(agentId);
        }

        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Registration Manager shut down");
    }

    /**
     * Registration event fired when agent registration state changes.
     */
    public static final class RegistrationEvent {
        private final String agentId;
        private final boolean registered;
        private final String message;

        public RegistrationEvent(String agentId, boolean registered, String message) {
            this.agentId = agentId;
            this.registered = registered;
            this.message = message;
        }

        public String getAgentId() {
            return agentId;
        }

        public boolean isRegistered() {
            return registered;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Validation result for agent requests.
     */
    private static final class ValidationResult {
        private final boolean valid;
        private final String error;

        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }

        public boolean isValid() {
            return valid;
        }

        public String getError() {
            return error;
        }
    }
}
