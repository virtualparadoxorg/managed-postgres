package eu.virtualparadox.managedpostgres.lifecycle.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JdbcProbeProxyFactory {

    private JdbcProbeProxyFactory() {
    }

    public static Connection connection(final JdbcProbeScenario scenario) {
        return proxy(Connection.class, (proxy, method, arguments) -> {
            final Object result;
            if ("createStatement".equals(method.getName())) {
                result = statement(scenario);
            } else {
                result = defaultValue(method);
            }

            return result;
        });
    }

    private static Statement statement(final JdbcProbeScenario scenario) {
        return proxy(Statement.class, (proxy, method, arguments) -> {
            final Object result;
            if ("executeQuery".equals(method.getName())) {
                result = resultSet(scenario, String.valueOf(arguments[0]));
            } else {
                result = defaultValue(method);
            }

            return result;
        });
    }

    private static ResultSet resultSet(final JdbcProbeScenario scenario, final String sql) throws SQLException {
        final ResultSet result;
        if ("SHOW data_directory".equals(sql)) {
            result = resultSet(scenario.dataDirectory(), scenario.dataDirectoryHasRow());
        } else if ("SHOW server_version".equals(sql)) {
            result = resultSet(scenario.serverVersion(), true);
        } else {
            throw new SQLException("unexpected SQL: " + sql);
        }

        return result;
    }

    private static ResultSet resultSet(final String value, final boolean hasRow) {
        final AtomicBoolean firstRead = new AtomicBoolean(true);
        return proxy(ResultSet.class, (proxy, method, arguments) -> {
            final Object result;
            if ("next".equals(method.getName())) {
                result = firstRead.compareAndSet(true, false) && hasRow;
            } else if ("getString".equals(method.getName())) {
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
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                invocationHandler));
    }
}
