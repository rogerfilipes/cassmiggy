package cassmiggy.spi;

import java.time.Duration;

/** Cluster-wide lock so only one migration runs at a time. */
public interface MigrationLock {

    void acquire(Duration timeout);

    void release();

    void startHeartbeat();

    void stopHeartbeat();

    /** Local, non-blocking check; false means the lock was lost or never acquired. */
    boolean isHeld();

    boolean isStale();

    /** Steals a stale lock; requires opt-in, throws IllegalStateException if disabled. */
    boolean forceTakeover();
}
