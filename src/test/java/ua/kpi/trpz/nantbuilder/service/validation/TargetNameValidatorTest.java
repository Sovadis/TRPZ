package ua.kpi.trpz.nantbuilder.service.validation;

import org.junit.jupiter.api.Test;
import ua.kpi.trpz.nantbuilder.model.BuildScript;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.run;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.target;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.validScript;

class TargetNameValidatorTest {

    private final TargetNameValidator validator = new TargetNameValidator();

    @Test
    void uniqueNamesHaveNoIssuesAndContinueChain() {
        List<ValidationIssue> issues = new ArrayList<>();

        assertTrue(validator.check(validScript(), issues));
        assertTrue(issues.isEmpty());
    }

    @Test
    void blankTargetNameIsErrorAndStopsChain() {
        BuildScript script = validScript();
        script.addTarget(target(""));
        List<ValidationIssue> issues = new ArrayList<>();

        assertFalse(validator.check(script, issues));
        assertEquals(List.of(new ValidationIssue(Severity.ERROR, "<target>", "Назва цілі не може бути порожньою")),
                issues);
    }

    @Test
    void duplicateTargetIsErrorAndStopsChain() {
        BuildScript script = validScript();
        script.addTarget(target("build"));
        script.addTarget(target("build"));
        List<ValidationIssue> issues = new ArrayList<>();

        assertFalse(validator.check(script, issues));
        assertEquals(List.of(new ValidationIssue(Severity.ERROR, "<target> build", "Ціль «build» оголошено двічі")),
                issues);
    }

    @Test
    void nextHandlerIsCalledOnlyWithoutErrors() {
        TestScripts.RecordingHandler next = new TestScripts.RecordingHandler();
        validator.setNext(next);

        run(validator, validScript());
        assertEquals(1, next.calls);

        BuildScript broken = validScript();
        broken.addTarget(target("clean"));
        run(validator, broken);
        assertEquals(1, next.calls);
    }
}
