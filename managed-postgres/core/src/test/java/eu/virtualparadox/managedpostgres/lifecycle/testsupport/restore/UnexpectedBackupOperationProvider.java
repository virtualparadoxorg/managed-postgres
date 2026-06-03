package eu.virtualparadox.managedpostgres.lifecycle.testsupport.restore;

import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperationProvider;

/**
 * Creates backup providers for tests that must not invoke backup behavior.
 */
public final class UnexpectedBackupOperationProvider {

    private UnexpectedBackupOperationProvider() {}

    public static PostgresBackupOperationProvider unexpectedBackupProvider() {
        return ignoredContext -> ignoredTarget -> {
            throw new AssertionError("backup is not expected in this test");
        };
    }
}
