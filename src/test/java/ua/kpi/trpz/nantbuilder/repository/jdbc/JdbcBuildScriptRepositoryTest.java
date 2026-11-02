package ua.kpi.trpz.nantbuilder.repository.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.model.BuildScript;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcBuildScriptRepositoryTest {

    @TempDir
    Path tempDir;

    private Database db;
    private JdbcBuildScriptRepository repository;

    @BeforeEach
    void setUp() {
        db = Database.openFile(tempDir.resolve("test.db"));
        db.initSchema();
        repository = new JdbcBuildScriptRepository(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void saveInsertsNewScriptAndAssignsId() {
        BuildScript script = newScript("MyLibrary");
        script.setDescription("Бібліотека");
        script.setDefaultTarget("build");
        script.setBaseDir("src");

        repository.save(script);

        assertNotNull(script.getId());
        BuildScript found = repository.findById(script.getId()).orElseThrow();
        assertEquals("MyLibrary", found.getName());
        assertEquals("Бібліотека", found.getDescription());
        assertEquals("build", found.getDefaultTarget());
        assertEquals("src", found.getBaseDir());
        assertEquals(script.getCreatedAt(), found.getCreatedAt());
        assertEquals(script.getUpdatedAt(), found.getUpdatedAt());
    }

    @Test
    void saveUpdatesExistingScript() {
        BuildScript script = repository.save(newScript("Old"));
        LocalDateTime later = script.getUpdatedAt().plusHours(1);
        script.setName("New");
        script.setDescription("змінено");
        script.setUpdatedAt(later);

        repository.save(script);

        BuildScript found = repository.findById(script.getId()).orElseThrow();
        assertEquals("New", found.getName());
        assertEquals("змінено", found.getDescription());
        assertEquals(later, found.getUpdatedAt());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void findAllReturnsScriptsOrderedByName() {
        repository.save(newScript("Charlie"));
        repository.save(newScript("alpha"));
        repository.save(newScript("Bravo"));

        List<String> names = repository.findAll().stream().map(BuildScript::getName).toList();

        assertEquals(List.of("alpha", "Bravo", "Charlie"), names);
    }

    @Test
    void findByNameReturnsHeaderOrEmpty() {
        BuildScript saved = repository.save(newScript("DemoShop"));

        Optional<BuildScript> found = repository.findByName("DemoShop");

        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertTrue(found.get().getTargets().isEmpty());
        assertTrue(repository.findByName("Unknown").isEmpty());
    }

    @Test
    void deleteByIdRemovesScript() {
        BuildScript saved = repository.save(newScript("ToDelete"));

        repository.deleteById(saved.getId());

        assertTrue(repository.findById(saved.getId()).isEmpty());
        assertTrue(repository.findAll().isEmpty());
    }

    private static BuildScript newScript(String name) {
        BuildScript script = new BuildScript();
        script.setName(name);
        LocalDateTime time = LocalDateTime.of(2026, 11, 2, 10, 30, 0);
        script.setCreatedAt(time);
        script.setUpdatedAt(time);
        return script;
    }
}
