package ua.kpi.trpz.nantbuilder.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Підключення до бази даних SQLite.
 * Тримає одне спільне з'єднання і вміє виконувати роботу в транзакції.
 */
public class Database implements AutoCloseable {

    private static final String SCHEMA_RESOURCE = "/schema.sql";

    private final Connection connection;
    /** Глибина вкладеності викликів inTransaction: вкладені виклики приєднуються до зовнішньої транзакції. */
    private int transactionDepth;

    /**
     * Відкриває з'єднання за JDBC-адресою, наприклад {@code jdbc:sqlite:C:/data/nantbuilder.db}.
     */
    public Database(String jdbcUrl) {
        try {
            connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Не вдалося відкрити базу даних " + jdbcUrl, e);
        }
    }

    /** Відкриває (або створює) файл бази даних; батьківські каталоги створюються автоматично. */
    public static Database openFile(Path file) {
        Path absolute = file.toAbsolutePath();
        try {
            Files.createDirectories(absolute.getParent());
        } catch (IOException e) {
            throw new DataAccessException("Не вдалося створити каталог " + absolute.getParent(), e);
        }
        return new Database("jdbc:sqlite:" + absolute);
    }

    /** Повертає спільне з'єднання (із увімкненими зовнішніми ключами). */
    public Connection getConnection() {
        return connection;
    }

    /** Створює таблиці, виконуючи скрипт {@code /schema.sql} з classpath. */
    public void initSchema() {
        String script = readSchemaScript();
        try (Statement statement = connection.createStatement()) {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql.trim());
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Не вдалося створити схему бази даних", e);
        }
    }

    /**
     * Виконує роботу в транзакції: commit у разі успіху, rollback у разі помилки.
     * Вкладений виклик не відкриває нову транзакцію, а стає частиною зовнішньої.
     */
    public <T> T inTransaction(SqlFunction<T> work) {
        if (transactionDepth > 0) {
            transactionDepth++;
            try {
                return work.apply(connection);
            } catch (SQLException e) {
                throw new DataAccessException("Помилка роботи з базою даних: " + e.getMessage(), e);
            } finally {
                transactionDepth--;
            }
        }

        try {
            connection.setAutoCommit(false);
            transactionDepth = 1;
            T result = work.apply(connection);
            connection.commit();
            return result;
        } catch (SQLException e) {
            rollbackQuietly();
            throw new DataAccessException("Помилка роботи з базою даних: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            rollbackQuietly();
            throw e;
        } finally {
            transactionDepth = 0;
            restoreAutoCommit();
        }
    }

    /** Закриває з'єднання з базою даних. */
    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DataAccessException("Не вдалося закрити базу даних", e);
        }
    }

    private String readSchemaScript() {
        try (InputStream in = Database.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new DataAccessException("Не знайдено ресурс " + SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("Не вдалося прочитати " + SCHEMA_RESOURCE, e);
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // початкова помилка важливіша за помилку відкату
        }
    }

    private void restoreAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // викликається у finally, тому не маскуємо початковий виняток
        }
    }
}
