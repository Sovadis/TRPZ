package ua.kpi.trpz.nantbuilder.service.validation;

import org.junit.jupiter.api.Test;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;
import ua.kpi.trpz.nantbuilder.model.TaskType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.messages;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.target;
import static ua.kpi.trpz.nantbuilder.service.validation.TestScripts.validScript;

class ScriptValidatorTest {

    private final ScriptValidator validator = ScriptValidator.standard();

    @Test
    void validScriptHasNoIssues() {
        assertTrue(validator.validate(validScript()).isEmpty());
    }

    @Test
    void handlersRunInStandardChainOrder() {
        // проблеми додано до скрипта у зворотному порядку, а знайдено їх у порядку ланцюжка
        BuildScript script = validScript();
        script.findTarget("build").orElseThrow().addTask(BuildTask.withDefaults(TaskType.ECHO));
        script.setDefaultTarget("deploy");
        script.findTarget("compile").orElseThrow().getDependsOn().add("missing");
        script.addProperty(new ScriptProperty("version", ""));
        script.setBaseDir("");

        assertEquals(List.of(
                "Не задано базову директорію (basedir), буде використано «.»",
                "Властивість «version» має порожнє значення",
                "Ціль «compile» залежить від невідомої цілі «missing»",
                "Ціль за замовчуванням «deploy» не знайдено серед цілей скрипта",
                "Завдання <echo> у цілі «build»: не задано атрибут «message»"), messages(validator.validate(script)));
    }

    @Test
    void chainStopsAfterTargetNameErrors() {
        BuildScript script = validScript();
        script.setBaseDir("");
        script.addProperty(new ScriptProperty("configuration", "Release"));
        script.addTarget(target("compile", "missing"));
        script.addTarget(target("a", "b"));
        script.addTarget(target("b", "a"));
        script.setDefaultTarget("deploy");
        script.findTarget("build").orElseThrow().addTask(BuildTask.withDefaults(TaskType.ECHO));

        assertEquals(List.of(
                "Не задано базову директорію (basedir), буде використано «.»",
                "Властивість «configuration» оголошено двічі",
                "Ціль «compile» оголошено двічі"), messages(validator.validate(script)));
    }

    @Test
    void allIssuesAreCollectedAtOnce() {
        BuildScript script = validScript();
        script.addProperty(new ScriptProperty("configuration", "Release"));
        script.findTarget("clean").orElseThrow().getDependsOn().add("build");
        script.setDefaultTarget(null);
        script.findTarget("clean").orElseThrow().getTasks().get(0).setAttribute("dir", "");

        List<ValidationIssue> issues = validator.validate(script);

        assertEquals(List.of(
                new ValidationIssue(Severity.ERROR, "<property> configuration", "Властивість «configuration» оголошено двічі"),
                new ValidationIssue(Severity.ERROR, "<target> clean", "Циклічна залежність цілей: clean → build → compile → clean"),
                new ValidationIssue(Severity.WARNING, "<project> MyLibrary",
                        "Не задано ціль за замовчуванням: під час запуску NAnt ціль доведеться вказувати явно"),
                new ValidationIssue(Severity.WARNING, "<delete> у цілі clean",
                        "Завдання <delete> у цілі «clean»: не задано атрибут «dir»")), issues);
        assertTrue(ScriptValidator.hasErrors(issues));
    }

    @Test
    void validateDoesNotChangeScript() {
        BuildScript script = validScript();
        script.setName("  MyLibrary  ");
        script.addProperty(new ScriptProperty("output", null));

        validator.validate(script);

        assertEquals("  MyLibrary  ", script.getName());
        assertNull(script.getProperties().get(1).getValue());
    }

    @Test
    void hasErrorsIgnoresWarnings() {
        ValidationIssue warning = ValidationIssue.warning("<project> X", "попередження");
        ValidationIssue error = ValidationIssue.error("<target> a", "помилка");

        assertFalse(ScriptValidator.hasErrors(List.of()));
        assertFalse(ScriptValidator.hasErrors(List.of(warning, warning)));
        assertTrue(ScriptValidator.hasErrors(List.of(warning, error)));
    }

    @Test
    void customChainRunsOnlyItsHandlers() {
        ValidationHandler first = new DefaultTargetValidator();
        first.setNext(new TaskAttributeValidator());
        BuildScript script = validScript();
        script.setName("");
        script.setDefaultTarget("deploy");

        assertEquals(List.of("Ціль за замовчуванням «deploy» не знайдено серед цілей скрипта"),
                messages(new ScriptValidator(first).validate(script)));
    }
}
