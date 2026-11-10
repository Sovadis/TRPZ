package ua.kpi.trpz.nantbuilder.service.validation;

import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.model.TaskType;

import java.util.List;
import java.util.Map;

/**
 * Конкретний обробник (ConcreteHandler): попереджає про незаповнені обов'язкові атрибути завдань NAnt
 * (наприклад, {@code message} у {@code <echo>}, {@code file} або {@code dir} у {@code <delete>}).
 */
public class TaskAttributeValidator extends ValidationHandler {

    /**
     * Обов'язкові атрибути кожного типу завдання. Кожен елемент списку — одна вимога; якщо в ній кілька
     * атрибутів через «|», достатньо заповнити будь-який із них.
     */
    private static final Map<TaskType, List<String>> REQUIRED = Map.of(
            TaskType.ECHO, List.of("message"),
            TaskType.MKDIR, List.of("dir"),
            TaskType.DELETE, List.of("file|dir"),
            TaskType.COPY, List.of("file", "tofile|todir"),
            TaskType.MSBUILD, List.of("project"),
            TaskType.CSC, List.of("target", "output"),
            TaskType.EXEC, List.of("program"));

    @Override
    protected boolean check(BuildScript script, List<ValidationIssue> issues) {
        for (Target target : script.getTargets()) {
            for (BuildTask task : target.getTasks()) {
                if (task.getType() == null) {
                    continue;
                }
                String tag = "<" + task.getType().getXmlName() + ">";
                for (String requirement : REQUIRED.getOrDefault(task.getType(), List.of())) {
                    List<String> alternatives = List.of(requirement.split("\\|"));
                    if (alternatives.stream().noneMatch(attribute -> isFilled(task, attribute))) {
                        issues.add(ValidationIssue.warning(tag + " у цілі " + target.getName(),
                                "Завдання " + tag + " у цілі «" + target.getName() + "»: не задано атрибут «"
                                        + String.join("» або «", alternatives) + "»"));
                    }
                }
            }
        }
        return true;
    }

    private static boolean isFilled(BuildTask task, String attribute) {
        String value = task.getAttribute(attribute);
        return value != null && !value.isBlank();
    }
}
