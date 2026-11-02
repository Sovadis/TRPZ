package ua.kpi.trpz.nantbuilder.repository.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.model.TaskType;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTargetRepositoryTest {

    @TempDir
    Path tempDir;

    private Database db;
    private JdbcTargetRepository repository;
    private JdbcTaskRepository tasks;
    private long scriptId;

    @BeforeEach
    void setUp() {
        db = Database.openFile(tempDir.resolve("test.db"));
        db.initSchema();
        repository = new JdbcTargetRepository(db);
        tasks = new JdbcTaskRepository(db);
        BuildScript script = new BuildScript();
        script.setName("Script");
        scriptId = new JdbcBuildScriptRepository(db).save(script).getId();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void findByScriptIdReturnsTargetsOrderedByPosition() {
        repository.save(target("test", 2));
        repository.save(target("clean", 0));
        Target compile = repository.save(target("compile", 1));
        compile.setDescription("компіляція");
        repository.save(compile);

        List<Target> found = repository.findByScriptId(scriptId);

        assertEquals(List.of("clean", "compile", "test"), found.stream().map(Target::getName).toList());
        assertNotNull(found.get(1).getId());
        assertEquals("компіляція", found.get(1).getDescription());
        assertTrue(found.get(1).getTasks().isEmpty(), "завдання цим методом не завантажуються");
    }

    @Test
    void saveDependenciesReplacesRowsAndKeepsOrder() {
        Target clean = repository.save(target("clean", 0));
        Target compile = repository.save(target("compile", 1));
        Target build = repository.save(target("build", 2));

        repository.saveDependencies(build.getId(), List.of(compile.getId(), clean.getId()));
        assertEquals(List.of(compile.getId(), clean.getId()), repository.findDependencyIds(build.getId()));

        repository.saveDependencies(build.getId(), List.of(clean.getId()));
        assertEquals(List.of(clean.getId()), repository.findDependencyIds(build.getId()));
    }

    @Test
    void deleteByScriptIdRemovesTargetsTasksAndDependencies() {
        Target clean = repository.save(target("clean", 0));
        Target build = repository.save(target("build", 1));
        repository.saveDependencies(build.getId(), List.of(clean.getId()));
        BuildTask task = BuildTask.withDefaults(TaskType.ECHO);
        task.setTargetId(build.getId());
        tasks.save(task);

        repository.deleteByScriptId(scriptId);

        assertTrue(repository.findByScriptId(scriptId).isEmpty());
        assertTrue(repository.findDependencyIds(build.getId()).isEmpty());
        assertTrue(tasks.findAll().isEmpty());
    }

    @Test
    void deleteByIdRemovesDependenciesOnThatTarget() {
        Target clean = repository.save(target("clean", 0));
        Target build = repository.save(target("build", 1));
        repository.saveDependencies(build.getId(), List.of(clean.getId()));

        repository.deleteById(clean.getId());

        assertTrue(repository.findById(clean.getId()).isEmpty());
        assertTrue(repository.findDependencyIds(build.getId()).isEmpty());
        assertEquals(1, repository.findAll().size());
    }

    private Target target(String name, int position) {
        Target target = new Target();
        target.setScriptId(scriptId);
        target.setName(name);
        target.setPosition(position);
        return target;
    }
}
