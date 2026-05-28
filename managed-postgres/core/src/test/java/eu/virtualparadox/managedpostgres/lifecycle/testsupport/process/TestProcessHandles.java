package eu.virtualparadox.managedpostgres.lifecycle.testsupport.process;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Test fixtures for Java process handle identity checks.
 */
public final class TestProcessHandles {

    private TestProcessHandles() {
    }

    public static ProcessHandle processHandle(final String command, final boolean alive) {
        return new TestProcessHandle(command, alive);
    }

    private record TestProcessHandle(String command, boolean alive) implements ProcessHandle {

        @Override
        public long pid() {
            return 123L;
        }

        @Override
        public Optional<ProcessHandle> parent() {
            return Optional.empty();
        }

        @Override
        public Stream<ProcessHandle> children() {
            return Stream.empty();
        }

        @Override
        public Stream<ProcessHandle> descendants() {
            return Stream.empty();
        }

        @Override
        public Info info() {
            return new TestProcessInfo(command);
        }

        @Override
        public CompletableFuture<ProcessHandle> onExit() {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public boolean supportsNormalTermination() {
            return true;
        }

        @Override
        public boolean destroy() {
            return true;
        }

        @Override
        public boolean destroyForcibly() {
            return true;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public int compareTo(final ProcessHandle other) {
            return Long.compare(pid(), other.pid());
        }
    }

    private static final class TestProcessInfo implements ProcessHandle.Info {

        private final String command;

        private TestProcessInfo(final String command) {
            this.command = command;
        }

        @Override
        public Optional<String> command() {
            return Optional.of(command);
        }

        @Override
        public Optional<String> commandLine() {
            return Optional.of(command);
        }

        @Override
        public Optional<String[]> arguments() {
            return Optional.of(new String[0]);
        }

        @Override
        public Optional<Instant> startInstant() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> totalCpuDuration() {
            return Optional.empty();
        }

        @Override
        public Optional<String> user() {
            return Optional.empty();
        }
    }
}
