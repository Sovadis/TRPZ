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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTaskRepositoryTest {

    @TempDir
    Path tempDir;

    private Database db;
    private JdbcTaskRepository repository;
    private long targetId;

    @BeforeEach
    void setUp() {
        db = Database.openFile(tempDir.resolve("test.db"));
        db.initSchema();
        repository = new JdbcTaskRepository(db);
        BuildScript script = new BuildScript();
        script.setName("Script");
        new JdbcBuildScriptRepository(db).save(script);
        Target target = new Target();
        target.setScriptId(script.getId());
        target.setName("build");
        targetId = new JdbcTargetRepository(db).save(target).getId();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void saveStoresTaskWithAttributesInOrder() {
        BuildTask exec = task(TaskType.EXEC, 0);
        exec.setAttribute("program", "msbuild");
        exec.setAttribute("commandline", "App.csproj /p:Configuration=${configuration}");
        exec.setAttribute("failonerror", "true");

        repository.save(exec);

        assertNotNull(exec.getId());
        BuildTask found = repository.findById(exec.getId()).orElseThrow();
        assertEquals(TaskType.EXEC, found.getType());
        assertEquals(targetId, found.getTargetId());
        assertEquals(List.of("program", "commandline", "failonerror"), List.copyOf(found.getAttributes().keySet()));
        assertEquals("App.csproj /p:Configuration=${configuration}", found.getAttribute("commandline"));
    }

    @Test
    void findByTargetIdReturnsTasksOrderedByPosition() {
        BuildTask echo = task(TaskType.ECHO, 1);
        echo.setAttribute("message", "Готово");
        repository.save(echo);
        BuildTask mkdir = task(TaskType.MKDIR, 0);
        mkdir.setAttribute("dir", "build");
        repository.save(mkdir);

        List<BuildTask> found = repository.findByTargetId(targetId);

        assertEquals(List.of(TaskType.MKDIR, TaskType.ECHO), found.stream().map(BuildTask::getType).toList());
        assertEquals("Готово", found.get(1).getAttribute("message"));
    }

    @Test
    void saveExistingTaskReplacesAttributes() throws Exception {
        BuildTask copy = task(TaskType.COPY, 0);
        copy.setAttribute("file", "a.txt");
        copy.setAttribute("todir", "out");
        repository.save(copy);

        copy.getAttributes().clear();
        copy.setAttribute("todir", "dist");
        repository.save(copy);

        BuildTask found = repository.findById(copy.getId()).orElseThrow();
        assertEquals(Map.of("todir", "dist"), found.getAttributes());
        assertEquals(1, countRows("task_attribute"));
    }

    @Test
    void deleteByIdRemovesTaskAndItsAttributes() throws Exception {
        BuildTask echo = task(TaskType.ECHO, 0);
        echo.setAttribute("message", "hi");
        repository.save(echo);

        repository.deleteById(echo.getId());

        assertTrue(repository.findAll().isEmpty());
        assertEquals(0, countRows("task_attribute"));
    }

    private BuildTask task(TaskType type, int position) {
        BuildTask task = new BuildTask();
        task.setType(type);
        task.setTargetId(targetId);
        task.setPosition(position);
        return task;
    }

    private int countRows(String table) throws Exception {
        try (Statement statement = db.getConnection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
