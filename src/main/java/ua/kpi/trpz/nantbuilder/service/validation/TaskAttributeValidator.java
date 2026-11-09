package ua.kpi.trpz.nantbuilder.service.validation;

import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.Target;

import java.util.List;

/**
 * Конкретний обробник (ConcreteHandler): попереджає про незаповнені стандартні атрибути завдань
 * (наприклад, {@code message} у {@code <echo>} чи {@code dir} у {@code <mkdir>}).
 */
public class TaskAttributeValidator extends ValidationHandler {

    @Override
    protected boolean check(BuildScript script, List<ValidationIssue> issues) {
        for (Target target : script.getTargets()) {
            for (BuildTask task : target.getTasks()) {
                if (task.getType() == null) {
                    continue;
                }
                String tag = "<" + task.getType().getXmlName() + ">";
                for (String attribute : task.getType().getDefaultAttributes()) {
                    String value = task.getAttribute(attribute);
                    if (value == null || value.isBlank()) {
                        issues.add(ValidationIssue.warning(tag + " у цілі " + target.getName(),
                                "Завдання " + tag + " у цілі «" + target.getName()
                                        + "»: не задано атрибут «" + attribute + "»"));
                    }
                }
            }
        }
        return true;
    }
}
