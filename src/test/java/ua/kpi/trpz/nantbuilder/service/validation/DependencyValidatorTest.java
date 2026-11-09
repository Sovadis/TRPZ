package ua.kpi.trpz.nantbuilder.service.validation;

import org.junit.jupiter.api.Test;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.Target;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.messages;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.run;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.target;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.validScript;

class DependencyValidatorTest {

    private final DependencyValidator validator = new DependencyValidator();

    @Test
    void validDependenciesHaveNoIssues() {
        assertTrue(run(validator, validScript()).isEmpty());
    }

    @Test
    void diamondWithoutCycleHasNoIssues() {
        BuildScript script = scriptWith(target("a", "b", "c"), target("b", "d"), target("c", "d"), target("d"));

        assertTrue(run(validator, script).isEmpty());
    }

    @Test
    void unknownDependencyIsError() {
        BuildScript script = validScript();
        script.findTarget("compile").orElseThrow().getDependsOn().add("missing");

        assertEquals(List.of(new ValidationIssue(Severity.ERROR, "<target> compile",
                "Ціль «compile» залежить від невідомої цілі «missing»")), run(validator, script));
    }

    @Test
    void selfDependencyIsErrorButNotCycle() {
        BuildScript script = validScript();
        script.findTarget("compile").orElseThrow().getDependsOn().add("compile");

        assertEquals(List.of("Ціль «compile» не може залежати від самої себе"), messages(run(validator, script)));
    }

    @Test
    void repeatedDependencyIsError() {
        BuildScript script = validScript();
        script.findTarget("build").orElseThrow().getDependsOn().add("compile");

        assertEquals(List.of("Ціль «build» двічі залежить від «compile»"), messages(run(validator, script)));
    }

    @Test
    void twoTargetCycleIsNamedInMessage() {
        BuildScript script = scriptWith(target("a", "b"), target("b", "a"));

        assertEquals(List.of(new ValidationIssue(Severity.ERROR, "<target> a", "Циклічна залежність цілей: a → b → a")),
                run(validator, script));
    }

    @Test
    void longerCycleIsFoundBehindAcyclicPrefix() {
        BuildScript script = scriptWith(target("build", "compile"), target("compile", "generate"),
                target("generate", "restore"), target("restore", "compile"));

        assertEquals(List.of("Циклічна залежність цілей: compile → generate → restore → compile"),
                messages(run(validator, script)));
    }

    @Test
    void independentCyclesAndOtherErrorsAreAllReported() {
        BuildScript script = scriptWith(target("a", "b"), target("b", "a"),
                target("c", "d", "missing"), target("d", "c"));

        assertEquals(List.of(
                "Ціль «c» залежить від невідомої цілі «missing»",
                "Циклічна залежність цілей: a → b → a",
                "Циклічна залежність цілей: c → d → c"), messages(run(validator, script)));
    }

    private static BuildScript scriptWith(Target... targets) {
        BuildScript script = new BuildScript();
        script.setName("Cycles");
        script.getTargets().addAll(List.of(targets));
        return script;
    }
}
