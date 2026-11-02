package ua.kpi.trpz.nantbuilder.repository.jdbc;

import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;
import ua.kpi.trpz.nantbuilder.repository.PropertyRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-реалізація {@link PropertyRepository} для таблиці {@code script_property}.
 */
public class JdbcPropertyRepository implements PropertyRepository {

    private static final String SELECT_COLUMNS =
            "SELECT id, script_id, name, value, read_only FROM script_property";

    private final Database db;

    /** Створює репозиторій, що працює з указаною базою даних. */
    public JdbcPropertyRepository(Database db) {
        this.db = db;
    }

    @Override
    public Optional<ScriptProperty> findById(Long id) {
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE id = ?")) {
                statement.setLong(1, id);
                List<ScriptProperty> found = readList(statement);
                return found.stream().findFirst();
            }
        });
    }

    @Override
    public List<ScriptProperty> findAll() {
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " ORDER BY id")) {
                return readList(statement);
            }
        });
    }

    @Override
    public List<ScriptProperty> findByScriptId(long scriptId) {
        return db.inTransaction(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(SELECT_COLUMNS + " WHERE script_id = ? ORDER BY id")) {
                statement.setLong(1, scriptId);
                return readList(statement);
            }
        });
    }

    @Override
    public ScriptProperty save(ScriptProperty property) {
        return property.getId() == null ? insert(property) : update(property);
    }

    @Override
    public void deleteById(Long id) {
        db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM script_property WHERE id = ?")) {
                statement.setLong(1, id);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public void deleteByScriptId(long scriptId) {
        db.inTransaction(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement("DELETE FROM script_property WHERE script_id = ?")) {
                statement.setLong(1, scriptId);
                return statement.executeUpdate();
            }
        });
    }

    private ScriptProperty insert(ScriptProperty property) {
        String sql = "INSERT INTO script_property (script_id, name, value, read_only) VALUES (?, ?, ?, ?)";
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, property.getScriptId());
                statement.setString(2, property.getName());
                statement.setString(3, property.getValue());
                statement.setInt(4, property.isReadOnly() ? 1 : 0);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    property.setId(keys.getLong(1));
                }
                return property;
            }
        });
    }

    private ScriptProperty update(ScriptProperty property) {
        String sql = "UPDATE script_property SET script_id = ?, name = ?, value = ?, read_only = ? WHERE id = ?";
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, property.getScriptId());
                statement.setString(2, property.getName());
                statement.setString(3, property.getValue());
                statement.setInt(4, property.isReadOnly() ? 1 : 0);
                statement.setLong(5, property.getId());
                statement.executeUpdate();
                return property;
            }
        });
    }

    private List<ScriptProperty> readList(PreparedStatement statement) throws SQLException {
        List<ScriptProperty> result = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                ScriptProperty property = new ScriptProperty(rs.getString("name"), rs.getString("value"));
                property.setId(rs.getLong("id"));
                property.setScriptId(rs.getLong("script_id"));
                property.setReadOnly(rs.getInt("read_only") != 0);
                result.add(property);
            }
        }
        return result;
    }
}
