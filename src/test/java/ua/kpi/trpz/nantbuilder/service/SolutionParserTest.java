package ua.kpi.trpz.nantbuilder.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolutionParserTest {

    /** Тести запускаються з кореня проєкту (робочий каталог Gradle). */
    static final Path DEMO_SHOP = Path.of("samples", "DemoShop", "DemoShop.sln");

    private final SolutionParser parser = new SolutionParser();

    @TempDir
    Path tempDir;

    @Test
    void parsesDemoShopProjectsAndProjectReferences() throws IOException {
        SolutionInfo solution = parser.parse(DEMO_SHOP);

        assertEquals("DemoShop", solution.name());
        assertEquals(DEMO_SHOP.toAbsolutePath().getParent(), solution.directory());
        // папка рішення "tests" не є проєктом
        assertEquals(List.of("DemoShop.Web", "DemoShop.Data", "DemoShop.Core", "DemoShop.Tests"),
                solution.projects().stream().map(SolutionProject::name).toList());

        Map<String, SolutionProject> byName = solution.projects().stream()
                .collect(Collectors.toMap(SolutionProject::name, Function.identity()));
        String core = byName.get("DemoShop.Core").guid();
        String data = byName.get("DemoShop.Data").guid();
        assertEquals("DemoShop.Data\\DemoShop.Data.csproj", byName.get("DemoShop.Data").relativePath());
        assertTrue(byName.get("DemoShop.Core").dependencyGuids().isEmpty());
        assertEquals(List.of(core), byName.get("DemoShop.Data").dependencyGuids());
        assertEquals(List.of(core, data), byName.get("DemoShop.Web").dependencyGuids());
        assertEquals(List.of(core), byName.get("DemoShop.Tests").dependencyGuids());
    }

    @Test
    void readsProjectDependenciesSectionOfSlnFile() throws IOException {
        Path sln = tempDir.resolve("Legacy.sln");
        Files.writeString(sln, """
                Microsoft Visual Studio Solution File, Format Version 12.00
                Project("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "Lib", "Lib\\Lib.csproj", "{11111111-1111-1111-1111-111111111111}"
                EndProject
                Project("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "App", "App\\App.csproj", "{22222222-2222-2222-2222-222222222222}"
                	ProjectSection(ProjectDependencies) = postProject
                		{11111111-1111-1111-1111-111111111111} = {11111111-1111-1111-1111-111111111111}
                	EndProjectSection
                EndProject
                """);

        SolutionInfo solution = parser.parse(sln);

        assertEquals("Legacy", solution.name());
        assertEquals(2, solution.projects().size());
        assertTrue(solution.projects().get(0).dependencyGuids().isEmpty());
        assertEquals(List.of("11111111-1111-1111-1111-111111111111"), solution.projects().get(1).dependencyGuids());
    }

    @Test
    void missingSolutionFileThrowsIOException() {
        assertThrows(IOException.class, () -> parser.parse(tempDir.resolve("missing.sln")));
    }
}
