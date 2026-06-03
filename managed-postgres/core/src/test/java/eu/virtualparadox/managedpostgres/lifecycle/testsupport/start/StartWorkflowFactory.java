package eu.virtualparadox.managedpostgres.lifecycle.testsupport.start;

import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.runtime.ExistingRuntimeResolver;
import java.time.Duration;

public final class StartWorkflowFactory {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(10);

    public StartWorkflowFactory() {}

    public StartPostgresWorkflow workflow() {
        return new StartPostgresWorkflow(
                new ExistingRuntimeResolver(),
                new FileSystemOperationJournal(),
                new PostgresLockService(),
                STARTUP_TIMEOUT);
    }
}
