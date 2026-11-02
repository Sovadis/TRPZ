package ua.kpi.trpz.nantbuilder.repository.jdbc;

import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.TaskType;
import ua.kpi.trpz.nantbuilder.repository.TaskRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC-реалізація {@link TaskRepository} для таблиць {@code task} і {@code task_attribute}.
 * Метод {@link #save(BuildTask)} також повністю замінює рядки атрибутів завдання.
 */
public class JdbcTaskRepository implements TaskRepository {

    private static final String SELECT_COLUMNS = "SELECT id, target_id, task_type, position FROM task";

    private final Database db;

    /** Створює репозиторій, що працює з указаною базою даних. */
    public JdbcTaskRepository(Database db) {
        this.db = db;
    }

    @Override
    public Optional<BuildTask> findById(Long id) {
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE id = ?")) {
                statement.setLong(1, id);
                return readList(connection, statement).stream().findFirst();
            }
        });
    }

    @Override
    public List<BuildTask> findAll() {
        return db.inTransaction(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(SELECT_COLUMNS + " ORDER BY target_id, position")) {
                return readList(connection, statement);
            }
        });
    }

    @Override
    public List<BuildTask> findByTargetId(long targetId) {
        return db.inTransaction(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(SELECT_COLUMNS + " WHERE target_id = ? ORDER BY position")) {
                statement.setLong(1, targetId);
                return readList(connection, statement);
            }
        });
    }

    @Override
    public BuildTask save(BuildTask task) {
        return db.inTransaction(connection -> {
            if (task.getId() == null) {
                insertTask(connection, task);
            } else {
                updateTask(connection, task);
            }
            replaceAttributes(connection, task);
            return task;
        });
    }

    @Override
    public void deleteById(Long id) {
        // атрибути видаляються каскадно (ON DELETE CASCADE)
        db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM task WHERE id = ?")) {
                statement.setLong(1, id);
                return statement.executeUpdate();
            }
        });
    }

    private void insertTask(Connection connection, BuildTask task) throws SQLException {
        String sql = "INSERT INTO task (target_id, task_type, position) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, task.getTargetId());
            statement.setString(2, task.getType().getXmlName());
            statement.setInt(3, task.getPosition());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                task.setId(keys.getLong(1));
            }
        }
    }

    private void updateTask(Connection connection, BuildTask task) throws SQLException {
        String sql = "UPDATE task SET target_id = ?, task_type = ?, position = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, task.getTargetId());
            statement.setString(2, task.getType().getXmlName());
            statement.setInt(3, task.getPosition());
            statement.setLong(4, task.getId());
            statement.executeUpdate();
        }
    }

    private void replaceAttributes(Connection connection, BuildTask task) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM task_attribute WHERE task_id = ?")) {
            delete.setLong(1, task.getId());
            delete.executeUpdate();
        }
        String sql = "INSERT INTO task_attribute (task_id, name, value) VALUES (?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (Map.Entry<String, String> attribute : task.getAttributes().entrySet()) {
                insert.setLong(1, task.getId());
                insert.setString(2, attribute.getKey());
                insert.setString(3, attribute.getValue() == null ? "" : attribute.getValue());
                insert.executeUpdate();
            }
        }
    }

    private List<BuildTask> readList(Connection connection, PreparedStatement statement) throws SQLException {
        List<BuildTask> result = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                BuildTask task = new BuildTask();
                task.setId(rs.getLong("id"));
                task.setTargetId(rs.getLong("target_id"));
                task.setType(TaskType.fromXmlName(rs.getString("task_type")));
                task.setPosition(rs.getInt("position"));
                result.add(task);
            }
        }
        for (BuildTask task : result) {
            loadAttributes(connection, task);
        }
        return result;
    }

    private void loadAttributes(Connection connection, BuildTask task) throws SQLException {
        String sql = "SELECT name, value FROM task_attribute WHERE task_id = ? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, task.getId());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    task.setAttribute(rs.getString("name"), rs.getString("value"));
                }
            }
        }
    }
}
