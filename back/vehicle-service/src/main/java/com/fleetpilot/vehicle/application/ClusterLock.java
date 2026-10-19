package com.fleetpilot.vehicle.application;

/** Mutual exclusion between instances of the service, for jobs that must run once (daily sweep, imports). */
public interface ClusterLock {

    /**
     * Tries to take the named lock for the duration of the current transaction.
     *
     * @return false when another instance holds it
     */
    boolean tryLock(String name);
}
