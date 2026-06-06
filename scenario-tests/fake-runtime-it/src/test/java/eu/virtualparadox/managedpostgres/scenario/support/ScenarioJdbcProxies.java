package eu.virtualparadox.managedpostgres.scenario.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class ScenarioJdbcProxies {

    private static final String CREATE_STATEMENT = "createStatement";
    private static final String EXECUTE_QUERY = "executeQuery";
    private static final String NEXT = "next";
    private static final String GET_STRING = "getString";
    private static final String SHOW_DATA_DIRECTORY = "SHOW data_directory";
    private static final String SHOW_SERVER_VERSION = "SHOW server_version";

    private ScenarioJdbcProxies() {}

    static Connection connection(final Path dataDirectory, final String serverVersion) {
        return proxy(Connection.class, (proxy, method, arguments) -> {
            final Object result;
            if (CREATE_STATEMENT.equals(method.getName())) {
                result = statement(dataDirectory, serverVersion);
            } else {
                result = defaultValue(method);
            }

            return result;
        });
    }

    private static Statement statement(final Path dataDirectory, final String serverVersion) {
        return proxy(Statement.class, (proxy, method, arguments) -> {
            final Object result;
            if (EXECUTE_QUERY.equals(method.getName())) {
                result = resultSet(dataDirectory, serverVersion, String.valueOf(arguments[0]));
            } else {
                result = defaultValue(method);
            }

            return result;
        });
    }

    private static ResultSet resultSet(final Path dataDirectory, final String serverVersion, final String sql)
            throws SQLException {
        final ResultSet result;
        if (SHOW_DATA_DIRECTORY.equals(sql)) {
            result = resultSet(dataDirectory.toString());
        } else if (SHOW_SERVER_VERSION.equals(sql)) {
            result = resultSet(serverVersion);
        } else {
            throw new SQLException("unexpected SQL: " + sql);
        }

        return result;
    }

    private static ResultSet resultSet(final String value) {
        final AtomicBoolean firstRead = new AtomicBoolean(true);

        return proxy(ResultSet.class, (proxy, method, arguments) -> {
            final Object result;
            if (NEXT.equals(method.getName())) {
                result = firstRead.compareAndSet(true, false);
            } else if (GET_STRING.equals(method.getName())) {
                result = value;
            } else {
                result = defaultValue(method);
            }

            return result;
        });
    }

    private static Object defaultValue(final Method method) {
        final Object result;
        if (method.getReturnType() == Void.TYPE) {
            result = new Object();
        } else if (method.getReturnType() == boolean.class) {
            result = false;
        } else if (method.getReturnType() == int.class) {
            result = 0;
        } else if (method.getReturnType() == String.class) {
            result = "";
        } else {
            result = new Object();
        }

        return result;
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler invocationHandler) {
        final Class<T> checkedType = Objects.requireNonNull(type, "type");

        return checkedType.cast(Proxy.newProxyInstance(
                checkedType.getClassLoader(),
                new Class<?>[] {checkedType},
                Objects.requireNonNull(invocationHandler, "invocationHandler")));
    }
}
