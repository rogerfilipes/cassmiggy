package cassmiggy.internal.lock;

import java.time.Instant;
import java.util.Objects;

/** Migration lock state. */
public final class LockInfo {
    private final String lockId;
    private final String instanceId;
    private final Instant acquiredAt;
    private final Instant heartbeat;

    public LockInfo(String lockId, String instanceId, Instant acquiredAt, Instant heartbeat) {
        Objects.requireNonNull(lockId, "lockId cannot be null");
        this.lockId = lockId;
        this.instanceId = instanceId;
        this.acquiredAt = acquiredAt;
        this.heartbeat = heartbeat;
    }

    public String getLockId() {
        return lockId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Instant getAcquiredAt() {
        return acquiredAt;
    }

    public Instant getHeartbeat() {
        return heartbeat;
    }
}
