## FEAGI Java SDK (skeleton)

This repository is a **starter skeleton** for a Rust-backed FEAGI Java SDK intended for robotics environments.

### Goals (guardrails)
- **Java 17 baseline** (robotics-friendly LTS).
- **No hardcoded runtime host/ports/timeouts** in SDK code.
- **Fail-fast configuration**: callers must supply config explicitly.
- **Rust-backed I/O** via a native library (`feagi-java-ffi`) with a stable C ABI.
- **ABI handshake required**: Java must check `feagi_abi_version()` at startup.

### Project layout
- `sdk-core`: public API types (config, enums, exceptions, interfaces). No JNI here.
- `sdk-native`: JNI-facing bindings and a small native loader (no networking defaults).

### JNI bridge location
The JNI bridge is the native C/C++ layer that implements the `native` methods in
`sdk-native/src/main/java/io/feagi/sdk/nativeffi/FeagiNativeBindings.java` and forwards them to
the C ABI in `feagi-java-ffi` via `feagi_java_ffi.h`.

Recommended placement for the bridge sources:
- `sdk-native/src/main/cpp` (JNI C/C++ implementations and build files)

### Build (local)
```bash
./gradlew build
```

### Native dependency model (planned)
- Publish native libs from `feagi-java-ffi` as **platform classifier artifacts** (e.g., `linux-aarch64`, `linux-x86_64`, `osx-aarch64`, `windows-x86_64`).
- `sdk-native` will be responsible for loading the correct native library and enforcing the ABI handshake.

### Next implementation steps
1. Implement a JNI layer in `sdk-native` that calls into the C ABI exposed by `feagi-java-ffi`.
   - Note: `feagi-java-ffi` is a **C ABI**, so the Java SDK needs a small JNI bridge library that links to it.
2. Implement a high-level `FeagiAgentClient` wrapper that:
   - requires explicit endpoints/timeouts
   - calls `connect()`
   - uses `feagi_client_registration_*` helpers to obtain ports/transports deterministically
3. Ensure capability types (vision/motor/visualization/sensory) remain aligned with `feagi-java-ffi`.
4. Enforce ABI handshake and error plumbing in the loader and client setup.
5. Add architecture compliance tests (no hardcoded host/ports/timeouts; fail-fast config).
6. Add a minimal runnable example (connect + send bytes + poll motor bytes) that uses explicit configuration only.

### Parity with Python SDK (pending)
The Python SDK includes broader functionality (agent framework, config tooling, CLI, examples, observability).
To bring the Java SDK to comparable capability:

1. Agent framework: BaseAgent-style lifecycle (initialize/map/run/close) and a scheduler loop.
2. Config tooling: TOML/YAML config loader for FEAGI endpoints/capabilities with strict validation.
3. CLI utilities: basic `feagi`-like commands for config init, connection checks, and examples.
4. Observability hooks: structured logging and optional metrics/tracing adapters.
5. Documentation + examples: guided examples mirroring Python SDK flows (sensory, motor, vision, viz).
6. Packaging: publish `sdk-core` and `sdk-native` to Maven, with native classifier artifacts.

Parity reference anchors from the Python SDK:
- Docs overview: `feagi-python-sdk/docs/README.md`
- Examples index: `feagi-python-sdk/examples/README.md`
- Observability examples: `feagi-python-sdk/examples/observability/README.md`
- CLI entrypoint: `feagi-python-sdk/feagi/cli/main.py`
- Agent framework: `feagi-python-sdk/feagi/agent/`

