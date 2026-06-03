package eu.virtualparadox.managedpostgres.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

public final class ManagedFilePermissionsTest {

    ManagedFilePermissionsTest() {}

    @Test
    void defaultsHaveNoExplicitPermissions() {
        final ManagedFilePermissions permissions = ManagedFilePermissions.defaults();

        assertThat(permissions.hasExplicitPermissions()).isFalse();
        assertThat(permissions.posixPermissions()).isEmpty();
        assertThat(permissions).hasToString("ManagedFilePermissions[explicitPermissions=false]");
    }

    @Test
    void ownerOnlyReadWriteRequestsOwnerReadAndWritePermissions() {
        final ManagedFilePermissions permissions = ManagedFilePermissions.ownerOnlyReadWrite();

        assertThat(permissions.hasExplicitPermissions()).isTrue();
        assertThat(permissions.posixPermissions())
                .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        assertThat(permissions).hasToString("ManagedFilePermissions[explicitPermissions=true]");
    }

    @Test
    void permissionsCompareByRequestedPermissionSet() {
        final ManagedFilePermissions first = ManagedFilePermissions.ownerOnlyReadWrite();
        final ManagedFilePermissions second = ManagedFilePermissions.ownerOnlyReadWrite();
        final ManagedFilePermissions defaults = ManagedFilePermissions.defaults();

        assertThat(first.equals(first)).isTrue();
        assertThat(first)
                .isEqualTo(second)
                .hasSameHashCodeAs(second)
                .isNotEqualTo(defaults)
                .isNotEqualTo(Set.of(PosixFilePermission.OWNER_READ))
                .isNotEqualTo(null);
    }
}
