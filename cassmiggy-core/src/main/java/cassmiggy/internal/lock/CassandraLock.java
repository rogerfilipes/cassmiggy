package cassmiggy.internal.lock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.Config;
import cassmiggy.exception.LockAcquisitionException;
import cassmiggy.spi.LockRepository;
import cassmiggy.spi.MigrationLock;

/**
 * Migration lock backed by Cassandra LWT. One holder at a time, via a single row tagged with this
 * instance's {@code instanceId}; every change is an {@code IF}-guarded (atomic) write.
 *
 * <ul>
 *   <li>Acquire - INSERT IF NOT EXISTS; retries until the timeout.</li>
 *   <li>Stale takeover - if the holder's heartbeat is older than staleLockThreshold (and takeover is
 *       enabled), steal it via UPDATE IF instance_id = old owner.</li>
 *   <li>Heartbeat - a daemon thread bumps the heartbeat so the lock doesn't look stale; if that
 *       write is rejected, the lock was taken over and is marked lost.</li>
 *   <li>Release - DELETE IF instance_id = me; only the owner can remove it.</li>
 * </ul>
 *
 * <p>Side note: a takeover only happens after the original holder's heartbeat went stale (it stalled
 * or died). If that holder comes back, its next heartbeat write is rejected - the row now names
 * someone else - so it marks the lock lost and aborts instead of carrying on.
 */
public class CassandraLock implements MigrationLock {

    private static final Logger log = LoggerFactory.getLogger(CassandraLock.class);
    private static final String LOCK_ID = "migration_lock";

    private final Config config;
    private final LockRepository lockRepository;
    private final String instanceId;

    private final AtomicBoolean lockHeld = new AtomicBoolean(false);
    private ScheduledExecutorService heartbeatExecutor;
    private ScheduledFuture<?> heartbeatFuture;

    public CassandraLock(Config config, LockRepository lockRepository) {
        this.config = config;
        this.lockRepository = lockRepository;
        this.instanceId = config.getInstanceId();
    }

    @Override
    public void acquire(Duration timeout) {
        log.debug("Attempting to acquire migration lock (timeout: {})", timeout);

        Instant deadline = Instant.now().plus(timeout);
        Duration retryInterval = Duration.ofSeconds(1);

        while (Instant.now().isBefore(deadline)) {
            if (lockRepository.tryAcquire(LOCK_ID, instanceId)) {
                lockHeld.set(true);
                log.debug("Migration lock acquired by instance {}", instanceId);
                return;
            }

            if (config.isAllowStaleLockTakeover() && tryAutoTakeover()) {
                return;
            }

            String currentOwner = getCurrentOwner();
            log.trace("Lock held by {}. Retrying in {}...", currentOwner, retryInterval);

            try {
                Thread.sleep(retryInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LockAcquisitionException(timeout, currentOwner, e);
            }
        }

        String currentOwner = getCurrentOwner();
        throw new LockAcquisitionException(timeout, currentOwner);
    }

    private boolean tryAutoTakeover() {
        Optional<LockInfo> lockInfoOpt = lockRepository.getLockInfo(LOCK_ID);
        if (lockInfoOpt.isEmpty()) {
            return false;
        }

        LockInfo lockInfo = lockInfoOpt.get();
        if (!isLockInfoStale(lockInfo)) {
            return false;
        }

        log.info("Detected stale lock held by {}. Attempting automatic takeover...", lockInfo.getInstanceId());

        if (lockRepository.tryTakeover(LOCK_ID, instanceId, lockInfo.getInstanceId())) {
            lockHeld.set(true);
            log.info("Automatic takeover successful - lock acquired from stale instance {}", lockInfo.getInstanceId());
            return true;
        }

        log.debug("Automatic takeover failed - another instance may have taken over first");
        return false;
    }

    @Override
    public void release() {
        if (!lockHeld.get()) {
            log.debug("Lock not held by this instance, nothing to release");
            return;
        }

        stopHeartbeat();

        log.debug("Releasing migration lock for instance {}", instanceId);
        boolean released = lockRepository.release(LOCK_ID, instanceId);

        if (released) {
            lockHeld.set(false);
            log.debug("Migration lock released");
        } else {
            log.warn("Lock release failed - lock may have been taken over");
            lockHeld.set(false);
        }
    }

    @Override
    public void startHeartbeat() {
        if (!lockHeld.get()) {
            log.warn("Cannot start heartbeat - lock not held");
            return;
        }

        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            log.debug("Heartbeat already running");
            return;
        }

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "migration-lock-heartbeat");
            t.setDaemon(true);
            return t;
        });

        long intervalMs = config.getLockHeartbeatInterval().toMillis();
        heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(
                this::updateHeartbeat, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        log.debug("Lock heartbeat started (interval: {})", config.getLockHeartbeatInterval());
    }

    @Override
    public void stopHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }

        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            heartbeatExecutor = null;
            log.debug("Lock heartbeat stopped");
        }
    }

    @Override
    public boolean isHeld() {
        return lockHeld.get();
    }

    @Override
    public boolean isStale() {
        Optional<LockInfo> lockInfo = lockRepository.getLockInfo(LOCK_ID);

        if (lockInfo.isEmpty()) {
            log.debug("No lock exists - not stale");
            return false;
        }

        LockInfo info = lockInfo.get();
        if (info.getHeartbeat() == null) {
            log.debug("Lock has no heartbeat - considered stale");
            return true;
        }

        Duration age = Duration.between(info.getHeartbeat(), Instant.now());
        boolean stale = age.compareTo(config.getStaleLockThreshold()) > 0;

        if (stale) {
            log.warn(
                    "Stale lock detected. Instance: {}, Last heartbeat: {} ({} ago)",
                    info.getInstanceId(), info.getHeartbeat(), age);
        }

        return stale;
    }

    @Override
    public boolean forceTakeover() {
        if (!config.isAllowStaleLockTakeover()) {
            throw new IllegalStateException(
                    "Stale lock takeover is not enabled. Set allowStaleLockTakeover=true in configuration.");
        }

        Optional<LockInfo> lockInfoOpt = lockRepository.getLockInfo(LOCK_ID);
        if (lockInfoOpt.isEmpty()) {
            log.debug("No lock exists to take over");
            return false;
        }

        LockInfo lockInfo = lockInfoOpt.get();
        if (!isLockInfoStale(lockInfo)) {
            log.warn("Cannot force takeover - lock is not stale");
            return false;
        }

        String currentInstanceId = lockInfo.getInstanceId();
        log.warn("Forcing takeover of stale lock from {}", currentInstanceId);

        // Atomic takeover using LWT - only succeeds if instance_id hasn't changed
        if (lockRepository.tryTakeover(LOCK_ID, instanceId, currentInstanceId)) {
            lockHeld.set(true);
            log.info("Successfully took over stale lock from {}", currentInstanceId);
            return true;
        }

        log.warn("Failed to takeover lock - another instance may have acquired it first");
        return false;
    }

    private boolean isLockInfoStale(LockInfo lockInfo) {
        if (lockInfo.getHeartbeat() == null) {
            return true;
        }
        Duration age = Duration.between(lockInfo.getHeartbeat(), Instant.now());
        return age.compareTo(config.getStaleLockThreshold()) > 0;
    }

    private void updateHeartbeat() {
        int maxAttempts = config.getHeartbeatRetries() + 1;
        long backoffMs = config.getHeartbeatRetryInitialDelay().toMillis();
        final long maxBackoffMs = 5000; // Cap at 5 seconds

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                boolean updated = lockRepository.updateHeartbeat(LOCK_ID, instanceId);
                if (updated) {
                    if (attempt > 1) {
                        log.debug("Lock heartbeat updated successfully after {} attempt(s)", attempt);
                    } else {
                        log.debug("Lock heartbeat updated");
                    }
                    return;
                } else {
                    // Definitive failure: lock was taken over by another instance.
                    // LWT condition failed - no retry will help.
                    log.error("Heartbeat update failed - lock was taken over by another instance");
                    lockHeld.set(false);
                    return;
                }
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    log.warn(
                            "Heartbeat update failed (attempt {}/{}): {}. Retrying in {}ms...",
                            attempt, maxAttempts, e.getMessage(), backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Heartbeat retry interrupted. Marking lock as lost.");
                        lockHeld.set(false);
                        return;
                    }
                    backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
                } else {
                    log.error(
                            "Failed to update lock heartbeat after {} attempts: {}. Marking lock as lost.",
                            maxAttempts, e.getMessage());
                    lockHeld.set(false);
                }
            }
        }
    }

    private String getCurrentOwner() {
        return lockRepository.getLockInfo(LOCK_ID).map(LockInfo::getInstanceId).orElse(null);
    }
}
