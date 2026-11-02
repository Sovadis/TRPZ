package ua.kpi.trpz.nantbuilder.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcBuildScriptRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcPropertyRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcTargetRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcTaskRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolutionImportServiceTest {

    @TempDir
    Path tempDir;

    private Database db;
    private ScriptService scriptService;
    private SolutionImportService importService;

    @BeforeEach
    void setUp() {
        db = Database.openFile(tempDir.resolve("test.db"));
        db.initSchema();
        scriptService = new ScriptService(db, new JdbcBuildScriptRepository(db), new JdbcPropertyRepository(db),
                new JdbcTargetRepository(db), new JdbcTaskRepository(db));
        importService = new SolutionImportService(new SolutionParser(), new ScriptGenerator(), scriptService);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void importSavesGeneratedScriptThatCanBeLoadedBack() throws Exception {
        BuildScript imported = importService.importSolution(SolutionParserTest.DEMO_SHOP);

        assertNotNull(imported.getId());
        BuildScript loaded = scriptService.loadScript(imported.getId());
        assertEquals("DemoShop", loaded.getName());
        assertEquals("build", loaded.getDefaultTarget());
        assertEquals(6, loaded.getTargets().size());
        assertEquals(List.of("build-DemoShop.Core", "build-DemoShop.Data"),
                loaded.findTarget("build-DemoShop.Web").map(Target::getDependsOn).orElseThrow());
    }

    @Test
    void repeatedImportGetsUniqueName() throws Exception {
        importService.importSolution(SolutionParserTest.DEMO_SHOP);
        importService.importSolution(SolutionParserTest.DEMO_SHOP);
        BuildScript third = importService.importSolution(SolutionParserTest.DEMO_SHOP);

        assertEquals("DemoShop (3)", third.getName());
        assertEquals(List.of("DemoShop", "DemoShop (2)", "DemoShop (3)"),
                scriptService.listScripts().stream().map(BuildScript::getName).toList());
    }

    @Test
    void solutionWithoutProjectsIsRejectedAndNothingIsSaved() throws Exception {
        Path sln = tempDir.resolve("Empty.sln");
        Files.writeString(sln, "Microsoft Visual Studio Solution File, Format Version 12.00\nGlobal\nEndGlobal\n");

        assertThrows(ValidationException.class, () -> importService.importSolution(sln));
        assertTrue(scriptService.listScripts().isEmpty());
    }
}
