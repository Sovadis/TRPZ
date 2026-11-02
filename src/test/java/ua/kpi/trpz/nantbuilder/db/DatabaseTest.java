package ua.kpi.trpz.nantbuilder.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcBuildScriptRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    @TempDir
    Path tempDir;

    private Database db;
    private JdbcBuildScriptRepository scripts;

    @BeforeEach
    void setUp() {
        db = Database.openFile(tempDir.resolve("nested/dir/test.db"));
        db.initSchema();
        scripts = new JdbcBuildScriptRepository(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void openFileCreatesParentDirectoriesAndSchemaIsIdempotent() {
        assertTrue(Files.isRegularFile(tempDir.resolve("nested/dir/test.db")));
        db.initSchema();
        assertTrue(scripts.findAll().isEmpty());
    }

    @Test
    void nestedTransactionsCommitTogether() throws SQLException {
        db.inTransaction(connection -> {
            scripts.save(script("A"));
            scripts.save(script("B"));
            return null;
        });

        assertEquals(2, scripts.findAll().size());
        assertTrue(db.getConnection().getAutoCommit());
    }

    @Test
    void failureInOuterTransactionRollsBackNestedWork() throws SQLException {
        assertThrows(IllegalStateException.class, () -> db.inTransaction(connection -> {
            scripts.save(script("A"));
            throw new IllegalStateException("помилка після вкладеного збереження");
        }));

        assertTrue(scripts.findAll().isEmpty());
        assertTrue(db.getConnection().getAutoCommit());
    }

    @Test
    void sqlErrorIsWrappedInDataAccessException() {
        scripts.save(script("A"));

        assertThrows(DataAccessException.class, () -> scripts.save(script("A")));
    }

    private static BuildScript script(String name) {
        BuildScript script = new BuildScript();
        script.setName(name);
        return script;
    }
}
