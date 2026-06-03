package eu.virtualparadox.managedpostgres.runtime.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class RuntimeArchiveTestSupport {

    private RuntimeArchiveTestSupport() {}

    public static void createUsableRuntime(final Path runtimeDirectory) throws IOException {
        final Path binDirectory = runtimeDirectory.resolve("bin");
        Files.createDirectories(binDirectory);
        Files.writeString(binDirectory.resolve("pg_ctl"), "pg_ctl");
        Files.writeString(binDirectory.resolve("psql"), "psql");
        Files.writeString(binDirectory.resolve("postgres"), "postgres");
    }

    public static Path zipWithEntries(final Path archive, final EntrySpec... entries) throws IOException {
        final Path parent = archive.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (final EntrySpec entry : entries) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.name()));
                if (!entry.directory()) {
                    zipOutputStream.write(entry.content().getBytes(StandardCharsets.UTF_8));
                }
                zipOutputStream.closeEntry();
            }
        }

        return archive;
    }

    public static EntrySpec entry(final String name, final String content) {
        return new EntrySpec(name, content, false);
    }

    public static EntrySpec directory(final String name) {
        return new EntrySpec(name, "", true);
    }

    public static void assertExecutableIfSupported(final Path path) throws IOException {
        final PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (view != null) {
            final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            assertThat(permissions).contains(PosixFilePermission.OWNER_EXECUTE);
        }
    }

    public static void assertNotExecutableIfSupported(final Path path) throws IOException {
        final PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (view != null) {
            final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            assertThat(permissions).doesNotContain(PosixFilePermission.OWNER_EXECUTE);
        }
    }

    public static void assertRuntimeFiles(final Path runtimeDirectory) throws IOException {
        assertThat(Files.readString(runtimeDirectory.resolve("PG_VERSION"))).isEqualTo("16");
        assertThat(Files.readString(runtimeDirectory.resolve("bin").resolve("pg_ctl")))
                .isEqualTo("pg_ctl");
        assertThat(Files.readString(runtimeDirectory.resolve("bin").resolve("postgres")))
                .isEqualTo("postgres");
    }

    public static String checksumText(final Path archive) throws IOException {
        final MessageDigest digest = sha256();
        try (InputStream inputStream = Files.newInputStream(archive)) {
            inputStream.transferTo(new MessageDigestOutputStream(digest));
        }

        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }

        return digest;
    }

    public record EntrySpec(String name, String content, boolean directory) {}

    private static final class MessageDigestOutputStream extends OutputStream {

        private final MessageDigest digest;

        private MessageDigestOutputStream(final MessageDigest digest) {
            this.digest = digest;
        }

        @Override
        public void write(final int value) {
            digest.update((byte) value);
        }
    }
}
