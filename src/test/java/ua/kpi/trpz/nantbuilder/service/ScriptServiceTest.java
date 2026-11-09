package ua.kpi.trpz.nantbuilder.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.model.TaskType;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcBuildScriptRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcPropertyRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcTargetRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcTaskRepository;
import ua.kpi.trpz.nantbuilder.service.validation.Severity;
import ua.kpi.trpz.nantbuilder.service.validation.ValidationIssue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptServiceTest {

    @TempDir
    Path tempDir;

    private Database db;
    private ScriptService service;
    private final XmlScriptSerializer serializer = new XmlScriptSerializer();

    @BeforeEach
    void setUp() {
        db = Database.openFile(tempDir.resolve("test.db"));
        db.initSchema();
        service = new ScriptService(db, new JdbcBuildScriptRepository(db), new JdbcPropertyRepository(db),
                new JdbcTargetRepository(db), new JdbcTaskRepository(db));
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void createScriptStoresHeaderWithEmptyDefaultTarget() {
        BuildScript created = service.createScript("  MyLibrary ", "Бібліотека класів", "  ");

        assertNotNull(created.getId());
        assertEquals("MyLibrary", created.getName());
        assertEquals(".", created.getBaseDir());
        assertEquals("build", created.getDefaultTarget());
        assertNotNull(created.getCreatedAt());

        BuildScript loaded = service.loadScript(created.getId());
        assertEquals("Бібліотека класів", loaded.getDescription());
        assertEquals(1, loaded.getTargets().size());
        assertEquals("build", loaded.getTargets().get(0).getName());
        assertTrue(loaded.getTargets().get(0).getTasks().isEmpty());
        assertEquals(List.of("MyLibrary"), service.listScripts().stream().map(BuildScript::getName).toList());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void createScriptRejectsBlankName(String name) {
        assertThrows(ValidationException.class, () -> service.createScript(name, "опис", "."));
        assertTrue(service.listScripts().isEmpty());
    }

    @Test
    void createScriptRejectsDuplicateName() {
        service.createScript("MyLibrary", null, ".");

        ValidationException error = assertThrows(ValidationException.class,
                () -> service.createScript("MyLibrary", "інший", "."));

        assertTrue(error.getMessage().contains("MyLibrary"));
        assertEquals(1, service.listScripts().size());
    }

    @Test
    void listScriptsIsOrderedByName() {
        service.createScript("Zeta", null, ".");
        service.createScript("Alpha", null, ".");

        assertEquals(List.of("Alpha", "Zeta"), service.listScripts().stream().map(BuildScript::getName).toList());
    }

    @Test
    void saveAndLoadRoundTripKeepsPropertiesTargetsDependenciesAndTasks() {
        BuildScript script = sampleScript();

        BuildScript saved = service.saveScript(script);
        BuildScript loaded = service.loadScript(saved.getId());

        assertEquals("MyLibrary", loaded.getName());
        assertEquals("test", loaded.getDefaultTarget());
        assertEquals("src", loaded.getBaseDir());
        assertEquals(2, loaded.getProperties().size());
        assertTrue(loaded.getProperties().get(1).isReadOnly());
        assertEquals(List.of("clean", "compile", "test"), loaded.getTargets().stream().map(Target::getName).toList());
        Target test = loaded.findTarget("test").orElseThrow();
        assertEquals(List.of("compile", "clean"), test.getDependsOn());
        assertEquals(List.of(TaskType.EXEC, TaskType.ECHO), test.getTasks().stream().map(BuildTask::getType).toList());
        assertEquals("nunit3-console", test.getTasks().get(0).getAttribute("program"));
        assertEquals("Тести пройдено", test.getTasks().get(1).getAttribute("message"));
        // повне порівняння всього графа через XML-подання
        assertEquals(serializer.toXml(script), serializer.toXml(loaded));
    }

    @Test
    void resavingReplacesChildrenInsteadOfDuplicatingThem() throws Exception {
        BuildScript loaded = service.loadScript(service.saveScript(sampleScript()).getId());

        // змінюємо скрипт: прибираємо ціль test, завдання mkdir, змінюємо атрибут, додаємо властивість
        loaded.getTargets().removeIf(target -> target.getName().equals("test"));
        loaded.setDefaultTarget("compile");
        Target compile = loaded.findTarget("compile").orElseThrow();
        compile.getTasks().removeIf(task -> task.getType() == TaskType.MKDIR);
        compile.getTasks().get(0).setAttribute("output", "${build.dir}/Changed.dll");
        loaded.addProperty(new ScriptProperty("version", "1.2"));
        String expectedXml = serializer.toXml(loaded);

        service.saveScript(loaded);
        BuildScript reloaded = service.loadScript(loaded.getId());

        assertEquals(expectedXml, serializer.toXml(reloaded));
        assertEquals(1, countRows("build_script"));
        assertEquals(3, countRows("script_property"));
        assertEquals(2, countRows("target"));
        assertEquals(1, countRows("target_dependency"));
        assertEquals(2, countRows("task"));
        assertEquals(3, countRows("task_attribute"));
    }

    @Test
    void saveScriptRejectsDependencyOnUnknownTarget() {
        BuildScript script = sampleScript();
        script.findTarget("compile").orElseThrow().getDependsOn().add("missing");

        assertThrows(ValidationException.class, () -> service.saveScript(script));
        assertTrue(service.listScripts().isEmpty());
    }

    @Test
    void saveScriptRejectsNameOfAnotherScript() {
        service.createScript("MyLibrary", null, ".");
        BuildScript other = service.createScript("Other", null, ".");
        other.setName("MyLibrary");

        assertThrows(ValidationException.class, () -> service.saveScript(other));
    }

    @Test
    void deleteScriptRemovesAllRows() throws Exception {
        BuildScript saved = service.saveScript(sampleScript());

        service.deleteScript(saved.getId());

        assertTrue(service.listScripts().isEmpty());
        for (String table : List.of("script_property", "target", "target_dependency", "task", "task_attribute")) {
            assertEquals(0, countRows(table), table);
        }
    }

    @Test
    void exportScriptWritesUtf8NantXml() throws Exception {
        BuildScript script = sampleScript();
        Path file = tempDir.resolve("MyLibrary.build");

        service.exportScript(script, file);

        assertEquals(serializer.toXml(script), Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void saveScriptRejectsDependencyCycle() {
        BuildScript script = sampleScript();
        script.findTarget("compile").orElseThrow().getDependsOn().add("test");

        ValidationException error = assertThrows(ValidationException.class, () -> service.saveScript(script));

        assertEquals("Циклічна залежність цілей: compile → test → compile", error.getMessage());
        assertTrue(service.listScripts().isEmpty());
    }

    @Test
    void saveScriptErrorMessageListsAllErrorsFirstErrorFirst() {
        BuildScript script = sampleScript();
        script.addProperty(new ScriptProperty("configuration", "Release"));
        script.findTarget("compile").orElseThrow().getDependsOn().add("missing");
        script.setDefaultTarget("deploy");

        ValidationException error = assertThrows(ValidationException.class, () -> service.saveScript(script));

        assertEquals(List.of(
                "Властивість «configuration» оголошено двічі",
                "Ціль «compile» залежить від невідомої цілі «missing»",
                "Ціль за замовчуванням «deploy» не знайдено серед цілей скрипта"), error.getMessage().lines().toList());
    }

    @Test
    void saveScriptWithOnlyWarningsSucceeds() {
        BuildScript script = sampleScript();
        script.setDefaultTarget(null);
        script.addProperty(new ScriptProperty("version", null));
        script.findTarget("test").orElseThrow().addTask(BuildTask.withDefaults(TaskType.ECHO));

        BuildScript loaded = service.loadScript(service.saveScript(script).getId());

        assertNull(loaded.getDefaultTarget());
        assertEquals("", loaded.getProperties().get(2).getValue());
        assertEquals("", loaded.findTarget("test").orElseThrow().getTasks().get(2).getAttribute("message"));
    }

    @Test
    void checkScriptReturnsWarningsAndErrorsWithoutThrowingOrSaving() {
        BuildScript script = sampleScript();
        script.setBaseDir("");
        script.findTarget("clean").orElseThrow().addTask(BuildTask.withDefaults(TaskType.MKDIR));

        List<ValidationIssue> warnings = service.checkScript(script);

        assertEquals(List.of(
                new ValidationIssue(Severity.WARNING, "<project> MyLibrary",
                        "Не задано базову директорію (basedir), буде використано «.»"),
                new ValidationIssue(Severity.WARNING, "<mkdir> у цілі clean",
                        "Завдання <mkdir> у цілі «clean»: не задано атрибут «dir»")), warnings);

        script.setDefaultTarget("deploy");
        assertTrue(service.checkScript(script).stream().anyMatch(issue -> issue.severity() == Severity.ERROR));
        assertTrue(service.listScripts().isEmpty());
    }

    @Test
    void exportScriptWithErrorsThrowsAndWritesNothing() {
        BuildScript script = sampleScript();
        script.findTarget("clean").orElseThrow().getDependsOn().add("clean");
        Path file = tempDir.resolve("MyLibrary.build");

        ValidationException error = assertThrows(ValidationException.class, () -> service.exportScript(script, file));

        assertEquals("Ціль «clean» не може залежати від самої себе", error.getMessage());
        assertFalse(Files.exists(file));
    }

    @Test
    void exportScriptWithOnlyWarningsWritesFile() throws Exception {
        BuildScript script = sampleScript();
        script.setDefaultTarget(null);
        Path file = tempDir.resolve("MyLibrary.build");

        service.exportScript(script, file);

        assertEquals(serializer.toXml(script), Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Скрипт з трьома цілями, залежностями, властивостями і завданнями. */
    static BuildScript sampleScript() {
        BuildScript script = new BuildScript();
        script.setName("MyLibrary");
        script.setDescription("Бібліотека класів");
        script.setDefaultTarget("test");
        script.setBaseDir("src");
        script.addProperty(new ScriptProperty("configuration", "Debug"));
        ScriptProperty buildDir = new ScriptProperty("build.dir", "build");
        buildDir.setReadOnly(true);
        script.addProperty(buildDir);

        Target clean = target("clean");
        clean.addTask(task(TaskType.DELETE, "dir", "${build.dir}"));
        Target compile = target("compile");
        compile.getDependsOn().add("clean");
        compile.addTask(task(TaskType.MKDIR, "dir", "${build.dir}"));
        BuildTask csc = task(TaskType.CSC, "target", "library");
        csc.setAttribute("output", "${build.dir}/MyLibrary.dll");
        compile.addTask(csc);
        Target test = target("test");
        test.getDependsOn().add("compile");
        test.getDependsOn().add("clean");
        BuildTask exec = task(TaskType.EXEC, "program", "nunit3-console");
        exec.setAttribute("commandline", "${build.dir}/MyLibrary.Tests.dll");
        test.addTask(exec);
        test.addTask(task(TaskType.ECHO, "message", "Тести пройдено"));

        script.addTarget(clean);
        script.addTarget(compile);
        script.addTarget(test);
        return script;
    }

    private static Target target(String name) {
        Target target = new Target();
        target.setName(name);
        return target;
    }

    private static BuildTask task(TaskType type, String attribute, String value) {
        BuildTask task = BuildTask.withDefaults(type);
        task.setAttribute(attribute, value);
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
