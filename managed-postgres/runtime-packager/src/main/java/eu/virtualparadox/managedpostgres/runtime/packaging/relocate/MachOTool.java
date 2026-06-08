package eu.virtualparadox.managedpostgres.runtime.packaging.relocate;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Inspects and rewrites Mach-O load commands using the macOS binary toolchain.
 *
 * <p>Implementations wrap {@code otool}, {@code install_name_tool} and {@code codesign}. The
 * interface exists so relocation orchestration can be driven and asserted without a real macOS
 * toolchain in unit tests.
 */
public interface MachOTool {

    /**
     * Returns the install id recorded in the Mach-O file ({@code otool -D}).
     *
     * @param file Mach-O file to inspect
     * @return the install id, or empty when the file declares none (executables)
     */
    Optional<String> installId(Path file);

    /**
     * Returns the recorded dependency load paths of the Mach-O file ({@code otool -L}).
     *
     * <p>For a dynamic library the returned list also contains the library's own install id as its
     * first entry, mirroring {@code otool -L} output.
     *
     * @param file Mach-O file to inspect
     * @return recorded dependency load paths
     */
    List<String> dependencies(Path file);

    /**
     * Rewrites the install id of a Mach-O library ({@code install_name_tool -id}).
     *
     * @param file Mach-O library to rewrite
     * @param installId new install id
     */
    void setInstallId(Path file, String installId);

    /**
     * Rewrites a dependency load path ({@code install_name_tool -change}).
     *
     * @param file Mach-O file to rewrite
     * @param oldReference current dependency load path
     * @param newReference replacement dependency load path
     */
    void changeDependency(Path file, String oldReference, String newReference);

    /**
     * Re-signs a Mach-O file with an ad-hoc signature ({@code codesign --force --sign -}).
     *
     * <p>Editing load commands invalidates the existing signature, which Apple Silicon rejects at
     * load time, so every rewritten file must be re-signed.
     *
     * @param file Mach-O file to re-sign
     */
    void signAdHoc(Path file);

    /**
     * Runs {@code <executable> --version} as a load-time smoke test.
     *
     * <p>The call must fail loudly when the dynamic loader cannot resolve a dependency, surfacing
     * the underlying {@code dyld} message.
     *
     * @param executable runtime executable to launch
     */
    void runVersionCheck(Path executable);
}
