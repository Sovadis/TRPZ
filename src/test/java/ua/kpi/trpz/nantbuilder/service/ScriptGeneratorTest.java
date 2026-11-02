package ua.kpi.trpz.nantbuilder.service;

import org.junit.jupiter.api.Test;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.model.TaskType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptGeneratorTest {

    private final ScriptGenerator generator = new ScriptGenerator();

    @Test
    void generatesDependencyOrderedTargetsForDemoShop() throws IOException {
        SolutionInfo solution = new SolutionParser().parse(SolutionParserTest.DEMO_SHOP);

        BuildScript script = generator.generate(solution);

        assertEquals("DemoShop", script.getName());
        assertEquals("build", script.getDefaultTarget());
        assertEquals(List.of("configuration=Release", "build.dir=build"),
                script.getProperties().stream().map(p -> p.getName() + "=" + p.getValue()).toList());
        assertEquals(List.of("clean", "build-DemoShop.Core", "build-DemoShop.Data", "build-DemoShop.Web",
                        "build-DemoShop.Tests", "build"),
                script.getTargets().stream().map(Target::getName).toList());
        assertEquals(4, script.getTargets().stream().filter(t -> t.getName().startsWith("build-")).count());

        // кожна залежність оголошена раніше за ціль, яка від неї залежить
        List<String> seen = new ArrayList<>();
        for (Target target : script.getTargets()) {
            assertTrue(seen.containsAll(target.getDependsOn()), target.getName());
            seen.add(target.getName());
        }

        assertEquals(List.of("clean"), script.findTarget("build-DemoShop.Core").orElseThrow().getDependsOn());
        assertEquals(List.of("build-DemoShop.Core", "build-DemoShop.Data"),
                script.findTarget("build-DemoShop.Web").orElseThrow().getDependsOn());
        assertEquals(List.of("build-DemoShop.Core", "build-DemoShop.Data", "build-DemoShop.Web", "build-DemoShop.Tests"),
                script.findTarget("build").orElseThrow().getDependsOn());
    }

    @Test
    void projectTargetRunsMsbuildAndCleanDeletesBuildDir() throws IOException {
        BuildScript script = generator.generate(new SolutionParser().parse(SolutionParserTest.DEMO_SHOP));

        BuildTask exec = script.findTarget("build-DemoShop.Data").orElseThrow().getTasks().get(0);
        assertEquals(TaskType.EXEC, exec.getType());
        assertEquals("msbuild", exec.getAttribute("program"));
        assertEquals("DemoShop.Data\\DemoShop.Data.csproj /p:Configuration=${configuration}",
                exec.getAttribute("commandline"));

        BuildTask delete = script.findTarget("clean").orElseThrow().getTasks().get(0);
        assertEquals(TaskType.DELETE, delete.getType());
        assertEquals("${build.dir}", delete.getAttribute("dir"));
    }

    @Test
    void cyclicProjectDependenciesAreRejected() {
        SolutionInfo solution = new SolutionInfo("Cycle", Path.of("."), List.of(
                new SolutionProject("A", "A\\A.csproj", "A-GUID", List.of("B-GUID")),
                new SolutionProject("B", "B\\B.csproj", "B-GUID", List.of("A-GUID"))));

        assertThrows(ValidationException.class, () -> generator.generate(solution));
    }

    @Test
    void generatedScriptPropertiesAreNotReadOnly() throws IOException {
        BuildScript script = generator.generate(new SolutionParser().parse(SolutionParserTest.DEMO_SHOP));

        assertTrue(script.getProperties().stream().noneMatch(ScriptProperty::isReadOnly));
    }
}
