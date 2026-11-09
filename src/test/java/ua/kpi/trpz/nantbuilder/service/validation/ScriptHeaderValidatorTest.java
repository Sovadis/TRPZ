package ua.kpi.trpz.nantbuilder.service.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.kpi.trpz.nantbuilder.model.BuildScript;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.run;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.validScript;

class ScriptHeaderValidatorTest {

    private final ScriptHeaderValidator validator = new ScriptHeaderValidator();

    @Test
    void validHeaderHasNoIssues() {
        assertTrue(run(validator, validScript()).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankScriptNameIsError(String name) {
        BuildScript script = validScript();
        script.setName(name);

        assertEquals(List.of(new ValidationIssue(Severity.ERROR, "<project>", "Назва скрипта не може бути порожньою")),
                run(validator, script));
    }

    @Test
    void blankBaseDirIsWarning() {
        BuildScript script = validScript();
        script.setBaseDir(" ");

        assertEquals(List.of(new ValidationIssue(Severity.WARNING, "<project> MyLibrary",
                "Не задано базову директорію (basedir), буде використано «.»")), run(validator, script));
    }

    @Test
    void chainContinuesEvenAfterError() {
        BuildScript script = validScript();
        script.setName("");

        assertTrue(validator.check(script, new ArrayList<>()));
    }
}
