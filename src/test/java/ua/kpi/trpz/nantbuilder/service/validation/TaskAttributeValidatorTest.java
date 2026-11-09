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
    void everyMissingDefaultAttributeIsReportedInOrder() {
        BuildScript script = validScript();
        BuildTask copy = new BuildTask();
        copy.setType(TaskType.COPY);
        copy.setAttribute("todir", " ");
        script.findTarget("compile").orElseThrow().addTask(copy);

        assertEquals(List.of(
                "Завдання <copy> у цілі «compile»: не задано атрибут «file»",
                "Завдання <copy> у цілі «compile»: не задано атрибут «todir»"), messages(run(validator, script)));
    }

    @Test
    void emptyNonDefaultAttributeIsIgnored() {
        BuildScript script = validScript();
        script.findTarget("build").orElseThrow().getTasks().get(0).setAttribute("level", "");

        assertTrue(run(validator, script).isEmpty());
    }
}
