package ua.kpi.trpz.nantbuilder.service.validation;

import ua.kpi.trpz.nantbuilder.model.BuildScript;

import java.util.List;

/**
 * Базовий обробник ланцюжка (Handler) шаблону Chain of Responsibility.
 * Кожен конкретний обробник перевіряє один аспект скрипта, додає знайдені проблеми
 * у спільний список і вирішує, чи передавати запит далі по ланцюжку.
 */
public abstract class ValidationHandler {

    private ValidationHandler next;

    /**
     * Встановлює наступний обробник ланцюжка.
     *
     * @return переданий обробник, щоб ланцюжок можна було будувати викликами {@code a.setNext(b).setNext(c)}
     */
    public ValidationHandler setNext(ValidationHandler next) {
        this.next = next;
        return next;
    }

    /**
     * Обробляє запит: виконує власну перевірку і, якщо вона дозволяє, передає запит наступному обробнику.
     */
    public final void handle(BuildScript script, List<ValidationIssue> issues) {
        if (check(script, issues) && next != null) {
            next.handle(script, issues);
        }
    }

    /**
     * Перевіряє свій аспект скрипта і додає знайдені проблеми в {@code issues}.
     *
     * @return {@code true}, щоб продовжити ланцюжок, {@code false}, щоб зупинити його
     */
    protected abstract boolean check(BuildScript script, List<ValidationIssue> issues);
}
