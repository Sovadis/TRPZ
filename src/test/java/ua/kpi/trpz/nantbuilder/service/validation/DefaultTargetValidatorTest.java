package ua.kpi.trpz.nantbuilder.service.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.kpi.trpz.nantbuilder.model.BuildScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.run;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.validScript;

class DefaultTargetValidatorTest {

    private final DefaultTargetValidator validator = new DefaultTargetValidator();

    @Test
    void existingDefaultTargetHasNoIssues() {
        assertTrue(run(validator, validScript()).isEmpty());
    }

    @Test
    void unknownDefaultTargetIsError() {
        BuildScript script = validScript();
        script.setDefaultTarget("deploy");

        assertEquals(List.of(new ValidationIssue(Severity.ERROR, "<project> MyLibrary",
                "Ціль за замовчуванням «deploy» не знайдено серед цілей скрипта")), run(validator, script));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void missingDefaultTargetIsWarning(String defaultTarget) {
        BuildScript script = validScript();
        script.setDefaultTarget(defaultTarget);

        assertEquals(List.of(new ValidationIssue(Severity.WARNING, "<project> MyLibrary",
                "Не задано ціль за замовчуванням: під час запуску NAnt ціль доведеться вказувати явно")),
                run(validator, script));
    }
}
