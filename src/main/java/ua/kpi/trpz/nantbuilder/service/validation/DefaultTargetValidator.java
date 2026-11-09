package ua.kpi.trpz.nantbuilder.service.validation;

import ua.kpi.trpz.nantbuilder.model.BuildScript;

import java.util.List;

/**
 * Конкретний обробник (ConcreteHandler): перевіряє ціль за замовчуванням (атрибут {@code default}).
 */
public class DefaultTargetValidator extends ValidationHandler {

    @Override
    protected boolean check(BuildScript script, List<ValidationIssue> issues) {
        String name = script.getName();
        String element = name == null || name.isBlank() ? "<project>" : "<project> " + name;
        String defaultTarget = script.getDefaultTarget();
        if (defaultTarget == null || defaultTarget.isBlank()) {
            issues.add(ValidationIssue.warning(element,
                    "Не задано ціль за замовчуванням: під час запуску NAnt ціль доведеться вказувати явно"));
        } else if (script.getTargets().stream().noneMatch(target -> defaultTarget.equals(target.getName()))) {
            issues.add(ValidationIssue.error(element,
                    "Ціль за замовчуванням «" + defaultTarget + "» не знайдено серед цілей скрипта"));
        }
        return true;
    }
}
