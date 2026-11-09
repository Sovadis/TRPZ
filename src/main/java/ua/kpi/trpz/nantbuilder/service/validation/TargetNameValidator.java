package ua.kpi.trpz.nantbuilder.service.validation;

import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.Target;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Конкретний обробник (ConcreteHandler): перевіряє, що назви цілей задані й унікальні.
 * Якщо знайдено хоча б одну помилку, ланцюжок зупиняється: перевіряти залежності
 * та ціль за замовчуванням без однозначних назв цілей немає сенсу.
 */
public class TargetNameValidator extends ValidationHandler {

    @Override
    protected boolean check(BuildScript script, List<ValidationIssue> issues) {
        int errors = 0;
        Set<String> names = new HashSet<>();
        Set<String> reportedDuplicates = new HashSet<>();
        for (Target target : script.getTargets()) {
            String name = target.getName();
            if (name == null || name.isBlank()) {
                issues.add(ValidationIssue.error("<target>", "Назва цілі не може бути порожньою"));
                errors++;
            } else if (!names.add(name) && reportedDuplicates.add(name)) {
                issues.add(ValidationIssue.error("<target> " + name, "Ціль «" + name + "» оголошено двічі"));
                errors++;
            }
        }
        return errors == 0;
    }
}
