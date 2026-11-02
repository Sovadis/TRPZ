package ua.kpi.trpz.nantbuilder.db;

/**
 * Неперевірюваний виняток рівня доступу до даних (обгортка над {@link java.sql.SQLException}).
 */
public class DataAccessException extends RuntimeException {

    /** Створює виняток із повідомленням. */
    public DataAccessException(String message) {
        super(message);
    }

    /** Створює виняток із повідомленням і причиною. */
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
