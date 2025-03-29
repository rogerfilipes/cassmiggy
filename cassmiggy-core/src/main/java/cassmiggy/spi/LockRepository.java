package cassmiggy.spi;

import java.util.Optional;

import cassmiggy.internal.lock.LockInfo;

/** Stores and updates the migration lock. Acquire/release/heartbeat/takeover are owner-guarded (LWT). */
public interface LockRepository {

    boolean tryAcquire(String lockId, String owner);

    boolean release(String lockId, String owner);

    boolean updateHeartbeat(String lockId, String owner);

    Optional<LockInfo> getLockInfo(String lockId);

    boolean tryTakeover(String lockId, String newOwner, String expectedOwner);
}
