package ua.kpi.trpz.nantbuilder.service.validation;

import ua.kpi.trpz.nantbuilder.model.BuildScript;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Збирає стандартний ланцюжок перевірки скрипта і запускає його.
 * Клієнтам не потрібно знати, які обробники є в ланцюжку і в якому порядку вони йдуть.
 */
public final class ScriptValidator {

    /** Перший обробник ланцюжка. */
    private final ValidationHandler chain;

    /** Створює валідатор, що запускає ланцюжок, який починається з заданого обробника. */
    public ScriptValidator(ValidationHandler first) {
        this.chain = Objects.requireNonNull(first);
    }

    /**
     * Стандартний ланцюжок: заголовок → властивості → назви цілей → залежності →
     * ціль за замовчуванням → атрибути завдань.
     */
    public static ScriptValidator standard() {
        ValidationHandler first = new ScriptHeaderValidator();
        first.setNext(new PropertyValidator())
                .setNext(new TargetNameValidator())
                .setNext(new DependencyValidator())
                .setNext(new DefaultTargetValidator())
                .setNext(new TaskAttributeValidator());
        return new ScriptValidator(first);
    }

    /** Перевіряє скрипт і повертає всі знайдені проблеми в порядку обробників (скрипт не змінюється). */
    public List<ValidationIssue> validate(BuildScript script) {
        List<ValidationIssue> issues = new ArrayList<>();
        chain.handle(script, issues);
        return List.copyOf(issues);
    }

    /** Чи є серед проблем хоча б одна помилка. */
    public static boolean hasErrors(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
    }
}
