package eu.virtualparadox.managedpostgres.filesystem;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;

/**
 * File permissions requested through the managed filesystem boundary.
 */
public final class ManagedFilePermissions {

    private final Set<PosixFilePermission> posixPermissions;

    private ManagedFilePermissions(final Set<PosixFilePermission> posixPermissions) {
        this.posixPermissions = Set.copyOf(Objects.requireNonNull(posixPermissions, "posixPermissions"));
    }

    /**
     * Returns a permission request that leaves platform defaults untouched.
     *
     * @return unmanaged/default permissions
     */
    public static ManagedFilePermissions defaults() {
        return new ManagedFilePermissions(Set.of());
    }

    /**
     * Returns owner-only read/write file permissions for secret-bearing files.
     *
     * @return owner-only read/write permissions
     */
    public static ManagedFilePermissions ownerOnlyReadWrite() {
        return new ManagedFilePermissions(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }

    /**
     * Reports whether explicit file permissions were requested.
     *
     * @return true when explicit file permissions are present
     */
    public boolean hasExplicitPermissions() {
        return !posixPermissions.isEmpty();
    }

    /**
     * Returns the posix permissions result.
     *
     * @return posix permissions result
     */
    public Set<PosixFilePermission> posixPermissions() {
        return posixPermissions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object other) {
        final boolean equal;
        if (this == other) {
            equal = true;
        } else if (other instanceof ManagedFilePermissions permissions) {
            equal = posixPermissions.equals(permissions.posixPermissions);
        } else {
            equal = false;
        }

        return equal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return posixPermissions.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "ManagedFilePermissions[explicitPermissions=" + hasExplicitPermissions() + "]";
    }
}
