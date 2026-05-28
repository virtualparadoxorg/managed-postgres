package eu.virtualparadox.managedpostgres.lifecycle.port;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.StringUtils;

/**
 * Selected loopback port allocation.
 */
public final class AllocatedPort implements AutoCloseable {

    private final String host;
    private final int port;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Creates a AllocatedPort instance.
     *
     * @param host host value
     * @param port port value
     */
    public AllocatedPort(final String host, final int port) {
        if (StringUtils.isBlank(host)) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        this.host = host;
        this.port = port;
    }

    /**
     * Returns the loopback host for this allocation.
     *
     * @return loopback host
     */
    public String host() {
        return host;
    }

    /**
     * Returns the reserved TCP port.
     *
     * @return reserved TCP port
     */
    public int port() {
        return port;
    }

    /**
     * Reports whether this allocation is still reserved.
     *
     * @return true when the reservation is open
     */
    public boolean open() {
        return !closed.get();
    }

    /**
     * Releases the reserved port.
     */
    @Override
    public void close() {
        closed.compareAndSet(false, true);
    }
}
