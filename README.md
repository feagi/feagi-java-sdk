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

### Next implementation steps
1. Implement a JNI layer in `sdk-native` that calls into the C ABI exposed by `feagi-java-ffi`.
2. Implement a high-level `FeagiAgentClient` wrapper that:
   - requires explicit endpoints/timeouts
   - calls `connect()`
   - uses `feagi_client_registration_*` helpers to obtain ports/transports deterministically
3. Add architecture compliance tests (no hardcoded network settings).

