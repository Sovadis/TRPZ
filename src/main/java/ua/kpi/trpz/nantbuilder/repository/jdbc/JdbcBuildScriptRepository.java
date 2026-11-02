package ua.kpi.trpz.nantbuilder.repository.jdbc;

import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.repository.BuildScriptRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-реалізація {@link BuildScriptRepository} для таблиці {@code build_script}.
 */
public class JdbcBuildScriptRepository implements BuildScriptRepository {

    private static final String SELECT_COLUMNS =
            "SELECT id, name, description, default_target, base_dir, created_at, updated_at FROM build_script";

    private final Database db;

    /** Створює репозиторій, що працює з указаною базою даних. */
    public JdbcBuildScriptRepository(Database db) {
        this.db = db;
    }

    @Override
    public Optional<BuildScript> findById(Long id) {
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE id = ?")) {
                statement.setLong(1, id);
                return readOne(statement);
            }
        });
    }

    @Override
    public Optional<BuildScript> findByName(String name) {
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE name = ?")) {
                statement.setString(1, name);
                return readOne(statement);
            }
        });
    }

    @Override
    public List<BuildScript> findAll() {
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " ORDER BY name COLLATE NOCASE");
                 ResultSet rs = statement.executeQuery()) {
                List<BuildScript> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        });
    }

    @Override
    public BuildScript save(BuildScript script) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (script.getCreatedAt() == null) {
            script.setCreatedAt(now);
        }
        if (script.getUpdatedAt() == null) {
            script.setUpdatedAt(now);
        }
        return script.getId() == null ? insert(script) : update(script);
    }

    @Override
    public void deleteById(Long id) {
        db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM build_script WHERE id = ?")) {
                statement.setLong(1, id);
                return statement.executeUpdate();
            }
        });
    }

    private BuildScript insert(BuildScript script) {
        String sql = "INSERT INTO build_script (name, description, default_target, base_dir, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, script.getName());
                statement.setString(2, script.getDescription());
                statement.setString(3, script.getDefaultTarget());
                statement.setString(4, script.getBaseDir());
                statement.setString(5, formatDate(script.getCreatedAt()));
                statement.setString(6, formatDate(script.getUpdatedAt()));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    script.setId(keys.getLong(1));
                }
                return script;
            }
        });
    }

    private BuildScript update(BuildScript script) {
        String sql = "UPDATE build_script SET name = ?, description = ?, default_target = ?, base_dir = ?, "
                + "updated_at = ? WHERE id = ?";
        return db.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, script.getName());
                statement.setString(2, script.getDescription());
                statement.setString(3, script.getDefaultTarget());
                statement.setString(4, script.getBaseDir());
                statement.setString(5, formatDate(script.getUpdatedAt()));
                statement.setLong(6, script.getId());
                statement.executeUpdate();
                return script;
            }
        });
    }

    private Optional<BuildScript> readOne(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
        }
    }

    private BuildScript mapRow(ResultSet rs) throws SQLException {
        BuildScript script = new BuildScript();
        script.setId(rs.getLong("id"));
        script.setName(rs.getString("name"));
        script.setDescription(rs.getString("description"));
        script.setDefaultTarget(rs.getString("default_target"));
        script.setBaseDir(rs.getString("base_dir"));
        script.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
        script.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at")));
        return script;
    }

    /** Дати зберігаються як текст ISO-8601, наприклад 2026-11-02T14:05:00. */
    private static String formatDate(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
