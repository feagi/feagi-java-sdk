/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.registration;

import static org.junit.jupiter.api.Assertions.*;

import io.feagi.sdk.core.AgentCapabilities;
import io.feagi.sdk.core.AgentType;
import io.feagi.sdk.core.VisionCapability;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RegistrationManager.
 */
public class RegistrationManagerTest {

    @Test
    public void testConstructorDoesNotThrow() {
        assertDoesNotThrow(() -> new RegistrationManager());
    }

    @Test
    public void testConfigureHeartbeatValid() {
        RegistrationManager manager = new RegistrationManager();
        assertDoesNotThrow(() -> manager.configureHeartbeat(5.0, 10.0));
    }

    @Test
    public void testConfigureHeartbeatInvalidInterval() {
        RegistrationManager manager = new RegistrationManager();
        assertThrows(IllegalArgumentException.class, () -> manager.configureHeartbeat(0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> manager.configureHeartbeat(-1, 10.0));
    }

    @Test
    public void testConfigureHeartbeatInvalidTimeout() {
        RegistrationManager manager = new RegistrationManager();
        assertThrows(IllegalArgumentException.class, () -> manager.configureHeartbeat(5.0, 0));
        assertThrows(IllegalArgumentException.class, () -> manager.configureHeartbeat(5.0, -1));
    }

    @Test
    public void testRegisterAgentSuccess() {
        RegistrationManager manager = new RegistrationManager();
        manager.configureHeartbeat(5.0, 10.0);

        AgentCapabilities capabilities = AgentCapabilities.builder()
            .vision(VisionCapability.fromTargetArea("camera", 640, 480, 3, "i_vision"))
            .build();

        AgentRegistrationRequest request = new AgentRegistrationRequest.Builder()
            .agentId("test-agent-001")
            .agentType(AgentType.SENSORY)
            .agentIp("127.0.0.1")
            .capabilities(capabilities)
            .build();

        AgentRegistrationResponse response = manager.registerAgent(request);

        assertTrue(response.isSuccess());
        assertEquals("test-agent-001", response.getAgentId());
        assertNotNull(response.getCorticalAreas());
    }

    @Test
    public void testRegisterAgentNullRequest() {
        RegistrationManager manager = new RegistrationManager();
        assertThrows(NullPointerException.class, () -> manager.registerAgent(null));
    }

    @Test
    public void testRegisterAgentInvalidAgentId() {
        RegistrationManager manager = new RegistrationManager();

        AgentCapabilities capabilities = AgentCapabilities.builder()
            .vision(VisionCapability.fromTargetArea("camera", 640, 480, 3, "i_vision"))
            .build();

        AgentRegistrationRequest request = new AgentRegistrationRequest.Builder()
            .agentId("")  // Empty agent ID
            .agentType(AgentType.SENSORY)
            .agentIp("127.0.0.1")
            .capabilities(capabilities)
            .build();

        AgentRegistrationResponse response = manager.registerAgent(request);

        assertFalse(response.isSuccess());
        assertEquals("VALIDATION_ERROR", response.getErrorCode());
    }

    @Test
    public void testUnregisterAgent() {
        RegistrationManager manager = new RegistrationManager();
        manager.configureHeartbeat(5.0, 10.0);

        AgentCapabilities capabilities = AgentCapabilities.builder()
            .vision(VisionCapability.fromTargetArea("camera", 640, 480, 3, "i_vision"))
            .build();

        AgentRegistrationRequest request = new AgentRegistrationRequest.Builder()
            .agentId("test-agent-002")
            .agentType(AgentType.SENSORY)
            .agentIp("127.0.0.1")
            .capabilities(capabilities)
            .build();

        // Register
        manager.registerAgent(request);
        assertTrue(manager.isAgentRegistered("test-agent-002"));

        // Unregister
        boolean unregistered = manager.unregisterAgent("test-agent-002");
        assertTrue(unregistered);
        assertFalse(manager.isAgentRegistered("test-agent-002"));
    }

    @Test
    public void testShutdown() {
        RegistrationManager manager = new RegistrationManager();
        manager.configureHeartbeat(5.0, 10.0);

        AgentCapabilities capabilities = AgentCapabilities.builder()
            .vision(VisionCapability.fromTargetArea("camera", 640, 480, 3, "i_vision"))
            .build();

        AgentRegistrationRequest request = new AgentRegistrationRequest.Builder()
            .agentId("test-agent-003")
            .agentType(AgentType.SENSORY)
            .agentIp("127.0.0.1")
            .capabilities(capabilities)
            .build();

        manager.registerAgent(request);
        assertDoesNotThrow(() -> manager.shutdown());
    }
}
