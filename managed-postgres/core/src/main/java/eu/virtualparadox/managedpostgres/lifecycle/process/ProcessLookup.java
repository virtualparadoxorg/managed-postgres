package eu.virtualparadox.managedpostgres.lifecycle.process;

import java.util.Objects;
import java.util.Optional;

/**
 * Internal process lookup boundary for attach validation.
 */
@FunctionalInterface
public interface ProcessLookup {

    /**
     * Looks up a process by process identifier.
     *
     * @param pid process identifier
     * @return process handle when present
     */
    public Optional<ProcessHandle> find(long pid);

    /**
     * Creates a lookup backed by the current JVM process API.
     *
     * @return process lookup
     */
    public static ProcessLookup system() {
        return ProcessHandle::of;
    }

    /**
     * Creates a lookup that always returns the supplied handle.
     *
     * @param processHandle process handle to return
     * @return fixed process lookup
     */
    public static ProcessLookup fixed(final Optional<ProcessHandle> processHandle) {
        final Optional<ProcessHandle> checkedProcessHandle = Objects.requireNonNull(processHandle, "processHandle");

        return pid -> checkedProcessHandle;
    }
}
