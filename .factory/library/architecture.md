# Architecture

Architectural decisions, patterns discovered, and design notes.

---

## Module Structure

| Module | Package | Purpose |
|--------|---------|---------|
| `feagi-sdk-core` | `io.feagi.sdk.core` | Public API types — config, capabilities, enums, interfaces. No JNI. |
| `feagi-sdk-native` | `io.feagi.sdk.nativeffi` | JNI binding skeleton + native library loader. Depends on `feagi-sdk-core`. |
| `feagi-sdk-engine` | `io.feagi.sdk.engine` | Engine layer (out of scope for this mission). |
| `feagi-sdk-cli` | `io.feagi.sdk.cli` | CLI tool (out of scope for this mission). |

## Maven Publishing Architecture

- **Parent POM** (`feagi-sdk-parent`) — aggregator with Maven Central metadata
- **central-publishing-maven-plugin** v0.9.0 — Sonatype Central Portal (newer approach, not legacy OSSRH staging)
- **Release profile** — attaches source JARs, javadoc JARs, GPG signatures
- **Classified JARs** — platform-specific native lib JARs for feagi-sdk-native (5 platforms: linux-x86_64, linux-aarch64, osx-x86_64, osx-aarch64, windows-x86_64). Produced by maven-assembly-plugin (Maven) and custom Jar tasks (Gradle `nativeJars`).

## Native Library Loading

`FeagiNativeLibrary.loadFromClasspath()` — auto-detects OS/arch → extracts native lib from classpath JAR → `System.load()` → fallback to `System.loadLibrary("feagi_java_ffi")`. Thread-safe via `volatile` + `synchronized`. Temp files marked `deleteOnExit()`. Legacy `load(String)` and `loadAndVerify(String)` methods preserved for manual path usage.

## Test Source Layout

- sdk-core: `tests/` (non-standard, configured via Gradle and Maven build-helper)
- sdk-native: `src/test/java/` (standard Maven layout)
- sdk-engine: `tests/` (non-standard)
- sdk-cli: `tests/` (non-standard)
