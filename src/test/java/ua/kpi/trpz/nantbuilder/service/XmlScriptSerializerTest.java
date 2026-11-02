package ua.kpi.trpz.nantbuilder.service;

import org.junit.jupiter.api.Test;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.model.TaskType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlScriptSerializerTest {

    private final XmlScriptSerializer serializer = new XmlScriptSerializer();

    @Test
    void toXmlProducesNantXmlWithTwoSpaceIndent() {
        BuildScript script = new BuildScript();
        script.setName("DemoShop");
        script.setDefaultTarget("build");
        script.setDescription("Згенеровано з DemoShop.sln");
        script.addProperty(new ScriptProperty("configuration", "Release"));
        Target clean = new Target();
        clean.setName("clean");
        clean.setDescription("Очистити каталог збірки");
        BuildTask delete = BuildTask.withDefaults(TaskType.DELETE);
        delete.setAttribute("dir", "${build.dir}");
        clean.addTask(delete);
        Target data = new Target();
        data.setName("build-DemoShop.Data");
        data.getDependsOn().add("build-DemoShop.Core");
        BuildTask exec = BuildTask.withDefaults(TaskType.EXEC);
        exec.setAttribute("program", "msbuild");
        exec.setAttribute("commandline", "DemoShop.Data\\DemoShop.Data.csproj /p:Configuration=${configuration}");
        data.addTask(exec);
        script.addTarget(clean);
        script.addTarget(data);

        String expected = """
                <?xml version="1.0" encoding="utf-8"?>
                <project name="DemoShop" default="build" basedir=".">
                  <description>Згенеровано з DemoShop.sln</description>
                  <property name="configuration" value="Release" />
                  <target name="clean" description="Очистити каталог збірки">
                    <delete dir="${build.dir}" />
                  </target>
                  <target name="build-DemoShop.Data" depends="build-DemoShop.Core">
                    <exec program="msbuild" commandline="DemoShop.Data\\DemoShop.Data.csproj /p:Configuration=${configuration}" />
                  </target>
                </project>
                """;
        assertEquals(expected, serializer.toXml(script));
    }

    @Test
    void emptyTargetAndReadOnlyPropertyAreWrittenCompactly() {
        BuildScript script = new BuildScript();
        script.setName("Empty");
        ScriptProperty version = new ScriptProperty("version", "1.0");
        version.setReadOnly(true);
        script.addProperty(version);
        Target build = new Target();
        build.setName("build");
        script.addTarget(build);

        String xml = serializer.toXml(script);

        assertTrue(xml.contains("<project name=\"Empty\" basedir=\".\">"), xml);
        assertTrue(xml.contains("<property name=\"version\" value=\"1.0\" readonly=\"true\" />"), xml);
        assertTrue(xml.contains("<target name=\"build\" />"), xml);
    }

    @Test
    void fromXmlOfToXmlGivesTheSameScript() {
        BuildScript original = ScriptServiceTest.sampleScript();

        String xml = serializer.toXml(original);
        BuildScript parsed = serializer.fromXml(xml);

        assertEquals(xml, serializer.toXml(parsed));
        assertEquals("MyLibrary", parsed.getName());
        assertEquals("Бібліотека класів", parsed.getDescription());
        assertEquals("test", parsed.getDefaultTarget());
        assertEquals("src", parsed.getBaseDir());
        assertTrue(parsed.getProperties().get(1).isReadOnly());
        Target test = parsed.findTarget("test").orElseThrow();
        assertEquals(List.of("compile", "clean"), test.getDependsOn());
        assertEquals(List.of("program", "commandline"), List.copyOf(test.getTasks().get(0).getAttributes().keySet()));
    }

    @Test
    void specialCharactersAreEscapedAndRestored() {
        BuildScript script = new BuildScript();
        script.setName("A&B");
        script.setDescription("Збірка <усього> & \"тестів\"");
        Target build = new Target();
        build.setName("build");
        BuildTask echo = BuildTask.withDefaults(TaskType.ECHO);
        echo.setAttribute("message", "x < y && \"z\"");
        build.addTask(echo);
        script.addTarget(build);

        String xml = serializer.toXml(script);
        BuildScript parsed = serializer.fromXml(xml);

        assertTrue(xml.contains("name=\"A&amp;B\""), xml);
        assertEquals("A&B", parsed.getName());
        assertEquals("Збірка <усього> & \"тестів\"", parsed.getDescription());
        assertEquals("x < y && \"z\"", parsed.getTargets().get(0).getTasks().get(0).getAttribute("message"));
    }

    @Test
    void fromXmlReadsHandWrittenScript() {
        String xml = """
                <project name="Hand" default="all">
                  <!-- коментар ігнорується -->
                  <target name="init"><mkdir dir="out"/></target>
                  <target name="all" depends=" init , init2 "/>
                </project>
                """;

        BuildScript parsed = serializer.fromXml(xml);

        assertEquals(".", parsed.getBaseDir());
        assertNull(parsed.getDescription());
        assertEquals(List.of("init", "init2"), parsed.findTarget("all").orElseThrow().getDependsOn());
        assertEquals("out", parsed.findTarget("init").orElseThrow().getTasks().get(0).getAttribute("dir"));
    }

    @Test
    void fromXmlRejectsMalformedXml() {
        assertThrows(ValidationException.class, () -> serializer.fromXml("<project name=\"X\"><target name=\"a\">"));
    }

    @Test
    void fromXmlRejectsUnknownTask() {
        ValidationException error = assertThrows(ValidationException.class, () -> serializer.fromXml(
                "<project name=\"X\"><target name=\"a\"><nunit2 /></target></project>"));
        assertTrue(error.getMessage().contains("nunit2"));
    }

    @Test
    void fromXmlRejectsWrongRootAndMissingName() {
        assertThrows(ValidationException.class, () -> serializer.fromXml("<build name=\"X\" />"));
        assertThrows(ValidationException.class, () -> serializer.fromXml("<project default=\"build\" />"));
    }
}
