/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.AgentCapabilities;
import io.feagi.sdk.core.AgentConfig;
import io.feagi.sdk.core.FeagiAgentClient;
import io.feagi.sdk.core.FeagiResolution;
import io.feagi.sdk.core.FeagiSdkException;
import io.feagi.sdk.core.MotorCapability;
import io.feagi.sdk.core.MotorUnitSpec;
import io.feagi.sdk.core.SensoryCapability;
import io.feagi.sdk.core.SensorySocketConfig;
import io.feagi.sdk.core.VisionCapability;
import io.feagi.sdk.core.VisualizationCapability;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link FeagiAgentClient} implementation backed by the Rust feagi-java-ffi library via JNI.
 *
 * <h2>Lifecycle</h2>
 * <pre>{@code
 * AgentConfig config = new AgentConfig(...);
 *
 * try (NativeFeagiAgentClient client = new NativeFeagiAgentClient(config)) {
 *     client.connect();            // retries with backoff per config
 *     client.sendSensoryBytes(payload);
 *     byte[] motor = client.pollMotorBytes();
 *     client.disconnect();         // graceful disconnect; close() also works
 * }
 * // This object is permanently closed after close()/disconnect().
 * // Construct a new instance if a new connection is needed.
 * }</pre>
 *
 * <h2>Retry</h2>
 * {@link #connect()} attempts registration up to {@code registrationRetries + 1} times,
 * sleeping {@code retryBackoff} between attempts. On each failed attempt the native
 * client handle is freed before the next try to prevent handle leaks.
 *
 * <h2>Heartbeat</h2>
 * When {@code heartbeatInterval} is positive, a background daemon thread sends a
 * heartbeat to the native layer at that interval. When the interval is zero,
 * heartbeating is disabled. The heartbeat stops automatically on {@link #close()}.
 *
 * <h2>Handle ownership</h2>
 * <ul>
 *   <li>{@code cfgHandle} — allocated in {@link #connect()}, freed in the same call after
 *       the client handle is created.</li>
 *   <li>{@code clientHandle} — allocated by {@link #connect()}, freed by {@link #close()}.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * {@link #sendSensoryBytes} and {@link #pollMotorBytes} are safe to call concurrently from
 * any thread after {@link #connect()} returns. A read-write lock guards the handle:
 * the read lock is held during send/poll native calls; the write lock is held during
 * {@link #connect()} and {@link #close()} to prevent concurrent double-connect or
 * use-after-free races. {@link #isConnected()} reads a {@code volatile} field and is
 * safe from any thread without holding the lock.
 *
 * <h2>FEAGI lifecycle recovery (TODO)</h2>
 * The Rust crate {@code feagi-agent::clients::recovery} ships the cross-SDK
 * source of truth for detecting genome reloads, FEAGI restarts, and network
 * drops. The Python SDK exposes this via {@code feagi.pns.health_monitor};
 * the Java SDK still needs equivalent surface:
 * <ul>
 *   <li>JNI wrappers for {@code HealthSnapshot}, {@code HealthWatcher},
 *       {@code RecoveryTrigger}, {@code ReconnectPolicyConfig},
 *       {@code ReconnectPolicy}, {@code ReconnectDecision}.</li>
 *   <li>JNI wrapper for {@code fetch_health_snapshot_blocking} (host, port,
 *       timeout — no defaults).</li>
 *   <li>A {@code reconnect(reason)} method on this class implementing the
 *       same contract as Python's {@code PyAgentClient.reconnect}: best-effort
 *       disconnect, fresh connect, replay of any cached device registrations.</li>
 *   <li>A {@code FeagiHealthMonitor} facade that composes the above in the
 *       documented tick order (fetch -> observe -> decide -> reconnect).</li>
 * </ul>
 * No new decision logic may live in Java code: every threshold/transition
 * goes through the Rust types so behaviour matches Rust and Python exactly.
 */
public final class NativeFeagiAgentClient implements FeagiAgentClient {

    private static final Logger LOG = Logger.getLogger(NativeFeagiAgentClient.class.getName());
    private static final long NULL_HANDLE = 0L;

    private final AgentConfig config;

    /**
     * Opaque pointer to the native {@code FeagiAgentClientHandle}.
     * Set by {@link #connect()}, cleared by {@link #close()}.
     *
     * <p>Note: {@code AtomicLong} is used here for its memory-model guarantees on
     * individual reads/writes, but all accesses already occur under {@link #handleLock}.
     * The combination is belt-and-suspenders: the lock provides the critical-section
     * guarantee; the AtomicLong makes the intent explicit and avoids requiring
     * readers to reason about lock scopes when they see {@code clientHandle.get()}.
     * Similarly, {@code connected} is {@code volatile} even though reads/writes happen
     * under the lock — this keeps the visibility guarantee self-documenting.
     */
    private final AtomicLong clientHandle = new AtomicLong(NULL_HANDLE);

    /**
     * Guards the handle against races between connect/close and concurrent send/poll.
     *
     * <ul>
     *   <li>Read lock: held by {@link #sendSensoryBytes} and {@link #pollMotorBytes}
     *       for the duration of the native call.</li>
     *   <li>Write lock: held for the entire body of {@link #connect()} and
     *       {@link #close()} so that two threads cannot both pass the
     *       {@code connected == false} guard, and so that close() cannot free the
     *       handle while a send/poll is in flight.</li>
     * </ul>
     */
    private final ReentrantReadWriteLock handleLock = new ReentrantReadWriteLock();

    private volatile boolean connected = false;

    /**
     * Set permanently to {@code true} by {@link #close()}. Once set, {@link #connect()}
     * throws {@link IllegalStateException} immediately — this object is not reusable after
     * close. Construct a new instance for a new connection.
     *
     * <p>Also checked in the post-connect write-lock block to detect the race where
     * {@code close()} ran while {@code attemptConnect()} was executing the blocking
     * network call — in that case the freshly allocated handle is freed immediately.
     */
    private volatile boolean closed = false;

    /**
     * CAS guard that ensures only one thread can enter the retry loop at a time.
     * {@code true} means a connect is in progress; the second caller sees this and throws
     * immediately rather than racing into native allocation. Cleared on success or failure.
     *
     * <p>This is separate from {@code connected} because {@code connected} reflects
     * "is fully connected", whereas {@code connecting} reflects "is mid-connect". Without
     * this guard, two concurrent callers could both pass the {@code connected == false}
     * check, both enter the retry loop, and the first thread's client handle would be
     * silently overwritten by the second and never freed.
     */
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    // ── Heartbeat ──────────────────────────────────────────────────────────────

    /**
     * Scheduler for the background heartbeat. {@code null} when heartbeat is disabled
     * or the client is not connected.
     *
     * <p>{@code volatile} is required here: {@link #shutdownHeartbeat()} reads and writes
     * this field in Phase 1 of {@link #close()} <em>without</em> holding the write lock
     * (to avoid a deadlock with the heartbeat thread's read lock). Without {@code volatile},
     * the JMM provides no happens-before between the write in {@link #startHeartbeatIfConfigured()}
     * (inside the write lock) and this lock-free read — Phase 1 could legally see a stale null.
     */
    private volatile ScheduledExecutorService heartbeatExecutor;
    // heartbeatTask is intentionally not stored: shutdownNow() on the executor subsumes
    // any per-task cancel(), so retaining the future would add no cancellation capability
    // and would create a misleading impression that an explicit cancellation path exists.

    // ── Construction ───────────────────────────────────────────────────────────

    /**
     * Create a new client. The native library must already be loaded via
     * {@link FeagiNativeLibrary#loadAndVerify(String)} before constructing this object.
     *
     * @param config fully-populated agent configuration; must not be null
     */
    public NativeFeagiAgentClient(AgentConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    // ── FeagiAgentClient ───────────────────────────────────────────────────────

    /**
     * Connect and register the agent with FEAGI, retrying on failure.
     *
     * <p>Attempts up to {@code registrationRetries + 1} times total, sleeping
     * {@code retryBackoff} between each attempt. On each failed attempt the native
     * client handle is freed before the next try. After a successful connection,
     * starts the background heartbeat if configured.
     *
     * <p>Thread safety: a {@code connecting} CAS guard ensures only one thread can
     * enter the retry loop at a time. A second concurrent caller receives
     * {@link IllegalStateException} immediately. The write lock is used only for the
     * final {@code connected = true} and handle assignment so it is not held across
     * the blocking network call.
     *
     * @throws FeagiSdkException     if all connection attempts fail
     * @throws IllegalStateException if already connected or a connect is already in progress
     */
    @Override
    public void connect() {
        // Permanent-close guard: once close() has been called, this object cannot be reused.
        // Native handles are freed in close() and cannot be reallocated on the same instance.
        // Construct a new NativeFeagiAgentClient if a new connection is needed.
        if (closed) {
            throw new IllegalStateException(
                    "NativeFeagiAgentClient for agent '" + config.agentId()
                    + "' has been permanently closed and cannot be reused. "
                    + "Construct a new instance.");
        }
        // Fast-path: avoid CAS overhead for the common already-connected case.
        // This is NOT the authoritative guard — two threads could both observe connected==false
        // here, both pass, and then one wins the CAS below while the other hits ISE from the
        // CAS block. The authoritative guard is the re-check of connected inside the try block
        // below (after the CAS), which cannot be reached by two threads simultaneously.
        if (connected) {
            throw new IllegalStateException("Already connected. Call close() first.");
        }
        // CAS guard: only one thread may enter the retry loop. Without this, two concurrent
        // callers could both pass the connected == false check and race into native allocation,
        // with the first thread's handle silently overwritten and leaked by the second.
        if (!connecting.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "A connect() call is already in progress for agent '"
                    + config.agentId() + "'.");
        }
        try {
            // Authoritative already-connected guard — reached by at most one thread at a time
            // due to the CAS above. The outer check was a fast-path only; do not remove this.
            if (connected) {
                throw new IllegalStateException("Already connected. Call close() first.");
            }

            int maxAttempts = config.registrationRetries() + 1;
            long backoffMs  = config.retryBackoff().toMillis();
            FeagiSdkException lastFailure = null;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (closed) {
                    throw new FeagiSdkException(
                            "NativeFeagiAgentClient was closed while connect() retries were in progress.");
                }
                if (attempt > 1) {
                    LOG.info("NativeFeagiAgentClient: retry " + attempt + "/" + maxAttempts
                            + " (backoff=" + backoffMs + "ms)");
                    if (backoffMs > 0) {
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new FeagiSdkException(
                                    "Interrupted during registration retry backoff", ie);
                        }
                    }
                } else {
                    LOG.info("NativeFeagiAgentClient: connecting → "
                            + config.endpoints().registrationEndpoint());
                }

                try {
                    attemptConnect();
                    // Set connected and start heartbeat atomically under the write lock.
                    // Also check whether close() ran while attemptConnect() was executing the
                    // blocking network call (no lock was held during that call). If close() ran
                    // in the interim, the freshly allocated handle must be freed immediately
                    // rather than stored — otherwise the client silently re-connects after
                    // close() has returned.
                    handleLock.writeLock().lock();
                    try {
                        if (closed) {
                            // close() ran while we were connecting — discard the handle.
                            long staleHandle = clientHandle.getAndSet(NULL_HANDLE);
                            if (staleHandle != NULL_HANDLE) {
                                try {
                                    FeagiNativeBindings.feagiClientFree(staleHandle);
                                } catch (Exception e) {
                                    LOG.log(Level.WARNING,
                                            "NativeFeagiAgentClient: error freeing handle "
                                            + "after close() raced with connect()", e);
                                }
                            }
                            throw new FeagiSdkException(
                                    "NativeFeagiAgentClient: close() was called while "
                                    + "connect() was in flight — connection aborted.");
                        }
                        connected = true;
                        try {
                            startHeartbeatIfConfigured();
                        } catch (Throwable t) {
                            // startHeartbeatIfConfigured() threw something unexpected
                            // (e.g. OutOfMemoryError from executor creation). Roll back:
                            // free the handle and clear state so the client is not left
                            // in a connected=true / no-heartbeat limbo.
                            connected = false;
                            long staleHandle = clientHandle.getAndSet(NULL_HANDLE);
                            if (staleHandle != NULL_HANDLE) {
                                try {
                                    FeagiNativeBindings.feagiClientFree(staleHandle);
                                } catch (Exception e) {
                                    LOG.log(Level.WARNING,
                                            "NativeFeagiAgentClient: error freeing handle "
                                            + "during heartbeat startup rollback", e);
                                }
                            }
                            throw new FeagiSdkException(
                                    "NativeFeagiAgentClient: failed to start heartbeat "
                                    + "after successful connect — connection rolled back", t);
                        }
                    } finally {
                        handleLock.writeLock().unlock();
                    }
                    LOG.info("NativeFeagiAgentClient connected: agentId=" + config.agentId()
                            + " type=" + config.agentType()
                            + " registration=" + config.endpoints().registrationEndpoint()
                            + " (attempt " + attempt + "/" + maxAttempts + ")");
                    return;
                } catch (FeagiSdkException e) {
                    if (closed) {
                        throw e;
                    }
                    lastFailure = e;
                    LOG.log(Level.WARNING,
                            "NativeFeagiAgentClient: attempt " + attempt + " failed: "
                            + e.getMessage());
                }
            }

            throw new FeagiSdkException(
                    "NativeFeagiAgentClient: failed to connect after " + maxAttempts
                    + " attempt(s) to " + config.endpoints().registrationEndpoint(),
                    lastFailure);
        } finally {
            connecting.set(false);
        }
    }

    /**
     * Single connection attempt. Allocates a native config + client handle, applies
     * all config, validates, and calls {@code feagiClientConnect}. Frees the config
     * handle unconditionally and frees the client handle on failure.
     *
     * @throws FeagiSdkException if any native step fails
     */
    private void attemptConnect() {
        // AgentTypeCode provides a stable ABI mapping — never use .ordinal() here.
        long cfgHandle = FeagiNativeBindings.feagiConfigNew(
                config.agentId(),
                AgentTypeCode.of(config.agentType()));
        if (cfgHandle == NULL_HANDLE) {
            throw new FeagiSdkException(
                    "feagiConfigNew failed for agent '" + config.agentId() + "': "
                    + nativeError());
        }

        long newClientHandle = NULL_HANDLE;
        try {
            applyEndpoints(cfgHandle);
            applyTimingConfig(cfgHandle);
            applySensorySocketConfig(cfgHandle);
            applyCapabilities(cfgHandle);
            // TODO: wire feagiConfigSetAgentDescriptor and feagiConfigSetAuthTokenBase64
            // once AgentConfig exposes manufacturer/agentName/agentVersion/authToken fields.

            checkStatus(FeagiNativeBindings.feagiConfigValidate(cfgHandle),
                    "feagiConfigValidate");

            long[] outClient = new long[1];
            checkStatus(FeagiNativeBindings.feagiClientNew(cfgHandle, outClient),
                    "feagiClientNew");

            newClientHandle = outClient[0];
            if (newClientHandle == NULL_HANDLE) {
                throw new FeagiSdkException(
                        "feagiClientNew returned null handle: " + nativeError());
            }

            checkStatus(FeagiNativeBindings.feagiClientConnect(newClientHandle),
                    "feagiClientConnect");

            clientHandle.set(newClientHandle);
            newClientHandle = NULL_HANDLE; // conditionally transferred — the write-lock block in
                                           // connect() will either confirm the handle (connected=true)
                                           // or free it (if close() raced). Do not free in finally.

        } finally {
            FeagiNativeBindings.feagiConfigFree(cfgHandle);
            if (newClientHandle != NULL_HANDLE) {
                FeagiNativeBindings.feagiClientFree(newClientHandle);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses try-send semantics — frames may be silently dropped under ZMQ backpressure
     * (real-time contract, no implicit buffering).
     */
    @Override
    public void sendSensoryBytes(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("payload must not be null or empty");
        }

        handleLock.readLock().lock();
        try {
            requireConnected("sendSensoryBytes");

            // outSent must be method-local: the read lock allows multiple threads to
            // hold it simultaneously, so a shared field would cause a data race between
            // concurrent sendSensoryBytes callers (thread A reads thread B's result).
            boolean[] outSent = new boolean[1];
            int status = FeagiNativeBindings.feagiClientTrySendSensoryBytes(
                    clientHandle.get(), payload, outSent);

            if (status != FeagiNativeBindings.FeagiStatus.OK.code()) {
                throw new FeagiSdkException(
                        "sendSensoryBytes failed (status=" + status + "): " + nativeError());
            }
            if (!outSent[0]) {
                LOG.fine("sendSensoryBytes: frame dropped (backpressure)");
            }
        } finally {
            handleLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Non-blocking. Returns {@code null} if no motor frame is currently available.
     * Returns an empty {@code byte[]} for a zero-length frame (distinguishable from
     * "no data available").
     *
     * @throws FeagiSdkException if the native call fails, or if {@code feagiBufferLen}
     *                           returns a negative value (indicating a native-side error)
     *                           or a value exceeding {@link Integer#MAX_VALUE}
     */
    @Override
    public byte[] pollMotorBytes() {
        handleLock.readLock().lock();
        try {
            requireConnected("pollMotorBytes");

            // Method-local out-param arrays — do NOT hoist to fields.
            // The read lock allows multiple threads to hold it simultaneously,
            // so shared fields would cause data races between concurrent pollMotorBytes
            // callers (same reason outSent is local in sendSensoryBytes).
            long[] outBufHandle = new long[1];
            boolean[] outHasData = new boolean[1];

            int status = FeagiNativeBindings.feagiClientReceiveMotorBuffer(
                    clientHandle.get(), outBufHandle, outHasData);

            if (status != FeagiNativeBindings.FeagiStatus.OK.code()) {
                throw new FeagiSdkException(
                        "pollMotorBytes failed (status=" + status + "): " + nativeError());
            }

            long bufHandle = outBufHandle[0];

            // Defensive free: if the native layer ever returns hasData=false with a
            // non-null buffer handle (inconsistent state), free it rather than leak.
            if (!outHasData[0]) {
                if (bufHandle != NULL_HANDLE) {
                    LOG.warning("pollMotorBytes: native returned hasData=false with non-null "
                            + "bufHandle — possible native-side inconsistency; freeing buffer.");
                    FeagiNativeBindings.feagiBufferFree(bufHandle);
                }
                return null;  // no frame available
            }
            if (bufHandle == NULL_HANDLE) {
                // hasData=true but null handle is a native-side contract violation —
                // throw rather than propagate a null pointer into feagiBufferLen.
                throw new FeagiSdkException(
                        "feagiClientReceiveMotorBuffer: hasData=true but null bufHandle "
                        + "— native inconsistency");
            }
            try {
                long len = FeagiNativeBindings.feagiBufferLen(bufHandle);

                // Negative length is a native-side error — throw rather than swallow.
                if (len < 0) {
                    throw new FeagiSdkException(
                            "feagiBufferLen returned negative length (" + len
                            + ") — native error: " + nativeError());
                }
                if (len > Integer.MAX_VALUE) {
                    throw new FeagiSdkException(
                            "feagiBufferLen returned oversized frame (" + len
                            + " bytes) which exceeds Java array limit");
                }
                // len == 0: copyNativeBuffer returns new byte[0], not null,
                // so callers can distinguish "zero-length frame" from "no frame".
                return copyNativeBuffer(bufHandle, (int) len);
            } finally {
                FeagiNativeBindings.feagiBufferFree(bufHandle);
            }
        } finally {
            handleLock.readLock().unlock();
        }
    }

    /**
     * Return {@code true} if currently connected to FEAGI.
     *
     * <p>Reflects real connection state: {@code true} after a successful
     * {@link #connect()}, {@code false} before connect or after {@link #close()}.
     * Reads a {@code volatile} field — safe to call from any thread without a lock.
     */
    @Override
    public boolean isConnected() {
        return connected;
    }

    /**
     * Gracefully disconnect from FEAGI and release native resources.
     *
     * <p>Equivalent to {@link #close()}. Provided as a named method to match the
     * acceptance criterion ("connect(), disconnect(), isConnected()") and to make
     * non-try-with-resources usage more readable.
     *
     * <p>Idempotent — safe to call multiple times.
     */
    @Override
    public void disconnect() {
        close();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stops the heartbeat scheduler (waiting up to 2 seconds for any in-flight tick
     * to complete), then acquires the write lock to set state and free the native handle.
     * Idempotent — safe to call multiple times.
     *
     * <p><b>Lock ordering:</b> {@code stopHeartbeat()} is called <em>before</em> acquiring
     * the write lock. This is critical: the heartbeat thread holds the read lock during each
     * tick, and read/write locks are mutually exclusive. If we held the write lock while
     * calling {@code awaitTermination()}, the heartbeat thread could be blocked waiting for
     * the read lock while we waited for the thread to finish — a deadlock. By stopping the
     * heartbeat first (outside the lock), we ensure the thread has exited before we acquire
     * the write lock.
     */
    @Override
    public void close() {
        // Phase 1: shut down the heartbeat executor BEFORE acquiring the write lock.
        // The heartbeat thread holds the read lock during each tick; read and write locks
        // are mutually exclusive, so calling awaitTermination() inside the write lock
        // would deadlock. We shut down here (outside the lock) to let the thread exit.
        ScheduledExecutorService executorToAwait = shutdownHeartbeat();

        String closedAgentId = null;
        ScheduledExecutorService raceExecutor = null;
        handleLock.writeLock().lock();
        try {
            closed = true;
            connected = false;
            long handle = clientHandle.getAndSet(NULL_HANDLE);
            if (handle != NULL_HANDLE) {
                try {
                    FeagiNativeBindings.feagiClientFree(handle);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error freeing native client handle", e);
                }
                closedAgentId = config.agentId();
            }
            // Phase 2 (still inside write lock): shut down any executor that was started
            // by connect() after Phase 1 ran. This closes the race where:
            //   1. close() calls shutdownHeartbeat() → sees heartbeatExecutor==null → returns null
            //   2. connect() (holding write lock) calls startHeartbeatIfConfigured(),
            //      sets heartbeatExecutor = newExecutor
            //   3. close() acquires write lock here
            // Without this second shutdown, the new executor is orphaned and runs forever.
            // We do NOT call awaitTermination here — that happens in Phase 3, outside the lock.
            raceExecutor = shutdownHeartbeat();
        } finally {
            handleLock.writeLock().unlock();
        }

        // Phase 3: wait for both executor threads to exit, outside the lock so the heartbeat
        // thread can acquire the read lock and observe connected=false on its final tick.
        // awaitHeartbeatTermination is a no-op for null arguments, so this is always safe.
        awaitHeartbeatTermination(executorToAwait);
        awaitHeartbeatTermination(raceExecutor);

        if (closedAgentId != null) {
            LOG.info("NativeFeagiAgentClient closed: agentId=" + closedAgentId);
        }
    }

    // ── Heartbeat ──────────────────────────────────────────────────────────────

    /**
     * Start the background heartbeat scheduler if {@code heartbeatInterval} is positive.
     * Uses a single daemon thread so it does not prevent JVM shutdown.
     *
     * <p>Must be called inside the write lock. Checks {@code closed} under the lock so that
     * if {@code close()} acquired the lock between {@code stopHeartbeat()} and here, we do
     * not start a scheduler that will never be stopped.
     */
    private void startHeartbeatIfConfigured() {
        if (closed) return; // close() ran while we were connecting — don't start scheduler
        long intervalMs = config.heartbeatInterval().toMillis();
        if (intervalMs <= 0) {
            LOG.fine("NativeFeagiAgentClient: heartbeat disabled (interval=0)");
            return;
        }
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "feagi-heartbeat-" + config.agentId());
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeat, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        // [WARNING] Warn at startup so callers who set a positive heartbeatInterval without reading
        // the sendHeartbeat() comment are alerted to the semantic risk. Remove this warning
        // once feagiClientHeartbeat() is available in feagi_java_ffi.h and wired here.
        LOG.warning("NativeFeagiAgentClient: heartbeat started using empty sensory payload "
                + "as keepalive — FEAGI may misinterpret this as 'no sensory data'. "
                + "Set heartbeatInterval=0 to disable until a dedicated ABI method exists. "
                + "(interval=" + intervalMs + "ms, agentId=" + config.agentId() + ")");
    }

    /**
     * Single heartbeat tick. Errors are logged and swallowed so the scheduler keeps running.
     * Skips silently if no longer connected — avoids a spurious read-lock acquisition
     * after {@link #close()} has set {@code connected = false}.
     */
    private void sendHeartbeat() {
        if (!connected) return;
        handleLock.readLock().lock();
        try {
            if (!connected) return; // re-check under lock
            long handle = clientHandle.get();
            if (handle == NULL_HANDLE) return;
            // new byte[0] is safe to allocate per-call: modern JVMs intern zero-length
            // arrays so there is no meaningful GC cost. A static shared array would be
            // unsafe if the native ABI ever modified the buffer in-place.
            // KNOWN SEMANTIC RISK — open a follow-up issue before shipping to production.
            // Sending an empty sensory payload is used as a keepalive ping because
            // feagi_java_ffi.h has no dedicated heartbeat method. FEAGI may interpret an
            // empty payload as "agent reported no sensory data this tick" rather than a
            // neutral keepalive, which could corrupt the sensory timeline depending on
            // FEAGI's internal logic. Until a feagiClientHeartbeat() ABI method exists,
            // consider disabling heartbeat entirely (heartbeatInterval=0) for production
            // deployments where sensory timing integrity is critical.
            // TODO: Replace with feagiClientHeartbeat() once available in feagi_java_ffi.h
            int status = FeagiNativeBindings.feagiClientSendSensoryBytes(handle, new byte[0]);
            if (status != FeagiNativeBindings.FeagiStatus.OK.code()) {
                LOG.warning("NativeFeagiAgentClient: heartbeat failed (status=" + status + "): "
                        + nativeError());
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "NativeFeagiAgentClient: heartbeat error", t);
        } finally {
            handleLock.readLock().unlock();
        }
    }

    /**
     * Shut down the heartbeat executor and return it for later awaiting.
     * Clears {@code heartbeatExecutor} and calls {@code shutdownNow()}, but does NOT
     * wait for the thread to exit (no {@code awaitTermination}).
     *
     * <p>Safe to call with or without the write lock held, because {@code heartbeatExecutor}
     * is {@code volatile}.
     *
     * @return the executor that was shut down, or {@code null} if none was running;
     *         pass to {@link #awaitHeartbeatTermination(ScheduledExecutorService)}
     *         outside the write lock for a best-effort exit guarantee
     */
    private ScheduledExecutorService shutdownHeartbeat() {
        ScheduledExecutorService executor = heartbeatExecutor;
        heartbeatExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        return executor;
    }

    /**
     * Wait for the heartbeat executor thread to exit.
     * Must be called <em>outside</em> the write lock: the heartbeat thread holds the
     * read lock during each tick, and read/write locks are mutually exclusive — waiting
     * inside the write lock would deadlock.
     *
     * <p>The timeout is {@code min(heartbeatInterval + 500ms, 5000ms)}. The interval +
     * buffer normally covers one in-flight tick, since {@code sendHeartbeat()} exits
     * immediately once {@code connected == false}. The 5-second cap prevents a very long
     * heartbeat interval from making {@link #close()} block for an equally long time —
     * after the cap expires the thread will still stop harmlessly on its next tick.
     *
     * @param executor the executor returned by {@link #shutdownHeartbeat()};
     *                 a {@code null} argument is a no-op
     */
    private void awaitHeartbeatTermination(ScheduledExecutorService executor) {
        if (executor == null) return;
        // Timeout = min(heartbeatInterval + 500ms buffer, 5000ms cap).
        // The interval + buffer is normally sufficient to cover one in-flight tick, since
        // sendHeartbeat() exits immediately once connected==false. The 5-second cap prevents
        // a pathologically long heartbeatInterval (e.g. 5 minutes) from blocking close() for
        // an equally long time. Even if the timeout expires, the thread stops harmlessly on
        // its next tick because connected==false is already set.
        long timeoutMs = Math.min(config.heartbeatInterval().toMillis() + 500, 5_000);
        try {
            if (!executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                LOG.warning("NativeFeagiAgentClient: heartbeat thread did not exit within "
                        + timeoutMs + "ms after shutdown");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOG.warning("NativeFeagiAgentClient: interrupted while waiting for "
                    + "heartbeat thread to stop");
        }
    }

    // ── Config helpers ─────────────────────────────────────────────────────────

    private void applyEndpoints(long cfgHandle) {
        var ep = config.endpoints();

        checkStatus(
                FeagiNativeBindings.feagiConfigSetRegistrationEndpoint(
                        cfgHandle, ep.registrationEndpoint()),
                "feagiConfigSetRegistrationEndpoint");

        if (ep.sensoryEndpoint() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetSensoryEndpoint(
                            cfgHandle, ep.sensoryEndpoint()),
                    "feagiConfigSetSensoryEndpoint");
        }
        if (ep.motorEndpoint() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetMotorEndpoint(
                            cfgHandle, ep.motorEndpoint()),
                    "feagiConfigSetMotorEndpoint");
        }
        if (ep.visualizationEndpoint() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetVisualizationEndpoint(
                            cfgHandle, ep.visualizationEndpoint()),
                    "feagiConfigSetVisualizationEndpoint");
        }
        if (ep.controlEndpoint() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetControlEndpoint(
                            cfgHandle, ep.controlEndpoint()),
                    "feagiConfigSetControlEndpoint");
        }
    }

    private void applyTimingConfig(long cfgHandle) {
        // Use toNanos() / 1e9 rather than toMillis() / 1000.0 to preserve
        // sub-millisecond precision (e.g. Duration.ofNanos(500_000) = 0.0005 s).
        double heartbeatSecs = config.heartbeatInterval().toNanos() / 1_000_000_000.0;
        checkStatus(
                FeagiNativeBindings.feagiConfigSetHeartbeatIntervalSeconds(
                        cfgHandle, heartbeatSecs),
                "feagiConfigSetHeartbeatIntervalSeconds");

        checkStatus(
                FeagiNativeBindings.feagiConfigSetConnectionTimeoutMs(
                        cfgHandle, config.connectionTimeout().toMillis()),
                "feagiConfigSetConnectionTimeoutMs");

        checkStatus(
                FeagiNativeBindings.feagiConfigSetRegistrationRetries(
                        cfgHandle, config.registrationRetries()),
                "feagiConfigSetRegistrationRetries");

        checkStatus(
                FeagiNativeBindings.feagiConfigSetRetryBackoffMs(
                        cfgHandle, config.retryBackoff().toMillis()),
                "feagiConfigSetRetryBackoffMs");
    }

    private void applySensorySocketConfig(long cfgHandle) {
        SensorySocketConfig sc = config.sensorySocketConfig();
        checkStatus(
                FeagiNativeBindings.feagiConfigSetSensorySocketConfig(
                        cfgHandle, sc.sendHwm(), sc.lingerMs(), sc.immediate()),
                "feagiConfigSetSensorySocketConfig");
    }

    private void applyCapabilities(long cfgHandle) {
        AgentCapabilities caps = config.capabilities();
        applyVisionCapability(cfgHandle, caps.vision());
        applyMotorCapability(cfgHandle, caps.motor());
        applyVisualizationCapability(cfgHandle, caps.visualization());
        applySensoryCapability(cfgHandle, caps.sensory());

        for (Map.Entry<String, String> entry : caps.customCapabilitiesJson().entrySet()) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetCustomCapabilityJson(
                            cfgHandle, entry.getKey(), entry.getValue()),
                    "feagiConfigSetCustomCapabilityJson[" + entry.getKey() + "]");
        }
    }

    private void applyVisionCapability(long cfgHandle, VisionCapability vision) {
        if (vision == null) return;

        if (vision.targetCorticalArea() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetVisionCapability(
                            cfgHandle,
                            vision.modality(),
                            vision.width(),
                            vision.height(),
                            vision.channels(),
                            vision.targetCorticalArea()),
                    "feagiConfigSetVisionCapability");
        } else {
            // unit() is guaranteed non-null here: VisionCapability.validateSelection()
            // enforces exactly one of {targetCorticalArea, unit+group} at construction time.
            // SensoryUnitCode: stable ABI mapping — never use .ordinal()
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetVisionUnit(
                            cfgHandle,
                            vision.modality(),
                            vision.width(),
                            vision.height(),
                            vision.channels(),
                            SensoryUnitCode.of(vision.unit()),
                            vision.group()),
                    "feagiConfigSetVisionUnit");
        }
    }

    private void applyMotorCapability(long cfgHandle, MotorCapability motor) {
        if (motor == null) return;

        if (motor.sourceCorticalAreas() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetMotorCapability(
                            cfgHandle,
                            motor.modality(),
                            motor.outputCount(),
                            toJsonStringArray(motor.sourceCorticalAreas())),
                    "feagiConfigSetMotorCapability");

        } else if (motor.sourceUnits() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetMotorUnitsJson(
                            cfgHandle,
                            motor.modality(),
                            motor.outputCount(),
                            motorUnitSpecsToJson(motor.sourceUnits())),
                    "feagiConfigSetMotorUnitsJson");

        } else {
            // unit() is guaranteed non-null here: MotorCapability.validateSelection()
            // enforces exactly one of {sourceCorticalAreas, sourceUnits, unit+group}
            // at construction time.
            // MotorUnitCode: stable ABI mapping — never use .ordinal()
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetMotorUnit(
                            cfgHandle,
                            motor.modality(),
                            motor.outputCount(),
                            MotorUnitCode.of(motor.unit()),
                            motor.group()),
                    "feagiConfigSetMotorUnit");
        }
    }

    private void applyVisualizationCapability(long cfgHandle, VisualizationCapability viz) {
        if (viz == null) return;

        FeagiResolution res = viz.resolution();
        boolean hasResolution = res != null;
        int resWidth  = hasResolution ? res.width()  : 0;
        int resHeight = hasResolution ? res.height() : 0;

        Double refreshRate = viz.refreshRateHz();
        boolean hasRefreshRate = refreshRate != null;
        double refreshRateHz = hasRefreshRate ? refreshRate : 0.0;

        checkStatus(
                FeagiNativeBindings.feagiConfigSetVisualizationCapability(
                        cfgHandle,
                        viz.visualizationType(),
                        hasResolution,
                        resWidth,
                        resHeight,
                        hasRefreshRate,
                        refreshRateHz,
                        viz.bridgeProxy()),
                "feagiConfigSetVisualizationCapability");
    }

    private void applySensoryCapability(long cfgHandle, SensoryCapability sensory) {
        if (sensory == null) return;

        checkStatus(
                FeagiNativeBindings.feagiConfigSetSensoryCapability(
                        cfgHandle,
                        sensory.rateHz(),
                        sensory.shmPath()),
                "feagiConfigSetSensoryCapability");
    }

    // ── JSON serialization ─────────────────────────────────────────────────────

    /**
     * Serialize to minimal JSON string array, e.g. {@code ["v1_motor","v2_drive"]}.
     *
     * <p>Validates that each ID contains only safe ASCII identifier characters
     * (alphanumeric, underscore, hyphen) to prevent JSON injection.
     *
     * @throws IllegalArgumentException if items is null, or any ID is null/empty/unsafe
     */
    static String toJsonStringArray(List<String> items) {
        if (items == null) {
            throw new IllegalArgumentException("items must not be null");
        }
        StringBuilder sb = new StringBuilder("[");
        // An empty list produces "[]" — validateCorticalAreaId is never called.
        // An empty array is intentionally valid: the caller decides whether to omit
        // the capability entirely or pass an empty list to the native layer.
        for (int i = 0; i < items.size(); i++) {
            validateCorticalAreaId(items.get(i));
            sb.append('"').append(items.get(i)).append('"');
            if (i < items.size() - 1) {
                sb.append(',');
            }
        }
        return sb.append(']').toString();
    }

    /**
     * Validate that a cortical area ID contains only safe ASCII characters.
     *
     * @throws IllegalArgumentException if the ID is null, empty, or contains unsafe characters
     */
    static void validateCorticalAreaId(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cortical area ID must not be null or empty");
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            // Strict ASCII check — intentionally not using Character.isLetterOrDigit()
            // which accepts Unicode letters (e-acute, CJK, etc.) and would allow
            // non-ASCII characters that could cause issues in JSON or cross-language contexts.
            boolean isAsciiAlphanumeric = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9');
            if (!isAsciiAlphanumeric && c != '_' && c != '-') {
                throw new IllegalArgumentException(
                        "Cortical area ID '" + id + "' contains invalid character '"
                        + c + "' (0x" + Integer.toHexString(c) + ") at index " + i
                        + ". Only ASCII alphanumeric, underscore, and hyphen are allowed.");
            }
        }
    }

    /**
     * Serialize a list of {@link MotorUnitSpec} to a JSON array of unit/group objects.
     *
     * <p>Example output: {@code [{"unit":0,"group":1},{"unit":2,"group":0}]}
     *
     * <p>Uses {@link MotorUnitCode} for stable ABI integer mapping — never {@code .ordinal()}.
     *
     * @throws IllegalArgumentException if specs is null
     */
    static String motorUnitSpecsToJson(List<MotorUnitSpec> specs) {
        if (specs == null) {
            throw new IllegalArgumentException("specs must not be null");
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < specs.size(); i++) {
            MotorUnitSpec s = specs.get(i);
            if (s == null) {
                throw new IllegalArgumentException(
                        "specs must not contain null elements (null at index " + i + ")");
            }
            // group is guaranteed in [0, 255] by MotorUnitSpec's constructor.
            sb.append("{\"unit\":").append(MotorUnitCode.of(s.unit()))
              .append(",\"group\":").append(s.group())
              .append('}');
            if (i < specs.size() - 1) {
                sb.append(',');
            }
        }
        return sb.append(']').toString();
    }

    // ── Native helpers ─────────────────────────────────────────────────────────

    /**
     * Copy bytes from a native {@code FeagiByteBufferHandle} into a Java byte array.
     * Returns {@code new byte[0]} for zero-length frames (never null for valid handles).
     * The caller is responsible for freeing the buffer handle after this call.
     */
    private static native byte[] copyNativeBuffer(long bufHandle, int length);

    private void requireConnected(String method) {
        if (!connected || clientHandle.get() == NULL_HANDLE) {
            throw new IllegalStateException(
                    method + "() called but client is not connected. Call connect() first.");
        }
    }

    /**
     * Throws {@link FeagiSdkException} if {@code status} is not OK, including the
     * native status code in the exception so callers can inspect or log it.
     *
     * <p>TODO: expose {@code status} as a field on {@link FeagiSdkException} once
     * that class is extended with a {@code int nativeStatus()} accessor. This avoids
     * callers having to parse the status out of the message string for error recovery.
     */
    private static void checkStatus(int status, String operation) {
        if (status != FeagiNativeBindings.FeagiStatus.OK.code()) {
            throw new FeagiSdkException(
                    operation + " failed (status=" + status + "): " + nativeError());
        }
    }

    /**
     * Returns the most recent native error message for the current thread, or a
     * placeholder if none.
     *
     * <p><b>Thread-safety:</b> {@code feagi_last_error_message_alloc} is documented in
     * {@code feagi_java_ffi.h} as "Error reporting (per-thread)". Each thread has its own
     * error slot, so concurrent send/poll calls on other threads cannot overwrite this
     * thread's last error. Calling this without a lock is therefore safe.
     */
    private static String nativeError() {
        String msg = FeagiNativeBindings.feagiLastErrorMessage();
        return msg != null ? msg : "(no native error message)";
    }
}
