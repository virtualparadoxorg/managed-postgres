package eu.virtualparadox.managedpostgres.runtime.testsupport;

import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.EntrySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

public final class TarGzipArchiveTestSupport {

    private TarGzipArchiveTestSupport() {
    }

    public static Path tarGzipWithEntries(final Path archive, final EntrySpec... entries) throws IOException {
        final Path parent = archive.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream outputStream = Files.newOutputStream(archive);
                GzipCompressorOutputStream gzipOutputStream = new GzipCompressorOutputStream(outputStream);
                TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(gzipOutputStream)) {
            tarOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_ERROR);
            for (final EntrySpec entry : entries) {
                writeTarEntry(tarOutputStream, entry);
            }
        }

        return archive;
    }

    public static Path tarGzipWithSymbolicLink(
            final Path archive,
            final String name,
            final String linkTarget) throws IOException {
        return tarGzipWithLink(archive, name, linkTarget, TarConstants.LF_SYMLINK);
    }

    public static Path tarGzipWithHardLink(
            final Path archive,
            final String name,
            final String linkTarget) throws IOException {
        return tarGzipWithLink(archive, name, linkTarget, TarConstants.LF_LINK);
    }

    private static Path tarGzipWithLink(
            final Path archive,
            final String name,
            final String linkTarget,
            final byte linkFlag) throws IOException {
        final Path parent = archive.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream outputStream = Files.newOutputStream(archive);
                GzipCompressorOutputStream gzipOutputStream = new GzipCompressorOutputStream(outputStream);
                TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(gzipOutputStream)) {
            final TarArchiveEntry entry = new TarArchiveEntry(name, linkFlag);
            entry.setLinkName(linkTarget);
            tarOutputStream.putArchiveEntry(entry);
            tarOutputStream.closeArchiveEntry();
        }

        return archive;
    }

    private static void writeTarEntry(
            final TarArchiveOutputStream tarOutputStream,
            final EntrySpec entry) throws IOException {
        final TarArchiveEntry tarEntry = new TarArchiveEntry(entry.name());
        if (entry.directory()) {
            tarOutputStream.putArchiveEntry(tarEntry);
        } else {
            writeTarFileEntry(tarOutputStream, tarEntry, entry);
        }
        tarOutputStream.closeArchiveEntry();
    }

    private static void writeTarFileEntry(
            final TarArchiveOutputStream tarOutputStream,
            final TarArchiveEntry tarEntry,
            final EntrySpec entry) throws IOException {
        final byte[] content = entry.content().getBytes(StandardCharsets.UTF_8);
        tarEntry.setSize(content.length);
        tarOutputStream.putArchiveEntry(tarEntry);
        tarOutputStream.write(content);
    }
}
