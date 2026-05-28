package eu.virtualparadox.managedpostgres.security;

import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperation;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFilePermissions;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

/**
 * Stores PostgreSQL credentials in an atomically written file.
 */
public final class FileCredentialStore implements CredentialStore {

    private static final ManagedFilePermissions CREDENTIAL_FILE_PERMISSIONS =
            ManagedFilePermissions.ownerOnlyReadWrite();

    private final Path path;
    private final ManagedFileSystem fileSystem;

    /**
     * Creates a credential store backed by a managed file system.
     *
     * @param path credentials file path
     * @param fileSystem managed file system adapter
     */
    public FileCredentialStore(final Path path, final ManagedFileSystem fileSystem) {
        this.path = Objects.requireNonNull(path, "path");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
    }

    /**
     * Writes credentials atomically.
     *
     * @param credentials credentials to write
     * @throws IOException when the backing store cannot be written
     */
    @Override
    public void write(final Credentials credentials) throws IOException {
        Objects.requireNonNull(credentials, "credentials");

        try (FileSystemOperation operation = fileSystem.beginOperation("write-credentials", operationRoot(path))) {
            operation.writeUtf8Atomically(path, content(credentials), CREDENTIAL_FILE_PERMISSIONS);
            operation.commit();
        }
    }

    /**
     * Reads credentials from the backing file when present.
     *
     * @return persisted credentials, or empty when the file does not exist
     * @throws IOException when the backing store cannot be read
     */
    public Optional<Credentials> read() throws IOException {
        final Optional<Credentials> credentials;
        if (Files.isRegularFile(path)) {
            credentials = Optional.of(readExisting());
        } else {
            credentials = Optional.empty();
        }

        return credentials;
    }

    private Credentials readExisting() throws IOException {
        final Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        return new Credentials(
                required(properties, "username"),
                Secret.of(required(properties, "password")),
                Boolean.parseBoolean(required(properties, "persistent")),
                Boolean.parseBoolean(required(properties, "localTrustOnly")));
    }

    private static String required(final Properties properties, final String key) throws IOException {
        final String value = properties.getProperty(key);
        if (StringUtils.isBlank(value)) {
            throw new IOException("missing credential property: " + key);
        }

        return value;
    }

    private static String content(final Credentials credentials) {
        return "username=%s%npassword=%s%npersistent=%s%nlocalTrustOnly=%s%n".formatted(
                credentials.username(),
                credentials.password().reveal(),
                credentials.persistent(),
                credentials.localTrustOnly());
    }

    private static Path operationRoot(final Path target) {
        final Path normalizedTarget = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        final Path parent = normalizedTarget.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("target must have a parent directory");
        }

        return parent;
    }
}
