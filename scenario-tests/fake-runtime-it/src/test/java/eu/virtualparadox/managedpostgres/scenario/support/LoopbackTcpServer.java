package eu.virtualparadox.managedpostgres.scenario.support;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLServerSocketFactory;

public final class LoopbackTcpServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final ExecutorService executor;
    private final AtomicBoolean closed;
    private final AtomicReference<IOException> failure;

    private LoopbackTcpServer(final String host, final int port) throws IOException {
        serverSocket = serverSocket(host, port);
        executor = executorService();
        closed = new AtomicBoolean();
        failure = new AtomicReference<>();
        executor.execute(this::acceptUntilClosed);
    }

    public static LoopbackTcpServer open(final String host, final int port) throws IOException {
        return new LoopbackTcpServer(Objects.requireNonNull(host, "host"), port);
    }

    public void assertHealthy() throws IOException {
        final IOException exception = failure.get();
        if (exception != null) {
            throw exception;
        }
    }

    @Override
    public void close() throws IOException {
        closed.set(true);
        serverSocket.close();
        executor.shutdown();
    }

    private void acceptUntilClosed() {
        boolean accepting = true;
        while (accepting) {
            accepting = acceptOneConnection();
        }
    }

    private boolean acceptOneConnection() {
        boolean accepting = true;
        try (Socket socket = serverSocket.accept()) {
            accepting = socket.isConnected() && !closed.get();
        } catch (final SocketException exception) {
            accepting = handleSocketException(exception);
        } catch (final IOException exception) {
            failure.compareAndSet(null, exception);
            accepting = false;
        }

        return accepting;
    }

    private boolean handleSocketException(final SocketException exception) {
        final boolean accepting;
        if (closed.get()) {
            accepting = false;
        } else {
            failure.compareAndSet(null, exception);
            accepting = false;
        }

        return accepting;
    }

    private static ExecutorService executorService() {
        return Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "managed-postgres-loopback-test");
            thread.setDaemon(true);

            return thread;
        });
    }

    private static ServerSocket serverSocket(final String host, final int port) throws IOException {
        return SSLServerSocketFactory.getDefault().createServerSocket(port, 50, InetAddress.getByName(host));
    }
}
