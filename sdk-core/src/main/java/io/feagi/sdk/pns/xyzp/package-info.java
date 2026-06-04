/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * XYZP neuron-data support types for the Java SDK.
 *
 * <h2>Motor/sensor decoding is owned by the Rust core (single source of truth)</h2>
 * The Java SDK does <strong>not</strong> decode XYZP motor or sensor data. All
 * neuron-voxel decoding lives in the feagi-core Rust {@code MotorDeviceCache}
 * (and its sensory counterparts) so that every SDK -- Python, Java, and future
 * ones -- shares one implementation and cannot diverge in behaviour. The Python
 * SDK already routes decode through Rust via {@code feagi_rust_py_libs}; the
 * retired Python module {@code feagi/pns/xyzp_decoders.py} and the previous
 * Java {@code XyzpDecoders} class were removed for this reason.
 *
 * <p>The Java SDK currently obtains <em>raw, undecoded</em> motor bytes from the
 * transport via {@code FeagiAgentClient.pollMotorBytes()}. There is intentionally
 * no Java-side decoder.
 *
 * <h2>TODO: Rust-FFI decode surface (required to wire typed motor decode)</h2>
 * Java motor decode must be implemented by exposing the existing Rust
 * {@code MotorDeviceCache} through {@code feagi-java-ffi}, mirroring the
 * {@code feagi_rust_py_libs} surface. No decode logic may be added to Java code;
 * the following native functions are needed (names illustrative, to match the
 * Rust/Python contract):
 * <ul>
 *   <li>Create/own a motor device cache handle bound to the agent's
 *       registered capabilities.</li>
 *   <li>Register typed motor units (e.g. positional servo, rotary, spatial
 *       pointer) with their group/channel/parameters -- the contract is typed
 *       registration, not generic index-to-power guessing.</li>
 *   <li>{@code feagi_motor_cache_load_bytes_and_verify(handle, bytes)} -- load a
 *       raw motor frame from {@code pollMotorBytes()} into the cache.</li>
 *   <li>{@code feagi_motor_cache_decode(handle)} -- decode the cached frame
 *       through the Rust decoder.</li>
 *   <li>{@code feagi_motor_cache_read_decoded_snapshot(handle)} -- read the flat
 *       {@code (group, channel, mode, value)} snapshot (parity with Python
 *       {@code motors_read_decoded_snapshot}).</li>
 * </ul>
 * Once those exist in {@code feagi_java_ffi.h} and the committed native libraries
 * are rebuilt for all target platforms, add the JNI bindings, wire the Java
 * outputs/agents to the snapshot, and keep this package free of decode math.
 *
 * <p>{@link io.feagi.sdk.pns.xyzp.XyzpNeuronSoA} and
 * {@link io.feagi.sdk.pns.xyzp.CorticalIdUtils} remain as lightweight,
 * decode-free helpers (a neuron struct-of-arrays DTO and cortical-ID parsing).
 * They are not currently consumed and are candidates for removal or reuse when
 * the Rust-FFI decode surface above is implemented.
 */
package io.feagi.sdk.pns.xyzp;
