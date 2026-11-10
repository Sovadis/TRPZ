package ua.kpi.trpz.nantbuilder.service.validation;

import org.junit.jupiter.api.Test;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.TaskType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.messages;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.run;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.validScript;

class TaskAttributeValidatorTest {

    private final TaskAttributeValidator validator = new TaskAttributeValidator();

    @Test
    void filledAttributesHaveNoIssues() {
        assertTrue(run(validator, validScript()).isEmpty());
    }

    @Test
    void emptyEchoMessageIsWarning() {
        BuildScript script = validScript();
        script.findTarget("build").orElseThrow().addTask(BuildTask.withDefaults(TaskType.ECHO));

        assertEquals(List.of(new ValidationIssue(Severity.WARNING, "<echo> у цілі build",
                "Завдання <echo> у цілі «build»: не задано атрибут «message»")), run(validator, script));
    }

    @Test
    void everyMissingRequiredAttributeIsReportedInOrder() {
        BuildScript script = validScript();
        BuildTask copy = new BuildTask();
        copy.setType(TaskType.COPY);
        copy.setAttribute("todir", " ");
        script.findTarget("compile").orElseThrow().addTask(copy);

        assertEquals(List.of(
                "Завдання <copy> у цілі «compile»: не задано атрибут «file»",
                "Завдання <copy> у цілі «compile»: не задано атрибут «tofile» або «todir»"),
                messages(run(validator, script)));
    }

    @Test
    void deleteNeedsFileOrDir() {
        BuildScript script = validScript();
        script.findTarget("build").orElseThrow().addTask(task(TaskType.DELETE, "file", "bin/app.dll"));
        script.findTarget("build").orElseThrow().addTask(task(TaskType.DELETE, "dir", ""));

        assertEquals(List.of("Завдання <delete> у цілі «build»: не задано атрибут «file» або «dir»"),
                messages(run(validator, script)));
    }

    @Test
    void copyToFileIsEnough() {
        BuildScript script = validScript();
        BuildTask copy = task(TaskType.COPY, "file", "app.config");
        copy.setAttribute("tofile", "bin/app.config");
        script.findTarget("build").orElseThrow().addTask(copy);

        assertTrue(run(validator, script).isEmpty());
    }

    @Test
    void execWithoutCommandlineIsValid() {
        BuildScript script = validScript();
        script.findTarget("build").orElseThrow().addTask(task(TaskType.EXEC, "program", "dotnet"));

        assertTrue(run(validator, script).isEmpty());
    }

    @Test
    void emptyNonDefaultAttributeIsIgnored() {
        BuildScript script = validScript();
        script.findTarget("build").orElseThrow().getTasks().get(0).setAttribute("level", "");

        assertTrue(run(validator, script).isEmpty());
    }

    private static BuildTask task(TaskType type, String attribute, String value) {
        BuildTask task = new BuildTask();
        task.setType(type);
        task.setAttribute(attribute, value);
        return task;
    }
}
