package ua.kpi.trpz.nantbuilder.repository.jdbc;

import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.repository.TargetRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-реалізація {@link TargetRepository} для таблиць {@code target} і {@code target_dependency}.
 * Завдання цілей завантажує {@link JdbcTaskRepository}.
 */
public class JdbcTargetRepository implements TargetRepository {

    private static final String SELECT_COLUMNS =
            "SELECT id, script_id, name, description, position FROM target";

    private final Database db;

    /** Створює репозиторій, що працює з указаною базою даних. */
    public JdbcTargetRepository(Database db) {
        this.db = db;
    }

    @Override
    public Optional<Target> findById(Long id) {
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE id = ?")) {
                statement.setLong(1, id);
                return readList(statement).stream().findFirst();
            }
        });
    }

    @Override
    public List<Target> findAll() {
        return db.inTransaction(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(SELECT_COLUMNS + " ORDER BY script_id, position")) {
                return readList(statement);
            }
        });
    }

    @Override
    public List<Target> findByScriptId(long scriptId) {
        return db.inTransaction(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(SELECT_COLUMNS + " WHERE script_id = ? ORDER BY position")) {
                statement.setLong(1, scriptId);
                return readList(statement);
            }
        });
    }

    @Override
    public Target save(Target target) {
        return target.getId() == null ? insert(target) : update(target);
    }

    @Override
    public void deleteById(Long id) {
        db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM target WHERE id = ?")) {
                statement.setLong(1, id);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public void deleteByScriptId(long scriptId) {
        // завдання, атрибути та залежності видаляються каскадно (ON DELETE CASCADE)
        db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM target WHERE script_id = ?")) {
                statement.setLong(1, scriptId);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public void saveDependencies(long targetId, List<Long> dependsOnIds) {
        db.inTransaction(connection -> {
            try (PreparedStatement delete =
                         connection.prepareStatement("DELETE FROM target_dependency WHERE target_id = ?")) {
                delete.setLong(1, targetId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO target_dependency (target_id, depends_on_id) VALUES (?, ?)")) {
                for (Long dependsOnId : dependsOnIds) {
                    insert.setLong(1, targetId);
                    insert.setLong(2, dependsOnId);
                    insert.executeUpdate();
                }
            }
            return null;
        });
    }

    @Override
    public List<Long> findDependencyIds(long targetId) {
        // rowid зберігає порядок вставки, тож залежності повертаються в тому порядку, у якому їх записали
        String sql = "SELECT depends_on_id FROM target_dependency WHERE target_id = ? ORDER BY rowid";
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, targetId);
                List<Long> result = new ArrayList<>();
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getLong("depends_on_id"));
                    }
                }
                return result;
            }
        });
    }

    private Target insert(Target target) {
        String sql = "INSERT INTO target (script_id, name, description, position) VALUES (?, ?, ?, ?)";
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, target.getScriptId());
                statement.setString(2, target.getName());
                statement.setString(3, target.getDescription());
                statement.setInt(4, target.getPosition());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    target.setId(keys.getLong(1));
                }
                return target;
            }
        });
    }

    private Target update(Target target) {
        String sql = "UPDATE target SET script_id = ?, name = ?, description = ?, position = ? WHERE id = ?";
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, target.getScriptId());
                statement.setString(2, target.getName());
                statement.setString(3, target.getDescription());
                statement.setInt(4, target.getPosition());
                statement.setLong(5, target.getId());
                statement.executeUpdate();
                return target;
            }
        });
    }

    private List<Target> readList(PreparedStatement statement) throws SQLException {
        List<Target> result = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Target target = new Target();
                target.setId(rs.getLong("id"));
                target.setScriptId(rs.getLong("script_id"));
                target.setName(rs.getString("name"));
                target.setDescription(rs.getString("description"));
                target.setPosition(rs.getInt("position"));
                result.add(target);
            }
        }
        return result;
    }
}
