# FEAGI Java SDK

**Build AI agents that learn like biological brains**

[![Java 17+](https://img.shields.io/badge/Java-17+-blue.svg)](https://openjdk.org/) [![Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

---

## Introduction

The FEAGI Java SDK is a library for building FEAGI agents in Java, supporting robotics, simulators, and edge device applications. The SDK provides high-performance I/O backed by Rust via JNI while maintaining a pure Java API.

**Key Features**:
- **Rust-backed I/O** - High-performance communication via `feagi-java-ffi`
- **Java 17+ baseline** - LTS version suitable for robotics environments
- **Cross-platform** - Supports Linux, macOS, and Windows
- **Python SDK parity** - Same PNS input/output model as the Python SDK
- **Observability** - Built-in metrics collection and data logging

### What is FEAGI?

**FEAGI (Framework for Evolutionary Artificial General Intelligence)** is a biologically inspired, modular neural execution engine designed for **embodied AI and robotics**. FEAGI enables spiking-neural-circuit-driven perception, cognition, and control across simulated and physical embodiments, with a strong emphasis on **real-time interaction, modularity, and cross-platform deployment**.

FEAGI serves as the core neural runtime behind **Neurorobotics Studio**, powering a growing ecosystem of reusable neural components ("brains"), tools, and integrations for robotics and physical AI.

---

## Installation

### Gradle Dependencies

```kotlin
dependencies {
    implementation("org.feagi:feagi-sdk-core:0.0.2")
    implementation("org.feagi:feagi-sdk-native:0.0.2")
    // Optional: Engine control
    implementation("org.feagi:feagi-sdk-engine:0.0.2")
    // Optional: CLI tools
    implementation("org.feagi:feagi-sdk-cli:0.0.2")
}
```

### Using the SDK as a Maven Dependency

Add the following to your `pom.xml` to consume the published artifacts:

**feagi-sdk-core** (public API types, no native code):

```xml
<dependency>
  <groupId>org.feagi</groupId>
  <artifactId>feagi-sdk-core</artifactId>
  <version>0.0.2</version>
</dependency>
```

**feagi-sdk-native** (JNI bindings + native library for your platform):

```xml
<!-- JNI binding classes -->
<dependency>
  <groupId>org.feagi</groupId>
  <artifactId>feagi-sdk-native</artifactId>
  <version>0.0.2</version>
</dependency>

<!-- Native library for your target platform (choose one classifier) -->
<dependency>
  <groupId>org.feagi</groupId>
  <artifactId>feagi-sdk-native</artifactId>
  <version>0.0.2</version>
  <classifier>linux-x86_64</classifier>
</dependency>
```

The classifier artifact contains the pre-built native library (`.so`, `.dylib`, or `.dll`) for the
specified platform. The SDK's native loader automatically extracts the library from the classpath at
runtime — no manual `java.library.path` configuration is required.

**Available platform classifiers:**

| Classifier | Platform |
|---|---|
| `linux-x86_64` | Linux, 64-bit x86 |
| `linux-aarch64` | Linux, 64-bit ARM (e.g. Raspberry Pi 4 / AWS Graviton) |
| `osx-x86_64` | macOS, Intel |
| `osx-aarch64` | macOS, Apple Silicon (M1/M2/M3) |
| `windows-x86_64` | Windows, 64-bit x86 |

**Gradle (Kotlin DSL) equivalent:**

```kotlin
dependencies {
    implementation("org.feagi:feagi-sdk-core:0.0.2")
    implementation("org.feagi:feagi-sdk-native:0.0.2")

    // Choose the classifier matching your target platform:
    runtimeOnly("org.feagi:feagi-sdk-native:0.0.2:linux-x86_64")
    // runtimeOnly("org.feagi:feagi-sdk-native:0.0.2:linux-aarch64")
    // runtimeOnly("org.feagi:feagi-sdk-native:0.0.2:osx-x86_64")
    // runtimeOnly("org.feagi:feagi-sdk-native:0.0.2:osx-aarch64")
    // runtimeOnly("org.feagi:feagi-sdk-native:0.0.2:windows-x86_64")
}
```

---

### Maven Central publication
This repository now includes a Maven multi-module build for publication:
- Parent POM: `pom.xml`
- Module POMs: `sdk-core/pom.xml`, `sdk-engine/pom.xml`, `sdk-native/pom.xml`, `sdk-cli/pom.xml`
- Release workflow: `.github/workflows/publish-maven-central.yml`

Before publishing, create and verify a Sonatype Central namespace for `org.feagi`, then configure these GitHub secrets:
- `OSSRH_USERNAME`
- `OSSRH_TOKEN`
- `MAVEN_GPG_PRIVATE_KEY` (base64-encoded armored private key)
- `MAVEN_GPG_PASSPHRASE`

`OSSRH_USERNAME` and `OSSRH_TOKEN` must be populated with the token-based `username` and `password` values from the Sonatype Central token settings snippet.

Local signed deployment command:
```bash
mvn -Prelease clean deploy
```

Release-triggered CI deployment:
```bash
# Create and publish GitHub release 0.0.2
# This triggers .github/workflows/publish-maven-central.yml
```

### Native dependency model (planned)
- Publish native libs from `feagi-java-ffi` as **platform classifier artifacts** (e.g., `linux-aarch64`, `linux-x86_64`, `osx-aarch64`, `windows-x86_64`).
- `feagi-sdk-native` will be responsible for loading the correct native library and enforcing the ABI handshake.

### Maven Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>org.feagi</groupId>
        <artifactId>feagi-sdk-core</artifactId>
        <version>0.0.2</version>
    </dependency>
    <dependency>
        <groupId>org.feagi</groupId>
        <artifactId>feagi-sdk-native</artifactId>
        <version>0.0.2</version>
    </dependency>
</dependencies>
```

### Native Library Requirements

The SDK requires the `feagi-java-ffi` native library. For local development, build from source:

```bash
# Clone feagi-java-ffi repository
git clone https://github.com/feagi/feagi-java-ffi.git
cd feagi-java-ffi
cargo build --release

# Set library path
export LD_LIBRARY_PATH=$PWD/target/release:$LD_LIBRARY_PATH  # Linux
export DYLD_LIBRARY_PATH=$PWD/target/release:$DYLD_LIBRARY_PATH  # macOS
set PATH=%PATH%;%CD%\target\release  # Windows
```

---

## Module Description

```
feagi-java-sdk/
├── sdk-core/      # Public API: config, PNS inputs/outputs, observability, BaseAgent
├── sdk-engine/    # FEAGI engine control: start/stop, config loading
├── sdk-cli/       # Command-line tools: feagi init/start/stop/status
└── sdk-native/    # JNI bindings (internal use, auto-loaded)
```

### feagi-sdk-core

Core API module containing:

- **Configuration**: `AgentConfig`, `FeagiConfig`, `AgentCapabilities`, `BrainInputConfig`, `BrainOutputConfig`
- **PNS Communication**: `BrainInput`, `BrainOutput` (singleton pattern like Python SDK)
- **PNS Inputs**: `Camera`, `InfraredInput`, `NumericStream`, `TextStream`
- **PNS Outputs**: `ServoMotor`, `RotaryMotor`
- **Observability**: `MetricsCollector`, `DataLogger`, `Monitor`
- **Agent Framework**: `BaseAgent` abstract base class, `VideoStreamAgent`
- **Client**: `NativeFeagiAgentClient` implementation

### feagi-sdk-engine

FEAGI engine control:

- `FeagiEngine` - Start/stop FEAGI process
- `FeagiConfig` - Load TOML configuration
- `BvDiscovery` - Brain Visualizer discovery
- `FeagiDiscovery` - FEAGI service discovery

### feagi-sdk-cli

Command-line tools (`feagi` command):

```bash
feagi init     # Initialize configuration
feagi start    # Start FEAGI
feagi stop     # Stop FEAGI
feagi status   # Check status
feagi bv start # Start Brain Visualizer
```

### feagi-sdk-native

JNI bindings and native library loading. This module automatically handles:

- ABI version verification
- Platform-specific library loading
- Error handling

---

## How to Deploy

### Configure FEAGI Connection

The FEAGI Java SDK uses **explicit configuration** - no default assumptions. Provide connection parameters via environment variables or configuration files.

#### Environment Variables (Recommended)

```bash
export FEAGI_HOST=localhost
export FEAGI_REGISTRATION_PORT=30001
export FEAGI_SENSORY_PORT=5555
export FEAGI_MOTOR_PORT=5564
export FEAGI_API_PORT=8080
export FEAGI_AGENT_ID=my-agent-001
```

#### Configuration File

```toml
# feagi_configuration.toml
[api]
host = "localhost"
port = 8080

[websocket]
host = "localhost"
enabled = true
sensory_port = 5555
motor_port = 5564
```

### System Requirements

- **Java**: 17 or higher
- **OS**: Linux (x86_64/aarch64), macOS (Intel/Apple Silicon), Windows (x86_64)
- **Native Dependencies**: `feagi-java-ffi` shared library

---

## How to Use the SDK

### Quick Start

The simplest agent example - connect, send sensory data, receive motor commands:

```java
import io.feagi.sdk.core.pns.BrainInput;
import io.feagi.sdk.core.pns.BrainOutput;
import io.feagi.sdk.core.pns.BrainInputConfig;
import io.feagi.sdk.core.BrainOutputConfig;

public class MinimalAgent {
    public static void main(String[] args) throws Exception {
        // Get singleton instances (like Python's brain_input/brain_output)
        BrainInput brainInput = BrainInput.getInstance();
        BrainOutput brainOutput = BrainOutput.getInstance();

        // Configure
        brainInput.configure(BrainInputConfig.create()
            .feagiHost(System.getenv("FEAGI_HOST"))
            .feagiPort(Integer.parseInt(System.getenv("FEAGI_SENSORY_PORT")))
            .build());

        brainOutput.configure(BrainOutputConfig.create()
            .feagiHost(System.getenv("FEAGI_HOST"))
            .agentId(System.getenv("FEAGI_AGENT_ID"))
            .registrationPort(Integer.parseInt(System.getenv("FEAGI_REGISTRATION_PORT")))
            .motorPort(Integer.parseInt(System.getenv("FEAGI_MOTOR_PORT")))
            .build());

        // Connect
        brainInput.connect();
        brainOutput.connect();
        System.out.println("Connected to FEAGI");

        // Main loop
        for (int i = 0; i < 100; i++) {
            // Send sensory data
            byte[] sensoryData = captureSensors();
            brainInput.send(sensoryData);

            // Receive motor commands
            MotorDataFrame motorData = brainOutput.receive();
            if (motorData != null) {
                applyMotors(motorData);
            }

            Thread.sleep(16);  // ~60 Hz
        }

        // Cleanup
        brainInput.close();
        brainOutput.close();
    }

    private static byte[] captureSensors() {
        // Implement sensor data capture
        return new byte[0];
    }

    private static void applyMotors(MotorDataFrame data) {
        // Implement motor control
    }
}
```

### Using PNS Devices

The Java SDK provides PNS input/output devices equivalent to the Python SDK:

```java
import io.feagi.sdk.pns.inputs.Camera;
import io.feagi.sdk.core.motor.ServoMotor;
import io.feagi.sdk.core.pns.BrainInput;
import io.feagi.sdk.core.pns.BrainOutput;

// Register devices
Camera camera = Camera.builder()
    .resolution(640, 480)
    .channels(3)
    .encoding("RGB")
    .build();

ServoMotor servo = ServoMotor.builder()
    .angleRange(0.0, 180.0)
    .encoding(ServoMotor.Encoding.ABSOLUTE)
    .build();

// Register with cache
camera._registerWithCache();
servo._registerWithCache();

// Use in main loop
BrainInput brainInput = BrainInput.getInstance();
BrainOutput brainOutput = BrainOutput.getInstance();

while (running) {
    // Set camera frame
    byte[] frameData = captureCamera();
    camera.setFrame(frameData);
    brainInput.send(camera.toBytes());

    // Read servo angle
    MotorDataFrame motorData = brainOutput.receive();
    if (motorData != null) {
        servo.updateFromFrame(motorData);
        double angle = servo.getAngle();
        setServoHardware(angle);
    }
}
```

### Using BaseAgent

`BaseAgent` provides a standard agent lifecycle:

```java
import io.feagi.sdk.agent.BaseAgent;
import io.feagi.sdk.core.*;

public class MyRobotAgent extends BaseAgent {
    public MyRobotAgent() {
        super("robot-001", buildCapabilities());
    }

    @Override
    public void initializeHardware() {
        // Initialize robot hardware
    }

    @Override
    public Map<String, byte[]> mapSensors(Object hardwareData) {
        // Convert hardware sensor data -> FEAGI format
        return Map.of("camera", imageBytes);
    }

    @Override
    public Object mapMotors(Map<String, Object> feagiOutput) {
        // Convert FEAGI commands -> hardware format
        return motorCommands;
    }

    @Override
    protected void runLoop() {
        // Implement main control loop
        while (isRunning()) {
            // Sense -> Send
            // Cognition -> FEAGI processing
            // Act -> Receive motor commands
        }
    }

    private static AgentCapabilities buildCapabilities() {
        return AgentCapabilities.builder()
            .vision(VisionCapability.builder()
                .modality("vision")
                .resolution(640, 480)
                .channels(3)
                .build())
            .motor(MotorCapability.builder()
                .modality("motor")
                .outputCount(4)
                .build())
            .build();
    }

    public static void main(String[] args) {
        MyRobotAgent agent = new MyRobotAgent();
        agent.initializeHardware();
        agent.connect();
        agent.run();
    }
}
```

### Observability

#### Metrics Collection

```java
import io.feagi.sdk.observability.MetricsCollector;
import io.feagi.sdk.observability.Monitor;

MetricsCollector metrics = new MetricsCollector();

// Attach to PNS events
brainInput.attachMonitor(metrics);
brainOutput.attachMonitor(metrics);

// Get statistics after running
InputStatistics inputStats = metrics.getInputStatistics();
System.out.println("Data rate: " + inputStats.getDataRateMbps() + " MB/s");

// Export
metrics.exportJson("metrics.json");
metrics.exportCsv("metrics.csv");
```

#### Data Logging

```java
import io.feagi.sdk.observability.DataLogger;

DataLogger logger = new DataLogger.Builder()
    .outputFile("agent_data.jsonl")
    .format(DataLogger.Format.JSONL)
    .logInputs(true)
    .logOutputs(true)
    .sampleRate(1.0)
    .build();

// Attach logger
brainInput.attachMonitor(logger);
brainOutput.attachMonitor(logger);

// Close after running
logger.close();
```

---

## Examples

See [`examples/`](./examples/) directory for complete runnable examples:

| Example | Description |
|---------|-------------|
| `minimal-agent` | Minimal connect + send sensory + poll motor using BrainInput/BrainOutput |
| `vision-agent` | Vision input agent using VideoStreamAgent |
| `motor-agent` | Motor output agent using BrainOutput |
| `servo-motor` | Servo motor control with multiple servos |
| `observability/` | Metrics collection and data logging examples |

---

## Differences from Python SDK

The Java SDK API follows Java conventions while maintaining functional parity with the Python SDK:

| Python SDK | Java SDK |
|------------|----------|
| `from feagi.pns import brain_input` | `BrainInput.getInstance()` |
| `Camera.register(...)` | `Camera.builder()...build()` |
| `enable_monitoring()` | `MetricsCollector` / `DataLogger` |
| `feagi start` (CLI) | `FeagiEngine.start()` / `feagi` CLI |

---

## Troubleshooting

### Native Library Loading Failure

```
UnsatisfiedLinkError: no feagi_java_ffi in java.library.path
```

**Solution**: Ensure `feagi-java-ffi` shared library is in the library path:

```bash
# Linux
export LD_LIBRARY_PATH=/path/to/feagi-java-ffi/target/release:$LD_LIBRARY_PATH

# macOS
export DYLD_LIBRARY_PATH=/path/to/feagi-java-ffi/target/release:$DYLD_LIBRARY_PATH

# Windows
set PATH=%PATH%;C:\path\to\feagi-java-ffi\target\release
```

### Connection Refused

```
FeagiSdkException: connect failed (status=1): Connection refused
```

**Solution**:
1. Verify FEAGI server is running
2. Check port configuration (default: registration 30001, sensory 5555, motor 5564)
3. Verify firewall settings

---

## Community & Support

- **Discord**: [Join our community](https://discord.gg/PTVC8fyGN8)
- **Issues**: [Report bugs](https://github.com/feagi/feagi-java-sdk/issues)
- **Homepage**: [feagi.org](https://feagi.org)

---

## Requirements

- Java 17 or higher
- Linux, macOS, and Windows supported

---

## License

Apache 2.0 - See [LICENSE](LICENSE) for details.

**Copyright 2016-2026 Neuraville Inc. All Rights Reserved.**

---

## About Neuraville

FEAGI is developed by **Neuraville**, a company focused on democratizing robotics and enabling the next generation of embodied AI through modular, biologically inspired intelligence systems.
