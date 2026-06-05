package eu.virtualparadox.managedpostgres.runtime.download.progress;

/**
 * Internal byte-transfer progress callback used by the runtime artifact fetcher.
 *
 * <p>The fetcher stays dumb: it reports raw {@code (done, total)} byte counts. Throttling and the
 * mapping to public progress events happen one layer up, in the downloaded-runtime cache publisher.
 */
@FunctionalInterface
public interface BytesTransferredListener {

    /**
     * A no-op listener that ignores all byte-transfer progress.
     */
    BytesTransferredListener NONE = (done, total) -> {};

    /**
     * Reports byte-transfer progress.
     *
     * @param done bytes transferred so far
     * @param total total bytes expected, or {@code 0} when unknown
     */
    void onBytesTransferred(long done, long total);
}
