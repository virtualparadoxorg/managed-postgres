package eu.virtualparadox.managedpostgres.runtime.packaging.build;

/**
 * Runtime packaging rollout phase for a target platform.
 */
public enum RolloutPhase {
    /** Target is supported by the initial GitHub-hosted rollout. */
    PHASE_ONE,
    /** Target requires the extended runner model or later rollout work. */
    PHASE_TWO
}
