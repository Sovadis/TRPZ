package ua.kpi.trpz.nantbuilder.service.validation;

import ua.kpi.trpz.nantbuilder.model.BuildScript;

import java.util.List;

/**
 * Конкретний обробник (ConcreteHandler): перевіряє заголовок скрипта — назву і базову директорію.
 */
public class ScriptHeaderValidator extends ValidationHandler {

    @Override
    protected boolean check(BuildScript script, List<ValidationIssue> issues) {
        String name = script.getName();
        boolean blankName = name == null || name.isBlank();
        String element = blankName ? "<project>" : "<project> " + name;
        if (blankName) {
            issues.add(ValidationIssue.error(element, "Назва скрипта не може бути порожньою"));
        }
        if (script.getBaseDir() == null || script.getBaseDir().isBlank()) {
            issues.add(ValidationIssue.warning(element, "Не задано базову директорію (basedir), буде використано «.»"));
        }
        return true;
    }
}
