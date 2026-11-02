package ua.kpi.trpz.nantbuilder.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Робота з базою даних, яку можна виконати в транзакції {@link Database#inTransaction(SqlFunction)}.
 *
 * @param <T> тип результату
 */
@FunctionalInterface
public interface SqlFunction<T> {

    /** Виконує роботу на заданому з'єднанні. */
    T apply(Connection connection) throws SQLException;
}
