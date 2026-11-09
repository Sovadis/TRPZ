package ua.kpi.trpz.nantbuilder.service.validation;

import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Конкретний обробник (ConcreteHandler): перевіряє властивості — порожні та повторювані назви,
 * порожні значення.
 */
public class PropertyValidator extends ValidationHandler {

    @Override
    protected boolean check(BuildScript script, List<ValidationIssue> issues) {
        Set<String> names = new HashSet<>();
        Set<String> reportedDuplicates = new HashSet<>();
        for (ScriptProperty property : script.getProperties()) {
            String name = property.getName();
            if (name == null || name.isBlank()) {
                issues.add(ValidationIssue.error("<property>", "Назва властивості не може бути порожньою"));
                continue;
            }
            String element = "<property> " + name;
            if (!names.add(name)) {
                if (reportedDuplicates.add(name)) {
                    issues.add(ValidationIssue.error(element, "Властивість «" + name + "» оголошено двічі"));
                }
                continue;
            }
            if (property.getValue() == null || property.getValue().isBlank()) {
                issues.add(ValidationIssue.warning(element, "Властивість «" + name + "» має порожнє значення"));
            }
        }
        return true;
    }
}
