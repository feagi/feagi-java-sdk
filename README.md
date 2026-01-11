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
3. Add architecture compliance tests (no hardcoded host/ports/timeouts; fail-fast config).
4. Add a minimal runnable example (connect + send bytes + poll motor bytes) that uses explicit configuration only.

