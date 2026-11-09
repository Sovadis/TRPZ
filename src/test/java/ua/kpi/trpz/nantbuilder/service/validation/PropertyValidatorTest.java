package ua.kpi.trpz.nantbuilder.service.validation;

import org.junit.jupiter.api.Test;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.run;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.validScript;

class PropertyValidatorTest {

    private final PropertyValidator validator = new PropertyValidator();

    @Test
    void validPropertiesHaveNoIssues() {
        assertTrue(run(validator, validScript()).isEmpty());
    }

    @Test
    void blankPropertyNameIsError() {
        BuildScript script = validScript();
        script.addProperty(new ScriptProperty(" ", "x"));

        assertEquals(List.of(new ValidationIssue(Severity.ERROR, "<property>", "Назва властивості не може бути порожньою")),
                run(validator, script));
    }

    @Test
    void duplicatePropertyIsReportedOnce() {
        BuildScript script = validScript();
        script.addProperty(new ScriptProperty("configuration", "Release"));
        script.addProperty(new ScriptProperty("configuration", "Test"));

        assertEquals(List.of(new ValidationIssue(Severity.ERROR, "<property> configuration",
                "Властивість «configuration» оголошено двічі")), run(validator, script));
    }

    @Test
    void emptyOrMissingValueIsWarning() {
        BuildScript script = validScript();
        script.addProperty(new ScriptProperty("version", ""));
        script.addProperty(new ScriptProperty("output", null));

        List<ValidationIssue> issues = run(validator, script);

        assertEquals(List.of(
                new ValidationIssue(Severity.WARNING, "<property> version", "Властивість «version» має порожнє значення"),
                new ValidationIssue(Severity.WARNING, "<property> output", "Властивість «output» має порожнє значення")),
                issues);
    }

    @Test
    void chainContinuesEvenAfterError() {
        BuildScript script = validScript();
        script.addProperty(new ScriptProperty("", ""));

        assertTrue(validator.check(script, new ArrayList<>()));
    }
}
