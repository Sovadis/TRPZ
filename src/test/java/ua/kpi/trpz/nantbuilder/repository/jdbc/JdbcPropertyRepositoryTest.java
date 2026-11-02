package ua.kpi.trpz.nantbuilder.repository.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.kpi.trpz.nantbuilder.db.DataAccessException;
import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPropertyRepositoryTest {

    @TempDir
    Path tempDir;

    private Database db;
    private JdbcBuildScriptRepository scripts;
    private JdbcPropertyRepository repository;
    private long scriptId;

    @BeforeEach
    void setUp() {
        db = Database.openFile(tempDir.resolve("test.db"));
        db.initSchema();
        scripts = new JdbcBuildScriptRepository(db);
        repository = new JdbcPropertyRepository(db);
        BuildScript script = new BuildScript();
        script.setName("Script");
        scriptId = scripts.save(script).getId();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void saveInsertsAndFindByScriptIdKeepsOrder() {
        repository.save(property("configuration", "Release", true));
        repository.save(property("build.dir", "build", false));

        List<ScriptProperty> found = repository.findByScriptId(scriptId);

        assertEquals(2, found.size());
        assertNotNull(found.get(0).getId());
        assertEquals("configuration", found.get(0).getName());
        assertEquals("Release", found.get(0).getValue());
        assertTrue(found.get(0).isReadOnly());
        assertEquals("build.dir", found.get(1).getName());
        assertFalse(found.get(1).isReadOnly());
    }

    @Test
    void saveUpdatesExistingProperty() {
        ScriptProperty saved = repository.save(property("configuration", "Debug", false));
        saved.setValue("Release");

        repository.save(saved);

        assertEquals("Release", repository.findById(saved.getId()).orElseThrow().getValue());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void duplicateNameInSameScriptIsRejected() {
        repository.save(property("configuration", "Debug", false));

        assertThrows(DataAccessException.class, () -> repository.save(property("configuration", "Release", false)));
    }

    @Test
    void deleteByIdAndDeleteByScriptId() {
        ScriptProperty first = repository.save(property("a", "1", false));
        repository.save(property("b", "2", false));
        repository.save(property("c", "3", false));

        repository.deleteById(first.getId());
        assertEquals(2, repository.findByScriptId(scriptId).size());

        repository.deleteByScriptId(scriptId);
        assertTrue(repository.findByScriptId(scriptId).isEmpty());
    }

    @Test
    void propertiesAreDeletedTogetherWithScript() {
        repository.save(property("a", "1", false));

        scripts.deleteById(scriptId);

        assertTrue(repository.findAll().isEmpty());
    }

    private ScriptProperty property(String name, String value, boolean readOnly) {
        ScriptProperty property = new ScriptProperty(name, value);
        property.setScriptId(scriptId);
        property.setReadOnly(readOnly);
        return property;
    }
}
