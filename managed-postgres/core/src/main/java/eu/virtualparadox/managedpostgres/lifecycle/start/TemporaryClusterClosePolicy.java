package eu.virtualparadox.managedpostgres.lifecycle.start;

final class TemporaryClusterClosePolicy {

    private TemporaryClusterClosePolicy() {}

    static boolean shouldDeleteOnClose(final StartPostgresWorkflow.Configuration configuration) {
        final boolean deleteOnClose;
        if (configuration.storage().temporaryStorage()) {
            deleteOnClose = configuration.cleanupPolicy().deleteTemporaryClusterOnClose();
        } else {
            deleteOnClose = false;
        }

        return deleteOnClose;
    }
}
